package br.com.fiap.mercadoexpress;

import br.com.fiap.mercadoexpress.model.Produto;
import br.com.fiap.mercadoexpress.repository.ProdutoRepository;
import br.com.fiap.mercadoexpress.support.TesteIntegracao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * CRUD completo de produtos pelo ADMIN, passando pelo filtro de segurança real.
 * PUT e DELETE são enviados como o navegador envia: POST com o campo oculto _method.
 */
@Transactional
@WithUserDetails(TesteIntegracao.ADMIN_EMAIL)
@DisplayName("CRUD de produtos (ADMIN)")
class ProdutoCrudTest extends TesteIntegracao {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ProdutoRepository repository;

    private Produto salvarProduto(String nome) {
        return repository.save(new Produto(nome, "Fruta", "Hortifruti", "1 kg",
                new BigDecimal("4.50"), new BigDecimal("3.00"), 10, "Produto de teste"));
    }

    @Test
    @DisplayName("READ: lista os produtos da tabela TDS_Sec_MVC_TB_Mercado")
    void listar() throws Exception {
        mvc.perform(get("/produtos"))
                .andExpect(status().isOk())
                .andExpect(view().name("produtos/lista"))
                .andExpect(model().attribute("produtos", hasSize((int) repository.count())))
                .andExpect(content().string(containsString("Banana Prata")))
                // ADMIN vê os botões de alteração
                .andExpect(content().string(containsString("+ Novo produto")))
                .andExpect(content().string(containsString(">Editar<")))
                .andExpect(content().string(containsString(">Excluir<")));
    }

    @Test
    @DisplayName("READ: busca por nome/tipo e filtro por setor")
    void buscar() throws Exception {
        mvc.perform(get("/produtos").param("q", "BANANA"))
                .andExpect(model().attribute("produtos", hasSize(1)));
        mvc.perform(get("/produtos").param("q", "limpeza"))
                .andExpect(model().attribute("produtos", hasSize(2)));
        mvc.perform(get("/produtos").param("setor", "Frios"))
                .andExpect(model().attribute("produtos", hasSize(2)));
        mvc.perform(get("/produtos").param("q", "leite").param("setor", "Frios"))
                .andExpect(model().attribute("produtos", hasSize(1)));
        mvc.perform(get("/produtos").param("q", "produto-que-nao-existe"))
                .andExpect(model().attribute("produtos", hasSize(0)))
                .andExpect(content().string(containsString("Nenhum produto encontrado")));
    }

