package br.com.fiap.mercadoexpress.service;

import br.com.fiap.mercadoexpress.exception.RecursoNaoEncontradoException;
import br.com.fiap.mercadoexpress.model.Produto;
import br.com.fiap.mercadoexpress.repository.ProdutoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProdutoService {

    private final ProdutoRepository repository;

    @Transactional(readOnly = true)
    public List<Produto> buscar(String termo, String setor) {
        String termoNormalizado = StringUtils.hasText(termo) ? termo.trim() : null;
        String setorNormalizado = StringUtils.hasText(setor) ? setor.trim() : null;
        return repository.buscar(termoNormalizado, setorNormalizado);
    }

    @Transactional(readOnly = true)
    public Produto buscarPorId(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Produto " + id + " não encontrado."));
    }

    @Transactional(readOnly = true)
    public List<String> listarSetores() {
        return repository.listarSetores();
    }

    @Transactional
    public Produto criar(Produto produto) {
        produto.setId(null);
        normalizar(produto);
        return repository.save(produto);
    }

    @Transactional
    public Produto atualizar(Long id, Produto dados) {
        Produto existente = buscarPorId(id);
        existente.setNome(dados.getNome());
        existente.setTipo(dados.getTipo());
        existente.setSetor(dados.getSetor());
        existente.setTamanho(dados.getTamanho());
        existente.setPreco(dados.getPreco());
        existente.setPrecoCusto(dados.getPrecoCusto());
        existente.setQuantidadeEstoque(dados.getQuantidadeEstoque());
        existente.setDescricao(dados.getDescricao());
        normalizar(existente);
        return repository.save(existente);
    }

    @Transactional
    public Produto excluir(Long id) {
        Produto produto = buscarPorId(id);
        repository.delete(produto);
        return produto;
    }

    // ---------- Indicadores para o painel do administrador ----------

    @Transactional(readOnly = true)
    public long totalProdutos() {
        return repository.count();
    }

    @Transactional(readOnly = true)
    public long quantidadeTotalEmEstoque() {
        return repository.quantidadeTotalEmEstoque();
    }

    @Transactional(readOnly = true)
    public BigDecimal valorTotalEmEstoque() {
        return repository.valorTotalEmEstoque();
    }

    @Transactional(readOnly = true)
    public List<Produto> estoqueBaixo() {
        return repository.estoqueAbaixoDe(Produto.LIMITE_ESTOQUE_BAIXO);
    }

    private void normalizar(Produto produto) {
        produto.setNome(produto.getNome().trim());
        produto.setTipo(produto.getTipo().trim());
        produto.setSetor(produto.getSetor().trim());
        produto.setTamanho(StringUtils.hasText(produto.getTamanho()) ? produto.getTamanho().trim() : null);
        produto.setDescricao(StringUtils.hasText(produto.getDescricao()) ? produto.getDescricao().trim() : null);
    }
}
