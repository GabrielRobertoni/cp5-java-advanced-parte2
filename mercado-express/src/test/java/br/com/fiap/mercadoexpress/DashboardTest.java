package br.com.fiap.mercadoexpress;

import br.com.fiap.mercadoexpress.mensageria.dto.EmailStatusMensagem;
import br.com.fiap.mercadoexpress.mensageria.dto.PagamentoResposta;
import br.com.fiap.mercadoexpress.model.CanalVenda;
import br.com.fiap.mercadoexpress.model.FormaPagamento;
import br.com.fiap.mercadoexpress.model.Pedido;
import br.com.fiap.mercadoexpress.model.Produto;
import br.com.fiap.mercadoexpress.repository.PedidoRepository;
import br.com.fiap.mercadoexpress.security.UsuarioAutenticado;
import br.com.fiap.mercadoexpress.security.UsuarioDetailsService;
import br.com.fiap.mercadoexpress.service.PagamentoFacade;
import br.com.fiap.mercadoexpress.service.PedidoService;
import br.com.fiap.mercadoexpress.support.DadosTeste;
import br.com.fiap.mercadoexpress.support.TesteIntegracao;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/** Dashboard da gerência: compras, vendas, lucro e estoque calculados a partir dos pedidos. */
@DisplayName("Dashboard da gerência")
class DashboardTest extends TesteIntegracao {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private DadosTeste dados;
    @Autowired
    private PedidoService pedidoService;
    @Autowired
    private PagamentoFacade pagamentoFacade;
    @Autowired
    private PedidoRepository pedidoRepository;
    @Autowired
    private UsuarioDetailsService usuarioDetailsService;

    private Pedido pago;
    private Pedido recusado;

    @BeforeEach
    void criarVendas() {
        UsuarioAutenticado cliente = (UsuarioAutenticado) usuarioDetailsService.loadUserByUsername(CLIENTE_EMAIL);
        Produto uva = dados.produto("Uva Dashboard", "Hortifruti", "10.00", "6.00", 50);
        Produto sabao = dados.produto("Sabão Dashboard", "Limpeza", "5.00", "2.00", 50);

        // pago: 3 uvas (30,00 / custo 18,00) + 2 sabões (10,00 / custo 4,00) = 40,00, lucro 18,00
        pago = pedidoService.criarPedido(cliente, Map.of(uva.getId(), 3, sabao.getId(), 2), FormaPagamento.PIX, CanalVenda.WEB);
        pagamentoFacade.sucessoPagamento(new PagamentoResposta(pago.getTransacaoId(), pago.getId(), "APROVADO",
                pago.getValorTotal(), "TQ-111111", null, LocalDateTime.now()));
        Pedido comEmail = pedidoRepository.findById(pago.getId()).orElseThrow();
        pagamentoFacade.atualizarStatusEmail(new EmailStatusMensagem(comEmail.getEmailSolicitacaoId(), UUID.randomUUID(),
                pago.getId(), CLIENTE_EMAIL, "SENT", null, LocalDateTime.now()));

        // recusado: não entra no faturamento
        recusado = pedidoService.criarPedido(cliente, Map.of(uva.getId(), 1), FormaPagamento.CARTAO_CREDITO, CanalVenda.API);
        pagamentoFacade.erroPagamento(new PagamentoResposta(recusado.getTransacaoId(), recusado.getId(), "RECUSADO",
                recusado.getValorTotal(), null, "Saldo insuficiente", LocalDateTime.now()));

        // aguardando
        pedidoService.criarPedido(cliente, Map.of(sabao.getId(), 1), FormaPagamento.PIX, CanalVenda.WEB);
    }

    @AfterEach
    void limpar() {
        dados.limpar();
    }

    @Test
    @WithUserDetails(ADMIN_EMAIL)
    @DisplayName("Indicadores: faturamento, lucro, margem, ticket médio, pedidos e e-mails")
    void indicadores() throws Exception {
        mvc.perform(get("/admin/painel/dados"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indicadores.faturamento").value(40.00))
                .andExpect(jsonPath("$.indicadores.lucro").value(18.00))
                .andExpect(jsonPath("$.indicadores.margemPercentual").value(45.0))
                .andExpect(jsonPath("$.indicadores.ticketMedio").value(40.00))
                .andExpect(jsonPath("$.indicadores.pedidosPagos").value(1))
                .andExpect(jsonPath("$.indicadores.pedidosRecusados").value(1))
                .andExpect(jsonPath("$.indicadores.pedidosAguardando").value(1))
                .andExpect(jsonPath("$.indicadores.unidadesVendidas").value(5))
                .andExpect(jsonPath("$.indicadores.emailsEnviados").value(1))
                .andExpect(jsonPath("$.pedidosPorStatus.Pago").value(1))
                .andExpect(jsonPath("$.pedidosPorStatus['Pagamento recusado']").value(1));
    }

    @Test
    @WithUserDetails(ADMIN_EMAIL)
    @DisplayName("Gráficos: vendas do dia, receita x lucro por setor, top produtos e estoque")
    void graficos() throws Exception {
        String hoje = String.format("%02d/%02d", LocalDate.now().getDayOfMonth(), LocalDate.now().getMonthValue());
        mvc.perform(get("/admin/painel/dados"))
                .andExpect(jsonPath("$.vendasPorDia.length()").value(7))
                .andExpect(jsonPath("$.vendasPorDia[6].rotulo").value(hoje))
                .andExpect(jsonPath("$.vendasPorDia[6].receita").value(40.00))
                .andExpect(jsonPath("$.vendasPorDia[6].lucro").value(18.00))
                .andExpect(jsonPath("$.vendasPorDia[0].receita").value(0))
                .andExpect(jsonPath("$.vendasPorSetor[?(@.rotulo == 'Hortifruti')].receita").value(30.00))
                .andExpect(jsonPath("$.vendasPorSetor[?(@.rotulo == 'Limpeza')].lucro").value(6.00))
                .andExpect(jsonPath("$.produtosMaisVendidos[0].rotulo").value("Uva Dashboard"))
                .andExpect(jsonPath("$.produtosMaisVendidos[0].quantidade").value(3))
                // estoque: uva 50 - 3 (pago) = 47; o recusado devolveu a unidade
                .andExpect(jsonPath("$.estoque[?(@.produto == 'Uva Dashboard')].quantidade").value(47))
                .andExpect(jsonPath("$.estoque[*].produto", hasItem("Sabão Dashboard")));
    }

    @Test
    @WithUserDetails(ADMIN_EMAIL)
    @DisplayName("Tela do dashboard e tela de compras realizadas")
    void telas() throws Exception {
        mvc.perform(get("/admin/painel"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/painel"))
                .andExpect(content().string(containsString("R$ 40,00")))
                .andExpect(content().string(containsString("R$ 18,00")))
                .andExpect(content().string(containsString("margem de 45,0%")))
                .andExpect(content().string(containsString("/js/vendor/chart.umd.min.js")));
        mvc.perform(get("/admin/pedidos"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/pedidos"))
                .andExpect(content().string(containsString("TQ-111111")))
                .andExpect(content().string(containsString("Saldo insuficiente")))
                .andExpect(content().string(containsString("Enviado")))
                .andExpect(content().string(containsString("Reenviar e-mail")));
    }

    @Test
    @WithUserDetails(CLIENTE_EMAIL)
    @DisplayName("Cliente não acessa o dashboard nem as compras de todos")
    void clienteSemAcesso() throws Exception {
        mvc.perform(get("/admin/painel/dados")).andExpect(status().isForbidden());
        mvc.perform(get("/admin/pedidos")).andExpect(status().isForbidden());
    }
}
