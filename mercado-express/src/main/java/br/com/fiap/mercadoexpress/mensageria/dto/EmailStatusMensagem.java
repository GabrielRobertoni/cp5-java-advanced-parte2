package br.com.fiap.mercadoexpress.mensageria.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;
import java.util.UUID;

/** Retorno do serviço de e-mail (email-response-queue): SENT ou ERROR. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EmailStatusMensagem(
        UUID solicitacaoId,
        UUID emailId,
        Long numeroPedido,
        String emailTo,
        String statusEmail,
        String erro,
        LocalDateTime atualizadoEm) {
}
