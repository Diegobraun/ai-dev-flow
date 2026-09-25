---
tarefa: renda-no-account-opened
etapa: refinamento
status: pronto
---

# Enviar `monthlyIncome` da abertura de conta só para quem precisa

## Objetivo

Na abertura de conta, o consumidor que precisa da renda mensal do cliente (`monthlyIncome`), hoje o `loan-service`, passa a recebê-la por evento e não precisa mais consultar o cliente. O evento de broadcast `account-opened` **não muda**. A renda vai num tópico novo e dedicado, lido só por Crédito.

## Resposta às áreas (rodada 2)

| Área | Motivo da recusa | Como o refinamento responde |
|---|---|---|
| pagamentos (Comunicação, `notification-service`) | Não precisa da renda e não pode receber dado financeiro pessoal (LGPD). Não publicar no `account-opened`, que é broadcast. | O `account-opened` fica exatamente como está (`accountId, customerId, openedAt`). A renda vai só para o tópico novo `account-opened-income`, que o `notification-service` não consome. Nada muda para pagamentos, e a área sai de "Áreas afetadas". |

## Contexto no código

- Ponto de entrada: `src/main/java/com/example/account/account/AccountController.java` (`POST /accounts`) → `AccountService.open` (`account/AccountService.java:33`).
- Onde o evento nasce: `messaging/AccountEventPublisher.java:16-18` monta `AccountOpenedEvent` e publica em `Topics.ACCOUNT_OPENED` com a chave `accountId`.
- Origem da renda: `customer/Customer.java` (`BigDecimal monthlyIncome`, obrigatória e `>= 0`, ver `customer/CreateCustomerRequest.java:8`). `AccountService.open` já carrega o cliente (linha 34), mas descarta o resultado.
- Padrão existente: eventos são records com `BigDecimal` para valores (`messaging/LoanDisbursedEvent.java`), nomes de tópico publicados ficam em `messaging/Topics.java`, e a serialização é JSON sem type headers (`application.yml`).
- O `loan-service` já consome `account-opened` com o payload `com.example.loan.messaging.AccountOpenedEvent(accountId, customerId, monthlyIncome)`, e hoje recebe `monthlyIncome` sempre `null` (warning do system-graph). O payload novo usa os mesmos nomes de campo, então o record atual dele serve sem mudanças.

## Mudanças propostas

| Arquivo | Mudança |
|---|---|
| `src/main/java/com/example/account/messaging/Topics.java` | Nova constante `ACCOUNT_OPENED_INCOME = "account-opened-income"`. |
| `src/main/java/com/example/account/messaging/AccountOpenedIncomeEvent.java` (novo, no pacote `messaging`, junto dos outros eventos) | `record AccountOpenedIncomeEvent(Long accountId, Long customerId, Instant openedAt, BigDecimal monthlyIncome)`. |
| `src/main/java/com/example/account/messaging/AccountEventPublisher.java` | `accountOpened(Account account, Customer customer)`: continua publicando o `AccountOpenedEvent` atual em `account-opened` **sem alteração** e publica também o `AccountOpenedIncomeEvent` em `account-opened-income`, com a mesma chave (`accountId`). O `KafkaTemplate` passa a ser `KafkaTemplate<String, Object>` para aceitar os dois tipos. |
| `src/main/java/com/example/account/account/AccountService.java` | Em `open`, guardar o `Customer` retornado por `customers.findById(...)` e passá-lo para `publisher.accountOpened(account, customer)`. Não precisa de nova consulta. |
| `src/main/java/com/example/account/messaging/AccountOpenedEvent.java` | **Sem alteração.** |
| `src/test/java/com/example/account/...` (novo, o projeto ainda não tem testes) | Testes do plano abaixo. |

## Contratos afetados

| Contrato | Consumidores | Compatível? |
|---|---|---|
| Kafka `account-opened` | `customer-service` `AccountOpenedListener.java:25`, `notification-service` `NotificationListeners.java:16`, `loan-service` `AccountOpenedConsumer.java:11` | Sem mudança: o payload continua `accountId, customerId, openedAt`. |
| Kafka `account-opened-income` (novo): `accountId, customerId, openedAt, monthlyIncome` | Ainda nenhum. Consumidor previsto: `loan-service` (`src/main/java/com/example/loan/messaging/AccountOpenedConsumer.java:11`, stream-function) | Sim, porque é um contrato novo. O record atual do `loan-service` já tem os campos `accountId, customerId, monthlyIncome`. Basta ele apontar o binding da função para `account-opened-income`. Enquanto não trocar, ele segue como hoje, recebendo `null`. |

