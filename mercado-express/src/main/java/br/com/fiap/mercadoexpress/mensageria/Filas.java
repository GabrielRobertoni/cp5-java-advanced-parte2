package br.com.fiap.mercadoexpress.mensageria;

/**
 * Nomes das exchanges, filas e routing keys usadas pelos três microsserviços
 * (mercado-express, banco-tranquilo e email-service). Os mesmos nomes da aula de RabbitMQ.
 *
 * <pre>
 * pagamento-request-exchange          --pagamento-request-rout-key-->          pagamento-request-queue          (mercado -> banco)
 * pagamento-response-sucesso-exchange --pagamento-response-sucesso-rout-key--> pagamento-response-sucesso-queue (banco -> mercado)
 * pagamento-response-erro-exchange    --pagamento-response-erro-rout-key-->    pagamento-response-erro-queue    (banco -> mercado)
 * email-request-exchange              --email-request-rout-key-->              email-request-queue              (mercado -> e-mail)
 * email-response-exchange             --email-response-rout-key-->             email-response-queue             (e-mail -> mercado)
 * mercado-dead-letter-exchange (fanout) ---------------------------------->    mercado-dead-letter-queue        (mensagens com erro)
 * </pre>
 */
public final class Filas {

    public static final String PAGAMENTO_REQUEST_EXCHANGE = "pagamento-request-exchange";
    public static final String PAGAMENTO_REQUEST_QUEUE = "pagamento-request-queue";
    public static final String PAGAMENTO_REQUEST_ROUTING_KEY = "pagamento-request-rout-key";

    public static final String PAGAMENTO_SUCESSO_EXCHANGE = "pagamento-response-sucesso-exchange";
    public static final String PAGAMENTO_SUCESSO_QUEUE = "pagamento-response-sucesso-queue";
    public static final String PAGAMENTO_SUCESSO_ROUTING_KEY = "pagamento-response-sucesso-rout-key";

    public static final String PAGAMENTO_ERRO_EXCHANGE = "pagamento-response-erro-exchange";
    public static final String PAGAMENTO_ERRO_QUEUE = "pagamento-response-erro-queue";
    public static final String PAGAMENTO_ERRO_ROUTING_KEY = "pagamento-response-erro-rout-key";

    public static final String EMAIL_REQUEST_EXCHANGE = "email-request-exchange";
    public static final String EMAIL_REQUEST_QUEUE = "email-request-queue";
    public static final String EMAIL_REQUEST_ROUTING_KEY = "email-request-rout-key";

    public static final String EMAIL_RESPONSE_EXCHANGE = "email-response-exchange";
    public static final String EMAIL_RESPONSE_QUEUE = "email-response-queue";
    public static final String EMAIL_RESPONSE_ROUTING_KEY = "email-response-rout-key";

    public static final String DEAD_LETTER_EXCHANGE = "mercado-dead-letter-exchange";
    public static final String DEAD_LETTER_QUEUE = "mercado-dead-letter-queue";

    private Filas() {
    }
}
