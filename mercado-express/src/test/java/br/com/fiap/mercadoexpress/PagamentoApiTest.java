package br.com.fiap.mercadoexpress;

import br.com.fiap.mercadoexpress.mensageria.Filas;
import br.com.fiap.mercadoexpress.mensageria.dto.PagamentoResposta;
import br.com.fiap.mercadoexpress.model.CanalVenda;
import br.com.fiap.mercadoexpress.model.Pedido;
import br.com.fiap.mercadoexpress.model.Produto;
import br.com.fiap.mercadoexpress.repository.PedidoRepository;
import br.com.fiap.mercadoexpress.repository.UsuarioRepository;
import br.com.fiap.mercadoexpress.service.PagamentoFacade;
import br.com.fiap.mercadoexpress.service.UsuarioService;
import br.com.fiap.mercadoexpress.model.Perfil;
import br.com.fiap.mercadoexpress.support.DadosTeste;
import br.com.fiap.mercadoexpress.support.TesteIntegracao;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** API REST usada no Postman/Insomnia: POST /api/pagamentos e consultas. */
@DisplayName("API REST de pagamentos (Postman)")
class PagamentoApiTest extends TesteIntegracao {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private DadosTeste dados;
    @Autowired
    private PedidoRepository pedidoRepository;
    @Autowired
    private PagamentoFacade pagamentoFacade;
    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @AfterEach
    void limpar() {
        dados.limpar();
        usuarioRepository.findByEmailIgnoreCase("outra@teste.com").ifPresent(usuarioRepository::delete);
    }

    private static String compra(long produtoId, int quantidade, String forma) {
        return """
                {"formaPagamento":"%s","itens":[{"produtoId":%d,"quantidade":%d}]}""".formatted(forma, produtoId, quantidade);
    }

