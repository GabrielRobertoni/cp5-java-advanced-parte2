package br.com.fiap.emailservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.UUID;

/** Registro de cada e-mail enviado (ou com falha) para controle do Mercado Express. */
@Entity
@Table(name = "TDS_Emails_Mercado")
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = "text")
public class EmailModel {

    @Id
    @Column(name = "email_id")
    private UUID emailId;

    /** Uma solicitação = um envio. Mensagens repetidas com o mesmo id não geram outro e-mail. */
    @Column(name = "solicitacao_id", nullable = false, unique = true)
    private UUID solicitacaoId;

    @Column(name = "owner_ref", nullable = false, length = 100)
    private String ownerRef;

    @Column(name = "numero_pedido")
    private Long numeroPedido;

    @Column(name = "email_from", nullable = false, length = 150)
    private String emailFrom;

    @Column(name = "email_to", nullable = false, length = 150)
    private String emailTo;

    @Column(name = "nome_destinatario", length = 100)
    private String nomeDestinatario;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_email", nullable = false, length = 20)
    private StatusEmail statusEmail;

    @Column(length = 500)
    private String erro;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "send_date_email")
    private LocalDateTime sendDateEmail;

    @PrePersist
    void aoCriar() {
        if (emailId == null) {
            emailId = UUID.randomUUID();
        }
        if (criadoEm == null) {
            criadoEm = LocalDateTime.now();
        }
    }
}
