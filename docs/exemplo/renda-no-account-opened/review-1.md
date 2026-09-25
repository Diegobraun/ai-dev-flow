---
tarefa: renda-no-account-opened
etapa: code-review
rodada: 1
veredito: aprovado
bloqueantes: 0
---

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
