---
tarefa: limite-diario-pix
etapa: teste-integrado
resultado: aprovado
---

Testes integrados em `src/test/java/com/example/payment/payment/PaymentDailyLimitIntegrationTest.java`:
`@SpringBootTest` + `@AutoConfigureMockMvc` em `POST /payments`, com os limites do `application.yml` real.
Com `@MockitoBean` estão mockados só as dependências externas: `AccountGraphClient`, `RiskProfileClient`, `FraudApi`,
`PixGatewayClient` e `PaymentEventPublisher`. O `PaymentService` é o bean real. O `Clock` é um bean `@Primary` de
teste (`MutableClock` em America/Sao_Paulo), usado para controlar a virada do dia.

Nome da classe: o refinamento sugeria `PaymentDailyLimitIT`, mas o `pom.xml` não tem `maven-failsafe-plugin`, e o
surefire não roda classes `*IT`. A classe se chama `...IntegrationTest` para rodar no `mvn verify`. Não alterei o
`pom.xml` porque ele está fora de `src/test`.

| Critério | Teste | Resultado |
|---|---|---|
| 1 | `PaymentDailyLimitIntegrationTest.criterio1_contaHighSemPagamentosNoDiaPaga1500_ficaCompletedEPublicaEvento` | passou |
| 2 | `PaymentDailyLimitIntegrationTest.criterio2_contaHighCom1500HojePaga500_ficaCompletedNoLimiteExato` | passou |
| 3 | `PaymentDailyLimitIntegrationTest.criterio3_contaHighCom1500HojePaga501_rejeitadaSemFraudeSemPixSemEvento` | passou |
| 4 | `PaymentDailyLimitIntegrationTest.criterio4_limiteDiarioDependeDaFaixa_mediumRejeitaLowAceita` | passou |
| 5 | `PaymentDailyLimitIntegrationTest.criterio5_clienteSemPerfilDeRiscoCom800HojePaga201_rejeitadoComLimitePadrao1000` | passou |
| 6 | `PaymentDailyLimitIntegrationTest.criterio6_pagamentosRejeitadosNaoEntramNaSoma` | passou |
| 7 | `PaymentDailyLimitIntegrationTest.criterio7_pagamentoDas2359DeOntemNaoContaParaHoje` | passou |
| 7 | `PaymentDailyLimitIntegrationTest.criterio7_pagamentoDas22hDeOntemEmSaoPauloContaComoOntemMesmoSendoHojeEmUtc` | passou |
| 8 | `PaymentDailyLimitIntegrationTest.criterio8_limiteEPorConta` | passou |
| 9 | `PaymentDailyLimitIntegrationTest.criterio9_doisPagamentosParalelosQueJuntosPassamDoLimite_exatamenteUmCompleted` | passou |

Observações:
- No critério 3, o teste limpa as invocações dos mocks depois do pagamento de 1500. Assim ele verifica que o
  pagamento de 501 não chama `FraudApi.evaluate`, `PixGatewayClient.transfer` nem `PaymentEventPublisher.paymentCompleted`.
  O teste unitário existente fazia essa checagem de forma mais fraca.
- No critério 9, as duas requisições HTTP partem juntas (liberadas por um `CountDownLatch`) e o pix-gateway mockado
  demora 300 ms. Resultado: exatamente 1 `COMPLETED` e 1 `REJECTED` com `above daily pix limit of 2000`.

## Falhas

Nenhuma.

## Suíte completa
`mvn -q verify`: 21 testes, 0 falhas, 0 erros, 0 ignorados (`PaymentServiceDailyLimitTest`: 11,
`PaymentDailyLimitIntegrationTest`: 10).
