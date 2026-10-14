package br.com.fiap.bancotranquilo.mensageria;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.support.converter.JacksonJavaTypeMapper;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

import static br.com.fiap.bancotranquilo.mensageria.Filas.*;

/**
 * Declara no RabbitMQ toda a topologia do sistema (exchanges, filas e bindings).
 * Os três microsserviços declaram exatamente a mesma topologia: qualquer um deles pode subir
 * primeiro e nenhuma mensagem se perde por falta de fila (a declaração é idempotente).
 */
@EnableRabbit
@Configuration
public class RabbitMQConfig {

    @Bean
    public Declarables topologia() {
        List<Declarable> itens = new ArrayList<>();

        FanoutExchange deadLetterExchange = new FanoutExchange(DEAD_LETTER_EXCHANGE, true, false);
        Queue deadLetterQueue = QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
        itens.add(deadLetterExchange);
        itens.add(deadLetterQueue);
        itens.add(BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange));

        itens.addAll(filaDireta(PAGAMENTO_REQUEST_EXCHANGE, PAGAMENTO_REQUEST_QUEUE, PAGAMENTO_REQUEST_ROUTING_KEY));
        itens.addAll(filaDireta(PAGAMENTO_SUCESSO_EXCHANGE, PAGAMENTO_SUCESSO_QUEUE, PAGAMENTO_SUCESSO_ROUTING_KEY));
        itens.addAll(filaDireta(PAGAMENTO_ERRO_EXCHANGE, PAGAMENTO_ERRO_QUEUE, PAGAMENTO_ERRO_ROUTING_KEY));
        itens.addAll(filaDireta(EMAIL_REQUEST_EXCHANGE, EMAIL_REQUEST_QUEUE, EMAIL_REQUEST_ROUTING_KEY));
        itens.addAll(filaDireta(EMAIL_RESPONSE_EXCHANGE, EMAIL_RESPONSE_QUEUE, EMAIL_RESPONSE_ROUTING_KEY));
        return new Declarables(itens);
    }

    /** Exchange direct + fila durável (com dead letter) + binding pela routing key. */
    private static List<Declarable> filaDireta(String exchange, String fila, String routingKey) {
        DirectExchange directExchange = new DirectExchange(exchange, true, false);
        Queue queue = QueueBuilder.durable(fila).deadLetterExchange(DEAD_LETTER_EXCHANGE).build();
        Binding binding = BindingBuilder.bind(queue).to(directExchange).with(routingKey);
        return List.of(directExchange, queue, binding);
    }

    /**
     * Mensagens em JSON. O tipo de destino é sempre o parâmetro do @RabbitListener (INFERRED),
     * assim cada microsserviço usa as próprias classes e aceita também mensagens publicadas
     * manualmente pelo painel do RabbitMQ (sem cabeçalho de tipo).
     */
    @Bean
    public MessageConverter jsonMessageConverter(JsonMapper jsonMapper) {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter(jsonMapper);
        converter.setTypePrecedence(JacksonJavaTypeMapper.TypePrecedence.INFERRED);
        converter.setAlwaysConvertToInferredType(true);
        return converter;
    }
}
