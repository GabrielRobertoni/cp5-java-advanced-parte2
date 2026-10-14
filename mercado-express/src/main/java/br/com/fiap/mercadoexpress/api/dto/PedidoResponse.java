package br.com.fiap.mercadoexpress.api.dto;

import br.com.fiap.mercadoexpress.model.ItemPedido;
import br.com.fiap.mercadoexpress.model.Pedido;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Situação de um pedido: pagamento no Banco Tranquilo e e-mail de confirmação. */
public record PedidoResponse(
        Long numeroPedido,
        UUID transacaoId,
        String status,
        String statusDescricao,
        boolean finalizado,
        String formaPagamento,
        String canal,
        BigDecimal valorTotal,
        String codigoAutorizacao,
        String motivoRecusa,
        String clienteNome,
        String clienteEmail,
        String emailStatus,
        String emailStatusDescricao,
        LocalDateTime criadoEm,
        LocalDateTime respondidoEm,
        List<Item> itens) {

    public record Item(Long produtoId, String produto, Integer quantidade, BigDecimal precoUnitario, BigDecimal subtotal) {
        static Item de(ItemPedido item) {
            return new Item(item.getProdutoId(), item.getProdutoNome(), item.getQuantidade(),
                    item.getPrecoUnitario(), item.getSubtotal());
        }
    }

    public static PedidoResponse de(Pedido pedido) {
        return new PedidoResponse(
                pedido.getId(),
                pedido.getTransacaoId(),
                pedido.getStatus().name(),
                pedido.getStatus().getDescricao(),
                !pedido.isAguardandoPagamento(),
                pedido.getFormaPagamento().name(),
                pedido.getCanal().name(),
                pedido.getValorTotal(),
                pedido.getCodigoAutorizacao(),
                pedido.getMotivoRecusa(),
                pedido.getClienteNome(),
                pedido.getClienteEmail(),
                pedido.getEmailStatus().name(),
                pedido.getEmailStatus().getDescricao(),
                pedido.getCriadoEm(),
                pedido.getRespondidoEm(),
                pedido.getItens().stream().map(Item::de).toList());
    }
}
