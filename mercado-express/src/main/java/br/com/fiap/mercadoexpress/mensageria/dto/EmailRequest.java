package br.com.fiap.mercadoexpress.mensageria.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Mensagem publicada em email-request-queue. Os campos ownerRef, emailFrom, emailTo, subject e text
 * são os mesmos do microsserviço de e-mail visto em aula; "pedido" leva os dados para o e-mail em HTML.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EmailRequest(
        UUID solicitacaoId,
        String ownerRef,
        String emailFrom,
        String emailTo,
        String nomeDestinatario,
        String subject,
        String text,
        Long numeroPedido,
        DetalhesPedido pedido) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DetalhesPedido(
            List<Item> itens,
            BigDecimal valorTotal,
            String formaPagamento,
            String codigoAutorizacao,
            UUID transacaoId,
            LocalDateTime pagoEm) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(String produto, Integer quantidade, BigDecimal precoUnitario, BigDecimal subtotal) {
    }
}
