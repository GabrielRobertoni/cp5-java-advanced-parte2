package br.com.fiap.bancotranquilo.mensageria;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Queue;

import static org.assertj.core.api.Assertions.assertThat;

/** O banco declara a mesma topologia dos outros microsserviços (pode subir primeiro sem perder mensagens). */
@DisplayName("Topologia do RabbitMQ no Banco Tranquilo")
class TopologiaTest {

    @Test
    @DisplayName("Mesmas exchanges, filas duráveis com dead letter e bindings")
    void topologia() {
        Declarables declarables = new RabbitMQConfig().topologia();
        assertThat(declarables.getDeclarablesByType(Exchange.class)).hasSize(6);
        assertThat(declarables.getDeclarablesByType(Queue.class)).hasSize(6).allMatch(Queue::isDurable);
        assertThat(declarables.getDeclarablesByType(Binding.class))
                .extracting(b -> b.getExchange() + " -> " + b.getRoutingKey() + " -> " + b.getDestination())
                .contains("pagamento-request-exchange -> pagamento-request-rout-key -> pagamento-request-queue",
                        "pagamento-response-sucesso-exchange -> pagamento-response-sucesso-rout-key -> pagamento-response-sucesso-queue",
                        "pagamento-response-erro-exchange -> pagamento-response-erro-rout-key -> pagamento-response-erro-queue");
    }
}