    @Test
    @DisplayName("READ: detalhe de um produto e 404 para id inexistente")
    void detalhe() throws Exception {
        Produto p = salvarProduto("Kiwi Importado");
        mvc.perform(get("/produtos/{id}", p.getId()))
                .andExpect(status().isOk())
                .andExpect(view().name("produtos/detalhe"))
                .andExpect(content().string(containsString("Kiwi Importado")))
                .andExpect(content().string(containsString("R$ 4,50")));

        mvc.perform(get("/produtos/{id}", 999_999))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("CREATE: cadastra produto válido e grava no PostgreSQL")
    void criar() throws Exception {
        long antes = repository.count();
        MvcResult resultado = mvc.perform(post("/produtos").with(csrf())
                        .param("nome", "  Uva Thompson ")
                        .param("tipo", "Fruta")
                        .param("setor", "Hortifruti")
                        .param("tamanho", "500 g")
                        .param("preco", "12.90")
                        .param("precoCusto", "8.40")
                        .param("quantidadeEstoque", "15")
                        .param("descricao", "Uva sem semente"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/produtos/*"))
                .andExpect(flash().attribute("sucesso", containsString("cadastrado com sucesso")))
                .andReturn();

        assertThat(repository.count()).isEqualTo(antes + 1);
        Long id = Long.valueOf(resultado.getResponse().getRedirectedUrl().replace("/produtos/", ""));
        Produto salvo = repository.findById(id).orElseThrow();
        assertThat(salvo.getNome()).isEqualTo("Uva Thompson");
        assertThat(salvo.getPreco()).isEqualByComparingTo("12.90");
        assertThat(salvo.getPrecoCusto()).isEqualByComparingTo("8.40");
        assertThat(salvo.getQuantidadeEstoque()).isEqualTo(15);
        assertThat(salvo.getCriadoEm()).isNotNull();
    }

    @Test
    @DisplayName("CREATE: dados inválidos voltam ao formulário com erros e nada é salvo")
    void criarInvalido() throws Exception {
        long antes = repository.count();
        mvc.perform(post("/produtos").with(csrf())
                        .param("nome", "")
                        .param("tipo", "")
                        .param("setor", "")
                        .param("preco", "0")
                        .param("quantidadeEstoque", "-3"))
                .andExpect(status().isOk())
                .andExpect(view().name("produtos/form"))
                .andExpect(model().attributeHasFieldErrors("produto",
                        "nome", "tipo", "setor", "preco", "precoCusto", "quantidadeEstoque"))
                .andExpect(content().string(containsString("Corrija os campos destacados")));
        assertThat(repository.count()).isEqualTo(antes);
    }

    @Test
    @DisplayName("CREATE: preço com texto inválido não gera erro 500")
    void criarPrecoTexto() throws Exception {
        mvc.perform(post("/produtos").with(csrf())
                        .param("nome", "Teste").param("tipo", "X").param("setor", "Y")
                        .param("preco", "abc").param("quantidadeEstoque", "1"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("produto", "preco"));
    }

    @Test
    @DisplayName("CREATE: o formulário não consegue sobrescrever um produto existente pelo campo id")
    void criarNaoSobrescreveId() throws Exception {
        Produto existente = salvarProduto("Original");
        long antes = repository.count();
        mvc.perform(post("/produtos").with(csrf())
                        .param("id", existente.getId().toString())
                        .param("nome", "Invasor").param("tipo", "X").param("setor", "Y")
                        .param("preco", "1.00").param("precoCusto", "0.50").param("quantidadeEstoque", "1"))
                .andExpect(status().is3xxRedirection());
        assertThat(repository.count()).isEqualTo(antes + 1);
        assertThat(repository.findById(existente.getId()).orElseThrow().getNome()).isEqualTo("Original");
    }

    @Test
    @DisplayName("UPDATE: formulário de edição carrega os dados e PUT atualiza o produto")
    void atualizar() throws Exception {
        Produto p = salvarProduto("Pera");

        mvc.perform(get("/produtos/{id}/editar", p.getId()))
                .andExpect(status().isOk())
                .andExpect(view().name("produtos/form"))
                .andExpect(content().string(containsString("value=\"Pera\"")))
                .andExpect(content().string(containsString("name=\"_method\" value=\"put\"")));

        mvc.perform(post("/produtos/{id}", p.getId()).with(csrf())
                        .param("_method", "put")
                        .param("nome", "Pera Williams")
                        .param("tipo", "Fruta")
                        .param("setor", "Hortifruti")
                        .param("tamanho", "1 kg")
                        .param("preco", "11.99")
                        .param("precoCusto", "7.00")
                        .param("quantidadeEstoque", "2")
                        .param("descricao", ""))
                .andExpect(redirectedUrl("/produtos/" + p.getId()))
                .andExpect(flash().attribute("sucesso", containsString("atualizado")));

        Produto atualizado = repository.findById(p.getId()).orElseThrow();
        assertThat(atualizado.getNome()).isEqualTo("Pera Williams");
        assertThat(atualizado.getPreco()).isEqualByComparingTo("11.99");
        assertThat(atualizado.getQuantidadeEstoque()).isEqualTo(2);
        assertThat(atualizado.getDescricao()).isNull();
        assertThat(atualizado.isEstoqueBaixo()).isTrue();
    }

    @Test
    @DisplayName("UPDATE: dados inválidos mantêm o formulário de edição")
    void atualizarInvalido() throws Exception {
        Produto p = salvarProduto("Caqui");
        mvc.perform(post("/produtos/{id}", p.getId()).with(csrf())
                        .param("_method", "put")
                        .param("nome", "").param("tipo", "Fruta").param("setor", "Hortifruti")
                        .param("preco", "-1").param("quantidadeEstoque", "1"))
                .andExpect(status().isOk())
                .andExpect(view().name("produtos/form"))
                .andExpect(model().attributeHasFieldErrors("produto", "nome", "preco"))
                .andExpect(content().string(containsString("name=\"_method\" value=\"put\"")));
        assertThat(repository.findById(p.getId()).orElseThrow().getNome()).isEqualTo("Caqui");
    }

    @Test
    @DisplayName("UPDATE: produto inexistente retorna 404")
    void atualizarInexistente() throws Exception {
        mvc.perform(post("/produtos/{id}", 999_999).with(csrf())
                        .param("_method", "put")
                        .param("nome", "X").param("tipo", "X").param("setor", "X")
                        .param("preco", "1").param("precoCusto", "0.5").param("quantidadeEstoque", "1"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE: exclui o produto pelo id")
    void excluir() throws Exception {
        Produto p = salvarProduto("Produto Temporário");
        mvc.perform(post("/produtos/{id}", p.getId()).with(csrf()).param("_method", "delete"))
                .andExpect(redirectedUrl("/produtos"))
                .andExpect(flash().attribute("sucesso", containsString("excluído")));
        assertThat(repository.existsById(p.getId())).isFalse();

        mvc.perform(post("/produtos/{id}", p.getId()).with(csrf()).param("_method", "delete"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Operações de escrita exigem token CSRF")
    void escritaSemCsrf() throws Exception {
        Produto p = salvarProduto("Protegido");
        mvc.perform(post("/produtos/{id}", p.getId()).param("_method", "delete"))
                .andExpect(status().isForbidden());
        assertThat(repository.existsById(p.getId())).isTrue();
    }

    @Test
    @DisplayName("CREATE: custo maior que o preço de venda é recusado")
    void criarCustoMaiorQuePreco() throws Exception {
        long antes = repository.count();
        mvc.perform(post("/produtos").with(csrf())
                        .param("nome", "Prejuízo").param("tipo", "X").param("setor", "Y")
                        .param("preco", "5.00").param("precoCusto", "9.00").param("quantidadeEstoque", "1"))
                .andExpect(status().isOk())
                .andExpect(view().name("produtos/form"))
                .andExpect(model().attributeHasFieldErrors("produto", "custoValido"))
                .andExpect(content().string(containsString("O custo não pode ser maior que o preço de venda")));
        assertThat(repository.count()).isEqualTo(antes);
    }

    @Test
    @DisplayName("Painel do admin mostra indicadores e produtos com estoque baixo")
    void painel() throws Exception {
        mvc.perform(get("/admin/painel"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/painel"))
                .andExpect(model().attributeExists("indicadores", "ultimosPedidos"))
                .andExpect(content().string(containsString("Dashboard da gerência")))
                .andExpect(content().string(containsString("Alface Crespa")))   // 4 unidades
                .andExpect(content().string(containsString("Queijo Muçarela Fatiado"))) // 3 unidades
                .andExpect(content().string(not(containsString("Arroz Branco Tipo 1</a>"))));
    }
}
