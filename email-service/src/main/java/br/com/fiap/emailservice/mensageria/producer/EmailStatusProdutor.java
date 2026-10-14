package br.com.fiap.emailservice.mensageria.producer;

import br.com.fiap.emailservice.mensageria.Filas;
import br.com.fiap.emailservice.mensageria.dto.EmailStatusMensagem;
import br.com.fiap.emailservice.model.EmailModel;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** Devolve ao Mercado Express o resultado do envio (email-response-queue). */
@Component
@RequiredArgsConstructor
public class EmailStatusProdutor {

    private final RabbitTemplate rabbitTemplate;

    public void informar(EmailModel email) {
        EmailStatusMensagem status = new EmailStatusMensagem(email.getSolicitacaoId(), email.getEmailId(),
                email.getNumeroPedido(), email.getEmailTo(), email.getStatusEmail().name(), email.getErro(),
                LocalDateTime.now());
        rabbitTemplate.convertAndSend(Filas.EMAIL_RESPONSE_EXCHANGE, Filas.EMAIL_RESPONSE_ROUTING_KEY, status);
    }
}
