package br.com.fiap.emailservice;

import br.com.fiap.emailservice.mensageria.Filas;
import br.com.fiap.emailservice.mensageria.consumer.EmailConsumer;
import br.com.fiap.emailservice.mensageria.dto.EmailRequest;
import br.com.fiap.emailservice.mensageria.dto.EmailStatusMensagem;
import br.com.fiap.emailservice.model.EmailModel;
import br.com.fiap.emailservice.model.StatusEmail;
import br.com.fiap.emailservice.repository.EmailRepository;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
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
import java.time.Duration;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * email-service de ponta a ponta com um servidor SMTP de teste (GreenMail) e PostgreSQL embarcado:
 * envia o e-mail, grava em TDS_Emails_Mercado e devolve o status ao Mercado Express.
 */
@SpringBootTest(properties = {
        "spring.rabbitmq.addresses=amqp://guest:guest@127.0.0.1:1",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.mail.host=127.0.0.1",
        "spring.mail.port=3025",
        "app.email.remetente=nao-responda@mercadoexpress.com"
})
@DisplayName("email-service: envio e controle dos e-mails")
class EmailServiceIntegracaoTest {

    @RegisterExtension
    static GreenMailExtension smtp = new GreenMailExtension(ServerSetupTest.SMTP);

    private static final EmbeddedPostgres POSTGRES = iniciarPostgres();

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private EmailConsumer consumer;
    @Autowired
    private EmailRepository repository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private MessageConverter converter;

