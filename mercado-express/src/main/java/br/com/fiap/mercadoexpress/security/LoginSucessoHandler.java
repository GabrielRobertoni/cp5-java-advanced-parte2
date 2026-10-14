package br.com.fiap.mercadoexpress.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Após o login, cada tipo de usuário vai para a sua tela de acesso restrito:
 * ADMIN -> /admin/painel | CLIENTE -> /produtos
 */
@Component
public class LoginSucessoHandler extends SimpleUrlAuthenticationSuccessHandler {

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        clearAuthenticationAttributes(request);
        getRedirectStrategy().sendRedirect(request, response, destinoPorPerfil(authentication));
    }

    public static String destinoPorPerfil(Authentication authentication) {
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        return admin ? "/admin/painel" : "/produtos";
    }
}
