package br.com.fiap.bancotranquilo;

import br.com.fiap.bancotranquilo.mensageria.Filas;
import br.com.fiap.bancotranquilo.mensageria.consumer.PagamentoRequestConsumidor;
import br.com.fiap.bancotranquilo.mensageria.dto.PagamentoRequest;
import br.com.fiap.bancotranquilo.mensageria.dto.PagamentoResposta;
import br.com.fiap.bancotranquilo.model.StatusTransacao;
import br.com.fiap.bancotranquilo.model.TransacaoBancaria;
import br.com.fiap.bancotranquilo.repository.TransacaoRepository;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.support.converter.SmartMessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Banco Tranquilo de ponta a ponta (sem broker real): recebe o pedido de pagamento, registra a
 * transação no PostgreSQL (schema banco) e responde na fila de sucesso ou de erro.
 */
@SpringBootTest(properties = {
        "spring.rabbitmq.addresses=amqp://guest:guest@127.0.0.1:1",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "banco.tempo-processamento=0s",
        "banco.limite-por-transacao=500.00",
        "banco.taxa-aprovacao=1.0"
})
@DisplayName("Banco Tranquilo: processamento das transações")
class BancoTranquiloIntegracaoTest {

    private static final EmbeddedPostgres POSTGRES = iniciarPostgres();

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private PagamentoRequestConsumidor consumidor;
    @Autowired
    private TransacaoRepository repository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private MessageConverter converter;

    private static EmbeddedPostgres iniciarPostgres() {
        try {
            EmbeddedPostgres postgres = EmbeddedPostgres.builder().start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    postgres.close();
                } catch (IOException ignored) {
                    // encerrando a JVM
                }
            }));
            return postgres;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void banco(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "");
    }

    @AfterEach
    void limpar() {
        repository.deleteAll();
    }

    private static PagamentoRequest pedido(long numero, String valor) {
        return new PagamentoRequest(UUID.randomUUID(), numero, new BigDecimal(valor), "PIX", "Ana",
                "ana@email.com", "Mercado Express - pedido #" + numero, LocalDateTime.now());
    }

    private PagamentoResposta respostaPublicada(String exchange, String routingKey) {
        ArgumentCaptor<Object> mensagem = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate).convertAndSend(eq(exchange), eq(routingKey), mensagem.capture());
        return (PagamentoResposta) mensagem.getValue();
    }

    @Test
    @DisplayName("Flyway cria o schema banco e a tabela TDS_Transacoes_Banco")
    void schemaBanco() {
        List<String> tabelas = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'banco'", String.class);
        assertThat(tabelas).contains("tds_transacoes_banco", "flyway_schema_history");
    }

    @Test
    @DisplayName("Pagamento aprovado: grava a transação e responde na fila de SUCESSO com código de autorização")
    void aprovado() throws Exception {
        PagamentoRequest request = pedido(10, "120.50");

        consumidor.receberMensagem(request);

        PagamentoResposta resposta = respostaPublicada(Filas.PAGAMENTO_SUCESSO_EXCHANGE, Filas.PAGAMENTO_SUCESSO_ROUTING_KEY);
        assertThat(resposta.status()).isEqualTo("APROVADO");
        assertThat(resposta.numeroPedido()).isEqualTo(10L);
        assertThat(resposta.transacaoId()).isEqualTo(request.transacaoId());
        assertThat(resposta.codigoAutorizacao()).matches("TQ-\\d{6}");
        verify(rabbitTemplate, never()).convertAndSend(eq(Filas.PAGAMENTO_ERRO_EXCHANGE), any(String.class), any(Object.class));

        TransacaoBancaria transacao = repository.findById(request.transacaoId()).orElseThrow();
        assertThat(transacao.getStatus()).isEqualTo(StatusTransacao.APROVADO);
        assertThat(transacao.getValor()).isEqualByComparingTo("120.50");
        assertThat(transacao.getClienteEmail()).isEqualTo("ana@email.com");
    }

    @Test
    @DisplayName("Pagamento recusado (acima do limite): responde na fila de ERRO com o motivo")
    void recusado() throws Exception {
        consumidor.receberMensagem(pedido(11, "800.00"));

        PagamentoResposta resposta = respostaPublicada(Filas.PAGAMENTO_ERRO_EXCHANGE, Filas.PAGAMENTO_ERRO_ROUTING_KEY);
        assertThat(resposta.status()).isEqualTo("RECUSADO");
        assertThat(resposta.motivo()).contains("Limite");
        assertThat(resposta.codigoAutorizacao()).isNull();
        verify(rabbitTemplate, never()).convertAndSend(eq(Filas.PAGAMENTO_SUCESSO_EXCHANGE), any(String.class), any(Object.class));
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Mensagem repetida (mesma transação) não cobra de novo: devolve a mesma resposta")
    void idempotente() throws Exception {
        PagamentoRequest request = pedido(12, "50.00");
        consumidor.receberMensagem(request);
        consumidor.receberMensagem(request);

        ArgumentCaptor<Object> respostas = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate, times(2)).convertAndSend(eq(Filas.PAGAMENTO_SUCESSO_EXCHANGE),
                eq(Filas.PAGAMENTO_SUCESSO_ROUTING_KEY), respostas.capture());
        PagamentoResposta primeira = (PagamentoResposta) respostas.getAllValues().get(0);
        PagamentoResposta segunda = (PagamentoResposta) respostas.getAllValues().get(1);
        assertThat(segunda.codigoAutorizacao()).isEqualTo(primeira.codigoAutorizacao());
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Aceita o JSON de exemplo da aula publicado pelo painel do RabbitMQ (sem transacaoId)")
    void jsonDaAula() throws Exception {
        String json = "{\"numeroPedido\":\"171\",\"valor\":550,\"produto\":\"camiseta GG\"}";
        Message mensagem = new Message(json.getBytes(StandardCharsets.UTF_8), new MessageProperties());
        PagamentoRequest request = (PagamentoRequest) ((SmartMessageConverter) converter)
                .fromMessage(mensagem, ParameterizedTypeReference.forType(PagamentoRequest.class));

        consumidor.receberMensagem(request);

        // 550 está acima do limite de 500: recusado
        PagamentoResposta resposta = respostaPublicada(Filas.PAGAMENTO_ERRO_EXCHANGE, Filas.PAGAMENTO_ERRO_ROUTING_KEY);
        assertThat(resposta.numeroPedido()).isEqualTo(171L);
        assertThat(resposta.transacaoId()).isNotNull();
        assertThat(repository.findById(resposta.transacaoId())).isPresent();
    }
}
