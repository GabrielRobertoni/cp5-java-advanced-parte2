package br.com.fiap.mercadoexpress;

import br.com.fiap.mercadoexpress.mensageria.Filas;
import br.com.fiap.mercadoexpress.mensageria.dto.PagamentoRequest;
import br.com.fiap.mercadoexpress.model.CanalVenda;
import br.com.fiap.mercadoexpress.model.FormaPagamento;
import br.com.fiap.mercadoexpress.model.Pedido;
import br.com.fiap.mercadoexpress.model.Produto;
import br.com.fiap.mercadoexpress.model.StatusEmail;
import br.com.fiap.mercadoexpress.model.StatusPedido;
import br.com.fiap.mercadoexpress.repository.PedidoRepository;
import br.com.fiap.mercadoexpress.repository.ProdutoRepository;
import br.com.fiap.mercadoexpress.support.DadosTeste;
import br.com.fiap.mercadoexpress.support.TesteIntegracao;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Carrinho (sessão) e finalização da compra pela interface web: o pedido é gravado,
 * o estoque é reservado e o pagamento é publicado no RabbitMQ para o Banco Tranquilo.
 */
@WithUserDetails(TesteIntegracao.CLIENTE_EMAIL)
@DisplayName("Carrinho e compra pelo site")
class CarrinhoECompraTest extends TesteIntegracao {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private DadosTeste dados;
    @Autowired
    private PedidoRepository pedidoRepository;
    @Autowired
    private ProdutoRepository produtoRepository;

    private final MockHttpSession sessao = new MockHttpSession();

    @AfterEach
    void limpar() {
        dados.limpar();
    }

    private void adicionar(Produto produto, int quantidade) throws Exception {
        mvc.perform(post("/carrinho/itens").session(sessao).with(csrf())
                        .param("produtoId", produto.getId().toString())
                        .param("quantidade", String.valueOf(quantidade))
                        .param("voltarPara", "/produtos"))
                .andExpect(redirectedUrl("/produtos"))
                .andExpect(flash().attribute("sucesso", containsString("adicionado ao carrinho")));
    }

    @Test
    @DisplayName("Adiciona produtos, mostra o total e o contador no cabeçalho")
    void adicionarEVerCarrinho() throws Exception {
        Produto uva = dados.produto("Uva Teste", "Hortifruti", "10.00", "6.00", 20);
        Produto sabao = dados.produto("Sabão Teste", "Limpeza", "4.50", "2.00", 20);
        adicionar(uva, 2);
        adicionar(sabao, 1);
        adicionar(uva, 1); // soma com o que já estava no carrinho

        mvc.perform(get("/carrinho").session(sessao))
                .andExpect(status().isOk())
                .andExpect(view().name("carrinho/ver"))
                .andExpect(content().string(containsString("Uva Teste")))
                .andExpect(content().string(containsString("Total (4 itens)")))
                .andExpect(content().string(containsString("R$ 34,50")))     // 3 x 10,00 + 4,50
                .andExpect(content().string(containsString("class=\"contador\">4<")));
    }

