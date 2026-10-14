package br.com.fiap.emailservice.mensageria.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Mensagem recebida em email-request-queue. Aceita o mesmo JSON do microsserviço de e-mail da aula
 * (ownerRef, emailFrom, emailTo, subject, text) e, opcionalmente, os dados do pedido para o e-mail em HTML.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EmailRequest(
        UUID solicitacaoId,
        @Size(max = 100, message = "ownerRef: no máximo 100 caracteres") String ownerRef,
        @Email(message = "emailFrom inválido") @Size(max = 150) String emailFrom,
        @NotBlank(message = "emailTo é obrigatório") @Email(message = "emailTo inválido") @Size(max = 150) String emailTo,
        @Size(max = 100) String nomeDestinatario,
        @NotBlank(message = "subject é obrigatório") @Size(max = 200, message = "subject: no máximo 200 caracteres") String subject,
        String text,
        Long numeroPedido,
        DetalhesPedido pedido) {

    @AssertTrue(message = "informe o text ou os dados do pedido")
    public boolean isConteudoInformado() {
        return (text != null && !text.isBlank()) || pedido != null;
    }

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
