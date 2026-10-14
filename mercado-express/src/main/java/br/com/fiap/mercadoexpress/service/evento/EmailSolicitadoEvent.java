package br.com.fiap.mercadoexpress.service.evento;

/** Pagamento aprovado (ou reenvio pedido pelo gerente): depois do commit, o e-mail é solicitado. */
public record EmailSolicitadoEvent(Long pedidoId) {
}
