package br.com.fiap.mercadoexpress.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Compra feita no Mercado Express. O número do pedido é o próprio id.
 * Guarda também o retorno do Banco Tranquilo e o controle do e-mail de confirmação.
 */
@Entity
@Table(name = "TDS_Pedidos_Mercado")
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = {"usuario", "itens"})
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Fica nulo se a conta do cliente for excluída; nome e e-mail continuam no pedido. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(name = "cliente_nome", nullable = false, length = 100)
    private String clienteNome;

    @Column(name = "cliente_email", nullable = false, length = 150)
    private String clienteEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CanalVenda canal;

    @Enumerated(EnumType.STRING)
    @Column(name = "forma_pagamento", nullable = false, length = 20)
    private FormaPagamento formaPagamento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StatusPedido status;

    @Column(name = "valor_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorTotal;

    @Column(name = "custo_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal custoTotal;

    /** Identificador da transação enviado ao Banco Tranquilo (UUID). */
    @Column(name = "transacao_id", nullable = false, unique = true)
    private UUID transacaoId;

    @Column(name = "codigo_autorizacao", length = 30)
    private String codigoAutorizacao;

    @Column(name = "motivo_recusa")
    private String motivoRecusa;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    /** Quando a mensagem foi publicada no broker. Nulo = ainda não publicada (o reenvio tenta de novo). */
    @Column(name = "pagamento_enviado_em")
    private LocalDateTime pagamentoEnviadoEm;

    @Column(name = "respondido_em")
    private LocalDateTime respondidoEm;

    @Enumerated(EnumType.STRING)
    @Column(name = "email_status", nullable = false, length = 20)
    private StatusEmail emailStatus = StatusEmail.NAO_APLICAVEL;

    /** Identifica cada pedido de envio de e-mail (evita enviar duas vezes a mesma mensagem). */
    @Column(name = "email_solicitacao_id")
    private UUID emailSolicitacaoId;

    /** Id do registro do e-mail no serviço de e-mail. */
    @Column(name = "email_id")
    private UUID emailId;

    @Column(name = "email_solicitado_em")
    private LocalDateTime emailSolicitadoEm;

    @Column(name = "email_atualizado_em")
    private LocalDateTime emailAtualizadoEm;

    @Column(name = "email_erro", length = 500)
    private String emailErro;

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<ItemPedido> itens = new ArrayList<>();

    public void adicionarItem(ItemPedido item) {
        item.setPedido(this);
        itens.add(item);
    }

    public void calcularTotais() {
        valorTotal = itens.stream().map(ItemPedido::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        custoTotal = itens.stream().map(ItemPedido::getCustoTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getLucro() {
        return valorTotal.subtract(custoTotal);
    }

    public int getQuantidadeItens() {
        return itens.stream().mapToInt(ItemPedido::getQuantidade).sum();
    }

    public boolean isAguardandoPagamento() {
        return status == StatusPedido.AGUARDANDO_PAGAMENTO;
    }

    public boolean isPago() {
        return status == StatusPedido.PAGO;
    }

    @PrePersist
    void aoCriar() {
        if (criadoEm == null) {
            criadoEm = LocalDateTime.now();
        }
        if (transacaoId == null) {
            transacaoId = UUID.randomUUID();
        }
    }
}
