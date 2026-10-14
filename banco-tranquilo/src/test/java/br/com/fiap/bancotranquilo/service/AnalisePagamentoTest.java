package br.com.fiap.bancotranquilo.service;

import br.com.fiap.bancotranquilo.config.RegrasBanco;
import br.com.fiap.bancotranquilo.mensageria.dto.PagamentoRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import java.util.random.RandomGenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Regras de aprovação do Banco Tranquilo (teste unitário, sem banco e sem broker). */
@DisplayName("Análise de pagamento do Banco Tranquilo")
class AnalisePagamentoTest {

    private static final RegrasBanco REGRAS = new RegrasBanco(Duration.ZERO, new BigDecimal("500.00"), 0.85);

    /** Gerador "aleatório" com valor fixo, para o teste ser previsível. */
    private static RandomGenerator sempre(double valor) {
        return new RandomGenerator() {
            @Override
            public long nextLong() {
                return 0;
            }

            @Override
            public double nextDouble() {
                return valor;
            }

            @Override
            public int nextInt(int limite) {
                return 0;
            }
        };
    }

    private static PagamentoRequest pagamento(String valor, String forma) {
        return new PagamentoRequest(UUID.randomUUID(), 1L, valor == null ? null : new BigDecimal(valor), forma,
                "Ana", "ana@email.com", "teste", null);
    }

    @Test
    @DisplayName("Aprova quando o valor está no limite e o sorteio fica abaixo da taxa de aprovação")
    void aprova() {
        AnalisePagamento analise = new AnalisePagamento(REGRAS, sempre(0.10));
        assertThat(analise.analisar(pagamento("500.00", "PIX")).aprovado()).isTrue();
        assertThat(analise.analisar(pagamento("0.01", "CARTAO_CREDITO")).aprovado()).isTrue();
        assertThat(analise.analisar(pagamento("10", null)).aprovado()).isTrue(); // forma não informada
    }

    @Test
    @DisplayName("Recusa acima do limite por transação, com o motivo")
    void recusaAcimaDoLimite() {
        AnalisePagamento.Decisao decisao = new AnalisePagamento(REGRAS, sempre(0.0)).analisar(pagamento("500.01", "PIX"));
        assertThat(decisao.aprovado()).isFalse();
        assertThat(decisao.motivo()).startsWith("Limite por transação excedido").contains("500,00");
    }

    @Test
    @DisplayName("Recusa valor nulo, zero ou negativo e forma de pagamento desconhecida")
    void recusaDadosInvalidos() {
        AnalisePagamento analise = new AnalisePagamento(REGRAS, sempre(0.0));
        assertThat(analise.analisar(pagamento(null, "PIX")).motivo()).isEqualTo("Valor inválido");
        assertThat(analise.analisar(pagamento("0", "PIX")).motivo()).isEqualTo("Valor inválido");
        assertThat(analise.analisar(pagamento("-5", "PIX")).motivo()).isEqualTo("Valor inválido");
        assertThat(analise.analisar(pagamento("10", "BOLETO")).motivo()).isEqualTo("Forma de pagamento não aceita");
    }

    @Test
    @DisplayName("Parte aleatória: sorteio acima da taxa recusa com um motivo de banco")
    void recusaAleatoria() {
        AnalisePagamento.Decisao decisao = new AnalisePagamento(REGRAS, sempre(0.99)).analisar(pagamento("50", "PIX"));
        assertThat(decisao.aprovado()).isFalse();
        assertThat(decisao.motivo()).isIn(AnalisePagamento.MOTIVOS_ALEATORIOS);
    }

    @Test
    @DisplayName("Taxa de aprovação 1.0 aprova tudo que está dentro das regras; 0.0 recusa tudo")
    void taxasExtremas() {
        RegrasBanco sempreAprova = new RegrasBanco(Duration.ZERO, new BigDecimal("500.00"), 1.0);
        RegrasBanco nuncaAprova = new RegrasBanco(Duration.ZERO, new BigDecimal("500.00"), 0.0);
        assertThat(new AnalisePagamento(sempreAprova, sempre(0.999)).analisar(pagamento("10", "PIX")).aprovado()).isTrue();
        assertThat(new AnalisePagamento(nuncaAprova, sempre(0.0)).analisar(pagamento("10", "PIX")).aprovado()).isFalse();
    }

    @Test
    @DisplayName("Configuração inválida da taxa é recusada ao iniciar")
    void configuracaoInvalida() {
        assertThatThrownBy(() -> new RegrasBanco(Duration.ZERO, BigDecimal.TEN, 1.5))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
