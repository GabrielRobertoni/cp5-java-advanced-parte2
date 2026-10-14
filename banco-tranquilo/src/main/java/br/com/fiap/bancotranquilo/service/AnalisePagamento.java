package br.com.fiap.bancotranquilo.service;

import br.com.fiap.bancotranquilo.config.RegrasBanco;
import br.com.fiap.bancotranquilo.mensageria.dto.PagamentoRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * Decide se o pagamento é aprovado. Não há integração real com bandeira de cartão: as regras
 * simulam o que um banco faria (validações + limite + uma parte aleatória de recusas, como o
 * worker da aula, para exercitar os dois fluxos: sucesso e erro).
 */
@Component
public class AnalisePagamento {

    static final Set<String> FORMAS_ACEITAS = Set.of("PIX", "CARTAO_CREDITO", "CARTAO_DEBITO");
    static final List<String> MOTIVOS_ALEATORIOS = List.of(
            "Saldo insuficiente",
            "Transação não autorizada pelo emissor");

    public record Decisao(boolean aprovado, String motivo) {
        static Decisao aprovar() {
            return new Decisao(true, null);
        }

        static Decisao recusar(String motivo) {
            return new Decisao(false, motivo);
        }
    }

    private final RegrasBanco regras;
    private final RandomGenerator aleatorio;

    @Autowired
    public AnalisePagamento(RegrasBanco regras) {
        this(regras, RandomGenerator.getDefault());
    }

    AnalisePagamento(RegrasBanco regras, RandomGenerator aleatorio) {
        this.regras = regras;
        this.aleatorio = aleatorio;
    }

    public Decisao analisar(PagamentoRequest request) {
        BigDecimal valor = request.valor();
        if (valor == null || valor.signum() <= 0) {
            return Decisao.recusar("Valor inválido");
        }
        if (request.formaPagamento() != null && !FORMAS_ACEITAS.contains(request.formaPagamento())) {
            return Decisao.recusar("Forma de pagamento não aceita");
        }
        if (valor.compareTo(regras.limitePorTransacao()) > 0) {
            NumberFormat moeda = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"));
            return Decisao.recusar("Limite por transação excedido (máximo " + moeda.format(regras.limitePorTransacao()) + ")");
        }
        if (aleatorio.nextDouble() < regras.taxaAprovacao()) {
            return Decisao.aprovar();
        }
        return Decisao.recusar(MOTIVOS_ALEATORIOS.get(aleatorio.nextInt(MOTIVOS_ALEATORIOS.size())));
    }
}
