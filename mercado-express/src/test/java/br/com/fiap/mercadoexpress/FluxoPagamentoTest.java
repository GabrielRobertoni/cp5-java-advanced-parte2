package br.com.fiap.mercadoexpress;

import br.com.fiap.mercadoexpress.mensageria.Filas;
import br.com.fiap.mercadoexpress.mensageria.consumer.EmailResponseConsumidor;
import br.com.fiap.mercadoexpress.mensageria.consumer.PagamentoResponseErroConsumidor;
import br.com.fiap.mercadoexpress.mensageria.consumer.PagamentoResponseSucessoConsumidor;
import br.com.fiap.mercadoexpress.mensageria.dto.EmailRequest;
import br.com.fiap.mercadoexpress.mensageria.dto.EmailStatusMensagem;
import br.com.fiap.mercadoexpress.mensageria.dto.PagamentoResposta;
import br.com.fiap.mercadoexpress.model.CanalVenda;
import br.com.fiap.mercadoexpress.model.FormaPagamento;
import br.com.fiap.mercadoexpress.model.Pedido;
import br.com.fiap.mercadoexpress.model.Produto;
import br.com.fiap.mercadoexpress.model.StatusEmail;
import br.com.fiap.mercadoexpress.model.StatusPedido;
import br.com.fiap.mercadoexpress.repository.PedidoRepository;
import br.com.fiap.mercadoexpress.security.UsuarioDetailsService;
import br.com.fiap.mercadoexpress.security.UsuarioAutenticado;
import br.com.fiap.mercadoexpress.service.PagamentoFacade;
import br.com.fiap.mercadoexpress.service.PedidoService;
import br.com.fiap.mercadoexpress.service.ReenvioMensagensJob;
import br.com.fiap.mercadoexpress.support.DadosTeste;
import br.com.fiap.mercadoexpress.support.TesteIntegracao;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.net.ConnectException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Fluxo assíncrono completo do lado do Mercado Express: respostas do Banco Tranquilo
 * (sucesso/erro), pedido de e-mail, retorno do email-service e reenvio quando o broker cai.
 * Os consumidores são chamados diretamente, como o listener do RabbitMQ faria.
 */
@DisplayName("Fluxo de pagamento e e-mail (mensageria)")
class FluxoPagamentoTest extends TesteIntegracao {

    @Autowired
    private DadosTeste dados;
    @Autowired
    private PedidoService pedidoService;
    @Autowired
    private PedidoRepository pedidoRepository;
    @Autowired
    private UsuarioDetailsService usuarioDetailsService;
    @Autowired
    private PagamentoResponseSucessoConsumidor sucessoConsumidor;
    @Autowired
    private PagamentoResponseErroConsumidor erroConsumidor;
    @Autowired
    private EmailResponseConsumidor emailConsumidor;
    @Autowired
    private PagamentoFacade pagamentoFacade;
    @Autowired
    private ReenvioMensagensJob reenvioJob;
    @Autowired
    private JdbcTemplate jdbc;

    private UsuarioAutenticado cliente;
    private Produto uva;
    private Produto cafe;

    @BeforeEach
    void preparar() {
        cliente = (UsuarioAutenticado) usuarioDetailsService.loadUserByUsername(CLIENTE_EMAIL);
        uva = dados.produto("Uva Fluxo", "Hortifruti", "10.00", "6.00", 10);
        cafe = dados.produto("Café Fluxo", "Mercearia", "20.00", "12.00", 10);
    }

    @AfterEach
    void limpar() {
        dados.limpar();
    }

    private Pedido comprar() {
        Pedido pedido = pedidoService.criarPedido(cliente, Map.of(uva.getId(), 2, cafe.getId(), 1),
                FormaPagamento.PIX, CanalVenda.WEB);
        clearInvocations(rabbitTemplate);
        return pedido;
    }

    private Pedido recarregar(Pedido pedido) {
        return pedidoRepository.buscarComItens(pedido.getId()).orElseThrow();
    }

    private static PagamentoResposta aprovado(Pedido pedido) {
        return new PagamentoResposta(pedido.getTransacaoId(), pedido.getId(), "APROVADO", pedido.getValorTotal(),
                "TQ-123456", null, LocalDateTime.now());
    }

    private static PagamentoResposta recusado(Pedido pedido, String motivo) {
        return new PagamentoResposta(pedido.getTransacaoId(), pedido.getId(), "RECUSADO", pedido.getValorTotal(),
                null, motivo, LocalDateTime.now());
    }

