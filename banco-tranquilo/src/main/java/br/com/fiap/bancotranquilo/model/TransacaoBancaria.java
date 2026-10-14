package br.com.fiap.bancotranquilo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Transação de pagamento processada pelo Banco Tranquilo. */
@Entity
@Table(name = "TDS_Transacoes_Banco")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class TransacaoBancaria {

    /** Mesmo id gerado pelo Mercado Express: garante que cada pagamento seja processado uma única vez. */
    @Id
    @Column(name = "transacao_id")
    private UUID transacaoId;

    @Column(name = "numero_pedido")
    private Long numeroPedido;

    @Column(precision = 12, scale = 2)
    private BigDecimal valor;

    @Column(name = "forma_pagamento", length = 30)
    private String formaPagamento;

    @Column(name = "cliente_nome", length = 100)
    private String clienteNome;

    @Column(name = "cliente_email", length = 150)
    private String clienteEmail;

    @Column(length = 255)
    private String descricao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusTransacao status;

    @Column(name = "codigo_autorizacao", length = 30)
    private String codigoAutorizacao;

    @Column(length = 255)
    private String motivo;

    @Column(name = "recebida_em", nullable = false)
    private LocalDateTime recebidaEm;

    @Column(name = "processada_em", nullable = false)
    private LocalDateTime processadaEm;

    public boolean isAprovada() {
        return status == StatusTransacao.APROVADO;
    }
}
