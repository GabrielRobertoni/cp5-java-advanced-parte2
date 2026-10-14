package br.com.fiap.emailservice.mensageria.consumer;

import br.com.fiap.emailservice.mensageria.Filas;
import br.com.fiap.emailservice.mensageria.dto.EmailRequest;
import br.com.fiap.emailservice.mensageria.producer.EmailStatusProdutor;
import br.com.fiap.emailservice.model.EmailModel;
import br.com.fiap.emailservice.service.EmailInvalidoException;
import br.com.fiap.emailservice.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/** Escuta email-request-queue: envia o e-mail, grava no banco e informa o resultado ao Mercado Express. */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailConsumer {

    private final EmailService emailService;
    private final EmailStatusProdutor statusProdutor;

    @RabbitListener(queues = Filas.EMAIL_REQUEST_QUEUE)
    public void listen(@Payload EmailRequest request) {
        EmailModel email;
        try {
            email = emailService.processar(request);
        } catch (EmailInvalidoException e) {
            log.warn("Mensagem de e-mail inválida enviada para a dead letter queue: {}", e.getMessage());
            throw new AmqpRejectAndDontRequeueException("E-mail inválido: " + e.getMessage(), e);
        }
        log.info("Email Status: {} (id {})", email.getStatusEmail(), email.getEmailId());
        statusProdutor.informar(email);
    }
}
