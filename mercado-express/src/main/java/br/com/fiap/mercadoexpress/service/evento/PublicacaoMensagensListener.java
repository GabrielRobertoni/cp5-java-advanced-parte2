package br.com.fiap.mercadoexpress.service.evento;

import br.com.fiap.mercadoexpress.service.PagamentoFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * As mensagens só são publicadas no RabbitMQ DEPOIS que a transação do banco de dados foi confirmada.
 * Assim o Banco Tranquilo nunca recebe um pedido que não existe (rollback) e a resposta nunca chega
 * antes de o pedido estar gravado.
 */
@Component
@RequiredArgsConstructor
public class PublicacaoMensagensListener {

    private final PagamentoFacade pagamentoFacade;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoCriarPedido(PedidoCriadoEvent evento) {
        pagamentoFacade.solicitarPagamento(evento.pedidoId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoSolicitarEmail(EmailSolicitadoEvent evento) {
        pagamentoFacade.solicitarEmail(evento.pedidoId());
    }
}
