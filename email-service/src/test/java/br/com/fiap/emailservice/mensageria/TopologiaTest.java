package br.com.fiap.emailservice.mensageria;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Queue;

import static org.assertj.core.api.Assertions.assertThat;

/** O email-service declara a mesma topologia dos outros microsserviços. */
@DisplayName("Topologia do RabbitMQ no email-service")
class TopologiaTest {

    @Test
    @DisplayName("Mesmas exchanges, filas duráveis com dead letter e bindings")
    void topologia() {
        Declarables declarables = new RabbitMQConfig().topologia();
        assertThat(declarables.getDeclarablesByType(Exchange.class)).hasSize(6);
        assertThat(declarables.getDeclarablesByType(Queue.class)).hasSize(6).allMatch(Queue::isDurable);
        assertThat(declarables.getDeclarablesByType(Binding.class))
                .extracting(b -> b.getExchange() + " -> " + b.getRoutingKey() + " -> " + b.getDestination())
                .contains("email-request-exchange -> email-request-rout-key -> email-request-queue",
                        "email-response-exchange -> email-response-rout-key -> email-response-queue");
    }
}