    @Test
    @DisplayName("POST /api/pagamentos com Basic Auth: 202 Accepted, pedido aguardando e mensagem publicada")
    void comprarPelaApi() throws Exception {
        Produto leite = dados.produto("Leite API", "Frios", "5.25", "3.50", 30);

        MvcResult resultado = mvc.perform(post("/api/pagamentos").with(httpBasic(CLIENTE_EMAIL, CLIENTE_SENHA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(compra(leite.getId(), 4, "PIX")))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", containsString("/api/pedidos/")))
                .andExpect(jsonPath("$.mensagem").value("Pagamento aguardando confirmação do Banco Tranquilo..."))
                .andExpect(jsonPath("$.status").value("AGUARDANDO_PAGAMENTO"))
                .andExpect(jsonPath("$.valorTotal").value(21.00))
                .andExpect(jsonPath("$.formaPagamento").value("PIX"))
                .andExpect(jsonPath("$.itens[0].produto").value("Leite API"))
                .andExpect(jsonPath("$.transacaoId").isNotEmpty())
                .andReturn();

        Long numero = Long.valueOf(resultado.getResponse().getHeader("Location").replace("/api/pedidos/", ""));
        Pedido pedido = pedidoRepository.findById(numero).orElseThrow();
        assertThat(pedido.getCanal()).isEqualTo(CanalVenda.API);
        assertThat(dados.estoque(leite.getId())).isEqualTo(26);
        verify(rabbitTemplate).convertAndSend(eq(Filas.PAGAMENTO_REQUEST_EXCHANGE),
                eq(Filas.PAGAMENTO_REQUEST_ROUTING_KEY), any(Object.class));

        // consulta do status pela API, antes e depois da resposta do banco
        mvc.perform(get("/api/pedidos/{n}", numero).with(httpBasic(CLIENTE_EMAIL, CLIENTE_SENHA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AGUARDANDO_PAGAMENTO"))
                .andExpect(jsonPath("$.finalizado").value(false));
        pagamentoFacade.sucessoPagamento(new PagamentoResposta(pedido.getTransacaoId(), numero, "APROVADO",
                pedido.getValorTotal(), "TQ-654321", null, LocalDateTime.now()));
        mvc.perform(get("/api/pedidos/{n}", numero).with(httpBasic(CLIENTE_EMAIL, CLIENTE_SENHA)))
                .andExpect(jsonPath("$.status").value("PAGO"))
                .andExpect(jsonPath("$.finalizado").value(true))
                .andExpect(jsonPath("$.codigoAutorizacao").value("TQ-654321"))
                .andExpect(jsonPath("$.emailStatus").value("SOLICITADO"));
        mvc.perform(get("/api/pedidos").with(httpBasic(CLIENTE_EMAIL, CLIENTE_SENHA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].numeroPedido", hasItem(numero.intValue())));
    }

    @Test
    @DisplayName("Itens repetidos do mesmo produto são somados")
    void itensRepetidos() throws Exception {
        Produto leite = dados.produto("Leite API", "Frios", "5.00", "3.00", 30);
        mvc.perform(post("/api/pagamentos").with(httpBasic(CLIENTE_EMAIL, CLIENTE_SENHA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"formaPagamento":"CARTAO_DEBITO","itens":[
                                  {"produtoId":%d,"quantidade":1},{"produtoId":%d,"quantidade":2}]}""".formatted(leite.getId(), leite.getId())))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.valorTotal").value(15.00))
                .andExpect(jsonPath("$.itens.length()").value(1));
    }

    @Test
    @DisplayName("Sem autenticação: 401 em JSON (sem redirecionar para a tela de login)")
    void semAutenticacao() throws Exception {
        mvc.perform(post("/api/pagamentos").contentType(MediaType.APPLICATION_JSON).content(compra(1, 1, "PIX")))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", containsString("Basic")))
                .andExpect(jsonPath("$.title").value("Não autenticado"));
        mvc.perform(get("/api/produtos").with(httpBasic(CLIENTE_EMAIL, "senha-errada")))
                .andExpect(status().isUnauthorized());
        verify(rabbitTemplate, never()).convertAndSend(any(String.class), any(String.class), any(Object.class));
    }

    @Test
    @DisplayName("A API não usa sessão nem cookie (stateless) e não exige CSRF")
    void apiStateless() throws Exception {
        Produto leite = dados.produto("Leite API", "Frios", "5.00", "3.00", 30);
        MvcResult resultado = mvc.perform(post("/api/pagamentos").with(httpBasic(CLIENTE_EMAIL, CLIENTE_SENHA))
                        .contentType(MediaType.APPLICATION_JSON).content(compra(leite.getId(), 1, "PIX")))
                .andExpect(status().isAccepted())
                .andReturn();
        assertThat(resultado.getResponse().getCookie("JSESSIONID")).isNull();
    }

    @Test
    @DisplayName("Validação: JSON incompleto ou inválido devolve 400 com os erros")
    void validacao() throws Exception {
        mvc.perform(post("/api/pagamentos").with(httpBasic(CLIENTE_EMAIL, CLIENTE_SENHA))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"itens\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.length()").value(2));
        mvc.perform(post("/api/pagamentos").with(httpBasic(CLIENTE_EMAIL, CLIENTE_SENHA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"formaPagamento\":\"PIX\",\"itens\":[{\"produtoId\":1,\"quantidade\":0}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("quantidade mínima")));
        mvc.perform(post("/api/pagamentos").with(httpBasic(CLIENTE_EMAIL, CLIENTE_SENHA))
                        .contentType(MediaType.APPLICATION_JSON).content(compra(1, 1, "BITCOIN")))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("CARTAO_CREDITO")));
        mvc.perform(post("/api/pagamentos").with(httpBasic(CLIENTE_EMAIL, CLIENTE_SENHA))
                        .contentType(MediaType.TEXT_PLAIN).content("oi"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    @DisplayName("Produto inexistente ou sem estoque: 422 e nada é gravado")
    void regraDeNegocio() throws Exception {
        Produto raro = dados.produto("Raro API", "Mercearia", "9.00", "5.00", 1);
        mvc.perform(post("/api/pagamentos").with(httpBasic(CLIENTE_EMAIL, CLIENTE_SENHA))
                        .contentType(MediaType.APPLICATION_JSON).content(compra(raro.getId(), 2, "PIX")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail", containsString("Estoque insuficiente")));
        mvc.perform(post("/api/pagamentos").with(httpBasic(CLIENTE_EMAIL, CLIENTE_SENHA))
                        .contentType(MediaType.APPLICATION_JSON).content(compra(999_999, 1, "PIX")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail", containsString("não encontrado")));
        assertThat(dados.estoque(raro.getId())).isEqualTo(1);
        assertThat(pedidoRepository.count()).isZero();
    }

    @Test
    @DisplayName("Cliente não consulta pedido de outra pessoa; ADMIN consulta qualquer um")
    void privacidadeDosPedidos() throws Exception {
        Produto leite = dados.produto("Leite API", "Frios", "5.00", "3.00", 30);
        usuarioService.criarSeNaoExistir("Outra Pessoa", "outra@teste.com", "Outra@123", Perfil.CLIENTE);
        MvcResult resultado = mvc.perform(post("/api/pagamentos").with(httpBasic("outra@teste.com", "Outra@123"))
                        .contentType(MediaType.APPLICATION_JSON).content(compra(leite.getId(), 1, "PIX")))
                .andExpect(status().isAccepted()).andReturn();
        String location = resultado.getResponse().getHeader("Location");

        mvc.perform(get(location).with(httpBasic(CLIENTE_EMAIL, CLIENTE_SENHA))).andExpect(status().isNotFound());
        mvc.perform(get(location).with(httpBasic(ADMIN_EMAIL, ADMIN_SENHA))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Dashboard da API: só ADMIN (cliente recebe 403 em JSON)")
    void dashboardApi() throws Exception {
        mvc.perform(get("/api/admin/dashboard").with(httpBasic(CLIENTE_EMAIL, CLIENTE_SENHA)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Acesso negado"));
        mvc.perform(get("/api/admin/dashboard").with(httpBasic(ADMIN_EMAIL, ADMIN_SENHA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indicadores.faturamento").exists())
                .andExpect(jsonPath("$.vendasPorDia.length()").value(7));
    }

    @Test
    @DisplayName("GET /api/produtos lista o catálogo para montar o JSON da compra")
    void catalogo() throws Exception {
        mvc.perform(get("/api/produtos").with(httpBasic(CLIENTE_EMAIL, CLIENTE_SENHA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].nome", hasItem("Banana Prata")))
                .andExpect(jsonPath("$[0].precoCusto").doesNotExist());
    }
}
