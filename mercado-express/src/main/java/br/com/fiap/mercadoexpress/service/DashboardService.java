package br.com.fiap.mercadoexpress.service;

import br.com.fiap.mercadoexpress.model.Produto;
import br.com.fiap.mercadoexpress.model.StatusEmail;
import br.com.fiap.mercadoexpress.model.StatusPedido;
import br.com.fiap.mercadoexpress.repository.PedidoRepository;
import br.com.fiap.mercadoexpress.repository.ProdutoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Indicadores e séries dos gráficos do dashboard da gerência. */
@Service
@RequiredArgsConstructor
public class DashboardService {

    /** Quantidade de dias exibidos no gráfico de vendas. */
    public static final int DIAS_GRAFICO = 7;

    private final PedidoRepository pedidoRepository;
    private final ProdutoRepository produtoRepository;

    public record Indicadores(
            BigDecimal faturamento,
            BigDecimal custo,
            BigDecimal lucro,
            BigDecimal margemPercentual,
            BigDecimal ticketMedio,
            long pedidosPagos,
            long pedidosRecusados,
            long pedidosAguardando,
            long unidadesVendidas,
            long unidadesEmEstoque,
            BigDecimal valorEmEstoque,
            long emailsEnviados,
            long emailsComErro) {
    }

    public record Serie(String rotulo, long quantidade, BigDecimal receita, BigDecimal lucro) {
    }

    public record Estoque(Long produtoId, String produto, int quantidade, boolean baixo) {
    }

    public record Dashboard(
            Indicadores indicadores,
            List<Serie> vendasPorDia,
            List<Serie> vendasPorSetor,
            List<Serie> produtosMaisVendidos,
            List<Estoque> estoque,
            Map<String, Long> pedidosPorStatus,
            Map<String, Long> emailsPorStatus) {
    }

    @Transactional(readOnly = true)
    public Dashboard montar() {
        return new Dashboard(indicadores(), vendasPorDia(), agrupar(pedidoRepository.vendasPorSetor()),
                agrupar(pedidoRepository.produtosMaisVendidos(5)), estoque(), pedidosPorStatus(), emailsPorStatus());
    }

    @Transactional(readOnly = true)
    public Indicadores indicadores() {
        BigDecimal faturamento = pedidoRepository.somarValor(StatusPedido.PAGO);
        BigDecimal custo = pedidoRepository.somarCusto(StatusPedido.PAGO);
        BigDecimal lucro = faturamento.subtract(custo);
        long pagos = pedidoRepository.countByStatus(StatusPedido.PAGO);
        BigDecimal margem = faturamento.signum() == 0 ? BigDecimal.ZERO
                : lucro.multiply(BigDecimal.valueOf(100)).divide(faturamento, 1, RoundingMode.HALF_UP);
        BigDecimal ticket = pagos == 0 ? BigDecimal.ZERO
                : faturamento.divide(BigDecimal.valueOf(pagos), 2, RoundingMode.HALF_UP);
        Map<String, Long> emails = emailsPorStatus();
        return new Indicadores(
                faturamento, custo, lucro, margem, ticket,
                pagos,
                pedidoRepository.countByStatus(StatusPedido.RECUSADO),
                pedidoRepository.countByStatus(StatusPedido.AGUARDANDO_PAGAMENTO),
                pedidoRepository.somarUnidades(StatusPedido.PAGO),
                produtoRepository.quantidadeTotalEmEstoque(),
                produtoRepository.valorTotalEmEstoque(),
                emails.getOrDefault(StatusEmail.ENVIADO.getDescricao(), 0L),
                emails.getOrDefault(StatusEmail.ERRO.getDescricao(), 0L));
    }

    /** Últimos dias, incluindo os dias sem venda (aparecem com zero no gráfico). */
    private List<Serie> vendasPorDia() {
        LocalDate hoje = LocalDate.now();
        LocalDate inicio = hoje.minusDays(DIAS_GRAFICO - 1L);
        Map<LocalDate, Serie> porDia = new LinkedHashMap<>();
        for (LocalDate dia = inicio; !dia.isAfter(hoje); dia = dia.plusDays(1)) {
            porDia.put(dia, new Serie(rotulo(dia), 0, BigDecimal.ZERO, BigDecimal.ZERO));
        }
        pedidoRepository.vendasPorDia(inicio.atStartOfDay()).forEach(venda ->
                porDia.computeIfPresent(venda.getDia(), (dia, vazio) ->
                        new Serie(rotulo(dia), venda.getPedidos(), venda.getReceita(), venda.getLucro())));
        return new ArrayList<>(porDia.values());
    }

    private static String rotulo(LocalDate dia) {
        return String.format("%02d/%02d", dia.getDayOfMonth(), dia.getMonthValue());
    }

    private static List<Serie> agrupar(List<PedidoRepository.VendaPorGrupo> grupos) {
        return grupos.stream()
                .map(g -> new Serie(g.getNome(), g.getUnidades(), g.getReceita(), g.getLucro()))
                .toList();
    }

    private List<Estoque> estoque() {
        return produtoRepository.findAllByOrderByNomeAsc().stream()
                .map(p -> new Estoque(p.getId(), p.getNome(), p.getQuantidadeEstoque(), p.isEstoqueBaixo()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Produto> estoqueBaixo() {
        return produtoRepository.estoqueAbaixoDe(Produto.LIMITE_ESTOQUE_BAIXO);
    }

    private Map<String, Long> pedidosPorStatus() {
        Map<StatusPedido, Long> contagem = new EnumMap<>(StatusPedido.class);
        for (StatusPedido status : StatusPedido.values()) {
            contagem.put(status, 0L);
        }
        pedidoRepository.contarPorStatus().forEach(c -> contagem.put((StatusPedido) c.getChave(), c.getTotal()));
        Map<String, Long> resultado = new LinkedHashMap<>();
        contagem.forEach((status, total) -> resultado.put(status.getDescricao(), total));
        return resultado;
    }

    private Map<String, Long> emailsPorStatus() {
        Map<StatusEmail, Long> contagem = new EnumMap<>(StatusEmail.class);
        pedidoRepository.contarPorStatusEmail().forEach(c -> contagem.put((StatusEmail) c.getChave(), c.getTotal()));
        Map<String, Long> resultado = new LinkedHashMap<>();
        for (StatusEmail status : StatusEmail.values()) {
            if (status != StatusEmail.NAO_APLICAVEL) {
                resultado.put(status.getDescricao(), contagem.getOrDefault(status, 0L));
            }
        }
        return resultado;
    }
}
