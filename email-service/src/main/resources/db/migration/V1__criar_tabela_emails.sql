-- =====================================================================
-- email-service - controle de envio de e-mails aos clientes (schema "email").
-- Mesmos campos do microsserviço de e-mail visto em aula (TB_EMAIL),
-- com o número do pedido e o identificador da solicitação.
-- =====================================================================
CREATE TABLE TDS_Emails_Mercado (
    email_id           UUID          PRIMARY KEY,
    solicitacao_id     UUID          NOT NULL,
    owner_ref          VARCHAR(100)  NOT NULL,
    numero_pedido      BIGINT,
    email_from         VARCHAR(150)  NOT NULL,
    email_to           VARCHAR(150)  NOT NULL,
    nome_destinatario  VARCHAR(100),
    subject            VARCHAR(200)  NOT NULL,
    text               TEXT          NOT NULL,
    status_email       VARCHAR(20)   NOT NULL,
    erro               VARCHAR(500),
    criado_em          TIMESTAMP     NOT NULL,
    send_date_email    TIMESTAMP,
    CONSTRAINT uk_emails_solicitacao UNIQUE (solicitacao_id),
    CONSTRAINT ck_emails_status CHECK (status_email IN ('PROCESSING', 'SENT', 'ERROR'))
);

CREATE INDEX idx_emails_pedido ON TDS_Emails_Mercado (numero_pedido);
CREATE INDEX idx_emails_status ON TDS_Emails_Mercado (status_email);
