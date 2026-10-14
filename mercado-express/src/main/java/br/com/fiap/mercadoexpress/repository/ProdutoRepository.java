package br.com.fiap.mercadoexpress.repository;

import br.com.fiap.mercadoexpress.model.Produto;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface ProdutoRepository extends JpaRepository<Produto, Long> {

    /**
     * Busca por trecho do nome (sem diferenciar maiúsculas) e, opcionalmente, por setor.
     * Parâmetros vazios são tratados como "sem filtro" no serviço.
     */
    @Query("""
            SELECT p FROM Produto p
            WHERE (:termo IS NULL OR LOWER(p.nome) LIKE LOWER(CONCAT('%', CAST(:termo AS string), '%'))
                   OR LOWER(p.tipo) LIKE LOWER(CONCAT('%', CAST(:termo AS string), '%')))
              AND (:setor IS NULL OR p.setor = :setor)
            ORDER BY p.nome
            """)
    List<Produto> buscar(@Param("termo") String termo, @Param("setor") String setor);

    @Query("SELECT DISTINCT p.setor FROM Produto p ORDER BY p.setor")
    List<String> listarSetores();

    @Query("SELECT p FROM Produto p WHERE p.quantidadeEstoque < :limite ORDER BY p.quantidadeEstoque, p.nome")
    List<Produto> estoqueAbaixoDe(@Param("limite") int limite);

    @Query("SELECT COALESCE(SUM(p.preco * p.quantidadeEstoque), 0) FROM Produto p")
    BigDecimal valorTotalEmEstoque();

    @Query("SELECT COALESCE(SUM(p.quantidadeEstoque), 0) FROM Produto p")
    Long quantidadeTotalEmEstoque();

    List<Produto> findAllByOrderByNomeAsc();

    /**
     * Carrega o produto bloqueando a linha até o fim da transação (SELECT ... FOR UPDATE).
     * Evita que duas compras simultâneas reservem a mesma unidade do estoque.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Produto p WHERE p.id = :id")
    Optional<Produto> buscarParaAtualizarEstoque(@Param("id") Long id);
}