## Áreas afetadas

| Área | Times | Serviços | O que muda para eles |
|---|---|---|---|
| credito | Crédito | loan-service | Para receber a renda, trocam o destino do binding de `AccountOpenedConsumer` de `account-opened` para `account-opened-income`. O payload é compatível com o record atual. Até a troca, nada muda. |

O `notification-service` (pagamentos) não é mais afetado, porque `account-opened` não muda. O `customer-service` (Cadastro) é da área `contas`, a mesma do `account-service`.

## Critérios de aceite

1. **Dado** um cliente com `monthlyIncome = 8500.00`, **quando** `POST /accounts` é chamado com o `customerId` dele, **então** é publicado em `account-opened-income`, com a chave `accountId`, um evento com `accountId`, `customerId`, `openedAt` e `monthlyIncome = 8500.00` (mesmo valor e escala).
2. **Dado** o mesmo cenário, **quando** a conta é aberta, **então** o JSON publicado em `account-opened` tem exatamente as chaves `accountId`, `customerId` e `openedAt`, **sem** `monthlyIncome`.
3. **Dado** um cliente com `monthlyIncome = 0`, **quando** a conta é aberta, **então** o evento em `account-opened-income` traz `monthlyIncome = 0`, não `null`.
4. **Dado** um `customerId` inexistente, **quando** `POST /accounts` é chamado, **então** a resposta é 422 (`customer not found`) e nada é publicado em nenhum dos dois tópicos.
5. **Dado** o JSON publicado em `account-opened-income`, **quando** ele é desserializado num record equivalente ao do `loan-service` (`accountId, customerId, monthlyIncome`), **então** a leitura não falha e `monthlyIncome` vem preenchido.

## Plano de teste

| Critério | Tipo de teste | Observação |
|---|---|---|
| 1, 2, 3 | unitário do `AccountService` + `AccountEventPublisher` | `KafkaTemplate` mockado e os envios capturados por tópico com `ArgumentCaptor`. `monthlyIncome` conferido com `compareTo` e escala. |
| 1, 2 | integração (`@SpringBootTest` + MockMvc + Kafka embarcado ou Testcontainers) | Criar o cliente via `POST /customers`, abrir a conta, consumir os dois tópicos e conferir o JSON cru (as chaves de `account-opened` e o `monthlyIncome` em `account-opened-income`). |
| 4 | integração (`@SpringBootTest` + MockMvc) | Verifica o 422 e que nenhuma mensagem chegou aos tópicos. |
| 5 | unitário de serialização (`ObjectMapper` do contexto) | Serializar `AccountOpenedIncomeEvent` e desserializar num record de teste `(accountId, customerId, monthlyIncome)`. |

## Riscos

- **Publicação em dois tópicos sem atomicidade:** se o envio para `account-opened-income` falhar depois do `account-opened`, o Crédito fica sem a renda daquela conta. O risco é o mesmo que já existe hoje com o envio único (não há outbox nem transação Kafka no serviço). Mitigação fora do escopo, ver abaixo.
- **Acesso ao tópico novo:** a separação só protege o dado se a leitura de `account-opened-income` for restrita ao Crédito (ACL do cluster). Sem ACL, qualquer serviço consegue assinar o tópico.
- **Valor desatualizado:** o evento carrega a renda do momento da abertura. Alterações posteriores não são publicadas.
- **Dado sensível em log:** não logar o payload de `AccountOpenedIncomeEvent` no publisher.

## Fora de escopo

- Mudanças no `loan-service`: trocar o binding e remover a consulta ao cliente ficam com o time de Crédito.
- Outbox ou transação Kafka para garantir a publicação nos dois tópicos.
- Criação do tópico e das ACLs no cluster (infra), além da retenção do tópico novo.
- Publicar evento de alteração de renda.
- Corrigir os avisos informativos do system-graph (`openedAt` ignorado).

## Perguntas em aberto

- Nome do tópico novo (sugestão: `account-opened-income`, que deixa claro que é a abertura com dado de renda). Confirmar com Crédito na aprovação.
- Quem cria o tópico e a ACL que restringe a leitura ao consumer group `loan-service`? Sugestão: o time Contas Correntes abre o pedido para infra antes do deploy, e o deploy só sai depois da ACL aplicada.
- Registrar no system-graph (`record_note`) a regra "renda mensal não vai em `account-opened` (broadcast), por LGPD"? Sugestão: sim, depois da aprovação de Crédito.
