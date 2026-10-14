package br.com.fiap.bancotranquilo.mensageria.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Mensagem publicada em pagamento-request-queue: pedido de pagamento ao Banco Tranquilo. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PagamentoRequest(
        UUID transacaoId,
        Long numeroPedido,
        BigDecimal valor,
        String formaPagamento,
        String clienteNome,
        String clienteEmail,
        String descricao,
        LocalDateTime solicitadoEm) {
}
