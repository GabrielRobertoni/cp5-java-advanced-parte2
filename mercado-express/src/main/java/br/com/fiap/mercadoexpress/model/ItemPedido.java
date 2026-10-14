package br.com.fiap.mercadoexpress.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;

/**
 * Item de um pedido. Nome, setor, preço e custo são copiados do produto no momento da compra,
 * assim o histórico de vendas não muda se o produto for editado ou excluído depois.
 */
@Entity
@Table(name = "TDS_Itens_Pedido_Mercado")
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = "pedido")
public class ItemPedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;

    /** Fica nulo se o produto for excluído do catálogo depois da venda. */
    @Column(name = "produto_id")
    private Long produtoId;

    @Column(name = "produto_nome", nullable = false, length = 100)
    private String produtoNome;

    @Column(name = "produto_setor", nullable = false, length = 50)
    private String produtoSetor;

    @Column(nullable = false)
    private Integer quantidade;

    @Column(name = "preco_unitario", nullable = false, precision = 10, scale = 2)
    private BigDecimal precoUnitario;

    @Column(name = "custo_unitario", nullable = false, precision = 10, scale = 2)
    private BigDecimal custoUnitario;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    public static ItemPedido de(Produto produto, int quantidade) {
        ItemPedido item = new ItemPedido();
        item.produtoId = produto.getId();
        item.produtoNome = produto.getNome();
        item.produtoSetor = produto.getSetor();
        item.quantidade = quantidade;
        item.precoUnitario = produto.getPreco();
        item.custoUnitario = produto.getPrecoCusto();
        item.subtotal = produto.getPreco().multiply(BigDecimal.valueOf(quantidade));
        return item;
    }

    public BigDecimal getCustoTotal() {
        return custoUnitario.multiply(BigDecimal.valueOf(quantidade));
    }
}
