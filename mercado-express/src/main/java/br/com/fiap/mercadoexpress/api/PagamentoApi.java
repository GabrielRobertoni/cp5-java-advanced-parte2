package br.com.fiap.mercadoexpress.api;

import br.com.fiap.mercadoexpress.api.dto.CompraRequest;
import br.com.fiap.mercadoexpress.api.dto.PedidoResponse;
import br.com.fiap.mercadoexpress.model.CanalVenda;
import br.com.fiap.mercadoexpress.model.Pedido;
import br.com.fiap.mercadoexpress.security.UsuarioAutenticado;
import br.com.fiap.mercadoexpress.service.PedidoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * API de compra usada nos testes com Postman/Insomnia (mesmo papel da PagamentoAPI da aula).
 * Autenticação: HTTP Basic com o e-mail e a senha de um usuário cadastrado.
 *
 * <p>Responde 202 (Accepted): o pedido foi aceito, mas a confirmação do pagamento chega depois,
 * de forma assíncrona, pelo RabbitMQ. Consulte GET /api/pedidos/{numeroPedido}.
 */
@RestController
@RequestMapping("/api/pagamentos")
@RequiredArgsConstructor
public class PagamentoApi {

    private final PedidoService pedidoService;

    @PostMapping(consumes = "application/json")
    public ResponseEntity<Map<String, Object>> processar(@Valid @RequestBody CompraRequest request,
                                                         @AuthenticationPrincipal UsuarioAutenticado usuario) {
        Pedido pedido = pedidoService.criarPedido(usuario, request.itensAgrupados(), request.formaPagamento(),
                CanalVenda.API);
        PedidoResponse resposta = PedidoResponse.de(pedido);

        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("mensagem", "Pagamento aguardando confirmação do Banco Tranquilo...");
        corpo.put("numeroPedido", resposta.numeroPedido());
        corpo.put("transacaoId", resposta.transacaoId());
        corpo.put("status", resposta.status());
        corpo.put("valorTotal", resposta.valorTotal());
        corpo.put("formaPagamento", resposta.formaPagamento());
        corpo.put("itens", resposta.itens());
        corpo.put("consultarStatus", "/api/pedidos/" + pedido.getId());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .location(URI.create("/api/pedidos/" + pedido.getId()))
                .body(corpo);
    }
}
