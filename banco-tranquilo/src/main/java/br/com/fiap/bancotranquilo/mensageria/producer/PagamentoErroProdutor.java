package br.com.fiap.bancotranquilo.mensageria.producer;

import br.com.fiap.bancotranquilo.mensageria.Filas;
import br.com.fiap.bancotranquilo.mensageria.dto.PagamentoResposta;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/** Publica a resposta de pagamento RECUSADO para o Mercado Express. */
@Component
@RequiredArgsConstructor
public class PagamentoErroProdutor {

    private final RabbitTemplate rabbitTemplate;

    public void gerarResposta(PagamentoResposta resposta) {
        rabbitTemplate.convertAndSend(Filas.PAGAMENTO_ERRO_EXCHANGE, Filas.PAGAMENTO_ERRO_ROUTING_KEY, resposta);
    }
}