    private static EmbeddedPostgres iniciarPostgres() {
        try {
            // Espera maior que o padrão (10 s): em computadores mais lentos o PostgreSQL demora para subir
            EmbeddedPostgres postgres = EmbeddedPostgres.builder().setPGStartupWait(Duration.ofMinutes(2)).start();
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

    private static EmailRequest confirmacaoDePagamento(UUID solicitacao, long pedido) {
        var itens = List.of(
                new EmailRequest.Item("Arroz Branco Tipo 1", 2, new BigDecimal("27.90"), new BigDecimal("55.80")),
                new EmailRequest.Item("Café Torrado e Moído", 1, new BigDecimal("19.90"), new BigDecimal("19.90")));
        var detalhes = new EmailRequest.DetalhesPedido(itens, new BigDecimal("75.70"), "Pix", "TQ-123456",
                UUID.randomUUID(), LocalDateTime.of(2026, 10, 14, 18, 30));
        return new EmailRequest(solicitacao, "Mercado Express", null, "ana@email.com", "Ana",
                "Pagamento confirmado - pedido #" + pedido + " | Mercado Express",
                "Olá, Ana! O Banco Tranquilo confirmou o pagamento do seu pedido.", pedido, detalhes);
    }

    private EmailStatusMensagem statusPublicado() {
        ArgumentCaptor<Object> mensagem = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate).convertAndSend(eq(Filas.EMAIL_RESPONSE_EXCHANGE), eq(Filas.EMAIL_RESPONSE_ROUTING_KEY),
                mensagem.capture());
        return (EmailStatusMensagem) mensagem.getValue();
    }

    @Test
    @DisplayName("Flyway cria o schema email e a tabela TDS_Emails_Mercado")
    void schemaEmail() {
        List<String> tabelas = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'email'", String.class);
        assertThat(tabelas).contains("tds_emails_mercado");
    }

    @Test
    @DisplayName("Confirmação de pagamento: envia e-mail em HTML com os itens, grava SENT e avisa o Mercado")
    void enviaConfirmacao() throws Exception {
        UUID solicitacao = UUID.randomUUID();
        consumer.listen(confirmacaoDePagamento(solicitacao, 42));

        MimeMessage[] recebidos = smtp.getReceivedMessages();
        assertThat(recebidos).hasSize(1);
        MimeMessage email = recebidos[0];
        assertThat(email.getSubject()).isEqualTo("Pagamento confirmado - pedido #42 | Mercado Express");
        assertThat(email.getAllRecipients()[0].toString()).isEqualTo("ana@email.com");
        assertThat(email.getFrom()[0].toString()).contains("nao-responda@mercadoexpress.com", "Mercado Express");
        String corpo = GreenMailUtil.getBody(email);
        assertThat(corpo).contains("text/html", "text/plain");

        EmailModel registro = repository.findBySolicitacaoId(solicitacao).orElseThrow();
        assertThat(registro.getStatusEmail()).isEqualTo(StatusEmail.SENT);
        assertThat(registro.getNumeroPedido()).isEqualTo(42L);
        assertThat(registro.getOwnerRef()).isEqualTo("Mercado Express");
        assertThat(registro.getSendDateEmail()).isNotNull();

        EmailStatusMensagem status = statusPublicado();
        assertThat(status.statusEmail()).isEqualTo("SENT");
        assertThat(status.numeroPedido()).isEqualTo(42L);
        assertThat(status.solicitacaoId()).isEqualTo(solicitacao);
        assertThat(status.emailId()).isEqualTo(registro.getEmailId());
    }

    @Test
    @DisplayName("HTML do e-mail traz itens, total em reais e código de autorização")
    void conteudoHtml() throws Exception {
        consumer.listen(confirmacaoDePagamento(UUID.randomUUID(), 43));
        String html = parteHtml(smtp.getReceivedMessages()[0].getContent());
        assertThat(html).contains("Arroz Branco Tipo 1", "Café Torrado e Moído", "75,70", "TQ-123456", "#43", "Ana");
    }

    private static String parteHtml(Object conteudo) throws Exception {
        if (conteudo instanceof Multipart partes) {
            for (int i = 0; i < partes.getCount(); i++) {
                BodyPart parte = partes.getBodyPart(i);
                if (parte.isMimeType("text/html")) {
                    return (String) parte.getContent();
                }
                String encontrado = parteHtml(parte.getContent());
                if (encontrado != null) {
                    return encontrado;
                }
            }
        }
        return null;
    }

    @Test
    @DisplayName("Aceita o JSON da aula (ownerRef, emailFrom, emailTo, subject, text) publicado no painel do RabbitMQ")
    void jsonDaAula() {
        String json = """
                {"ownerRef":"Tranquilo","emailFrom":"professor@fiap.com.br","emailTo":"aluno@fiap.com.br",
                 "subject":"Aproveita que está fácil","text":"Olá, tudo bem? Tranquilo!"}""";
        Message mensagem = new Message(json.getBytes(StandardCharsets.UTF_8), new MessageProperties());
        EmailRequest request = (EmailRequest) ((SmartMessageConverter) converter)
                .fromMessage(mensagem, ParameterizedTypeReference.forType(EmailRequest.class));

        consumer.listen(request);

        assertThat(smtp.getReceivedMessages()).hasSize(1);
        assertThat(GreenMailUtil.getBody(smtp.getReceivedMessages()[0])).contains("Tranquilo!");
        EmailModel registro = repository.findAll().getFirst();
        assertThat(registro.getStatusEmail()).isEqualTo(StatusEmail.SENT);
        assertThat(registro.getEmailFrom()).isEqualTo("professor@fiap.com.br");
        assertThat(registro.getOwnerRef()).isEqualTo("Tranquilo");
        assertThat(statusPublicado().numeroPedido()).isNull();
    }

    @Test
    @DisplayName("Mensagem repetida (mesma solicitação) não envia o e-mail duas vezes")
    void naoDuplica() {
        UUID solicitacao = UUID.randomUUID();
        consumer.listen(confirmacaoDePagamento(solicitacao, 44));
        consumer.listen(confirmacaoDePagamento(solicitacao, 44));

        assertThat(smtp.getReceivedMessages()).hasSize(1);
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Mensagem sem destinatário é rejeitada (vai para a dead letter queue) e nada é enviado")
    void mensagemInvalida() {
        EmailRequest semDestino = new EmailRequest(UUID.randomUUID(), "Mercado Express", null, "", null,
                "Assunto", "Texto", 1L, null);
        assertThatThrownBy(() -> consumer.listen(semDestino))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessageContaining("emailTo");
        EmailRequest emailInvalido = new EmailRequest(UUID.randomUUID(), null, null, "nao-e-email", null,
                "Assunto", null, null, null);
        assertThatThrownBy(() -> consumer.listen(emailInvalido))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessageContaining("emailTo inválido")
                .hasMessageContaining("informe o text");

        assertThat(smtp.getReceivedMessages()).isEmpty();
        assertThat(repository.count()).isZero();
        verify(rabbitTemplate, never()).convertAndSend(any(String.class), any(String.class), any(Object.class));
    }

    @Test
    @DisplayName("SMTP fora do ar: grava ERROR com o motivo e avisa o Mercado")
    void smtpForaDoAr() {
        smtp.stop();
        try {
            UUID solicitacao = UUID.randomUUID();
            consumer.listen(confirmacaoDePagamento(solicitacao, 45));

            EmailModel registro = repository.findBySolicitacaoId(solicitacao).orElseThrow();
            assertThat(registro.getStatusEmail()).isEqualTo(StatusEmail.ERROR);
            assertThat(registro.getErro()).startsWith("Falha no envio");
            EmailStatusMensagem status = statusPublicado();
            assertThat(status.statusEmail()).isEqualTo("ERROR");
            assertThat(status.erro()).isNotBlank();
        } finally {
            smtp.start();
        }
    }
}
