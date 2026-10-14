package br.com.fiap.mercadoexpress.mensageria.producer;

import br.com.fiap.mercadoexpress.mensageria.Filas;
import br.com.fiap.mercadoexpress.mensageria.dto.PagamentoRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/** Publica o pedido de pagamento para o Banco Tranquilo (exchange + routing key). */
@Slf4j
@Component
@RequiredArgsConstructor
public class PagamentoRequestProducer {

    private final RabbitTemplate rabbitTemplate;

    public void integrar(PagamentoRequest request) {
        rabbitTemplate.convertAndSend(Filas.PAGAMENTO_REQUEST_EXCHANGE, Filas.PAGAMENTO_REQUEST_ROUTING_KEY, request);
        log.info("Pagamento do pedido {} enviado ao Banco Tranquilo (transação {}, R$ {})",
                request.numeroPedido(), request.transacaoId(), request.valor());
    }
}
