package br.com.fiap.mercadoexpress.model;

/** Controle do e-mail de confirmação de pagamento de cada pedido. */
public enum StatusEmail {
    /** Pedido ainda não pago (ou recusado): não há e-mail a enviar. */
    NAO_APLICAVEL("—"),
    /** Pagamento aprovado, mas a solicitação ainda não chegou ao broker. */
    PENDENTE("Pendente"),
    /** Solicitação publicada na fila do serviço de e-mail. */
    SOLICITADO("Na fila"),
    ENVIADO("Enviado"),
    ERRO("Erro no envio");

    private final String descricao;

    StatusEmail(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
