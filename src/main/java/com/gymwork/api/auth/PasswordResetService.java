package com.gymwork.api.auth;

import com.gymwork.api.common.exception.InvalidFieldException;
import com.gymwork.api.mail.MailService;
import com.gymwork.api.security.AuthProperties;
import com.gymwork.api.security.OpaqueTokens;
import com.gymwork.api.security.RefreshTokenService;
import com.gymwork.api.user.Emails;
import com.gymwork.api.user.User;
import com.gymwork.api.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recuperação de senha por link de uso único enviado por e-mail.
 *
 * <p>O pedido nunca revela se o e-mail existe (mesma resposta e envio assíncrono). Redefinir a
 * senha encerra todas as sessões abertas.
 */
@Service
public class PasswordResetService {

    static final String INVALID_LINK = "Link inválido ou expirado. Solicite um novo.";

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final MailService mailService;
    private final AuthProperties properties;
    private final Clock clock;

    public PasswordResetService(
            UserRepository userRepository,
            PasswordResetTokenRepository tokenRepository,
            PasswordEncoder passwordEncoder,
            RefreshTokenService refreshTokenService,
            MailService mailService,
            AuthProperties properties,
            Clock clock) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.mailService = mailService;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public void requestReset(String email) {
        userRepository.findByEmail(Emails.normalize(email)).ifPresent(this::issueAndSend);
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        Instant now = clock.instant();
        PasswordResetToken token = tokenRepository.findByTokenHash(OpaqueTokens.hash(rawToken))
                .filter(t -> t.isUsable(now))
                .orElseThrow(() -> new InvalidFieldException("token", INVALID_LINK));
        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new InvalidFieldException("token", INVALID_LINK));

        user.changePasswordHash(passwordEncoder.encode(newPassword));
        token.markUsed(now);
        refreshTokenService.revokeAll(user.getId());
    }

    private void issueAndSend(User user) {
        Instant now = clock.instant();
        tokenRepository.invalidateAllByUserId(user.getId(), now);
        String raw = OpaqueTokens.generate();
        tokenRepository.save(new PasswordResetToken(
                user.getId(), OpaqueTokens.hash(raw), now.plus(properties.passwordResetTtl())));

        String link = properties.frontendUrl().replaceAll("/+$", "") + "/redefinir-senha?token=" + raw;
        long minutes = properties.passwordResetTtl().toMinutes();
        mailService.send(user.getEmail(), "Gym Work — redefinição de senha", """
                Olá, %s!

                Recebemos um pedido para redefinir a senha da sua conta no Gym Work.
                Para criar uma nova senha, acesse o link abaixo (válido por %d minutos):

                %s

                Se não foi você, ignore este e-mail: sua senha continua a mesma.
                """.formatted(user.getName(), minutes, link));
    }
}
