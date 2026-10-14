package br.com.fiap.bancotranquilo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * Regras da simulação do Banco Tranquilo (application.yml, prefixo "banco").
 *
 * @param tempoProcessamento  espera antes de responder (deixa visível a resposta assíncrona)
 * @param limitePorTransacao  compras acima deste valor são recusadas
 * @param taxaAprovacao       chance de aprovação das demais compras (0.0 a 1.0)
 */
@ConfigurationProperties(prefix = "banco")
public record RegrasBanco(Duration tempoProcessamento, BigDecimal limitePorTransacao, double taxaAprovacao) {

    public RegrasBanco {
        if (tempoProcessamento == null || tempoProcessamento.isNegative()) {
            tempoProcessamento = Duration.ZERO;
        }
        if (limitePorTransacao == null) {
            limitePorTransacao = new BigDecimal("500.00");
        }
        if (taxaAprovacao < 0 || taxaAprovacao > 1) {
            throw new IllegalArgumentException("banco.taxa-aprovacao deve estar entre 0.0 e 1.0");
        }
    }
}
