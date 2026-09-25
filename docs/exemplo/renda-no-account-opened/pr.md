Incluir o campo monthlyIncome (renda mensal do cliente) no evento account-opened publicado pelo account-service, para que os consumidores não precisem consultar o cliente de novo.

Gerado pelo devflow: 1 rodada(s) de code review, custo dos agentes US$ 2.76.

<details>
<summary>Refinamento</summary>

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
</details>

<details>
<summary>Último code review</summary>

# Review 1: renda-no-account-opened

Base `ed073d18631130ebd5963d9e5d3bddcdea71bfff`, 2 commits, 6 arquivos (4 de produção e 2 de teste, +179/-6).

Build: `mvn -q verify` passou. São 6 testes (`AccountServiceTest` e `AccountEventPublisherTest`), sem falhas.

## Critérios de aceite

| Critério | Implementado | Onde |
|---|---|---|
| 1. Renda 8500.00 publicada em `account-opened-income` com a chave `accountId` | sim | `AccountEventPublisher.java:18-23`, `AccountService.java:35-38`. Teste em `AccountEventPublisherTest.java:33-53` (confere tópico, chave, `compareTo` e escala 2) |
| 2. `account-opened` com exatamente `accountId`, `customerId`, `openedAt` | sim | `AccountEventPublisher.java:19-20` (`AccountOpenedEvent` não mudou). Teste em `AccountEventPublisherTest.java:55-65`, sobre o JSON serializado |
| 3. Renda 0 publicada como 0, não `null` | sim | `AccountEventPublisher.java:21-22`. Teste em `AccountEventPublisherTest.java:67-80` |
| 4. Cliente inexistente: 422 e nada publicado | sim | `AccountService.java:35-36`: o `orElseThrow` vem antes de `save` e do publish. Teste unitário em `AccountServiceTest.java:38-45` (`verifyNoInteractions(publisher)`) |
| 5. JSON lido por um record no formato do `loan-service` | sim | `AccountOpenedIncomeEvent.java:6`. Teste em `AccountEventPublisherTest.java:82-93` |

## Bloqueantes

Nenhum.

## Importantes

### I1. Nenhum teste sobe o contexto Spring, então a injeção do `KafkaTemplate<String, Object>` não foi testada

`src/main/java/com/example/account/messaging/AccountEventPublisher.java:11-13`. O tipo genérico mudou de `KafkaTemplate<String, AccountOpenedEvent>` para `KafkaTemplate<String, Object>`. O projeto não tem `@SpringBootTest`, então o `verify` não prova que o bean autoconfigurado (`KafkaTemplate<?, ?>`) é injetado. Pela resolução de genéricos do Spring deveria funcionar, porque é o mesmo caso de antes. Se falhasse, a aplicação nem subiria. Os testes integrados previstos no plano (MockMvc + Kafka para os critérios 1, 2 e 4) cobrem isso. Eles precisam existir na etapa de teste integrado antes do PR.

### I2. Critérios 1, 2 e 4 ainda não foram testados de ponta a ponta

O plano de teste pede um teste de integração para esses critérios: `POST /customers` e `POST /accounts`, depois o consumo dos dois tópicos e a verificação do 422 sem mensagens. O `desenvolvimento.md` deixa isso para a etapa de teste integrado, o que é aceitável. Por enquanto a cobertura desses critérios é só unitária.

## Sugestões

- `AccountEventPublisher.java:20-23`: os dois `send` são assíncronos e o resultado não é observado. Se o envio de `account-opened-income` falhar, ninguém fica sabendo. O refinamento já aceitou esse risco e deixou outbox fora de escopo. Um `whenComplete` que registre só o `accountId` em caso de falha, sem o payload, ajudaria a operação a identificar as contas sem renda publicada.
- Depois do merge, registrar com `record_note` a regra "renda mensal não vai em `account-opened` (broadcast), por LGPD", como o próprio refinamento sugere.

## Checklist

- Dinheiro: `monthlyIncome` é `BigDecimal` do início ao fim, e os testes usam `compareTo` e conferem a escala. OK.
- Dados sensíveis: o payload com a renda não vai para log (o publisher não tem logger) e só é publicado no tópico dedicado. `account-opened` não recebe o campo novo. OK.
- Transação e mensageria: não há transação nova. A publicação sem atomicidade nos dois tópicos está registrada como risco aceito no refinamento. O serviço não ganhou consumer novo nem `group.id` novo.
- Testes: nenhum teste existente foi removido. Os testes novos usam `Instant` fixo e não acessam a rede.

