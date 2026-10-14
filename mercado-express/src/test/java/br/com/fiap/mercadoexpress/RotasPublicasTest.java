package br.com.fiap.mercadoexpress;

import br.com.fiap.mercadoexpress.support.TesteIntegracao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@DisplayName("Rotas públicas e páginas base")
class RotasPublicasTest extends TesteIntegracao {

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("Landing page é a index.html e é pública")
    void landingPageIndexPublica() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(content().string(containsString("Mercado")))
                .andExpect(content().string(containsString("Criar conta")));
    }

    @Test
    @DisplayName("Página inicial e tela de login mostram as contas de avaliação dos dois perfis")
    void credenciaisVisiveis() throws Exception {
        for (String rota : new String[]{"/", "/login"}) {
            mvc.perform(get(rota))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Acesso para avaliação")))
                    .andExpect(content().string(containsString(ADMIN_EMAIL)))
                    .andExpect(content().string(containsString(ADMIN_SENHA)))
                    .andExpect(content().string(containsString(CLIENTE_EMAIL)))
                    .andExpect(content().string(containsString(CLIENTE_SENHA)));
        }
        mvc.perform(get("/login"))
                .andExpect(content().string(containsString("data-preencher-email=\"" + ADMIN_EMAIL + "\"")));
    }

    @Test
    @DisplayName("A landing page também abre pelo endereço /index.html, sem login")
    void landingPagePorIndexHtml() throws Exception {
        mvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(content().string(containsString("Criar conta")));
    }

    @Test
    @DisplayName("Tela de login é a personalizada (não a padrão do Spring Security)")
    void loginPersonalizado() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"))
                .andExpect(content().string(containsString("Cadastre-se (Sign Up)")))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                // texto da tela padrão do Spring Security
                .andExpect(content().string(not(containsString("Please sign in"))));
    }

    @Test
    @DisplayName("Tela de Sign Up é pública")
    void cadastroPublico() throws Exception {
        mvc.perform(get("/cadastro"))
                .andExpect(status().isOk())
                .andExpect(view().name("cadastro"));
    }

    @Test
    @DisplayName("Arquivos estáticos são públicos")
    void estaticosPublicos() throws Exception {
        mvc.perform(get("/css/style.css")).andExpect(status().isOk());
        mvc.perform(get("/js/app.js")).andExpect(status().isOk());
        mvc.perform(get("/img/logo.svg")).andExpect(status().isOk());
        mvc.perform(get("/favicon.svg")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Rotas restritas redirecionam o visitante para o login")
    void restritasExigemLogin() throws Exception {
        mvc.perform(get("/produtos")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/produtos/1")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/admin/painel")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/admin/usuarios")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("Cabeçalhos de segurança estão presentes")
    void cabecalhosDeSeguranca() throws Exception {
        mvc.perform(get("/"))
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'self'")))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }
}
