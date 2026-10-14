package br.com.fiap.mercadoexpress.controller;

import br.com.fiap.mercadoexpress.dto.CadastroForm;
import br.com.fiap.mercadoexpress.exception.RegraNegocioException;
import br.com.fiap.mercadoexpress.security.LoginSucessoHandler;
import br.com.fiap.mercadoexpress.service.UsuarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Sign Up: cadastro público de novos usuários (perfil CLIENTE) na tabela TDS_Users_Mercado. */
@Controller
@RequestMapping("/cadastro")
@RequiredArgsConstructor
public class CadastroController {

    private final UsuarioService usuarioService;

    @GetMapping
    public String formulario(@RequestParam(required = false) String email,
                             Authentication authentication, Model model) {
        if (HomeController.estaLogado(authentication)) {
            return "redirect:" + LoginSucessoHandler.destinoPorPerfil(authentication);
        }
        CadastroForm form = new CadastroForm();
        form.setEmail(email);
        model.addAttribute("cadastroForm", form);
        return "cadastro";
    }

    @PostMapping
    public String cadastrar(@Valid @ModelAttribute("cadastroForm") CadastroForm form,
                            BindingResult resultado, RedirectAttributes redirect) {
        if (resultado.hasErrors()) {
            return "cadastro";
        }
        try {
            usuarioService.cadastrarCliente(form);
        } catch (RegraNegocioException e) {
            resultado.rejectValue("email", "email.duplicado", e.getMessage());
            return "cadastro";
        }
        redirect.addFlashAttribute("sucesso", "Cadastro realizado! Agora faça login com seu e-mail e senha.");
        redirect.addAttribute("email", UsuarioService.normalizarEmail(form.getEmail()));
        return "redirect:/login";
    }
}
