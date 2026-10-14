# Mensagens para testar pelo painel do RabbitMQ

Painel: <http://localhost:15672> (usuário `guest`, senha `guest`) → aba **Exchanges** → clique na exchange →
**Publish message**. Cole o JSON em **Payload** e informe a **Routing key**. Não precisa preencher *Properties*
(o sistema lê o JSON mesmo sem `content_type`).

| Arquivo | Exchange | Routing key | O que acontece |
|---|---|---|---|
| [`pagamento-request.json`](pagamento-request.json) | `pagamento-request-exchange` | `pagamento-request-rout-key` | O Banco Tranquilo processa e responde na fila de sucesso ou erro (550 está acima do limite de R$ 500,00, então é recusado). Mesmo JSON de exemplo da aula. |
| [`pagamento-request-aprovado.json`](pagamento-request-aprovado.json) | `pagamento-request-exchange` | `pagamento-request-rout-key` | Valor dentro do limite: em geral é aprovado e a resposta vai para `pagamento-response-sucesso-queue`. |
| [`email-request.json`](email-request.json) | `email-request-exchange` | `email-request-rout-key` | O email-service envia o e-mail, grava em `email.tds_emails_mercado` e publica o status em `email-response-queue`. Mesmo formato do JSON de e-mail da aula. |
| [`email-request-invalido.json`](email-request-invalido.json) | `email-request-exchange` | `email-request-rout-key` | Sem destinatário: a mensagem é rejeitada e vai para `mercado-dead-letter-queue`. |

Também dá para usar **Queues → (fila) → Get messages** para ver o conteúdo das mensagens que estão esperando,
por exemplo com o `banco-tranquilo` parado: o pedido fica guardado em `pagamento-request-queue` até o banco voltar.
