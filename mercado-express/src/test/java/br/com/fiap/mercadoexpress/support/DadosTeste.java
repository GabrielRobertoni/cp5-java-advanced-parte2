package br.com.fiap.mercadoexpress.support;

import br.com.fiap.mercadoexpress.model.Produto;
import br.com.fiap.mercadoexpress.repository.PedidoRepository;
import br.com.fiap.mercadoexpress.repository.ProdutoRepository;
import org.springframework.boot.test.context.TestComponent;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Cria produtos próprios para cada teste de compra e apaga tudo no final
 * (os testes de compra não podem rodar dentro de uma transação de teste, porque a
 * publicação no RabbitMQ acontece só depois do commit).
 */
@TestComponent
public class DadosTeste {

    private final ProdutoRepository produtoRepository;
    private final PedidoRepository pedidoRepository;
    private final List<Long> produtosCriados = new ArrayList<>();

    public DadosTeste(ProdutoRepository produtoRepository, PedidoRepository pedidoRepository) {
        this.produtoRepository = produtoRepository;
        this.pedidoRepository = pedidoRepository;
    }

    public Produto produto(String nome, String setor, String preco, String custo, int estoque) {
        Produto produto = produtoRepository.save(new Produto(nome, "Teste", setor, "1 un",
                new BigDecimal(preco), new BigDecimal(custo), estoque, null));
        produtosCriados.add(produto.getId());
        return produto;
    }

    public int estoque(Long produtoId) {
        return produtoRepository.findById(produtoId).orElseThrow().getQuantidadeEstoque();
    }

    public void limpar() {
        pedidoRepository.deleteAll();
        produtoRepository.deleteAllById(produtosCriados);
        produtosCriados.clear();
    }
}
