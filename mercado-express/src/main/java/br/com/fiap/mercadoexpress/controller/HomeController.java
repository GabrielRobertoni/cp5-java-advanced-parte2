package br.com.fiap.mercadoexpress.controller;

import br.com.fiap.mercadoexpress.config.ContasDemonstracao;
import br.com.fiap.mercadoexpress.security.LoginSucessoHandler;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final ContasDemonstracao contasDemonstracao;

    /** Credenciais de avaliação exibidas na página inicial e abaixo do formulário de login. */
    @ModelAttribute("contasDemonstracao")
    public ContasDemonstracao contasDemonstracao() {
        return contasDemonstracao;
    }

    /** Landing page pública (templates/index.html), acessível por / e por /index.html. */
    @GetMapping({"/", "/index.html"})
    public String index() {
        return "index";
    }

    /** Tela de login personalizada. Quem já está logado vai direto para a sua área. */
    @GetMapping("/login")
    public String login(Authentication authentication) {
        if (estaLogado(authentication)) {
            return "redirect:" + LoginSucessoHandler.destinoPorPerfil(authentication);
        }
        return "login";
    }

    @GetMapping("/acesso-negado")
    public String acessoNegado(HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        return "acesso-negado";
    }

    static boolean estaLogado(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }
}
