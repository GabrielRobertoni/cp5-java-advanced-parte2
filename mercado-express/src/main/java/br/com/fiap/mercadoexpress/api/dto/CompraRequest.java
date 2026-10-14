package br.com.fiap.mercadoexpress.api.dto;

import br.com.fiap.mercadoexpress.model.FormaPagamento;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Corpo do POST /api/pagamentos (compra pelo Postman/Insomnia).
 * <pre>
 * { "formaPagamento": "PIX", "itens": [ { "produtoId": 1, "quantidade": 2 } ] }
 * </pre>
 */
public record CompraRequest(
        @NotNull(message = "Informe a forma de pagamento: PIX, CARTAO_CREDITO ou CARTAO_DEBITO.")
        FormaPagamento formaPagamento,

        @NotEmpty(message = "Informe ao menos um item.")
        @Size(max = 50, message = "No máximo 50 itens por compra.")
        List<@NotNull @Valid Item> itens) {

    public record Item(
            @NotNull(message = "Informe o produtoId.")
            Long produtoId,

            @NotNull(message = "Informe a quantidade.")
            @Min(value = 1, message = "A quantidade mínima é 1.")
            @Max(value = 99, message = "A quantidade máxima é 99.")
            Integer quantidade) {
    }

    /** Junta itens repetidos do mesmo produto. */
    public Map<Long, Integer> itensAgrupados() {
        Map<Long, Integer> agrupados = new LinkedHashMap<>();
        itens.forEach(item -> agrupados.merge(item.produtoId(), item.quantidade(), Integer::sum));
        return agrupados;
    }
}
