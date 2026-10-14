package br.com.fiap.mercadoexpress.carrinho;

import br.com.fiap.mercadoexpress.exception.RegraNegocioException;
import br.com.fiap.mercadoexpress.model.CanalVenda;
import br.com.fiap.mercadoexpress.model.FormaPagamento;
import br.com.fiap.mercadoexpress.model.Pedido;
import br.com.fiap.mercadoexpress.model.Produto;
import br.com.fiap.mercadoexpress.repository.ProdutoRepository;
import br.com.fiap.mercadoexpress.security.UsuarioAutenticado;
import br.com.fiap.mercadoexpress.service.PedidoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CarrinhoService {

    private final Carrinho carrinho;
    private final ProdutoRepository produtoRepository;
    private final PedidoService pedidoService;

    /** Linha do carrinho com os dados atuais do produto. */
    public record ItemCarrinho(Produto produto, int quantidade) {
        public BigDecimal getSubtotal() {
            return produto.getPreco().multiply(BigDecimal.valueOf(quantidade));
        }

        public boolean isEstoqueSuficiente() {
            return produto.getQuantidadeEstoque() >= quantidade;
        }
    }

    public record ResumoCarrinho(List<ItemCarrinho> itens, BigDecimal total, int quantidadeTotal) {
        public boolean isVazio() {
            return itens.isEmpty();
        }

        public boolean isPodeFinalizar() {
            return !itens.isEmpty() && itens.stream().allMatch(ItemCarrinho::isEstoqueSuficiente);
        }
    }

    @Transactional(readOnly = true)
    public ResumoCarrinho resumo() {
        Map<Long, Integer> conteudo = carrinho.getItens();
        Map<Long, Produto> produtos = produtoRepository.findAllById(conteudo.keySet()).stream()
                .collect(Collectors.toMap(Produto::getId, Function.identity()));
        List<ItemCarrinho> itens = new ArrayList<>();
        conteudo.forEach((produtoId, quantidade) -> {
            Produto produto = produtos.get(produtoId);
            if (produto == null) {
                carrinho.remover(produtoId); // excluído do catálogo depois de ir para o carrinho
            } else {
                itens.add(new ItemCarrinho(produto, quantidade));
            }
        });
        BigDecimal total = itens.stream().map(ItemCarrinho::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        int quantidadeTotal = itens.stream().mapToInt(ItemCarrinho::quantidade).sum();
        return new ResumoCarrinho(itens, total, quantidadeTotal);
    }

    @Transactional(readOnly = true)
    public Produto adicionar(Long produtoId, int quantidade) {
        if (quantidade < 1) {
            throw new RegraNegocioException("Informe uma quantidade maior que zero.");
        }
        Produto produto = produtoRepository.findById(produtoId)
                .orElseThrow(() -> new RegraNegocioException("Produto não encontrado."));
        int desejado = carrinho.quantidadeDe(produtoId) + quantidade;
        if (desejado > produto.getQuantidadeEstoque()) {
            throw new RegraNegocioException("Estoque insuficiente para \"" + produto.getNome() + "\": disponível "
                    + produto.getQuantidadeEstoque() + " unidade(s).");
        }
        if (desejado > PedidoService.QUANTIDADE_MAXIMA_POR_ITEM) {
            throw new RegraNegocioException("Limite de " + PedidoService.QUANTIDADE_MAXIMA_POR_ITEM
                    + " unidades por produto.");
        }
        carrinho.adicionar(produtoId, quantidade);
        return produto;
    }

    public void alterar(Long produtoId, int quantidade) {
        carrinho.alterar(produtoId, quantidade);
    }

    public void remover(Long produtoId) {
        carrinho.remover(produtoId);
    }

    /** Fecha a compra: cria o pedido (com reserva de estoque) e esvazia o carrinho. */
    public Pedido finalizar(UsuarioAutenticado cliente, FormaPagamento formaPagamento) {
        if (carrinho.isVazio()) {
            throw new RegraNegocioException("O carrinho está vazio.");
        }
        Pedido pedido = pedidoService.criarPedido(cliente, carrinho.getItens(), formaPagamento, CanalVenda.WEB);
        carrinho.limpar();
        return pedido;
    }
}
