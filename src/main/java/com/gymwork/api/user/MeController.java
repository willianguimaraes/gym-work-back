package com.gymwork.api.user;

import com.gymwork.api.auth.AuthResult;
import com.gymwork.api.auth.dto.AuthResponse;
import com.gymwork.api.config.OpenApiConfig;
import com.gymwork.api.security.CurrentUserId;
import com.gymwork.api.security.RefreshTokenCookie;
import com.gymwork.api.user.dto.ChangePasswordRequest;
import com.gymwork.api.user.dto.DeleteAccountRequest;
import com.gymwork.api.user.dto.ProfileResponse;
import com.gymwork.api.user.dto.SettingsResponse;
import com.gymwork.api.user.dto.UpdateProfileRequest;
import com.gymwork.api.user.dto.UpdateSettingsRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Conta do usuário autenticado. Não existe rota que receba o ID de outro usuário. */
@RestController
@RequestMapping("/api/me")
@Tag(name = "Minha conta")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class MeController {

    private final AccountService accountService;
    private final RefreshTokenCookie refreshCookie;

    public MeController(AccountService accountService, RefreshTokenCookie refreshCookie) {
        this.accountService = accountService;
        this.refreshCookie = refreshCookie;
    }

    @GetMapping
    @Operation(summary = "Perfil do usuário autenticado")
    public ProfileResponse getProfile(@CurrentUserId UUID userId) {
        return accountService.getProfile(userId);
    }

    @PutMapping
    @Operation(summary = "Atualiza nome, altura e data de nascimento")
    public ProfileResponse updateProfile(@CurrentUserId UUID userId, @Valid @RequestBody UpdateProfileRequest request) {
        return accountService.updateProfile(userId, request);
    }

    @GetMapping("/settings")
    @Operation(summary = "Preferências: unidades, tema, descanso padrão, som e fuso horário")
    public SettingsResponse getSettings(@CurrentUserId UUID userId) {
        return accountService.getSettings(userId);
    }

    @PutMapping("/settings")
    @Operation(summary = "Atualiza as preferências")
    public SettingsResponse updateSettings(
            @CurrentUserId UUID userId, @Valid @RequestBody UpdateSettingsRequest request) {
        return accountService.updateSettings(userId, request);
    }

    @PutMapping("/password")
    @Operation(summary = "Troca a senha; encerra as outras sessões e devolve uma nova sessão")
    public ResponseEntity<AuthResponse> changePassword(
            @CurrentUserId UUID userId,
            @Valid @RequestBody ChangePasswordRequest request,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        AuthResult result = accountService.changePassword(userId, request, userAgent);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.create(result.refreshToken()).toString())
                .body(result.response());
    }

    @DeleteMapping
    @Operation(summary = "Exclui a conta e todos os dados (exige a senha)")
    public ResponseEntity<Void> deleteAccount(
            @CurrentUserId UUID userId, @Valid @RequestBody DeleteAccountRequest request) {
        accountService.deleteAccount(userId, request.password());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.clear().toString())
                .build();
    }
}
