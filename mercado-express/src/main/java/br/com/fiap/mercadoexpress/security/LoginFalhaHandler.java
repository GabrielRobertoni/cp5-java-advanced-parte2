package br.com.fiap.mercadoexpress.security;

import br.com.fiap.mercadoexpress.service.UsuarioService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

/**
 * Requisito do CP5: "caso o usuário não possua um login, deve-se redirecioná-lo
 * para uma tela de Sign Up".
 * - E-mail não cadastrado -> /cadastro (com o e-mail já preenchido)
 * - E-mail cadastrado, senha errada -> /login?erro
 */
@Component
@RequiredArgsConstructor
public class LoginFalhaHandler implements AuthenticationFailureHandler {

    private final UsuarioService usuarioService;
    private final RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        String email = request.getParameter("email");
        if (email != null && !email.isBlank() && !usuarioService.emailCadastrado(email)) {
            String destino = UriComponentsBuilder.fromPath("/cadastro")
                    .queryParam("semConta")
                    .queryParam("email", email.trim())
                    .encode()
                    .toUriString();
            redirectStrategy.sendRedirect(request, response, destino);
            return;
        }
        redirectStrategy.sendRedirect(request, response, "/login?erro");
    }
}
