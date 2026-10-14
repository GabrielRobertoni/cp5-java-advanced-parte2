package br.com.fiap.bancotranquilo.mensageria.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Resposta do Banco Tranquilo, recebida em pagamento-response-sucesso-queue (APROVADO)
 * ou em pagamento-response-erro-queue (RECUSADO).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PagamentoResposta(
        UUID transacaoId,
        Long numeroPedido,
        String status,
        BigDecimal valor,
        String codigoAutorizacao,
        String motivo,
        LocalDateTime processadoEm) {
}
