---
tarefa: renda-no-account-opened
etapa: desenvolvimento
rodada: 1
build: ok
---

## O que mudou
- `messaging/Topics.java`: nova constante `ACCOUNT_OPENED_INCOME = "account-opened-income"`.
- `messaging/AccountOpenedIncomeEvent.java` (novo): `record(Long accountId, Long customerId, Instant openedAt, BigDecimal monthlyIncome)`.
- `messaging/AccountEventPublisher.java`: `accountOpened(Account, Customer)` publica o `AccountOpenedEvent` em `account-opened` sem mudar nada e publica também o `AccountOpenedIncomeEvent` em `account-opened-income`, com a mesma chave (`accountId`). O `KafkaTemplate` passou a ser `<String, Object>`. O payload não vai para log.
- `account/AccountService.java`: `open` guarda o `Customer` que já carregava e passa para o publisher. Não faz consulta nova.
- `src/test/.../messaging/AccountEventPublisherTest.java` (novo), cobre os critérios 1, 2, 3 e 5:
  - `KafkaTemplate` mockado, com os envios capturados por tópico.
  - Chave e escala de `monthlyIncome` conferidas.
  - O JSON de `account-opened` tem só `accountId`, `customerId` e `openedAt`.
  - Renda 0 sai como 0 e não como `null`.
  - O JSON do evento novo é lido num record igual ao do `loan-service`.
- `src/test/.../account/AccountServiceTest.java` (novo):
  - O cliente carregado chega ao publisher.
  - Com `customerId` inexistente, `open` retorna 422 e não publica nada (critério 4, lado unitário).

Build: `mvn -q verify` passou, com 6 testes e 0 falhas.

## Desvios do refinamento
- No teste de serialização (critério 5), usei o `JsonSerializer` do spring-kafka com `addTypeInfo=false`, que é o mesmo que `application.yml` usa, e para a leitura usei o `JacksonUtils.enhancedObjectMapper()`. O plano falava no "`ObjectMapper` do contexto". Mudei porque o `ObjectMapper` do contexto não é o que o produtor Kafka usa: o `JsonSerializer` cria o seu próprio. Assim o teste reflete o JSON que vai de fato para o tópico.
- Os testes integrados (MockMvc + Kafka) ficaram para a etapa de teste integrado, como a skill determina.

## Pontos do review tratados
- nenhum (rodada 1)
