package br.com.fiap.mercadoexpress.controller;

import br.com.fiap.mercadoexpress.config.ContasDemonstracao;
import br.com.fiap.mercadoexpress.exception.RegraNegocioException;
import br.com.fiap.mercadoexpress.model.Perfil;
import br.com.fiap.mercadoexpress.model.Usuario;
import br.com.fiap.mercadoexpress.security.UsuarioAutenticado;
import br.com.fiap.mercadoexpress.service.DashboardService;
import br.com.fiap.mercadoexpress.service.PagamentoFacade;
import br.com.fiap.mercadoexpress.service.PedidoService;
import br.com.fiap.mercadoexpress.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Área restrita da gerência: dashboard de vendas, compras realizadas e usuários. */
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final DashboardService dashboardService;
    private final PedidoService pedidoService;
    private final PagamentoFacade pagamentoFacade;
    private final UsuarioService usuarioService;
    private final ContasDemonstracao contasDemonstracao;

    @GetMapping("/painel")
    public String painel(Model model) {
        model.addAttribute("indicadores", dashboardService.indicadores());
        model.addAttribute("estoqueBaixo", dashboardService.estoqueBaixo());
        model.addAttribute("ultimosPedidos", pedidoService.pedidosRecentes(8));
        model.addAttribute("totalAdmins", usuarioService.contarPorPerfil(Perfil.ADMIN));
        model.addAttribute("totalClientes", usuarioService.contarPorPerfil(Perfil.CLIENTE));
        return "admin/painel";
    }

    /** Dados dos gráficos do painel (lidos pelo dashboard.js). */
    @GetMapping("/painel/dados")
    @ResponseBody
    public DashboardService.Dashboard dadosDoPainel() {
        return dashboardService.montar();
    }

    /** Compras realizadas: status do pagamento e do e-mail de cada pedido. */
    @GetMapping("/pedidos")
    public String pedidos(Model model) {
        model.addAttribute("pedidos", pedidoService.pedidosRecentes(200));
        return "admin/pedidos";
    }

    @PostMapping("/pedidos/{id:\\d+}/reenviar-email")
    public String reenviarEmail(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            pagamentoFacade.reenviarEmail(id);
            redirect.addFlashAttribute("sucesso", "E-mail do pedido #" + id + " enviado novamente para a fila.");
        } catch (RegraNegocioException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/admin/pedidos";
    }

    @GetMapping("/usuarios")
    public String usuarios(Model model) {
        model.addAttribute("usuarios", usuarioService.listarTodos());
        model.addAttribute("contasDemonstracao", contasDemonstracao);
        model.addAttribute("perfis", Perfil.values());
        return "admin/usuarios";
    }

    @PutMapping("/usuarios/{id:\\d+}/perfil")
    public String alterarPerfil(@PathVariable Long id, @RequestParam Perfil perfil,
                                @AuthenticationPrincipal UsuarioAutenticado logado,
                                RedirectAttributes redirect) {
        try {
            Usuario usuario = usuarioService.alterarPerfil(id, perfil, logado.getEmail());
            redirect.addFlashAttribute("sucesso",
                    usuario.getNome() + " agora é " + usuario.getPerfil().getDescricao() + ".");
        } catch (RegraNegocioException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/admin/usuarios";
    }

    @DeleteMapping("/usuarios/{id:\\d+}")
    public String excluirUsuario(@PathVariable Long id,
                                 @AuthenticationPrincipal UsuarioAutenticado logado,
                                 RedirectAttributes redirect) {
        try {
            Usuario removido = usuarioService.excluir(id, logado.getEmail());
            redirect.addFlashAttribute("sucesso", "Usuário " + removido.getNome() + " excluído.");
        } catch (RegraNegocioException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/admin/usuarios";
    }
}
