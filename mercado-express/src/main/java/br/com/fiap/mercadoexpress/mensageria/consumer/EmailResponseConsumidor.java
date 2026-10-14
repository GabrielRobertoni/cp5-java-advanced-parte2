package br.com.fiap.mercadoexpress.mensageria.consumer;

import br.com.fiap.mercadoexpress.mensageria.Filas;
import br.com.fiap.mercadoexpress.mensageria.dto.EmailStatusMensagem;
import br.com.fiap.mercadoexpress.service.PagamentoFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/** Resultado do envio do e-mail de confirmação (SENT ou ERROR), vindo do email-service. */
@Component
@RequiredArgsConstructor
public class EmailResponseConsumidor {

    private final PagamentoFacade pagamentoFacade;

    @RabbitListener(queues = Filas.EMAIL_RESPONSE_QUEUE)
    public void receber(@Payload EmailStatusMensagem status) {
        pagamentoFacade.atualizarStatusEmail(status);
    }
}
