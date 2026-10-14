package br.com.fiap.mercadoexpress.controller;

import br.com.fiap.mercadoexpress.carrinho.Carrinho;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Dados usados no cabeçalho de todas as telas do site (ex.: contador do carrinho). */
@ControllerAdvice(basePackageClasses = HomeController.class)
@RequiredArgsConstructor
public class ModeloGlobalAdvice {

    private final ObjectProvider<Carrinho> carrinho;

    @ModelAttribute("itensNoCarrinho")
    public int itensNoCarrinho(Authentication authentication) {
        // Visitante não tem carrinho (e não cria sessão à toa)
        return HomeController.estaLogado(authentication) ? carrinho.getObject().getQuantidadeTotal() : 0;
    }
}
