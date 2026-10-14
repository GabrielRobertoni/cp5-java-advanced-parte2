package br.com.fiap.mercadoexpress.controller;

import br.com.fiap.mercadoexpress.api.dto.PedidoResponse;
import br.com.fiap.mercadoexpress.security.UsuarioAutenticado;
import br.com.fiap.mercadoexpress.service.PedidoService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/** "Meus pedidos": acompanhamento do pagamento e do e-mail de confirmação. */
@Controller
@RequestMapping("/pedidos")
@RequiredArgsConstructor
public class PedidoController {

    private final PedidoService pedidoService;

    @GetMapping
    public String listar(@AuthenticationPrincipal UsuarioAutenticado usuario, Model model) {
        model.addAttribute("pedidos", pedidoService.meusPedidos(usuario));
        return "pedidos/lista";
    }

    @GetMapping("/{id:\\d+}")
    public String detalhe(@PathVariable Long id, @AuthenticationPrincipal UsuarioAutenticado usuario, Model model) {
        model.addAttribute("pedido", pedidoService.buscar(id, usuario));
        return "pedidos/detalhe";
    }

    /** Consultado pela tela de detalhe (a cada 2 s) enquanto o banco ainda não respondeu. */
    @GetMapping("/{id:\\d+}/status")
    @ResponseBody
    public PedidoResponse status(@PathVariable Long id, @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return PedidoResponse.de(pedidoService.buscar(id, usuario));
    }
}
