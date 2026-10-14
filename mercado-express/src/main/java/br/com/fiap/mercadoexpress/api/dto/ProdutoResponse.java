package br.com.fiap.mercadoexpress.api.dto;

import br.com.fiap.mercadoexpress.model.Produto;

import java.math.BigDecimal;

public record ProdutoResponse(Long id, String nome, String tipo, String setor, String tamanho,
                              BigDecimal preco, Integer quantidadeEstoque) {

    public static ProdutoResponse de(Produto produto) {
        return new ProdutoResponse(produto.getId(), produto.getNome(), produto.getTipo(), produto.getSetor(),
                produto.getTamanho(), produto.getPreco(), produto.getQuantidadeEstoque());
    }
}
