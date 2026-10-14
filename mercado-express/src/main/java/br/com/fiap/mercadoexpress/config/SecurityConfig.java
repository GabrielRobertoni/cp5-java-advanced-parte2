package br.com.fiap.mercadoexpress.config;

import br.com.fiap.mercadoexpress.security.LoginFalhaHandler;
import br.com.fiap.mercadoexpress.security.LoginSucessoHandler;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Regras de acesso do Mercado Express.
 *
 * <p><b>API REST (/api/**)</b> - usada no Postman/Insomnia: HTTP Basic (e-mail e senha), sem sessão
 * e sem CSRF (não usa cookie). Erros 401/403 em JSON.
 *
 * <p><b>Site (todo o resto)</b>:
 * Público ........ /, /login, /cadastro, arquivos estáticos e páginas de erro
 * ADMIN e CLIENTE  catálogo, carrinho e "meus pedidos"
 * Somente ADMIN .. criar/editar/excluir produtos e tudo em /admin/** (dashboard, compras, usuários)
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/**")
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().hasAnyRole("ADMIN", "CLIENTE")
            )
            .httpBasic(basic -> basic.authenticationEntryPoint((request, response, e) -> {
                response.setHeader("WWW-Authenticate", "Basic realm=\"Mercado Express\"");
                escreverErro(response, HttpServletResponse.SC_UNAUTHORIZED, "Não autenticado",
                        "Use autenticação Basic com o e-mail e a senha de um usuário cadastrado.");
            }))
            .exceptionHandling(ex -> ex.accessDeniedHandler((request, response, e) ->
                escreverErro(response, HttpServletResponse.SC_FORBIDDEN, "Acesso negado",
                        "Seu perfil não tem permissão para este recurso.")))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Sem sessão e sem cookie: CSRF não se aplica à API (autenticação vai em todo request)
            .csrf(csrf -> csrf.disable());
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain webFilterChain(HttpSecurity http,
                                              LoginSucessoHandler loginSucessoHandler,
                                              LoginFalhaHandler loginFalhaHandler) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                // Rotas públicas
                .requestMatchers("/", "/index.html", "/login", "/cadastro", "/acesso-negado", "/error",
                        "/css/**", "/js/**", "/img/**", "/favicon.ico", "/favicon.svg").permitAll()
                // Área da gerência
                .requestMatchers("/admin/**").hasRole("ADMIN")
                // Telas e operações de escrita de produtos: somente ADMIN
                .requestMatchers(HttpMethod.GET, "/produtos/novo", "/produtos/*/editar").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/produtos", "/produtos/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/produtos/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/produtos/**").hasRole("ADMIN")
                // Catálogo, carrinho e pedidos: qualquer usuário logado (ADMIN ou CLIENTE)
                .requestMatchers(HttpMethod.GET, "/produtos", "/produtos/*").hasAnyRole("ADMIN", "CLIENTE")
                .requestMatchers("/carrinho/**", "/pedidos/**").hasAnyRole("ADMIN", "CLIENTE")
                .anyRequest().authenticated()
            )
            // Tela de login própria (não usa a tela padrão do Spring Security)
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .usernameParameter("email")
                .passwordParameter("senha")
                .successHandler(loginSucessoHandler)
                .failureHandler(loginFalhaHandler)
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/?saiu")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            )
            .exceptionHandling(ex -> ex.accessDeniedPage("/acesso-negado"))
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                    "default-src 'self'; img-src 'self' data:; style-src 'self'; script-src 'self'; "
                    + "connect-src 'self'; form-action 'self'; frame-ancestors 'none'; base-uri 'self'"))
                .referrerPolicy(rp -> rp.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
            );
        // CSRF permanece HABILITADO no site: os formulários Thymeleaf enviam o token automaticamente.
        return http.build();
    }

    private static void escreverErro(HttpServletResponse response, int status, String titulo, String detalhe)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"status\":" + status + ",\"title\":\"" + titulo + "\",\"detail\":\"" + detalhe + "\"}");
    }
}
