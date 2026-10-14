package br.com.fiap.mercadoexpress;

import br.com.fiap.mercadoexpress.repository.ProdutoRepository;
import br.com.fiap.mercadoexpress.support.TesteIntegracao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** O perfil CLIENTE só consulta; tudo que altera dados ou é de gestão é bloqueado (403). */
@Transactional
@WithUserDetails(TesteIntegracao.CLIENTE_EMAIL)
@DisplayName("Controle de acesso do CLIENTE")
class ControleDeAcessoClienteTest extends TesteIntegracao {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ProdutoRepository repository;

    @Test
    @DisplayName("Cliente consulta a lista e o detalhe, sem botões de alteração")
    void clienteConsulta() throws Exception {
        mvc.perform(get("/produtos"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Banana Prata")))
                .andExpect(content().string(containsString("Cliente Demonstração")))
                .andExpect(content().string(not(containsString(">Editar<"))))
                .andExpect(content().string(not(containsString(">Excluir<"))))
                .andExpect(content().string(not(containsString("+ Novo produto"))));

        mvc.perform(get("/produtos/1"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(">Excluir<"))));
    }

    @Test
    @DisplayName("Cliente não acessa telas de criação/edição (403)")
    void clienteSemTelasDeEscrita() throws Exception {
        mvc.perform(get("/produtos/novo"))
                .andExpect(status().isForbidden())
                .andExpect(forwardedUrl("/acesso-negado"));
        mvc.perform(get("/produtos/1/editar")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Cliente não cria, altera nem exclui produtos (403)")
    void clienteSemEscrita() throws Exception {
        long antes = repository.count();
        mvc.perform(post("/produtos").with(csrf())
                        .param("nome", "Invasão").param("tipo", "X").param("setor", "X")
                        .param("preco", "1").param("quantidadeEstoque", "1"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/produtos/1").with(csrf()).param("_method", "put")
                        .param("nome", "Alterado").param("tipo", "X").param("setor", "X")
                        .param("preco", "1").param("quantidadeEstoque", "1"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/produtos/1").with(csrf()).param("_method", "delete"))
                .andExpect(status().isForbidden());

        assertThat(repository.count()).isEqualTo(antes);
        assertThat(repository.findById(1L).orElseThrow().getNome()).isEqualTo("Banana Prata");
    }

    @Test
    @DisplayName("Cliente não acessa a área administrativa (403)")
    void clienteSemAdmin() throws Exception {
        mvc.perform(get("/admin/painel")).andExpect(status().isForbidden());
        mvc.perform(get("/admin/usuarios")).andExpect(status().isForbidden());
        mvc.perform(post("/admin/usuarios/1/perfil").with(csrf())
                        .param("_method", "put").param("perfil", "ADMIN"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("A página de acesso negado é exibida com status 403")
    void paginaAcessoNegado() throws Exception {
        mvc.perform(get("/acesso-negado"))
                .andExpect(status().isForbidden())
                .andExpect(content().string(containsString("Acesso negado")));
    }
}
