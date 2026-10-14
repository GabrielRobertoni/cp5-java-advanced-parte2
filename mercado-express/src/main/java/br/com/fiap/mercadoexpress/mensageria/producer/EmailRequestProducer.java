package br.com.fiap.mercadoexpress.mensageria.producer;

import br.com.fiap.mercadoexpress.mensageria.Filas;
import br.com.fiap.mercadoexpress.mensageria.dto.EmailRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/** Publica a solicitação de e-mail de confirmação para o email-service. */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailRequestProducer {

    private final RabbitTemplate rabbitTemplate;

    public void solicitar(EmailRequest request) {
        rabbitTemplate.convertAndSend(Filas.EMAIL_REQUEST_EXCHANGE, Filas.EMAIL_REQUEST_ROUTING_KEY, request);
        log.info("E-mail de confirmação do pedido {} solicitado para {}", request.numeroPedido(), request.emailTo());
    }
}
