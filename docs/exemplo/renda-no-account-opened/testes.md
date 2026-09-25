---
tarefa: renda-no-account-opened
etapa: teste-integrado
resultado: aprovado
---

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