    @Test
    @DisplayName("Altera a quantidade e remove item do carrinho")
    void alterarERemover() throws Exception {
        Produto uva = dados.produto("Uva Teste", "Hortifruti", "10.00", "6.00", 20);
        Produto sabao = dados.produto("Sabão Teste", "Limpeza", "4.50", "2.00", 20);
        adicionar(uva, 1);
        adicionar(sabao, 1);

        mvc.perform(post("/carrinho/itens/{id}", uva.getId()).session(sessao).with(csrf())
                        .param("_method", "put").param("quantidade", "5"))
                .andExpect(redirectedUrl("/carrinho"));
        mvc.perform(post("/carrinho/itens/{id}", sabao.getId()).session(sessao).with(csrf())
                        .param("_method", "delete"))
                .andExpect(redirectedUrl("/carrinho"));

        mvc.perform(get("/carrinho").session(sessao))
                .andExpect(content().string(containsString("Total (5 itens)")))
                .andExpect(content().string(containsString("R$ 50,00")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Sabão Teste"))));
    }

    @Test
    @DisplayName("Não deixa colocar no carrinho mais do que o estoque")
    void estoqueInsuficienteNoCarrinho() throws Exception {
        Produto raro = dados.produto("Raro Teste", "Mercearia", "9.90", "5.00", 2);
        mvc.perform(post("/carrinho/itens").session(sessao).with(csrf())
                        .param("produtoId", raro.getId().toString()).param("quantidade", "3"))
                .andExpect(redirectedUrl("/carrinho"))
                .andExpect(flash().attribute("erro", containsString("Estoque insuficiente")));
    }

    @Test
    @DisplayName("O parâmetro voltarPara não aceita endereço de outro site")
    void redirecionamentoSeguro() throws Exception {
        Produto uva = dados.produto("Uva Teste", "Hortifruti", "10.00", "6.00", 20);
        mvc.perform(post("/carrinho/itens").session(sessao).with(csrf())
                        .param("produtoId", uva.getId().toString())
                        .param("voltarPara", "https://site-malicioso.com"))
                .andExpect(redirectedUrl("/carrinho"));
    }

    @Test
    @DisplayName("Finalizar: cria o pedido, reserva o estoque, esvazia o carrinho e publica o pagamento no RabbitMQ")
    void finalizarCompra() throws Exception {
        Produto uva = dados.produto("Uva Teste", "Hortifruti", "10.00", "6.00", 20);
        Produto sabao = dados.produto("Sabão Teste", "Limpeza", "4.50", "2.00", 20);
        adicionar(uva, 3);
        adicionar(sabao, 2);

        MvcResult resultado = mvc.perform(post("/carrinho/finalizar").session(sessao).with(csrf())
                        .param("formaPagamento", "CARTAO_CREDITO"))
                .andExpect(redirectedUrlPattern("/pedidos/*"))
                .andExpect(flash().attribute("sucesso", containsString("Aguardando a confirmação")))
                .andReturn();
        Long id = Long.valueOf(resultado.getResponse().getRedirectedUrl().replace("/pedidos/", ""));

        Pedido pedido = pedidoRepository.buscarComItens(id).orElseThrow();
        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.AGUARDANDO_PAGAMENTO);
        assertThat(pedido.getCanal()).isEqualTo(CanalVenda.WEB);
        assertThat(pedido.getFormaPagamento()).isEqualTo(FormaPagamento.CARTAO_CREDITO);
        assertThat(pedido.getClienteEmail()).isEqualTo(CLIENTE_EMAIL);
        assertThat(pedido.getValorTotal()).isEqualByComparingTo("39.00");   // 30,00 + 9,00
        assertThat(pedido.getCustoTotal()).isEqualByComparingTo("22.00");   // 18,00 + 4,00
        assertThat(pedido.getItens()).hasSize(2);
        assertThat(pedido.getEmailStatus()).isEqualTo(StatusEmail.NAO_APLICAVEL);
        assertThat(pedido.getPagamentoEnviadoEm()).isNotNull();

        // estoque reservado
        assertThat(dados.estoque(uva.getId())).isEqualTo(17);
        assertThat(dados.estoque(sabao.getId())).isEqualTo(18);

        // mensagem publicada para o Banco Tranquilo
        ArgumentCaptor<Object> mensagem = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate).convertAndSend(eq(Filas.PAGAMENTO_REQUEST_EXCHANGE),
                eq(Filas.PAGAMENTO_REQUEST_ROUTING_KEY), mensagem.capture());
        PagamentoRequest request = (PagamentoRequest) mensagem.getValue();
        assertThat(request.numeroPedido()).isEqualTo(id);
        assertThat(request.transacaoId()).isEqualTo(pedido.getTransacaoId());
        assertThat(request.valor()).isEqualByComparingTo("39.00");
        assertThat(request.formaPagamento()).isEqualTo("CARTAO_CREDITO");
        assertThat(request.clienteEmail()).isEqualTo(CLIENTE_EMAIL);

        // carrinho vazio depois da compra
        mvc.perform(get("/carrinho").session(sessao))
                .andExpect(content().string(containsString("Seu carrinho está vazio")));

        // tela do pedido acompanha o status
        mvc.perform(get("/pedidos/{id}", id).session(sessao))
                .andExpect(status().isOk())
                .andExpect(view().name("pedidos/detalhe"))
                .andExpect(content().string(containsString("Aguardando pagamento")))
                .andExpect(content().string(containsString("data-acompanhar=\"/pedidos/" + id + "/status\"")));
        mvc.perform(get("/pedidos/{id}/status", id).session(sessao))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"status\":\"AGUARDANDO_PAGAMENTO\"")));
    }

    @Test
    @DisplayName("Finalizar com carrinho vazio mostra erro e não publica nada")
    void finalizarVazio() throws Exception {
        mvc.perform(post("/carrinho/finalizar").session(sessao).with(csrf()).param("formaPagamento", "PIX"))
                .andExpect(redirectedUrl("/carrinho"))
                .andExpect(flash().attribute("erro", containsString("vazio")));
        verify(rabbitTemplate, never()).convertAndSend(any(String.class), any(String.class), any(Object.class));
    }

    @Test
    @DisplayName("Se o estoque acabou antes de finalizar, a compra não é feita")
    void estoqueAcabouAntesDeFinalizar() throws Exception {
        Produto ultimo = dados.produto("Último Teste", "Mercearia", "3.00", "1.00", 2);
        adicionar(ultimo, 2);
        // outra pessoa comprou uma unidade nesse meio tempo
        Produto noBanco = produtoRepository.findById(ultimo.getId()).orElseThrow();
        noBanco.setQuantidadeEstoque(1);
        produtoRepository.save(noBanco);

        mvc.perform(get("/carrinho").session(sessao))
                .andExpect(content().string(containsString("só 1 em estoque")));
        mvc.perform(post("/carrinho/finalizar").session(sessao).with(csrf()).param("formaPagamento", "PIX"))
                .andExpect(redirectedUrl("/carrinho"))
                .andExpect(flash().attribute("erro", containsString("Estoque insuficiente")));
        assertThat(dados.estoque(ultimo.getId())).isEqualTo(1);
        assertThat(pedidoRepository.count()).isZero();
        verify(rabbitTemplate, never()).convertAndSend(any(String.class), any(String.class), any(Object.class));
    }

    @Test
    @DisplayName("Meus pedidos: cliente vê apenas os próprios pedidos")
    void meusPedidos() throws Exception {
        Produto uva = dados.produto("Uva Teste", "Hortifruti", "10.00", "6.00", 20);
        adicionar(uva, 1);
        mvc.perform(post("/carrinho/finalizar").session(sessao).with(csrf()).param("formaPagamento", "PIX"));

        mvc.perform(get("/pedidos").session(sessao))
                .andExpect(status().isOk())
                .andExpect(view().name("pedidos/lista"))
                .andExpect(content().string(containsString("R$ 10,00")));
    }
}
