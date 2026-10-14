package br.com.fiap.mercadoexpress.mensageria.consumer;

import br.com.fiap.mercadoexpress.mensageria.Filas;
import br.com.fiap.mercadoexpress.mensageria.dto.PagamentoResposta;
import br.com.fiap.mercadoexpress.service.PagamentoFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/** Pagamento aprovado pelo Banco Tranquilo. */
@Component
@RequiredArgsConstructor
public class PagamentoResponseSucessoConsumidor {

    private final PagamentoFacade pagamentoFacade;

    @RabbitListener(queues = Filas.PAGAMENTO_SUCESSO_QUEUE)
    public void receber(@Payload PagamentoResposta resposta) {
        pagamentoFacade.sucessoPagamento(resposta);
    }
}
