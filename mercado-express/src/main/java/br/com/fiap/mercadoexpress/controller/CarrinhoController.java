package br.com.fiap.mercadoexpress.controller;

import br.com.fiap.mercadoexpress.carrinho.CarrinhoService;
import br.com.fiap.mercadoexpress.exception.RegraNegocioException;
import br.com.fiap.mercadoexpress.model.FormaPagamento;
import br.com.fiap.mercadoexpress.model.Pedido;
import br.com.fiap.mercadoexpress.model.Produto;
import br.com.fiap.mercadoexpress.security.UsuarioAutenticado;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Carrinho de compras (CLIENTE e ADMIN). Ao finalizar, o pedido é criado e o pagamento
 * é enviado ao Banco Tranquilo pelo RabbitMQ.
 */
@Controller
@RequestMapping("/carrinho")
@RequiredArgsConstructor
public class CarrinhoController {

    private final CarrinhoService carrinhoService;

    @GetMapping
    public String ver(Model model) {
        model.addAttribute("carrinho", carrinhoService.resumo());
        model.addAttribute("formasPagamento", FormaPagamento.values());
        return "carrinho/ver";
    }

    @PostMapping("/itens")
    public String adicionar(@RequestParam Long produtoId,
                            @RequestParam(defaultValue = "1") int quantidade,
                            @RequestParam(required = false) String voltarPara,
                            RedirectAttributes redirect) {
        try {
            Produto produto = carrinhoService.adicionar(produtoId, quantidade);
            redirect.addFlashAttribute("sucesso", quantidade + "x \"" + produto.getNome() + "\" adicionado ao carrinho.");
        } catch (RegraNegocioException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:" + destinoSeguro(voltarPara);
    }

    @PutMapping("/itens/{produtoId:\\d+}")
    public String alterar(@PathVariable Long produtoId, @RequestParam int quantidade) {
        carrinhoService.alterar(produtoId, quantidade);
        return "redirect:/carrinho";
    }

    @DeleteMapping("/itens/{produtoId:\\d+}")
    public String remover(@PathVariable Long produtoId, RedirectAttributes redirect) {
        carrinhoService.remover(produtoId);
        redirect.addFlashAttribute("sucesso", "Produto removido do carrinho.");
        return "redirect:/carrinho";
    }

    @PostMapping("/finalizar")
    public String finalizar(@RequestParam(required = false) FormaPagamento formaPagamento,
                            @AuthenticationPrincipal UsuarioAutenticado usuario,
                            RedirectAttributes redirect) {
        try {
            Pedido pedido = carrinhoService.finalizar(usuario, formaPagamento);
            redirect.addFlashAttribute("sucesso", "Pedido #" + pedido.getId()
                    + " recebido! Aguardando a confirmação do pagamento pelo Banco Tranquilo.");
            return "redirect:/pedidos/" + pedido.getId();
        } catch (RegraNegocioException e) {
            redirect.addFlashAttribute("erro", e.getMessage());
            return "redirect:/carrinho";
        }
    }

    /** Só volta para páginas internas do catálogo (evita redirecionamento para outro site). */
    private static String destinoSeguro(String voltarPara) {
        if (voltarPara != null && voltarPara.matches("/produtos(/\\d+)?(\\?[\\w=&%+.-]*)?")) {
            return voltarPara;
        }
        return "/carrinho";
    }
}
