package br.com.fiap.mercadoexpress.model;

/** Situação do pedido em relação ao pagamento no Banco Tranquilo. */
public enum StatusPedido {
    AGUARDANDO_PAGAMENTO("Aguardando pagamento"),
    PAGO("Pago"),
    RECUSADO("Pagamento recusado");

    private final String descricao;

    StatusPedido(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
