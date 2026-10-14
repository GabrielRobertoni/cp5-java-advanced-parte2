package br.com.fiap.bancotranquilo.mensageria.consumer;

import br.com.fiap.bancotranquilo.config.RegrasBanco;
import br.com.fiap.bancotranquilo.mensageria.Filas;
import br.com.fiap.bancotranquilo.mensageria.dto.PagamentoRequest;
import br.com.fiap.bancotranquilo.mensageria.dto.PagamentoResposta;
import br.com.fiap.bancotranquilo.mensageria.producer.PagamentoErroProdutor;
import br.com.fiap.bancotranquilo.mensageria.producer.PagamentoSucessoProdutor;
import br.com.fiap.bancotranquilo.service.PagamentoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Recebe os pedidos de pagamento do Mercado Express (pagamento-request-queue), processa no banco
 * e responde na fila de sucesso ou de erro.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PagamentoRequestConsumidor {

    private final PagamentoService pagamentoService;
    private final PagamentoSucessoProdutor sucessoProdutor;
    private final PagamentoErroProdutor erroProdutor;
    private final RegrasBanco regras;

    @RabbitListener(queues = Filas.PAGAMENTO_REQUEST_QUEUE)
    public void receberMensagem(@Payload PagamentoRequest request) throws InterruptedException {
        log.info("Pagamento recebido: pedido {} | transação {} | R$ {} | {}",
                request.numeroPedido(), request.transacaoId(), request.valor(), request.formaPagamento());
        Thread.sleep(regras.tempoProcessamento().toMillis());

        PagamentoResposta resposta = pagamentoService.processar(request);
        if ("APROVADO".equals(resposta.status())) {
            sucessoProdutor.gerarResposta(resposta);
        } else {
            erroProdutor.gerarResposta(resposta);
        }
    }
}
