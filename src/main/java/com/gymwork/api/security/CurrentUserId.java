package com.gymwork.api.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

/**
 * Injeta o ID ({@link java.util.UUID}) do usuário autenticado, lido do "sub" do JWT.
 *
 * <p>É a única fonte confiável do dono dos recursos: os serviços recebem esse ID e filtram toda
 * consulta por ele. Nunca aceite o ID do usuário vindo do corpo ou da URL da requisição.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@AuthenticationPrincipal(expression = "T(java.util.UUID).fromString(subject)")
public @interface CurrentUserId {
}