    private EmailRequest emailPublicado() {
        ArgumentCaptor<Object> mensagem = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate).convertAndSend(eq(Filas.EMAIL_REQUEST_EXCHANGE), eq(Filas.EMAIL_REQUEST_ROUTING_KEY),
                mensagem.capture());
        return (EmailRequest) mensagem.getValue();
    }

    @Test
    @DisplayName("Pagamento APROVADO: pedido fica PAGO e o e-mail de confirmação é pedido ao email-service")
    void pagamentoAprovado() {
        Pedido pedido = comprar();

        sucessoConsumidor.receber(aprovado(pedido));

        Pedido pago = recarregar(pedido);
        assertThat(pago.getStatus()).isEqualTo(StatusPedido.PAGO);
        assertThat(pago.getCodigoAutorizacao()).isEqualTo("TQ-123456");
        assertThat(pago.getRespondidoEm()).isNotNull();
        assertThat(pago.getEmailStatus()).isEqualTo(StatusEmail.SOLICITADO);
        assertThat(pago.getEmailSolicitadoEm()).isNotNull();
        // estoque continua baixado
        assertThat(dados.estoque(uva.getId())).isEqualTo(8);

        EmailRequest email = emailPublicado();
        assertThat(email.emailTo()).isEqualTo(CLIENTE_EMAIL);
        assertThat(email.ownerRef()).isEqualTo("Mercado Express");
        assertThat(email.numeroPedido()).isEqualTo(pedido.getId());
        assertThat(email.solicitacaoId()).isEqualTo(pago.getEmailSolicitacaoId());
        assertThat(email.subject()).contains("Pagamento confirmado", "#" + pedido.getId());
        assertThat(email.text()).contains("Uva Fluxo", "TQ-123456", "40,00");
        assertThat(email.pedido().itens()).hasSize(2);
        assertThat(email.pedido().valorTotal()).isEqualByComparingTo("40.00");
        assertThat(email.pedido().transacaoId()).isEqualTo(pedido.getTransacaoId());
    }

    @Test
    @DisplayName("Pagamento RECUSADO: pedido fica RECUSADO, o estoque volta e nenhum e-mail é enviado")
    void pagamentoRecusado() {
        Pedido pedido = comprar();
        assertThat(dados.estoque(uva.getId())).isEqualTo(8);

        erroConsumidor.receber(recusado(pedido, "Saldo insuficiente"));

        Pedido recusadoNoBanco = recarregar(pedido);
        assertThat(recusadoNoBanco.getStatus()).isEqualTo(StatusPedido.RECUSADO);
        assertThat(recusadoNoBanco.getMotivoRecusa()).isEqualTo("Saldo insuficiente");
        assertThat(recusadoNoBanco.getEmailStatus()).isEqualTo(StatusEmail.NAO_APLICAVEL);
        assertThat(dados.estoque(uva.getId())).isEqualTo(10);
        assertThat(dados.estoque(cafe.getId())).isEqualTo(10);
        verify(rabbitTemplate, never()).convertAndSend(eq(Filas.EMAIL_REQUEST_EXCHANGE), any(String.class), any(Object.class));
    }

    @Test
    @DisplayName("Resposta repetida do banco é ignorada (processamento idempotente)")
    void respostaRepetida() {
        Pedido pedido = comprar();
        sucessoConsumidor.receber(aprovado(pedido));
        clearInvocations(rabbitTemplate);

        sucessoConsumidor.receber(aprovado(pedido));
        erroConsumidor.receber(recusado(pedido, "Atrasada"));

        Pedido depois = recarregar(pedido);
        assertThat(depois.getStatus()).isEqualTo(StatusPedido.PAGO);
        assertThat(dados.estoque(uva.getId())).isEqualTo(8); // não devolveu estoque
        verify(rabbitTemplate, never()).convertAndSend(any(String.class), any(String.class), any(Object.class));
    }

    @Test
    @DisplayName("Resposta com transação diferente ou de pedido inexistente é ignorada")
    void respostaInvalida() {
        Pedido pedido = comprar();
        sucessoConsumidor.receber(new PagamentoResposta(UUID.randomUUID(), pedido.getId(), "APROVADO",
                BigDecimal.TEN, "TQ-1", null, LocalDateTime.now()));
        sucessoConsumidor.receber(new PagamentoResposta(UUID.randomUUID(), 999_999L, "APROVADO",
                BigDecimal.TEN, "TQ-1", null, LocalDateTime.now()));
        // mensagem de teste publicada pelo painel do RabbitMQ, sem número de pedido
        erroConsumidor.receber(new PagamentoResposta(null, null, "RECUSADO", null, null, "teste", null));

        assertThat(recarregar(pedido).getStatus()).isEqualTo(StatusPedido.AGUARDANDO_PAGAMENTO);
    }

    @Test
    @DisplayName("Retorno do email-service: SENT marca ENVIADO e ERROR marca ERRO com o motivo")
    void retornoDoEmail() {
        Pedido pedido = comprar();
        sucessoConsumidor.receber(aprovado(pedido));
        Pedido pago = recarregar(pedido);
        UUID emailId = UUID.randomUUID();

        emailConsumidor.receber(new EmailStatusMensagem(pago.getEmailSolicitacaoId(), emailId, pedido.getId(),
                CLIENTE_EMAIL, "ERROR", "Falha no envio (SocketTimeoutException): Read timed out", LocalDateTime.now()));
        Pedido comErro = recarregar(pedido);
        assertThat(comErro.getEmailStatus()).isEqualTo(StatusEmail.ERRO);
        assertThat(comErro.getEmailErro()).contains("Read timed out");

        emailConsumidor.receber(new EmailStatusMensagem(pago.getEmailSolicitacaoId(), emailId, pedido.getId(),
                CLIENTE_EMAIL, "SENT", null, LocalDateTime.now()));
        Pedido enviado = recarregar(pedido);
        assertThat(enviado.getEmailStatus()).isEqualTo(StatusEmail.ENVIADO);
        assertThat(enviado.getEmailId()).isEqualTo(emailId);
        assertThat(enviado.getEmailErro()).isNull();

        // status de uma solicitação antiga não muda o pedido
        emailConsumidor.receber(new EmailStatusMensagem(UUID.randomUUID(), UUID.randomUUID(), pedido.getId(),
                CLIENTE_EMAIL, "ERROR", "antigo", LocalDateTime.now()));
        assertThat(recarregar(pedido).getEmailStatus()).isEqualTo(StatusEmail.ENVIADO);
    }

    @Test
    @DisplayName("Gerente reenvia o e-mail: nova solicitação é publicada")
    void reenviarEmail() {
        Pedido pedido = comprar();
        sucessoConsumidor.receber(aprovado(pedido));
        UUID primeira = recarregar(pedido).getEmailSolicitacaoId();
        clearInvocations(rabbitTemplate);

        pagamentoFacade.reenviarEmail(pedido.getId());

        EmailRequest email = emailPublicado();
        assertThat(email.solicitacaoId()).isNotEqualTo(primeira);
        assertThat(recarregar(pedido).getEmailStatus()).isEqualTo(StatusEmail.SOLICITADO);
    }

    @Test
    @DisplayName("RabbitMQ fora do ar: o pedido é criado e o pagamento é publicado depois pelo job de reenvio")
    void brokerForaDoAr() {
        doThrow(new AmqpConnectException(new ConnectException("Connection refused")))
                .when(rabbitTemplate).convertAndSend(eq(Filas.PAGAMENTO_REQUEST_EXCHANGE), any(String.class), any(Object.class));

        Pedido pedido = pedidoService.criarPedido(cliente, Map.of(uva.getId(), 1), FormaPagamento.PIX, CanalVenda.API);
        assertThat(recarregar(pedido).getPagamentoEnviadoEm()).isNull();
        assertThat(recarregar(pedido).getStatus()).isEqualTo(StatusPedido.AGUARDANDO_PAGAMENTO);

        // broker voltou; o job procura pedidos não publicados há mais de 20 s
        doNothing().when(rabbitTemplate).convertAndSend(any(String.class), any(String.class), any(Object.class));
        jdbc.update("UPDATE TDS_Pedidos_Mercado SET criado_em = criado_em - INTERVAL '1 minute' WHERE id = ?", pedido.getId());
        reenvioJob.reenviarPendentes();

        assertThat(recarregar(pedido).getPagamentoEnviadoEm()).isNotNull();
        verify(rabbitTemplate, times(2)).convertAndSend(eq(Filas.PAGAMENTO_REQUEST_EXCHANGE),
                eq(Filas.PAGAMENTO_REQUEST_ROUTING_KEY), any(Object.class));

        // rodar o job de novo não publica outra vez
        reenvioJob.reenviarPendentes();
        verify(rabbitTemplate, times(2)).convertAndSend(eq(Filas.PAGAMENTO_REQUEST_EXCHANGE),
                eq(Filas.PAGAMENTO_REQUEST_ROUTING_KEY), any(Object.class));
    }
}
