package br.com.fiap.mercadoexpress.service;

import br.com.fiap.mercadoexpress.exception.RecursoNaoEncontradoException;
import br.com.fiap.mercadoexpress.exception.RegraNegocioException;
import br.com.fiap.mercadoexpress.model.CanalVenda;
import br.com.fiap.mercadoexpress.model.FormaPagamento;
import br.com.fiap.mercadoexpress.model.ItemPedido;
import br.com.fiap.mercadoexpress.model.Pedido;
import br.com.fiap.mercadoexpress.model.Produto;
import br.com.fiap.mercadoexpress.model.StatusPedido;
import br.com.fiap.mercadoexpress.model.Usuario;
import br.com.fiap.mercadoexpress.repository.PedidoRepository;
import br.com.fiap.mercadoexpress.repository.ProdutoRepository;
import br.com.fiap.mercadoexpress.repository.UsuarioRepository;
import br.com.fiap.mercadoexpress.security.UsuarioAutenticado;
import br.com.fiap.mercadoexpress.service.evento.PedidoCriadoEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Criação e consulta de pedidos (compras). */
@Slf4j
@Service
@RequiredArgsConstructor
public class PedidoService {

    /** Limite por item de uma compra (evita digitação errada como 1000 unidades). */
    public static final int QUANTIDADE_MAXIMA_POR_ITEM = 99;

    private final PedidoRepository pedidoRepository;
    private final ProdutoRepository produtoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ApplicationEventPublisher eventos;

    /**
     * Cria o pedido, reserva (baixa) o estoque e, depois do commit, envia o pagamento ao Banco Tranquilo.
     * Se o pagamento for recusado, o estoque reservado é devolvido.
     *
     * @param itens produtoId -> quantidade
     */
    @Transactional
    public Pedido criarPedido(UsuarioAutenticado cliente, Map<Long, Integer> itens,
                              FormaPagamento formaPagamento, CanalVenda canal) {
        if (itens == null || itens.isEmpty()) {
            throw new RegraNegocioException("O carrinho está vazio.");
        }
        if (formaPagamento == null) {
            throw new RegraNegocioException("Escolha a forma de pagamento.");
        }
        Usuario usuario = usuarioRepository.findById(cliente.getId())
                .orElseThrow(() -> new RegraNegocioException("Usuário não encontrado. Faça login novamente."));

        Pedido pedido = new Pedido();
        pedido.setUsuario(usuario);
        pedido.setClienteNome(usuario.getNome());
        pedido.setClienteEmail(usuario.getEmail());
        pedido.setCanal(canal);
        pedido.setFormaPagamento(formaPagamento);
        pedido.setStatus(StatusPedido.AGUARDANDO_PAGAMENTO);

        // Ordena pelo id do produto: todas as compras bloqueiam as linhas na mesma ordem (sem deadlock)
        for (Map.Entry<Long, Integer> entrada : new TreeMap<>(itens).entrySet()) {
            Long produtoId = entrada.getKey();
            int quantidade = entrada.getValue() == null ? 0 : entrada.getValue();
            if (quantidade < 1 || quantidade > QUANTIDADE_MAXIMA_POR_ITEM) {
                throw new RegraNegocioException("Quantidade inválida para o produto " + produtoId
                        + " (de 1 a " + QUANTIDADE_MAXIMA_POR_ITEM + ").");
            }
            Produto produto = produtoRepository.buscarParaAtualizarEstoque(produtoId)
                    .orElseThrow(() -> new RegraNegocioException("Produto " + produtoId + " não encontrado."));
            if (produto.getQuantidadeEstoque() < quantidade) {
                throw new RegraNegocioException("Estoque insuficiente para \"" + produto.getNome() + "\": disponível "
                        + produto.getQuantidadeEstoque() + ", solicitado " + quantidade + ".");
            }
            produto.setQuantidadeEstoque(produto.getQuantidadeEstoque() - quantidade);
            pedido.adicionarItem(ItemPedido.de(produto, quantidade));
        }
        pedido.calcularTotais();

        Pedido salvo = pedidoRepository.save(pedido);
        log.info("Pedido {} criado ({}, {} itens, R$ {}) - aguardando o Banco Tranquilo",
                salvo.getId(), canal, salvo.getQuantidadeItens(), salvo.getValorTotal());
        eventos.publishEvent(new PedidoCriadoEvent(salvo.getId()));
        return salvo;
    }

    /** Pedido com itens. Cliente só vê os próprios pedidos; ADMIN vê todos. */
    @Transactional(readOnly = true)
    public Pedido buscar(Long id, UsuarioAutenticado solicitante) {
        Pedido pedido = pedidoRepository.buscarComItens(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pedido " + id + " não encontrado."));
        boolean dono = pedido.getUsuario() != null && pedido.getUsuario().getId().equals(solicitante.getId());
        if (!dono && !solicitante.isAdmin()) {
            // Mesmo erro de "não encontrado": não revela pedidos de outras pessoas
            throw new RecursoNaoEncontradoException("Pedido " + id + " não encontrado.");
        }
        return pedido;
    }

    @Transactional(readOnly = true)
    public List<Pedido> meusPedidos(UsuarioAutenticado cliente) {
        return pedidoRepository.findByUsuarioIdOrderByIdDesc(cliente.getId());
    }

    @Transactional(readOnly = true)
    public List<Pedido> pedidosRecentes(int quantidade) {
        List<Long> ids = pedidoRepository.idsMaisRecentes(PageRequest.of(0, quantidade));
        return ids.isEmpty() ? List.of() : pedidoRepository.findByIdInOrderByIdDesc(ids);
    }
}