## Contratos

`impact_of_change` para `account-service` / `account-opened`:
- Consumidores: `customer-service` (`AccountOpenedListener.java:25`, área contas), `notification-service` (`NotificationListeners.java:16`, área pagamentos) e `loan-service` (`AccountOpenedConsumer.java:11`, área credito). Batem com a seção "Contratos afetados" do refinamento.
- `affectedAreas`: credito e pagamentos. O diff não altera o payload de `account-opened`: `AccountOpenedEvent.java` não foi tocado, e o producer continua enviando `accountId, customerId, openedAt` (`AccountEventPublisher.java:19`, confirmado pelo teste do JSON). Por isso pagamentos não é afetado por esta mudança. Isso bate com o refinamento, que tirou a área depois da recusa dela. Ter só credito entre as áreas que aprovaram é coerente com o diff.
- O aviso `monthlyIncome always null` para o `loan-service` continua em `account-opened`, como esperado. A correção depende de o Crédito trocar o binding para `account-opened-income`, o que está fora de escopo.

`impact_of_change` para `account-opened-income`: o tópico ainda não está no grafo ("contract not found"), porque é novo e não tem consumidor. Isso bate com o refinamento, que lista o `loan-service` como consumidor futuro, na área credito, que aprovou.

veredito: aprovado
</details>

<details>
<summary>Testes integrados</summary>

| Critério | Teste | Resultado |
|---|---|---|
| 1 | `AccountOpenedIncomeIntegrationTest.criterio1_publicaRendaNoTopicoAccountOpenedIncomeComChaveAccountId` | passou |
| 2 | `AccountOpenedIncomeIntegrationTest.criterio2_accountOpenedMantemSoAccountIdCustomerIdOpenedAtSemRenda` | passou |
| 3 | `AccountOpenedIncomeIntegrationTest.criterio3_rendaZeroEPublicadaComoZeroENaoNull` | passou |
| 4 | `AccountOpenedIncomeIntegrationTest.criterio4_clienteInexistenteRetorna422ENaoPublicaEmNenhumTopico` | passou |
| 5 | `AccountOpenedIncomeIntegrationTest.criterio5_jsonDeAccountOpenedIncomeEhLidoPeloRecordDoLoanService` | passou |

## Como os testes rodam

- `@SpringBootTest` + `@AutoConfigureMockMvc` com o contexto inteiro: `CustomerController`, `AccountController`,
  `AccountService`, os repositórios e o `AccountEventPublisher` reais. O cliente é criado por `POST /customers` e a
  conta é aberta por `POST /accounts`.
- Kafka: o projeto não tem Testcontainers nem `spring-kafka-test`, e esta etapa não altera o `pom.xml`. Por isso só
  o transporte foi trocado. O `KafkaTemplate` do contexto usa um `MockProducer` (via `MockProducerFactory`) com o
  value serializer montado de `spring.kafka.producer.*` do `application.yml` (`JsonSerializer`, sem type headers).
  Os testes conferem tópico, chave, headers e o JSON serializado exatamente como iria para o broker.
- Os listeners Kafka ficam desligados (`spring.kafka.listener.auto-startup=false`). Não há acesso a rede externa.
- Critério 5: o JSON de `account-opened-income` é lido num record de teste com o formato do `loan-service`
  (`accountId, customerId, monthlyIncome`) usando o `ObjectMapper` do contexto.

## Falhas

Nenhuma.

## Observações

- O arquivo `src/test/java/com/example/account/account/AccountOpenedIncomeIT.java` foi criado primeiro e depois
  substituído por `AccountOpenedIncomeIntegrationTest.java`, porque o pom não tem Failsafe e o Surefire ignora
  `*IT`. As permissões do ambiente não deixaram apagar o arquivo antigo. Ele ficou só com um comentário, não
  entrou no commit e pode ser apagado.
- Fica sem cobertura aqui, porque só dá para testar com broker real ou infraestrutura: ACL do tópico novo e falha
  parcial entre os dois envios (riscos do refinamento, fora de escopo).

## Suíte completa
`mvn -o verify`: 11 testes, 0 falhas (5 de integração novos, mais 6 unitários existentes).
</details>
