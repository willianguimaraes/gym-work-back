package com.gymwork.api.auth;

import com.gymwork.api.common.exception.TooManyAttemptsException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Limite de tentativas nos endpoints públicos de autenticação, em memória (uma instância da API).
 * Por conta e IP e, mais folgado, só por IP:
 *
 * <ul>
 *   <li>login: {@value #LOGIN_PER_ACCOUNT} senhas erradas por conta e IP, {@value #LOGIN_PER_IP} por IP,
 *       a cada 15 minutos; login certo zera a contagem da conta;</li>
 *   <li>recuperação de senha: {@value #RESET_PER_ACCOUNT} pedidos por e-mail e IP, {@value #RESET_PER_IP} por IP;</li>
 *   <li>cadastro: {@value #REGISTER_PER_IP} por IP a cada hora.</li>
 * </ul>
 *
 * Com várias instâncias, trocar por um armazenamento compartilhado (Redis, por exemplo).
 */
@Component
public class AuthRateLimiter {

    static final int LOGIN_PER_ACCOUNT = 10;
    static final int LOGIN_PER_IP = 50;
    static final int RESET_PER_ACCOUNT = 5;
    static final int RESET_PER_IP = 20;
    static final int REGISTER_PER_IP = 30;
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final Duration REGISTER_WINDOW = Duration.ofHours(1);
    private static final int CLEANUP_THRESHOLD = 10_000;

    private final Map<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();
    private final Clock clock;

    public AuthRateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** Antes do login: bloqueia se já houve senhas erradas demais. */
    public void checkLogin(String ip, String email) {
        check("login:" + ip + "|" + normalize(email), LOGIN_PER_ACCOUNT, WINDOW);
        check("login-ip:" + ip, LOGIN_PER_IP, WINDOW);
    }

    public void loginFailed(String ip, String email) {
        record("login:" + ip + "|" + normalize(email));
        record("login-ip:" + ip);
    }

    public void loginSucceeded(String ip, String email) {
        attempts.remove("login:" + ip + "|" + normalize(email));
    }

    /** Conta o pedido de recuperação (exista o e-mail ou não). */
    public void passwordReset(String ip, String email) {
        hit("reset:" + ip + "|" + normalize(email), RESET_PER_ACCOUNT, WINDOW);
        hit("reset-ip:" + ip, RESET_PER_IP, WINDOW);
    }

    public void register(String ip) {
        hit("register:" + ip, REGISTER_PER_IP, REGISTER_WINDOW);
    }

    private void hit(String key, int max, Duration window) {
        check(key, max, window);
        record(key);
    }

    private void check(String key, int max, Duration window) {
        Deque<Instant> times = attempts.get(key);
        if (times == null) {
            return;
        }
        Instant now = clock.instant();
        synchronized (times) {
            prune(times, now.minus(window));
            if (times.size() >= max) {
                Duration retryAfter = Duration.between(now, times.peekFirst().plus(window));
                throw new TooManyAttemptsException(retryAfter.isNegative() ? Duration.ZERO : retryAfter);
            }
        }
    }

    private void record(String key) {
        if (attempts.size() > CLEANUP_THRESHOLD) {
            cleanup();
        }
        Deque<Instant> times = attempts.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (times) {
            times.addLast(clock.instant());
        }
    }

    /** Remove contagens antigas para a memória não crescer sem limite. */
    private void cleanup() {
        Instant limit = clock.instant().minus(REGISTER_WINDOW);
        attempts.entrySet().removeIf(entry -> {
            synchronized (entry.getValue()) {
                prune(entry.getValue(), limit);
                return entry.getValue().isEmpty();
            }
        });
    }

    private static void prune(Deque<Instant> times, Instant limit) {
        while (!times.isEmpty() && times.peekFirst().isBefore(limit)) {
            times.removeFirst();
        }
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
