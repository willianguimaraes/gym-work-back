package com.gymwork.api.auth;

import com.gymwork.api.auth.dto.AuthResponse;
import com.gymwork.api.auth.dto.ForgotPasswordRequest;
import com.gymwork.api.auth.dto.LoginRequest;
import com.gymwork.api.auth.dto.RegisterRequest;
import com.gymwork.api.auth.dto.ResetPasswordRequest;
import com.gymwork.api.common.exception.GlobalExceptionHandler;
import com.gymwork.api.common.exception.InvalidRefreshTokenException;
import com.gymwork.api.security.RefreshTokenCookie;
import com.gymwork.api.common.exception.InvalidCredentialsException;
import jakarta.servlet.http.HttpServletRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticação")
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final RefreshTokenCookie refreshCookie;
    private final AuthRateLimiter rateLimiter;

    public AuthController(
            AuthService authService,
            PasswordResetService passwordResetService,
            RefreshTokenCookie refreshCookie,
            AuthRateLimiter rateLimiter) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
        this.refreshCookie = refreshCookie;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/register")
    @Operation(summary = "Cria uma conta e inicia a sessão")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent,
            HttpServletRequest http) {
        rateLimiter.register(http.getRemoteAddr());
        return withSession(HttpStatus.CREATED, authService.register(request, userAgent));
    }

    @PostMapping("/login")
    @Operation(summary = "Autentica e inicia a sessão")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent,
            HttpServletRequest http) {
        String ip = http.getRemoteAddr();
        rateLimiter.checkLogin(ip, request.email());
        AuthResult result;
        try {
            result = authService.login(request, userAgent);
        } catch (InvalidCredentialsException e) {
            rateLimiter.loginFailed(ip, request.email());
            throw e;
        }
        rateLimiter.loginSucceeded(ip, request.email());
        return withSession(HttpStatus.OK, result);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Renova o token de acesso usando o cookie de sessão (rotaciona o cookie)")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = RefreshTokenCookie.NAME, required = false) String refreshToken,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new InvalidRefreshTokenException();
        }
        return withSession(HttpStatus.OK, authService.refresh(refreshToken, userAgent));
    }

    @PostMapping("/logout")
    @Operation(summary = "Encerra a sessão atual")
    public ResponseEntity<Void> logout(
            @CookieValue(name = RefreshTokenCookie.NAME, required = false) String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            authService.logout(refreshToken);
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.clear().toString())
                .build();
    }

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Envia o link de redefinição de senha (sempre 204, exista o e-mail ou não)")
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest request, HttpServletRequest http) {
        rateLimiter.passwordReset(http.getRemoteAddr(), request.email());
        passwordResetService.requestReset(request.email());
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Define uma nova senha a partir do link recebido por e-mail")
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.token(), request.newPassword());
    }

    /** Sessão inválida: responde 401 e apaga o cookie do navegador. */
    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ProblemDetail> handleInvalidRefresh(InvalidRefreshTokenException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.SET_COOKIE, refreshCookie.clear().toString())
                .body(GlobalExceptionHandler.problem(HttpStatus.UNAUTHORIZED, ex.getMessage()));
    }

    private ResponseEntity<AuthResponse> withSession(HttpStatus status, AuthResult result) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, refreshCookie.create(result.refreshToken()).toString())
                .body(result.response());
    }
}
