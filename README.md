# 🛒 Mercado Express + 🏦 Banco Tranquilo — Microsserviços com RabbitMQ

**Checkpoint 5 · Parte II (Microsserviços e Mensageria) · Java Advanced · FIAP – Análise e Desenvolvimento de Sistemas (TDS)**
Prof. Dr. Marcel Stefan Wagner

Continuação do Mercado Express da Parte I. O cliente monta o **carrinho** e finaliza a compra; o pagamento é enviado
ao **Banco Tranquilo** por uma fila do **RabbitMQ**; o banco aprova ou recusa e responde por outra fila; quando o
pagamento é confirmado, o **serviço de e-mail** envia a confirmação para quem comprou e **grava o envio no PostgreSQL**.
A gerência acompanha tudo em um **dashboard** com compras, vendas, lucro e estoque.

| | |
|---|---|
| 💻 **IDE utilizada** | IntelliJ IDEA |
| 🐇 **Broker** | RabbitMQ 4 (com o painel *Management*) — funciona também com CloudAMQP/LavinMQ |
| 🐘 **Banco de dados** | PostgreSQL 16 (o mesmo banco `mercado_express` da Parte I) |
| 🎬 **Vídeo** | [https://youtu.be/BLynvjXv9UE](https://youtu.be/BLynvjXv9UE) (YouTube, não listado) |
| 🔗 **Parte I** | [cp5-java-advanced-parte1](https://github.com/GabrielRobertoni/cp5-java-advanced-parte1) |

### 🔑 Acesso para avaliação

| Perfil | E-mail | Senha |
|---|---|---|
| **ADMIN** (dashboard, compras, CRUD, usuários) | `admin@mercadoexpress.com` | `Admin@123` |
| **CLIENTE** (catálogo, carrinho, meus pedidos) | `cliente@mercadoexpress.com` | `Cliente@123` |

Além dessas duas contas, **qualquer pessoa pode criar uma conta nova** pela tela **Criar conta (Sign Up)**,
escolhendo o tipo: **Cliente** ou **Administrador** (gerente). A conta é gravada na tabela `TDS_Users_Mercado`
com a senha em BCrypt e já pode ser usada no login. Um administrador também pode mudar o perfil de qualquer conta
(exceto as duas de avaliação) na tela **Usuários**.

## 👥 Integrantes

| Nome | RM |
|---|---|
| Gabriel Robertoni Padilha | 566293 |
| Bruno Ferreira | 563489 |

---

## 📑 Sumário

1. [Requisitos do CP5 × o que foi implementado](#-requisitos-do-cp5--o-que-foi-implementado)
2. [Arquitetura](#️-arquitetura)
3. [Diagramas de classes e de relacionamento](#-diagramas-de-classes-e-de-relacionamento)
4. [Exchanges, filas e mensagens](#-exchanges-filas-e-mensagens)
5. [Os três microsserviços](#-os-três-microsserviços)
6. [Banco de dados](#-banco-de-dados)
7. [Telas (com prints)](#️-telas-com-prints)
8. [Testes com Postman e pelo painel do RabbitMQ](#-testes-com-postman-e-pelo-painel-do-rabbitmq)
9. [Como executar](#️-como-executar)
10. [E-mail real pelo Gmail](#-e-mail-real-pelo-gmail-senha-de-app)
11. [Testes automatizados](#-testes-automatizados)

---

## ✅ Requisitos do CP5 × o que foi implementado

| Requisito do enunciado (Parte II) | Como foi atendido |
|---|---|
| Programa em Java no Mercado Express com conexão a uma API do Banco Tranquilo | Microsserviço **`banco-tranquilo`** (a "API de pagamentos" do banco) conectado ao **`mercado-express`** só por mensageria |
| Spring Boot criado pelo Spring Initializr | Três projetos Spring Boot 4.1.1 / Java 21 / Maven (configuração do Initializr de cada um [mais abaixo](#-os-três-microsserviços)) |
| RabbitMQ como broker, com **exchanges e queues** para as transações bancárias, com requisições e respostas | 5 exchanges *direct* + 6 filas duráveis: requisição de pagamento, resposta de **sucesso**, resposta de **erro**, requisição de e-mail, resposta de e-mail e uma *dead letter queue* ([detalhes](#-exchanges-filas-e-mensagens)) |
| Compra confirmada pela API do banco ⇒ e-mail de confirmação para quem comprou | Ao receber o **APROVADO**, o mercado publica em `email-request-queue`; o **`email-service`** envia o e-mail (HTML com os itens, total e código de autorização) para o e-mail do cliente logado |
| Banco de dados (de preferência o PostgreSQL da Parte I) | **PostgreSQL**, banco `mercado_express` da Parte I. Cada microsserviço tem seu schema: `public` (mercado), `banco` e `email`. Tabelas versionadas com **Flyway** |
| Informação de configuração do BD e exemplo de requisição JSON para Postman/Insomnia (ou script de testes) | Configuração em cada `application.yml` + [seção Banco de dados](#-banco-de-dados). JSON de exemplo em [`postman/exemplo-compra.json`](postman/exemplo-compra.json) e **coleção do Postman com testes automáticos** em [`postman/`](postman/) |
| Tela com o carrinho e, ao finalizar, execução da integração de pagamentos | Carrinho (`/carrinho`) com quantidades, total e forma de pagamento. Ao finalizar, o pedido é criado e o pagamento vai para a fila do banco; a tela do pedido **se atualiza sozinha** quando a resposta chega |
| Vídeo mostrando a integração, o **Dashboard do RabbitMQ**, testes internos pelo RabbitMQ e pelo Postman | Roteiro em [Testes com Postman e pelo painel do RabbitMQ](#-testes-com-postman-e-pelo-painel-do-rabbitmq) e mensagens prontas em [`exemplos-rabbitmq/`](exemplos-rabbitmq/) |
| Envio de e-mail armazenado no PostgreSQL para controle | Tabela **`email.TDS_Emails_Mercado`** (mesmos campos do serviço de e-mail da aula: `ownerRef`, `emailFrom`, `emailTo`, `subject`, `text`, `statusEmail`, `sendDateEmail`) + status do e-mail em cada pedido |
| Dashboard final da gerência com compras, gráficos de vendas, lucros, estoque | `/admin/painel`: 8 indicadores (faturamento, lucro, margem, ticket médio...) e 5 gráficos (vendas por dia, receita × lucro por setor, mais vendidos, status dos pagamentos, estoque). `/admin/pedidos`: todas as compras com retorno do banco e do e-mail |

---

## 🏗️ Arquitetura

```mermaid
flowchart LR
    C([Cliente<br/>navegador]) -->|carrinho / finalizar| M
    P([Postman /<br/>Insomnia]) -->|POST /api/pagamentos| M
    G([Gerente]) -->|dashboard| M

    subgraph RabbitMQ
        Q1[[pagamento-request-queue]]
        Q2[[pagamento-response-sucesso-queue]]
        Q3[[pagamento-response-erro-queue]]
        Q4[[email-request-queue]]
        Q5[[email-response-queue]]
    end

    M[mercado-express<br/>:8080] -->|pedido de pagamento| Q1
    Q1 --> B[banco-tranquilo]
    B -->|APROVADO| Q2
    B -->|RECUSADO| Q3
    Q2 --> M
    Q3 --> M
    M -->|pagamento aprovado| Q4
    Q4 --> E[email-service]
    E -->|SENT / ERROR| Q5
    Q5 --> M
    E -->|SMTP| S([Gmail / Mailpit])

    M --- DB[(PostgreSQL<br/>mercado_express)]
    B --- DB
    E --- DB
```

**Por que mensageria e não uma chamada REST direta ao banco?** (conceitos da Aula 17) A chamada fica
**assíncrona**: o mercado responde na hora (`202 Accepted` / "aguardando confirmação") e não trava esperando o banco.
Se o Banco Tranquilo ou o serviço de e-mail estiverem fora do ar, **as mensagens ficam guardadas nas filas** e são
processadas quando eles voltarem — nenhuma compra se perde. Se o próprio RabbitMQ cair, o mercado guarda o pedido
como "não publicado" e um job reenvia quando o broker voltar.

### Fluxo de uma compra

```mermaid
sequenceDiagram
    autonumber
    actor Cliente
    participant M as mercado-express
    participant R as RabbitMQ
    participant B as banco-tranquilo
    participant E as email-service
    Cliente->>M: Finalizar compra (carrinho)
    M->>M: grava o pedido (AGUARDANDO_PAGAMENTO) e reserva o estoque
    M-->>Cliente: Pedido #N recebido, aguardando o banco
    M->>R: pagamento-request-exchange (após o commit)
    R->>B: pagamento-request-queue
    B->>B: analisa e grava a transação (schema banco)
    alt aprovado
        B->>R: pagamento-response-sucesso-exchange
        R->>M: pagamento-response-sucesso-queue
        M->>M: pedido PAGO
        M->>R: email-request-exchange
        R->>E: email-request-queue
        E->>E: envia o e-mail e grava (schema email)
        E->>R: email-response-exchange (SENT/ERROR)
        R->>M: email-response-queue → status do e-mail no pedido
    else recusado
        B->>R: pagamento-response-erro-exchange
        R->>M: pagamento-response-erro-queue
        M->>M: pedido RECUSADO e estoque devolvido
    end
    Cliente->>M: tela do pedido consulta o status (a cada 2 s) e se atualiza
```

---

## 📐 Diagramas de classes e de relacionamento

### Diagrama de classes – domínio do Mercado Express

Entidades JPA do `mercado-express` e os enums que elas usam. O pedido guarda os itens com o preço e o custo da
época da venda (por isso o item aponta para o produto só pelo `produtoId`).

```mermaid
classDiagram
    direction LR
    class Usuario {
        -Long id
        -String nome
        -String email
        -String senha
        -Perfil perfil
        -LocalDateTime criadoEm
    }
    class Produto {
        -Long id
        -String nome
        -String tipo
        -String setor
        -String tamanho
        -BigDecimal preco
        -BigDecimal precoCusto
        -Integer quantidadeEstoque
        -String descricao
        +isEstoqueBaixo() boolean
        +isDisponivel() boolean
    }
    class Pedido {
        -Long id
        -String clienteNome
        -String clienteEmail
        -CanalVenda canal
        -FormaPagamento formaPagamento
        -StatusPedido status
        -BigDecimal valorTotal
        -BigDecimal custoTotal
        -UUID transacaoId
        -String codigoAutorizacao
        -String motivoRecusa
        -StatusEmail emailStatus
        -UUID emailSolicitacaoId
        +adicionarItem(ItemPedido item)
        +calcularTotais()
        +getLucro() BigDecimal
    }
    class ItemPedido {
        -Long id
        -Long produtoId
        -String produtoNome
        -String produtoSetor
        -Integer quantidade
        -BigDecimal precoUnitario
        -BigDecimal custoUnitario
        -BigDecimal subtotal
        +getCustoTotal() BigDecimal
    }
    class Perfil {
        <<enumeration>>
        ADMIN
        CLIENTE
    }
    class StatusPedido {
        <<enumeration>>
        AGUARDANDO_PAGAMENTO
        PAGO
        RECUSADO
    }
    class StatusEmail {
        <<enumeration>>
        NAO_APLICAVEL
        PENDENTE
        SOLICITADO
        ENVIADO
        ERRO
    }
    class FormaPagamento {
        <<enumeration>>
        PIX
        CARTAO_CREDITO
        CARTAO_DEBITO
    }
    class CanalVenda {
        <<enumeration>>
        WEB
        API
    }
    Pedido "0..*" --> "0..1" Usuario : usuario
    Pedido "1" *-- "1..*" ItemPedido : itens
    ItemPedido ..> Produto : produtoId
    Usuario --> Perfil
    Pedido --> StatusPedido
    Pedido --> StatusEmail
    Pedido --> FormaPagamento
    Pedido --> CanalVenda
```

### Diagrama de classes – serviços e mensageria dos três microsserviços

As setas contínuas são chamadas entre classes do mesmo serviço; as tracejadas são mensagens que passam pelas
filas do RabbitMQ (um serviço nunca chama o outro diretamente).

```mermaid
classDiagram
    direction TB
    namespace mercadoExpress {
        class CarrinhoService {
            +finalizar(cliente, formaPagamento) Pedido
        }
        class PagamentoApi {
            +processar(CompraRequest) ResponseEntity
        }
        class PedidoService {
            +criarPedido(cliente, itens, forma, canal) Pedido
            +buscar(id, solicitante) Pedido
        }
        class PagamentoFacade {
            +solicitarPagamento(pedidoId) boolean
            +sucessoPagamento(PagamentoResposta)
            +erroPagamento(PagamentoResposta)
            +solicitarEmail(pedidoId) boolean
            +atualizarStatusEmail(EmailStatusMensagem)
        }
        class PagamentoRequestProducer {
            +integrar(PagamentoRequest)
        }
        class EmailRequestProducer {
            +solicitar(EmailRequest)
        }
        class PagamentoResponseSucessoConsumidor {
            +receber(PagamentoResposta)
        }
        class PagamentoResponseErroConsumidor {
            +receber(PagamentoResposta)
        }
        class EmailResponseConsumidor {
            +receber(EmailStatusMensagem)
        }
    }
    namespace bancoTranquilo {
        class PagamentoRequestConsumidor {
            +receberMensagem(PagamentoRequest)
        }
        class PagamentoService {
            +processar(PagamentoRequest) PagamentoResposta
        }
        class AnalisePagamento {
            +analisar(PagamentoRequest) Decisao
        }
        class PagamentoSucessoProdutor {
            +gerarResposta(PagamentoResposta)
        }
        class PagamentoErroProdutor {
            +gerarResposta(PagamentoResposta)
        }
    }
    namespace emailService {
        class EmailConsumer {
            +listen(EmailRequest)
        }
        class EmailService {
            +processar(EmailRequest) EmailModel
        }
        class EmailStatusProdutor {
            +informar(EmailModel)
        }
    }
    CarrinhoService --> PedidoService
    PagamentoApi --> PedidoService
    PedidoService ..> PagamentoFacade : evento após o commit
    PagamentoFacade --> PagamentoRequestProducer
    PagamentoFacade --> EmailRequestProducer
    PagamentoResponseSucessoConsumidor --> PagamentoFacade
    PagamentoResponseErroConsumidor --> PagamentoFacade
    EmailResponseConsumidor --> PagamentoFacade
    PagamentoRequestProducer ..> PagamentoRequestConsumidor : pagamento-request-queue
    PagamentoRequestConsumidor --> PagamentoService
    PagamentoService --> AnalisePagamento
    PagamentoRequestConsumidor --> PagamentoSucessoProdutor
    PagamentoRequestConsumidor --> PagamentoErroProdutor
    PagamentoSucessoProdutor ..> PagamentoResponseSucessoConsumidor : pagamento-response-sucesso-queue
    PagamentoErroProdutor ..> PagamentoResponseErroConsumidor : pagamento-response-erro-queue
    EmailRequestProducer ..> EmailConsumer : email-request-queue
    EmailConsumer --> EmailService
    EmailConsumer --> EmailStatusProdutor
    EmailStatusProdutor ..> EmailResponseConsumidor : email-response-queue
```

### Diagrama de relacionamento (banco de dados)

Tabelas do banco `mercado_express`. As três primeiras do Mercado (schema `public`) têm chaves estrangeiras de verdade.
`TDS_TRANSACOES_BANCO` (schema `banco`) e `TDS_EMAILS_MERCADO` (schema `email`) pertencem a outros microsserviços:
a ligação com o pedido é feita pelo conteúdo das mensagens (`transacao_id` e `numero_pedido`), por isso aparece
tracejada.

```mermaid
erDiagram
    TDS_USERS_MERCADO ||--o{ TDS_PEDIDOS_MERCADO : "faz"
    TDS_PEDIDOS_MERCADO ||--|{ TDS_ITENS_PEDIDO_MERCADO : "contém"
    TDS_SEC_MVC_TB_MERCADO |o--o{ TDS_ITENS_PEDIDO_MERCADO : "vendido em"
    TDS_PEDIDOS_MERCADO ||..o| TDS_TRANSACOES_BANCO : "transacao_id"
    TDS_PEDIDOS_MERCADO ||..o{ TDS_EMAILS_MERCADO : "numero_pedido"

    TDS_USERS_MERCADO {
        BIGINT id PK
        VARCHAR nome
        VARCHAR email UK
        VARCHAR senha "hash BCrypt"
        VARCHAR perfil "ADMIN ou CLIENTE"
        TIMESTAMP criado_em
    }
    TDS_SEC_MVC_TB_MERCADO {
        BIGINT id PK
        VARCHAR nome
        VARCHAR tipo
        VARCHAR setor
        VARCHAR tamanho
        NUMERIC preco
        NUMERIC preco_custo
        INTEGER quantidade_estoque
        VARCHAR descricao
        TIMESTAMP criado_em
        TIMESTAMP atualizado_em
    }
    TDS_PEDIDOS_MERCADO {
        BIGINT id PK "numero do pedido"
        BIGINT usuario_id FK
        VARCHAR cliente_nome
        VARCHAR cliente_email
        VARCHAR canal "WEB ou API"
        VARCHAR forma_pagamento
        VARCHAR status "AGUARDANDO_PAGAMENTO, PAGO, RECUSADO"
        NUMERIC valor_total
        NUMERIC custo_total
        UUID transacao_id UK
        VARCHAR codigo_autorizacao
        VARCHAR motivo_recusa
        TIMESTAMP criado_em
        TIMESTAMP pagamento_enviado_em
        TIMESTAMP respondido_em
        VARCHAR email_status
        UUID email_solicitacao_id
        UUID email_id
        TIMESTAMP email_atualizado_em
        VARCHAR email_erro
    }
    TDS_ITENS_PEDIDO_MERCADO {
        BIGINT id PK
        BIGINT pedido_id FK
        BIGINT produto_id FK
        VARCHAR produto_nome
        VARCHAR produto_setor
        INTEGER quantidade
        NUMERIC preco_unitario
        NUMERIC custo_unitario
        NUMERIC subtotal
    }
    TDS_TRANSACOES_BANCO {
        UUID transacao_id PK
        BIGINT numero_pedido
        NUMERIC valor
        VARCHAR forma_pagamento
        VARCHAR cliente_email
        VARCHAR status "APROVADO ou RECUSADO"
        VARCHAR codigo_autorizacao
        VARCHAR motivo
        TIMESTAMP recebida_em
        TIMESTAMP processada_em
    }
    TDS_EMAILS_MERCADO {
        UUID email_id PK
        UUID solicitacao_id UK
        VARCHAR owner_ref
        BIGINT numero_pedido
        VARCHAR email_from
        VARCHAR email_to
        VARCHAR subject
        TEXT text
        VARCHAR status_email "PROCESSING, SENT, ERROR"
        VARCHAR erro
        TIMESTAMP criado_em
        TIMESTAMP send_date_email
    }
```

---

## 🐇 Exchanges, filas e mensagens

Mesmos nomes usados na aula de RabbitMQ (exchange + routing key → queue), com as filas de e-mail e uma dead letter queue:

| Exchange (direct) | Routing key | Fila (durável) | De → para |
|---|---|---|---|
| `pagamento-request-exchange` | `pagamento-request-rout-key` | `pagamento-request-queue` | mercado → banco |
| `pagamento-response-sucesso-exchange` | `pagamento-response-sucesso-rout-key` | `pagamento-response-sucesso-queue` | banco → mercado |
| `pagamento-response-erro-exchange` | `pagamento-response-erro-rout-key` | `pagamento-response-erro-queue` | banco → mercado |
| `email-request-exchange` | `email-request-rout-key` | `email-request-queue` | mercado → e-mail |
| `email-response-exchange` | `email-response-rout-key` | `email-response-queue` | e-mail → mercado |
| `mercado-dead-letter-exchange` (fanout) | — | `mercado-dead-letter-queue` | mensagens com erro |

- **Não é preciso criar nada no painel:** cada microsserviço declara toda a topologia ao subir
  ([`RabbitMQConfig`](mercado-express/src/main/java/br/com/fiap/mercadoexpress/mensageria/RabbitMQConfig.java)).
  A declaração é idempotente, então qualquer serviço pode subir primeiro.
- **Mensagens em JSON** (`JacksonJsonMessageConverter`). O tipo de destino é o parâmetro do `@RabbitListener`, então
  também são aceitas mensagens publicadas à mão pelo painel do RabbitMQ (inclusive os JSONs de exemplo das aulas).
- **Mensagem com erro não fica em loop:** 3 tentativas e depois vai para a `mercado-dead-letter-queue`.
- **Idempotência:** a transação tem um `transacaoId` (UUID). Se o banco receber a mesma mensagem duas vezes, devolve a
  mesma resposta sem cobrar de novo; o mercado ignora respostas repetidas; o serviço de e-mail não envia duas vezes a
  mesma solicitação.
- **Publicação depois do commit:** o pagamento só é publicado depois que o pedido foi gravado
  (`@TransactionalEventListener(AFTER_COMMIT)`), então o banco nunca recebe um pedido que não existe.

Exemplo de mensagem do mercado para o banco (`pagamento-request-queue`):

```json
{
  "transacaoId": "c0a8e1f4-5b7d-4c55-9f0e-0a1b2c3d4e5f",
  "numeroPedido": 2,
  "valor": 83.70,
  "formaPagamento": "CARTAO_CREDITO",
  "clienteNome": "Cliente Demonstração",
  "clienteEmail": "cliente@mercadoexpress.com",
  "descricao": "Mercado Express - pedido #2 (3 itens)",
  "solicitadoEm": "2026-10-14T18:25:31"
}
```

Resposta do banco (`pagamento-response-sucesso-queue` ou `pagamento-response-erro-queue`):

```json
{ "transacaoId": "c0a8e1f4-...", "numeroPedido": 2, "status": "APROVADO", "valor": 83.70,
  "codigoAutorizacao": "TQ-237882", "motivo": null, "processadoEm": "2026-10-14T18:25:35" }
```

---

## 🧩 Os três microsserviços

```
cp5-java-advanced-parte2/
├── pom.xml                  # agregador: compila e testa os três de uma vez (./mvnw test)
├── mercado-express/         # loja (MVC + Security), API REST, carrinho, pedidos e dashboard  → porta 8080
├── banco-tranquilo/         # API de pagamentos do banco (worker RabbitMQ, sem servidor web)
├── email-service/           # envio e controle dos e-mails (worker RabbitMQ, sem servidor web)
├── postman/                 # coleção do Postman com testes automáticos + JSON de exemplo
├── exemplos-rabbitmq/       # mensagens prontas para publicar pelo painel do RabbitMQ
└── docker-compose.yml       # PostgreSQL + RabbitMQ + Mailpit (para quem usa Docker)
```

### 1. `mercado-express` (porta 8080)

Evolução do projeto da Parte I (login próprio, perfis ADMIN/CLIENTE e CRUD de produtos). O **Sign Up** agora deixa
escolher o tipo de conta (Cliente ou Administrador), para cadastrar novos gerentes e novos clientes. Além disso:

| Pacote | Classes principais |
|---|---|
| `mensageria` | `RabbitMQConfig` (topologia + conversor JSON), `Filas` (nomes) |
| `mensageria.producer` | `PagamentoRequestProducer`, `EmailRequestProducer` |
| `mensageria.consumer` | `PagamentoResponseSucessoConsumidor`, `PagamentoResponseErroConsumidor`, `EmailResponseConsumidor` |
| `service` | `PagamentoFacade` (como na aula: solicita o pagamento e trata sucesso/erro), `PedidoService` (cria o pedido e reserva o estoque), `DashboardService`, `ReenvioMensagensJob` |
| `carrinho` | `Carrinho` (sessão do usuário) e `CarrinhoService` |
| `api` | `PagamentoApi` (`POST /api/pagamentos`), `PedidoApi` (consultas), tratamento de erros em JSON |
| `controller` | `CarrinhoController`, `PedidoController`, `AdminController` (dashboard e compras) e os da Parte I |

**Spring Initializr:** Maven · Java 21 · Spring Boot 4.1.1 · Jar · YAML · dependências **Spring Web, Thymeleaf,
Spring Security, Spring for RabbitMQ, Spring Data JPA, PostgreSQL Driver, Flyway Migration, Validation, Lombok,
Spring Boot DevTools**.

### 2. `banco-tranquilo` (worker)

Recebe `pagamento-request-queue`, decide, grava a transação em `banco.TDS_Transacoes_Banco` e responde na fila de
sucesso ou de erro (`PagamentoRequestConsumidor`, `PagamentoSucessoProdutor`, `PagamentoErroProdutor`).
Não existe integração com bandeira de cartão: as regras simulam um banco (e, como o worker da aula, uma parte
aleatória das compras é recusada para exercitar os dois fluxos):

| Regra | Resultado |
|---|---|
| Valor ausente, zero ou negativo | RECUSADO – "Valor inválido" |
| Forma de pagamento diferente de PIX / CARTAO_CREDITO / CARTAO_DEBITO | RECUSADO |
| Valor acima de **R$ 500,00** (`banco.limite-por-transacao`) | RECUSADO – "Limite por transação excedido" |
| Demais compras | APROVADO em **85%** dos casos (`banco.taxa-aprovacao`), com código `TQ-123456`; os outros 15% são recusados com "Saldo insuficiente" ou "Transação não autorizada pelo emissor" |

O banco espera 2 segundos antes de responder (`banco.tempo-processamento`), para dar para ver no vídeo o pedido
"aguardando" e a resposta chegando. Tudo isso é configurável no `application.yml` ou por variável de ambiente.

**Spring Initializr:** Maven · Java 21 · Spring Boot 4.1.1 · Jar · YAML · **Spring for RabbitMQ, Spring Data JPA,
PostgreSQL Driver, Flyway Migration, Lombok**.

### 3. `email-service` (worker)

Baseado no microsserviço de e-mail da Aula 16: escuta `email-request-queue`, grava o registro como `PROCESSING`,
envia pelo SMTP (`JavaMailSender`) e marca `SENT` ou `ERROR`. Quando a mensagem traz os dados do pedido, o e-mail sai
em **HTML** (modelo Thymeleaf com itens, total, forma de pagamento e código de autorização) com versão em texto.
Depois publica o resultado em `email-response-queue`, e o mercado mostra o status do e-mail em cada compra.

**Spring Initializr:** Maven · Java 21 · Spring Boot 4.1.1 · Jar · YAML · **Spring for RabbitMQ, Java Mail Sender,
Thymeleaf, Validation, Spring Data JPA, PostgreSQL Driver, Flyway Migration, Lombok**.

---

## 🐘 Banco de dados

Um único banco PostgreSQL, **`mercado_express`** (o da Parte I), com um schema por microsserviço. As tabelas são
criadas pelo **Flyway** e o Hibernate só valida o mapeamento.

| Schema | Tabela | Conteúdo |
|---|---|---|
| `public` | `TDS_Sec_MVC_TB_Mercado` | Produtos (Parte I) + **`preco_custo`** para o cálculo do lucro |
| `public` | `TDS_Users_Mercado` | Usuários e perfis (Parte I) |
| `public` | **`TDS_Pedidos_Mercado`** | Compras: cliente, forma de pagamento, total, custo, `transacao_id`, status do pagamento, código de autorização, motivo da recusa, status do e-mail |
| `public` | **`TDS_Itens_Pedido_Mercado`** | Itens de cada compra (nome, setor, preço e custo da época da venda) |
| `banco` | **`TDS_Transacoes_Banco`** | Transações processadas pelo Banco Tranquilo |
| `email` | **`TDS_Emails_Mercado`** | Controle de envio de e-mails: `owner_ref`, `email_from`, `email_to`, `subject`, `text`, `status_email` (PROCESSING/SENT/ERROR), `erro`, `send_date_email` |

**Configuração** (igual nos três `application.yml`; valores padrão para rodar local, sobrescritos por variáveis de ambiente):

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:mercado_express}
    username: ${DB_USER:postgres}
    password: ${DB_PASSWORD:postgres}
  rabbitmq:
    addresses: ${RABBITMQ_ADDRESSES:amqp://guest:guest@localhost:5672}
```

Consultas úteis (pgAdmin, DBeaver ou `psql`):

```sql
SELECT id, cliente_email, valor_total, status, codigo_autorizacao, email_status FROM tds_pedidos_mercado ORDER BY id DESC;
SELECT numero_pedido, valor, status, codigo_autorizacao, motivo FROM banco.tds_transacoes_banco ORDER BY processada_em DESC;
SELECT numero_pedido, email_to, subject, status_email, send_date_email FROM email.tds_emails_mercado ORDER BY criado_em DESC;
```

---

## 🖥️ Telas (com prints)

> Prints feitos com o sistema rodando na máquina (PostgreSQL 16, RabbitMQ 4.3 e os três microsserviços).

### Landing page
![Landing page](docs/prints/01-landing.png)

### Login (o mesmo da Parte I)
![Login](docs/prints/02-login.png)

### Cadastro (Sign Up) com escolha do tipo de conta
Qualquer pessoa cria uma conta de **Cliente** ou de **Administrador** (gerente). Depois do cadastro, o login leva
cada perfil para a sua área: o administrador para o dashboard e o cliente para o catálogo.

![Cadastro com tipo de conta](docs/prints/22-cadastro-tipo-de-conta.png)

### Catálogo do cliente com "+ Carrinho"
![Catálogo](docs/prints/03-catalogo-cliente.png)

![Produto adicionado](docs/prints/04-produto-adicionado.png)

### Carrinho e forma de pagamento
![Carrinho](docs/prints/05-carrinho.png)

### Pedido aguardando o Banco Tranquilo
Logo depois de finalizar: o pedido foi gravado e a mensagem foi para a fila. A página consulta o status a cada 2 s.

![Aguardando o banco](docs/prints/06-pedido-aguardando-banco.png)

### Pagamento confirmado e e-mail enviado
A página se atualiza sozinha quando chegam as respostas do banco e do serviço de e-mail.

![Pedido pago](docs/prints/07-pedido-pago-email-enviado.png)

### Pagamento recusado (acima do limite do banco)
O estoque reservado é devolvido.

![Pedido recusado](docs/prints/08-pedido-recusado.png)

### Meus pedidos
![Meus pedidos](docs/prints/09-meus-pedidos.png)

### E-mail de confirmação recebido
Caixa local do Mailpit (com o Gmail configurado, o mesmo e-mail chega na caixa do cliente).

![Caixa de e-mails](docs/prints/10-mailpit-caixa.png)

![E-mail de confirmação](docs/prints/11-email-confirmacao.png)

### Dashboard da gerência
![Dashboard](docs/prints/12-dashboard.png)

### Compras realizadas (retorno do banco e controle de e-mail)
![Compras realizadas](docs/prints/14-compras-realizadas.png)

### Cadastro de produto com custo (para o lucro)
![Produto com custo](docs/prints/15-produto-com-custo.png)

### Versão para celular
<img src="docs/prints/21-mobile-carrinho.png" alt="Carrinho no celular" width="320">

### RabbitMQ Management
| Visão geral | Filas |
|---|---|
| ![Overview](docs/prints/16-rabbitmq-overview.png) | ![Filas](docs/prints/17-rabbitmq-filas.png) |

| Exchanges | Binding da exchange de pagamento |
|---|---|
| ![Exchanges](docs/prints/18-rabbitmq-exchanges.png) | ![Binding](docs/prints/19-rabbitmq-binding-pagamento.png) |

Fila `pagamento-request-queue` (detalhes, consumidor conectado e gráfico de mensagens):

![Fila de pagamento](docs/prints/20-rabbitmq-fila-pagamento.png)

---

## 🧪 Testes com Postman e pelo painel do RabbitMQ

### Postman / Insomnia

**Autenticação:** *Basic Auth* com o e-mail e a senha de um usuário (ex.: `cliente@mercadoexpress.com` / `Cliente@123`).

`POST http://localhost:8080/api/pagamentos` — corpo ([`postman/exemplo-compra.json`](postman/exemplo-compra.json)):

```json
{
    "formaPagamento": "PIX",
    "itens": [
        { "produtoId": 1, "quantidade": 2 },
        { "produtoId": 6, "quantidade": 1 }
    ]
}
```

Resposta **202 Accepted** (o pagamento foi para a fila; a confirmação chega depois):

```json
{
  "mensagem": "Pagamento aguardando confirmação do Banco Tranquilo...",
  "numeroPedido": 1,
  "transacaoId": "ee4d6254-c68f-455f-ba2f-8188c1541d4d",
  "status": "AGUARDANDO_PAGAMENTO",
  "valorTotal": 22.77,
  "formaPagamento": "PIX",
  "itens": [ { "produtoId": 1, "produto": "Banana Prata", "quantidade": 2, "precoUnitario": 6.99, "subtotal": 13.98 },
             { "produtoId": 6, "produto": "Feijão Carioca", "quantidade": 1, "precoUnitario": 8.79, "subtotal": 8.79 } ],
  "consultarStatus": "/api/pedidos/1"
}
```

| Método | Rota | Descrição | Perfil |
|---|---|---|---|
| GET | `/api/produtos` | Catálogo (para descobrir o `produtoId`) | CLIENTE, ADMIN |
| POST | `/api/pagamentos` | Compra: cria o pedido e envia o pagamento ao banco (202) | CLIENTE, ADMIN |
| GET | `/api/pedidos` | Meus pedidos | CLIENTE, ADMIN |
| GET | `/api/pedidos/{numeroPedido}` | Status do pagamento e do e-mail | dono do pedido ou ADMIN |
| GET | `/api/admin/dashboard` | Indicadores e séries do dashboard | ADMIN |

Erros em JSON: `400` (dados inválidos, com a lista de erros), `401` (sem login), `403` (perfil sem acesso),
`404` (pedido não encontrado), `422` (produto inexistente ou sem estoque).

**Script de testes:** a coleção [`postman/Mercado-Express-CP5.postman_collection.json`](postman/Mercado-Express-CP5.postman_collection.json)
tem 10 requisições com testes automáticos (compra aprovada até o e-mail, compra recusada por limite, validação,
401, 403, dashboard). No Postman: *Import* → *Run collection*. Pelo terminal:

```bash
npx newman run postman/Mercado-Express-CP5.postman_collection.json
```

### Pelo painel do RabbitMQ (<http://localhost:15672>, `guest` / `guest`)

- **Queues**: as 6 filas, com consumidores conectados, mensagens prontas e taxas de entrada e saída.
- **Exchanges → Publish message**: mensagens prontas em [`exemplos-rabbitmq/`](exemplos-rabbitmq/), como o JSON
  de pagamento da aula (`{"numeroPedido":"171","valor":550,"produto":"camiseta GG"}` → o banco recusa por limite) e o
  JSON de e-mail da aula (o `email-service` envia e grava na tabela).
- **Teste de resiliência**: pare o `banco-tranquilo`, faça uma compra e veja a mensagem esperando em
  `pagamento-request-queue` (*Get messages*); ao subir o banco de novo, ela é processada e o pedido é confirmado.

---

## ▶️ Como executar

**Pré-requisitos:** JDK 21 (o IntelliJ baixa em *File → Project Structure → SDK*), PostgreSQL, RabbitMQ e um
servidor SMTP. Duas formas de subir a infraestrutura:

### Opção A — Docker (mais simples)

```bash
docker compose up -d
```

Sobe PostgreSQL (`localhost:5432`, banco `mercado_express`, `postgres`/`postgres`), RabbitMQ com painel
(`localhost:15672`, `guest`/`guest`) e Mailpit (caixa de e-mails em `localhost:8025`).

### Opção B — Sem Docker (Windows)

1. **PostgreSQL**: o mesmo da Parte I. Crie o banco: `CREATE DATABASE mercado_express;`
   (usuário/senha diferentes de `postgres`/`postgres`? use as variáveis `DB_USER` e `DB_PASSWORD`).
2. **RabbitMQ**: instale o [Erlang/OTP](https://www.erlang.org/downloads) e o
   [RabbitMQ](https://www.rabbitmq.com/docs/install-windows), depois habilite o painel:
   `rabbitmq-plugins enable rabbitmq_management`. Ou use um broker na nuvem (CloudAMQP/LavinMQ, visto na Aula 16):
   `RABBITMQ_ADDRESSES=amqps://usuario:senha@host/vhost`.
3. **E-mail**: [Mailpit](https://mailpit.axllent.org/) (um executável: `mailpit.exe`) ou o Gmail
   ([ver abaixo](#-e-mail-real-pelo-gmail-senha-de-app)).

### Subindo os microsserviços

No IntelliJ: *File → Open* na pasta do projeto (o `pom.xml` da raiz importa os três módulos) e execute, em qualquer
ordem: **`MercadoExpressApplication`**, **`BancoTranquiloApplication`** e **`EmailServiceApplication`**.

Pelo terminal (uma janela para cada):

```bash
./mvnw -f mercado-express spring-boot:run
./mvnw -f banco-tranquilo spring-boot:run
./mvnw -f email-service spring-boot:run
```

Acesse **http://localhost:8080**. As tabelas e os schemas são criados pelo Flyway na primeira execução, e as filas
e exchanges aparecem sozinhas no painel do RabbitMQ.

### Variáveis de ambiente (opcionais)

| Variável | Padrão | Serviço |
|---|---|---|
| `DB_HOST` / `DB_PORT` / `DB_NAME` | `localhost` / `5432` / `mercado_express` | todos |
| `DB_USER` / `DB_PASSWORD` | `postgres` / `postgres` | todos |
| `RABBITMQ_ADDRESSES` | `amqp://guest:guest@localhost:5672` | todos |
| `MAIL_HOST` / `MAIL_PORT` | `127.0.0.1` / `1025` (Mailpit) | email-service |
| `MAIL_USERNAME` / `MAIL_PASSWORD` / `MAIL_FROM` | — / — / `nao-responda@mercadoexpress.com` | email-service |
| `MAIL_SMTP_AUTH` / `MAIL_SMTP_STARTTLS` | `false` / `false` | email-service |
| `BANCO_LIMITE` / `BANCO_TAXA_APROVACAO` / `BANCO_TEMPO_PROCESSAMENTO` | `500.00` / `0.85` / `2s` | banco-tranquilo |

---

## 📧 E-mail real pelo Gmail (senha de app)

Como na Aula 16: na conta Google (com verificação em duas etapas) crie uma **senha de app** em
<https://myaccount.google.com/apppasswords> e rode o `email-service` com:

```
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=sua.conta@gmail.com
MAIL_PASSWORD=<senha de app de 16 letras>
MAIL_SMTP_AUTH=true
MAIL_SMTP_STARTTLS=true
MAIL_FROM=sua.conta@gmail.com
```

No IntelliJ: *Run → Edit Configurations → EmailServiceApplication → Environment variables*. **Nunca coloque a senha
no código nem no GitHub.** O e-mail é enviado para o e-mail do usuário que fez a compra: crie uma conta pelo
*Sign Up* com o seu e-mail real para recebê-lo.

---

## 🧪 Testes automatizados

```bash
./mvnw test
```

Não precisam de Docker, RabbitMQ nem PostgreSQL instalados: usam **PostgreSQL 16 embarcado** (zonky), um servidor
SMTP em memória (**GreenMail**) e o `RabbitTemplate` simulado (os testes chamam os consumidores como o listener faria
e verificam exatamente o que seria publicado em cada exchange).

| Módulo | Classe | O que verifica |
|---|---|---|
| mercado-express | `CarrinhoECompraTest` | carrinho na sessão, quantidades, estoque, finalizar → pedido gravado, estoque reservado e **mensagem publicada em `pagamento-request-exchange`** |
| mercado-express | `FluxoPagamentoTest` | resposta APROVADO → pedido PAGO + pedido de e-mail; RECUSADO → estoque devolvido; respostas repetidas/inválidas ignoradas; retorno SENT/ERROR do e-mail; **RabbitMQ fora do ar → reenvio** |
| mercado-express | `PagamentoApiTest` | API do Postman: 202, 400, 401, 403, 422, Basic Auth sem sessão, privacidade dos pedidos |
| mercado-express | `DashboardTest` | faturamento, lucro, margem, ticket médio, gráficos e telas da gerência |
| mercado-express | `MensageriaContratoTest` | JSON das mensagens e topologia (exchanges, filas, bindings, dead letter) |
| mercado-express | testes da Parte I | login, Sign Up de Cliente e de Administrador, perfis, CRUD de produtos (agora com custo), Flyway |
| banco-tranquilo | `AnalisePagamentoTest`, `BancoTranquiloIntegracaoTest`, `TopologiaTest` | regras do banco, transação gravada no schema `banco`, resposta na fila certa, idempotência, JSON da aula |
| email-service | `EmailServiceIntegracaoTest`, `TopologiaTest` | e-mail HTML enviado e gravado como SENT, JSON da aula, sem duplicar, mensagem inválida → dead letter, SMTP fora do ar → ERROR |
