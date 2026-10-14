-- =====================================================================
-- Banco Tranquilo - registro das transações de pagamento recebidas
-- pela fila pagamento-request-queue (schema "banco").
-- =====================================================================
CREATE TABLE TDS_Transacoes_Banco (
    transacao_id        UUID          PRIMARY KEY,
    numero_pedido       BIGINT,
    valor               NUMERIC(12,2),
    forma_pagamento     VARCHAR(30),
    cliente_nome        VARCHAR(100),
    cliente_email       VARCHAR(150),
    descricao           VARCHAR(255),
    status              VARCHAR(20)   NOT NULL,
    codigo_autorizacao  VARCHAR(30),
    motivo              VARCHAR(255),
    recebida_em         TIMESTAMP     NOT NULL,
    processada_em       TIMESTAMP     NOT NULL,
    CONSTRAINT ck_transacoes_status CHECK (status IN ('APROVADO', 'RECUSADO'))
);

CREATE INDEX idx_transacoes_pedido ON TDS_Transacoes_Banco (numero_pedido);
CREATE INDEX idx_transacoes_status ON TDS_Transacoes_Banco (status);
