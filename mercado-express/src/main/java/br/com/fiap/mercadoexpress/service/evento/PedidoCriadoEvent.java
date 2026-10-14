package br.com.fiap.mercadoexpress.service.evento;

/** Pedido gravado: depois do commit, o pagamento é enviado ao Banco Tranquilo. */
public record PedidoCriadoEvent(Long pedidoId) {
}
