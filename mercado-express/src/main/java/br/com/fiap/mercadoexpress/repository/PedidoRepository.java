package br.com.fiap.mercadoexpress.repository;

import br.com.fiap.mercadoexpress.model.Pedido;
import br.com.fiap.mercadoexpress.model.StatusEmail;
import br.com.fiap.mercadoexpress.model.StatusPedido;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    @EntityGraph(attributePaths = "itens")
    @Query("SELECT p FROM Pedido p WHERE p.id = :id")
    Optional<Pedido> buscarComItens(@Param("id") Long id);

    @EntityGraph(attributePaths = "itens")
    List<Pedido> findByUsuarioIdOrderByIdDesc(Long usuarioId);

    /** Ids dos pedidos mais recentes (paginação feita no banco, sem carregar os itens). */
    @Query("SELECT p.id FROM Pedido p ORDER BY p.id DESC")
    List<Long> idsMaisRecentes(Pageable pageable);

    @EntityGraph(attributePaths = "itens")
    List<Pedido> findByIdInOrderByIdDesc(List<Long> ids);

    /** Bloqueia o pedido enquanto a resposta do banco é processada (evita processar duas vezes). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Pedido p WHERE p.id = :id")
    Optional<Pedido> buscarParaAtualizar(@Param("id") Long id);

    /** Pedidos cuja mensagem de pagamento ainda não foi publicada no broker. */
    @Query("SELECT p.id FROM Pedido p WHERE p.status = :status AND p.pagamentoEnviadoEm IS NULL AND p.criadoEm < :limite")
    List<Long> pagamentosNaoPublicados(@Param("status") StatusPedido status, @Param("limite") LocalDateTime limite);

    /** Pedidos pagos cujo pedido de e-mail ainda não foi publicado no broker. */
    @Query("SELECT p.id FROM Pedido p WHERE p.emailStatus = :status AND p.emailAtualizadoEm < :limite")
    List<Long> emailsNaoPublicados(@Param("status") StatusEmail status, @Param("limite") LocalDateTime limite);

    @Modifying
    @Query("UPDATE Pedido p SET p.pagamentoEnviadoEm = :agora WHERE p.id = :id AND p.pagamentoEnviadoEm IS NULL")
    int marcarPagamentoPublicado(@Param("id") Long id, @Param("agora") LocalDateTime agora);

    /**
     * Só passa de PENDENTE para SOLICITADO: se o serviço de e-mail responder muito rápido
     * (ENVIADO/ERRO), essa atualização não sobrescreve o resultado.
     */
    @Modifying
    @Query("""
            UPDATE Pedido p SET p.emailStatus = br.com.fiap.mercadoexpress.model.StatusEmail.SOLICITADO,
                                p.emailSolicitadoEm = :agora
            WHERE p.id = :id AND p.emailSolicitacaoId = :solicitacaoId
              AND p.emailStatus = br.com.fiap.mercadoexpress.model.StatusEmail.PENDENTE
            """)
    int marcarEmailPublicado(@Param("id") Long id, @Param("solicitacaoId") UUID solicitacaoId,
                             @Param("agora") LocalDateTime agora);

    long countByStatus(StatusPedido status);

    // ------------------------------------------------------------------ dashboard

    @Query("SELECT COALESCE(SUM(p.valorTotal), 0) FROM Pedido p WHERE p.status = :status")
    BigDecimal somarValor(@Param("status") StatusPedido status);

    @Query("SELECT COALESCE(SUM(p.custoTotal), 0) FROM Pedido p WHERE p.status = :status")
    BigDecimal somarCusto(@Param("status") StatusPedido status);

    @Query("SELECT COALESCE(SUM(i.quantidade), 0) FROM ItemPedido i WHERE i.pedido.status = :status")
    Long somarUnidades(@Param("status") StatusPedido status);

    @Query("SELECT p.status AS chave, COUNT(p) AS total FROM Pedido p GROUP BY p.status")
    List<Contagem> contarPorStatus();

    @Query("SELECT p.emailStatus AS chave, COUNT(p) AS total FROM Pedido p GROUP BY p.emailStatus")
    List<Contagem> contarPorStatusEmail();

    @Query(value = """
            SELECT CAST(p.respondido_em AS DATE)       AS dia,
                   COUNT(*)                            AS pedidos,
                   SUM(p.valor_total)                  AS receita,
                   SUM(p.valor_total - p.custo_total)  AS lucro
            FROM TDS_Pedidos_Mercado p
            WHERE p.status = 'PAGO' AND p.respondido_em >= :inicio
            GROUP BY CAST(p.respondido_em AS DATE)
            ORDER BY dia
            """, nativeQuery = true)
    List<VendaDiaria> vendasPorDia(@Param("inicio") LocalDateTime inicio);

    @Query(value = """
            SELECT i.produto_setor                                   AS nome,
                   SUM(i.quantidade)                                 AS unidades,
                   SUM(i.subtotal)                                   AS receita,
                   SUM(i.subtotal - i.custo_unitario * i.quantidade) AS lucro
            FROM TDS_Itens_Pedido_Mercado i
            JOIN TDS_Pedidos_Mercado p ON p.id = i.pedido_id
            WHERE p.status = 'PAGO'
            GROUP BY i.produto_setor
            ORDER BY receita DESC
            """, nativeQuery = true)
    List<VendaPorGrupo> vendasPorSetor();

    @Query(value = """
            SELECT i.produto_nome                                    AS nome,
                   SUM(i.quantidade)                                 AS unidades,
                   SUM(i.subtotal)                                   AS receita,
                   SUM(i.subtotal - i.custo_unitario * i.quantidade) AS lucro
            FROM TDS_Itens_Pedido_Mercado i
            JOIN TDS_Pedidos_Mercado p ON p.id = i.pedido_id
            WHERE p.status = 'PAGO'
            GROUP BY i.produto_nome
            ORDER BY unidades DESC, receita DESC
            LIMIT :limite
            """, nativeQuery = true)
    List<VendaPorGrupo> produtosMaisVendidos(@Param("limite") int limite);

    interface Contagem {
        Object getChave();

        Long getTotal();
    }

    interface VendaDiaria {
        LocalDate getDia();

        Long getPedidos();

        BigDecimal getReceita();

        BigDecimal getLucro();
    }

    /** Agrupamento genérico (por setor ou por produto). */
    interface VendaPorGrupo {
        String getNome();

        Long getUnidades();

        BigDecimal getReceita();

        BigDecimal getLucro();
    }
}
