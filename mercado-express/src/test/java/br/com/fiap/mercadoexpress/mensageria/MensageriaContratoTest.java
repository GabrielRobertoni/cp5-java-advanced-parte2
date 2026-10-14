package br.com.fiap.mercadoexpress.mensageria;

import br.com.fiap.mercadoexpress.mensageria.dto.EmailStatusMensagem;
import br.com.fiap.mercadoexpress.mensageria.dto.PagamentoRequest;
import br.com.fiap.mercadoexpress.mensageria.dto.PagamentoResposta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.support.converter.SmartMessageConverter;
import org.springframework.core.ParameterizedTypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrato das mensagens JSON trocadas com o Banco Tranquilo e o email-service
 * e topologia declarada no RabbitMQ (mesmos nomes da aula).
 */
@DisplayName("Mensageria: contrato JSON e topologia do RabbitMQ")
class MensageriaContratoTest {

    private final MessageConverter converter = new RabbitMQConfig().jsonMessageConverter(JsonMapper.builder().build());

    private <T> T ler(String json, Class<T> tipo) {
        // mensagem publicada "na mão" pelo painel do RabbitMQ: sem content_type e sem cabeçalho de tipo
        Message mensagem = new Message(json.getBytes(StandardCharsets.UTF_8), new MessageProperties());
        return tipo.cast(((SmartMessageConverter) converter).fromMessage(mensagem, ParameterizedTypeReference.forType(tipo)));
    }

    @Test
    @DisplayName("PagamentoRequest vira JSON com os campos esperados pelo Banco Tranquilo")
    void pagamentoRequestEmJson() {
        UUID transacao = UUID.fromString("0b6f6a3e-3a52-4a52-9d6c-0b1c2d3e4f50");
        PagamentoRequest request = new PagamentoRequest(transacao, 171L, new BigDecimal("550.00"), "PIX",
                "Ana", "ana@email.com", "pedido #171", LocalDateTime.of(2026, 10, 1, 10, 30));

        Message mensagem = converter.toMessage(request, new MessageProperties());
        String json = new String(mensagem.getBody(), StandardCharsets.UTF_8);

        assertThat(mensagem.getMessageProperties().getContentType()).isEqualTo("application/json");
        assertThat(json).contains("\"transacaoId\":\"0b6f6a3e-3a52-4a52-9d6c-0b1c2d3e4f50\"",
                "\"numeroPedido\":171", "\"valor\":550.00", "\"formaPagamento\":\"PIX\"",
                "\"clienteEmail\":\"ana@email.com\"", "\"solicitadoEm\":\"2026-10-01T10:30:00\"");
    }

    @Test
    @DisplayName("Lê a resposta do banco publicada pelo painel do RabbitMQ (sem content_type e com campos extras)")
    void respostaDoBancoSemCabecalhos() {
        PagamentoResposta resposta = ler("""
                {"transacaoId":"0b6f6a3e-3a52-4a52-9d6c-0b1c2d3e4f50","numeroPedido":"171","status":"APROVADO",
                 "valor":550,"codigoAutorizacao":"TQ-123456","campoDesconhecido":true}""", PagamentoResposta.class);

        assertThat(resposta.numeroPedido()).isEqualTo(171L);
        assertThat(resposta.status()).isEqualTo("APROVADO");
        assertThat(resposta.valor()).isEqualByComparingTo("550");
        assertThat(resposta.codigoAutorizacao()).isEqualTo("TQ-123456");
    }

    @Test
    @DisplayName("Lê o retorno do email-service (statusEmail SENT/ERROR)")
    void retornoDoEmail() {
        EmailStatusMensagem status = ler("""
                {"solicitacaoId":"0b6f6a3e-3a52-4a52-9d6c-0b1c2d3e4f50","emailId":"1b6f6a3e-3a52-4a52-9d6c-0b1c2d3e4f50",
                 "numeroPedido":7,"emailTo":"ana@email.com","statusEmail":"SENT","atualizadoEm":"2026-10-01T10:31:00"}""",
                EmailStatusMensagem.class);
        assertThat(status.numeroPedido()).isEqualTo(7L);
        assertThat(status.statusEmail()).isEqualTo("SENT");
        assertThat(status.atualizadoEm()).isEqualTo(LocalDateTime.of(2026, 10, 1, 10, 31));
    }

    @Test
    @DisplayName("Topologia: 5 exchanges direct + dead letter, 6 filas duráveis e os bindings pelas routing keys")
    void topologia() {
        Declarables declarables = new RabbitMQConfig().topologiaMercadoExpress();

        assertThat(declarables.getDeclarablesByType(Exchange.class)).extracting(Exchange::getName)
                .containsExactlyInAnyOrder("pagamento-request-exchange", "pagamento-response-sucesso-exchange",
                        "pagamento-response-erro-exchange", "email-request-exchange", "email-response-exchange",
                        "mercado-dead-letter-exchange");
        assertThat(declarables.getDeclarablesByType(Queue.class))
                .allMatch(Queue::isDurable)
                .extracting(Queue::getName)
                .containsExactlyInAnyOrder("pagamento-request-queue", "pagamento-response-sucesso-queue",
                        "pagamento-response-erro-queue", "email-request-queue", "email-response-queue",
                        "mercado-dead-letter-queue");
        assertThat(declarables.getDeclarablesByType(Queue.class))
                .filteredOn(q -> !q.getName().equals("mercado-dead-letter-queue"))
                .allMatch(q -> "mercado-dead-letter-exchange".equals(q.getArguments().get("x-dead-letter-exchange")));
        assertThat(declarables.getDeclarablesByType(Binding.class))
                .extracting(b -> b.getExchange() + " -> " + b.getRoutingKey() + " -> " + b.getDestination())
                .contains("pagamento-request-exchange -> pagamento-request-rout-key -> pagamento-request-queue",
                        "pagamento-response-sucesso-exchange -> pagamento-response-sucesso-rout-key -> pagamento-response-sucesso-queue",
                        "pagamento-response-erro-exchange -> pagamento-response-erro-rout-key -> pagamento-response-erro-queue",
                        "email-request-exchange -> email-request-rout-key -> email-request-queue",
                        "email-response-exchange -> email-response-rout-key -> email-response-queue");
    }
}
