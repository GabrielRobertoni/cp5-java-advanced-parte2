package br.com.fiap.mercadoexpress.controller;

import br.com.fiap.mercadoexpress.model.Produto;
import br.com.fiap.mercadoexpress.service.ProdutoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * CRUD de produtos (tabela TDS_Sec_MVC_TB_Mercado).
 *
 * GET    /produtos              -> READ   (lista + busca)       ADMIN e CLIENTE
 * GET    /produtos/{id}         -> READ   (detalhe)             ADMIN e CLIENTE
 * GET    /produtos/novo         -> formulário de criação        ADMIN
 * POST   /produtos              -> CREATE                       ADMIN
 * GET    /produtos/{id}/editar  -> formulário de edição         ADMIN
 * PUT    /produtos/{id}         -> UPDATE                       ADMIN
 * DELETE /produtos/{id}         -> DELETE                       ADMIN
 */
@Controller
@RequestMapping("/produtos")
@RequiredArgsConstructor
public class ProdutoController {

    private final ProdutoService produtoService;

    /** Impede que o formulário altere campos controlados pelo sistema. */
    @InitBinder("produto")
    void configurarBinder(WebDataBinder binder) {
        binder.setDisallowedFields("id", "criadoEm", "atualizadoEm");
    }

    @GetMapping
    public String listar(@RequestParam(required = false) String q,
                         @RequestParam(required = false) String setor,
                         Model model) {
        model.addAttribute("produtos", produtoService.buscar(q, setor));
        model.addAttribute("setores", produtoService.listarSetores());
        model.addAttribute("q", q);
        model.addAttribute("setorSelecionado", setor);
        return "produtos/lista";
    }

    @GetMapping("/{id:\\d+}")
    public String detalhe(@PathVariable Long id, Model model) {
        model.addAttribute("produto", produtoService.buscarPorId(id));
        return "produtos/detalhe";
    }

    @GetMapping("/novo")
    public String novo(Model model) {
        Produto produto = new Produto();
        produto.setQuantidadeEstoque(0);
        model.addAttribute("produto", produto);
        model.addAttribute("setores", produtoService.listarSetores());
        return "produtos/form";
    }

    @PostMapping
    public String criar(@Valid @ModelAttribute("produto") Produto produto, BindingResult resultado,
                        Model model, RedirectAttributes redirect) {
        if (resultado.hasErrors()) {
            model.addAttribute("setores", produtoService.listarSetores());
            return "produtos/form";
        }
        Produto salvo = produtoService.criar(produto);
        redirect.addFlashAttribute("sucesso", "Produto \"" + salvo.getNome() + "\" cadastrado com sucesso.");
        return "redirect:/produtos/" + salvo.getId();
    }

    @GetMapping("/{id:\\d+}/editar")
    public String editar(@PathVariable Long id, Model model) {
        model.addAttribute("produto", produtoService.buscarPorId(id));
        model.addAttribute("setores", produtoService.listarSetores());
        return "produtos/form";
    }

    @PutMapping("/{id:\\d+}")
    public String atualizar(@PathVariable Long id, @Valid @ModelAttribute("produto") Produto produto,
                            BindingResult resultado, Model model, RedirectAttributes redirect) {
        if (resultado.hasErrors()) {
            produto.setId(id);
            model.addAttribute("setores", produtoService.listarSetores());
            return "produtos/form";
        }
        Produto atualizado = produtoService.atualizar(id, produto);
        redirect.addFlashAttribute("sucesso", "Produto \"" + atualizado.getNome() + "\" atualizado com sucesso.");
        return "redirect:/produtos/" + id;
    }

    @DeleteMapping("/{id:\\d+}")
    public String excluir(@PathVariable Long id, RedirectAttributes redirect) {
        Produto removido = produtoService.excluir(id);
        redirect.addFlashAttribute("sucesso", "Produto \"" + removido.getNome() + "\" excluído.");
        return "redirect:/produtos";
    }
}
