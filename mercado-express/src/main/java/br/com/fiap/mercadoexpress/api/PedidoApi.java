package br.com.fiap.mercadoexpress.api;

import br.com.fiap.mercadoexpress.api.dto.PedidoResponse;
import br.com.fiap.mercadoexpress.api.dto.ProdutoResponse;
import br.com.fiap.mercadoexpress.security.UsuarioAutenticado;
import br.com.fiap.mercadoexpress.service.DashboardService;
import br.com.fiap.mercadoexpress.service.PedidoService;
import br.com.fiap.mercadoexpress.service.ProdutoService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Consultas da API REST: produtos, pedidos e dados do dashboard. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PedidoApi {

    private final PedidoService pedidoService;
    private final ProdutoService produtoService;
    private final DashboardService dashboardService;

    /** Catálogo, para descobrir o produtoId usado no POST /api/pagamentos. */
    @GetMapping("/produtos")
    public List<ProdutoResponse> produtos() {
        return produtoService.buscar(null, null).stream().map(ProdutoResponse::de).toList();
    }

    @GetMapping("/pedidos")
    public List<PedidoResponse> meusPedidos(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return pedidoService.meusPedidos(usuario).stream().map(PedidoResponse::de).toList();
    }

    @GetMapping("/pedidos/{numeroPedido:\\d+}")
    public PedidoResponse pedido(@PathVariable Long numeroPedido, @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return PedidoResponse.de(pedidoService.buscar(numeroPedido, usuario));
    }

    /** Indicadores da gerência (somente ADMIN). */
    @GetMapping("/admin/dashboard")
    public DashboardService.Dashboard dashboard() {
        return dashboardService.montar();
    }
}
