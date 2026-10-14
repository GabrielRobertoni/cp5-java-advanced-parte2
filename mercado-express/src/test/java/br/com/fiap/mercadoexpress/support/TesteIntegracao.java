package br.com.fiap.mercadoexpress.support;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Base dos testes de integração.
 *
 * <p>Sobe um PostgreSQL 16 REAL embarcado (biblioteca zonky), sem precisar de Docker
 * nem de banco instalado. O Flyway aplica as mesmas migrations da produção e o
 * Hibernate valida o mapeamento (ddl-auto=validate).
 *
 * <p>Mensageria: o {@link RabbitTemplate} é substituído por um mock (os testes verificam o que
 * seria publicado) e os listeners não são iniciados - os testes chamam os consumidores
 * diretamente, simulando as respostas do Banco Tranquilo e do email-service. Assim os testes
 * não dependem de um RabbitMQ rodando e nunca consomem mensagens de um broker real.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(DadosTeste.class)
public abstract class TesteIntegracao {

    public static final String ADMIN_EMAIL = "admin@mercadoexpress.com";
    public static final String ADMIN_SENHA = "Admin@123";
    public static final String CLIENTE_EMAIL = "cliente@mercadoexpress.com";
    public static final String CLIENTE_SENHA = "Cliente@123";

    private static final EmbeddedPostgres POSTGRES = iniciarPostgres();

    @MockitoBean
    protected RabbitTemplate rabbitTemplate;

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
            throw new UncheckedIOException("Não foi possível iniciar o PostgreSQL embarcado", e);
        }
    }

    @DynamicPropertySource
    static void configurar(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "");
        // Nenhuma conexão com broker real durante os testes
        registry.add("spring.rabbitmq.addresses", () -> "amqp://guest:guest@127.0.0.1:1");
        registry.add("spring.rabbitmq.listener.simple.auto-startup", () -> "false");
        registry.add("app.pagamentos.reenvio-intervalo", () -> "1h");
    }
}
