package br.com.fiap.mercadoexpress.carrinho;

import br.com.fiap.mercadoexpress.service.PedidoService;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Carrinho de compras do usuário logado, guardado na sessão HTTP (produtoId -> quantidade). */
@Component
@SessionScope
public class Carrinho implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Map<Long, Integer> itens = new LinkedHashMap<>();

    public synchronized void adicionar(Long produtoId, int quantidade) {
        int total = Math.min(itens.getOrDefault(produtoId, 0) + quantidade, PedidoService.QUANTIDADE_MAXIMA_POR_ITEM);
        itens.put(produtoId, total);
    }

    public synchronized void alterar(Long produtoId, int quantidade) {
        if (quantidade <= 0) {
            itens.remove(produtoId);
        } else {
            itens.put(produtoId, Math.min(quantidade, PedidoService.QUANTIDADE_MAXIMA_POR_ITEM));
        }
    }

    public synchronized void remover(Long produtoId) {
        itens.remove(produtoId);
    }

    public synchronized void limpar() {
        itens.clear();
    }

    public synchronized int quantidadeDe(Long produtoId) {
        return itens.getOrDefault(produtoId, 0);
    }

    public synchronized Map<Long, Integer> getItens() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(itens));
    }

    public synchronized int getQuantidadeTotal() {
        return itens.values().stream().mapToInt(Integer::intValue).sum();
    }

    public synchronized boolean isVazio() {
        return itens.isEmpty();
    }
}
