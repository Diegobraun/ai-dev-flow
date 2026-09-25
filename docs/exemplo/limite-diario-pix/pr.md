Criar um limite diário de PIX por conta. A soma dos pagamentos concluídos da conta no dia mais o valor do novo pagamento não pode passar do limite diário. Se passar, o pagamento é rejeitado.

Gerado pelo devflow: 1 rodada(s) de code review, custo dos agentes US$ 2.37.

<details>
<summary>Refinamento</summary>

# Limite diário de PIX por conta

## Objetivo

Um pagamento PIX é rejeitado quando a soma dos pagamentos `COMPLETED` da conta no dia (America/Sao_Paulo) mais o
valor do novo pagamento passa do limite diário. O limite depende da faixa de risco do cliente dono da conta.

## Contexto no código

- Ponto de entrada: `src/main/java/com/example/payment/payment/PaymentController.java` (`POST /payments`) →
  `PaymentService.pay(PaymentRequest)`.
- Onde a regra mora hoje: `src/main/java/com/example/payment/payment/PaymentService.java:47-72`. As validações
  formam uma cadeia `if / else if`: conta não `ACTIVE` → saldo insuficiente → limite por transação da faixa de
  risco → fraude `DENY`. Cada rejeição grava um `Payment` com `status = REJECTED` e um `reason` em texto, e a
  resposta continua `201`. Não existe exceção nem `@ControllerAdvice`.
- Faixa de risco: `PaymentService.java:53` já busca o `RiskProfile` (`RiskProfileClient.riskProfile(customerId)`)
  e resolve o limite por transação com `.map(RiskProfile::tier).map(limits::get).orElse(DEFAULT_LIMIT)`. O
  `RiskProfileClient` devolve `Optional.empty()` quando o perfil não existe **ou** quando o customer-service falha
  (`WebClientException`).
- Padrão existente a seguir: `app.limits` no `application.yml`, lido com `Binder` no construtor
  (`PaymentService.java:44`), e `reason = "above pix limit of " + limit`.
- Pagamentos ficam só em memória (`ConcurrentHashMap`), com filtro por conta em `byAccount(Long)`
  (`PaymentService.java:77-79`).
- `Payment.createdAt` é `Instant.now()`. Não há `Clock` injetado.

## Mudanças propostas

| Arquivo | Mudança |
|---|---|
| `src/main/resources/application.yml` | Nova seção `app.daily-limits` com `LOW: 20000`, `MEDIUM: 8000`, `HIGH: 2000`. |
| `src/main/java/com/example/payment/ClockConfig.java` (novo, junto de `PaymentServiceApplication`) | Bean `Clock` = `Clock.system(ZoneId.of("America/Sao_Paulo"))`. O fuso define o "dia" e deixa os testes controlarem a virada. |
| `src/main/java/com/example/payment/payment/PaymentService.java` | (a) Receber `Clock` no construtor e trocar todos os `Instant.now()` por `Instant.now(clock)`. (b) Ler `app.daily-limits` com `Binder`, igual a `app.limits`, e constante `DEFAULT_DAILY_LIMIT = 1000`. (c) Resolver o limite diário a partir do mesmo `Optional<RiskProfile>` já consultado na linha 53 (uma única chamada ao customer-service): `tier` → `dailyLimits.get(tier)`, e `1000` quando não há perfil ou a faixa não está mapeada. (d) Novo método privado `completedTodayTotal(Long accountId)`: soma `amount` dos pagamentos da conta com `status == COMPLETED` e `createdAt` em `LocalDate.now(clock)` na zona do `clock`. (e) Novo `else if` depois do limite por transação e **antes** do fraud-service: se `completedTodayTotal + amount > dailyLimit`, `reason = "above daily pix limit of " + dailyLimit`. Valor igual ao limite passa. (f) Lock por conta (`ConcurrentHashMap<Long, Object>` + `synchronized`) envolvendo desde a soma do dia até o `save` do `COMPLETED`, para dois pagamentos simultâneos da mesma conta não passarem juntos. |
| `src/test/java/com/example/payment/payment/PaymentServiceDailyLimitTest.java` (novo, `src/test` ainda não existe) | Testes unitários da regra, clients mockados e `Clock` controlável. |
| `src/test/java/com/example/payment/payment/PaymentDailyLimitIT.java` (novo) | `@SpringBootTest` + MockMvc em `POST /payments`, com `@MockitoBean` para `AccountGraphClient`, `RiskProfileClient`, `FraudApi`, `PixGatewayClient` e `PaymentEventPublisher`. |

README não muda (decisão da revisão).

## Contratos afetados

| Contrato | Consumidores | Compatível? |
|---|---|---|
| `POST /payments`: resposta `201` com `status: REJECTED` e `reason` novo `above daily pix limit of <limite>` | consumidores externos não verificados (tools do system-graph indisponíveis nesta sessão). Neste repositório, nenhum. | sim, desde que ninguém faça parse de `reason`. Formato e status HTTP não mudam. |
| `GET /risk-profiles/{customerId}` (customer-service, consumido) | este serviço | sem alteração: mesma chamada, sem chamada extra. |
| Kafka `payment-completed` | account-service, notification-service, fraud-service (segundo o README) | sem alteração: rejeitado não publica evento, payload igual. |
| `GET /payments/{id}`, `GET /payments?accountId=` | consumidores externos não verificados | sem alteração de formato. |

Antes de implementar, rodar `impact_of_change` em `POST /payments` para confirmar que ninguém depende do texto de
`reason`.

## Critérios de aceite

Saldo suficiente, limite por transação maior que os valores usados e fraude `ALLOW`, salvo quando dito.

1. **Dado** uma conta `HIGH` sem pagamentos no dia, **quando** paga 1500, **então** fica `COMPLETED` e o evento
   `payment-completed` é publicado.
2. **Dado** uma conta `HIGH` com 1500 `COMPLETED` hoje, **quando** paga 500, **então** fica `COMPLETED` (limite
   exato de 2000).
3. **Dado** uma conta `HIGH` com 1500 `COMPLETED` hoje, **quando** paga 501, **então** a resposta é `201` com
   `status = REJECTED`, `reason = "above daily pix limit of 2000"`, o fraud-service e o pix-gateway não são
   chamados e nenhum evento é publicado.
4. **Dado** uma conta `MEDIUM` com 7000 `COMPLETED` hoje, **quando** paga 1001, **então** é rejeitada com
   `above daily pix limit of 8000`; **e** uma conta `LOW` com 7000 hoje que paga 1001 fica `COMPLETED`.
5. **Dado** um cliente sem perfil de risco (`RiskProfileClient` devolve vazio) com 800 `COMPLETED` hoje, **quando**
   paga 201, **então** é rejeitado com `above daily pix limit of 1000`.
6. **Dado** uma conta `HIGH` com 1900 em pagamentos `REJECTED` hoje, **quando** paga 1500, **então** fica
   `COMPLETED` (rejeitados não entram na soma).
7. **Dado** uma conta `HIGH` com 2000 `COMPLETED` às 23:59 de ontem em America/Sao_Paulo, **quando** paga 1500 às
   00:01 de hoje, **então** fica `COMPLETED`. **E** um pagamento às 22:00 de ontem em São Paulo (01:00 UTC de
   hoje) conta como ontem.
8. **Dado** a conta A (`HIGH`) com 2000 `COMPLETED` hoje, **quando** a conta B (`HIGH`) paga 1500, **então** B
   fica `COMPLETED` (limite por conta).
9. **Dado** uma conta `HIGH` com 1500 `COMPLETED` hoje, **quando** chegam em paralelo dois pagamentos de 500,
   **então** exatamente um fica `COMPLETED` e o outro `REJECTED` por limite diário.

## Plano de teste

| Critério | Tipo de teste | Observação |
|---|---|---|
| 1, 3 | integração (`@SpringBootTest` + MockMvc) | `RiskProfileClient` mockado com tier `HIGH`. Em 3, verificar que `FraudApi.evaluate`, `PixGatewayClient.transfer` e `PaymentEventPublisher.paymentCompleted` não são chamados. |
| 2, 4, 5, 6, 8 | unitário (`PaymentServiceDailyLimitTest`) | pagamentos anteriores criados chamando `pay()` (repositório é o map em memória). Limites carregados via `MockEnvironment` com os mesmos valores do `application.yml`. |
| 7 | unitário | `Clock` mutável (ou `Clock` mockado) em `America/Sao_Paulo`, avançado entre os pagamentos. |
| 9 | unitário concorrente | duas threads com `CountDownLatch`, pix-gateway mockado com atraso. Esperado: exatamente 1 `COMPLETED`. |

## Riscos

- **Customer-service fora do ar**: `RiskProfileClient` devolve vazio também em erro, então o limite diário cai
  para 1000 e contas `LOW`/`MEDIUM` podem ser rejeitadas indevidamente durante a falha. É o mesmo comportamento
  que o limite por transação já tem hoje.
- **Lock por conta**: segura a conta durante as chamadas ao fraud-service e ao pix-gateway, serializando os
  pagamentos de uma mesma conta. Aprovado na revisão. O map de locks cresce com o número de contas (irrelevante
  enquanto tudo já é em memória).
- **Persistência em memória**: restart zera o acumulado do dia e, com mais de uma instância, cada uma tem a sua
  soma. O limite só é confiável com uma instância e sem restart no dia.
- **Fallback do pix-gateway**: pagamentos `COMPLETED` com `SIMULATED-<uuid>` entram na soma do dia.

## Fora de escopo

- Persistir os pagamentos em banco.
- Limite diário individual por conta vindo do account-service, e endpoint para consultar ou alterar limite.
- Limite por período (diurno/noturno) ou por quantidade de transações.
- Trocar o fallback `SIMULATED-` do pix-gateway ou o fallback do `RiskProfileClient`.
- Atualizar o README.

## Perguntas em aberto

- Faixa de risco que vem do customer-service mas não está em `app.daily-limits` (ex.: um tier novo): usa 1000,
  como conta sem perfil? (sugestão: sim, é o que o limite por transação já faz)
</details>

<details>
<summary>Último code review</summary>

# Review 1: limite-diario-pix

Base `573b2a2770554afbb61f38f9e69bb06065a56eeb`, 2 commits, 4 arquivos (+313 / -19).

## Critérios de aceite

| Critério | Implementado | Onde |
|---|---|---|
| 1. HIGH sem pagamentos, paga 1500 → COMPLETED + evento | sim | `PaymentService.java:72,80-82`; teste `PaymentServiceDailyLimitTest.java:62` |
| 2. HIGH 1500 + 500 → COMPLETED (limite exato) | sim | `PaymentService.java:72` (`> 0`, igual passa); teste `:72` |
| 3. HIGH 1500 + 501 → REJECTED `above daily pix limit of 2000`, sem fraud/pix/evento | sim | `PaymentService.java:72-73` (antes do `fraudDecision` na cadeia); teste `:80` |
| 4. MEDIUM 7000+1001 rejeitado (8000); LOW 7000+1001 COMPLETED | sim | `PaymentService.java:63`; teste `:94` |
| 5. Sem perfil, 800+201 → rejeitado (1000) | sim | `PaymentService.java:63` (`DEFAULT_DAILY_LIMIT`); teste `:109` |
| 6. REJECTED não entra na soma | sim | `PaymentService.java:99`; teste `:128` |
| 7. Virada do dia em America/Sao_Paulo; 22:00 SP = ontem | sim | `ClockConfig.java:13`, `PaymentService.java:96,100`; testes `:138`, `:149` |
| 8. Limite por conta | sim | `PaymentService.java:98`; teste `:162` |
| 9. Dois pagamentos paralelos de 500 com 1500 no dia → exatamente 1 COMPLETED | sim | `PaymentService.java:64` (lock por conta cobre soma → `save` do COMPLETED); teste `:171` |

Pergunta em aberto do refinamento (tier não mapeado): resolvida com 1000, como sugerido
(`PaymentService.java:63`, teste `:120`).

Os itens (a) a (f) das mudanças propostas estão todos presentes: `Clock` injetado e todos os `Instant.now()` trocados
por `Instant.now(clock)` (linhas 59, 78, 81), `Binder` para `app.daily-limits`, uma única chamada ao
`RiskProfileClient` (linha 61), `completedTodayTotal` usando a zona do `clock`, novo `else if` entre o limite por
transação e o fraud-service, e lock por conta.

## Bloqueantes

Nenhum.

## Importantes

### I1. Teste de integração `PaymentDailyLimitIT` previsto no refinamento não foi criado

`src/test/java/com/example/payment/payment/PaymentDailyLimitIT.java` (ausente). O plano de teste pede
`@SpringBootTest` + MockMvc para os critérios 1 e 3 (resposta `201` com `status: REJECTED` via HTTP e wiring do bean
`Clock` no contexto Spring). O desvio foi declarado em `desenvolvimento.md` (fica para a etapa de teste integrado) e
os critérios têm cobertura unitária, então não bloqueia aqui, mas a etapa de teste integrado precisa cobrir:
o contexto sobe com o novo parâmetro `Clock` no construtor e `POST /payments` devolve `201` + `REJECTED` com o novo
`reason`.

### I2. Build e testes não foram executados por este review

Não tive permissão para rodar `mvn test` nesta sessão. A leitura do código e dos testes não mostra erro de
compilação (o único `new PaymentService(...)` é no teste novo, já com o `Clock`), mas o `build: ok` vem só do
`desenvolvimento.md`. Confirmar na etapa de teste.

## Sugestões

- `PaymentService.java:56,66-68`: `account` (status e saldo) é lido antes do lock, então dois pagamentos
  simultâneos da mesma conta podem passar ambos pela checagem de saldo com o mesmo saldo. É comportamento anterior
  à tarefa e fora de escopo (o saldo é do account-service); só registrando.
- `PaymentServiceDailyLimitTest.java:88-89`: `verify(pix, never()).transfer("key", BigDecimal.valueOf(501))` e
  `verify(publisher, never()).paymentCompleted(payment)` estão corretos, mas `verify(pix, times(1)).transfer(any(), any())`
  e `verify(publisher, times(1)).paymentCompleted(any())` seriam mais robustos (não dependem de igualdade de
  `BigDecimal`/argumento exato).
- `PaymentServiceDailyLimitTest.java:175`: o `Thread.sleep(200)` no mock do pix-gateway só alarga a janela da
  corrida; com o lock o resultado é determinístico, então o teste não fica frágil, só ~200 ms mais lento.

## Checklist

- Dinheiro: `BigDecimal` e `compareTo` em toda a regra; soma com `BigDecimal::add`. Sem divisão.
- Concorrência: leitura da soma e `save` do `COMPLETED` dentro do mesmo `synchronized` por conta; exceção no
  pix-gateway libera o lock. Lock segurado durante fraud/pix foi aceito no refinamento.
- Mensageria: evento só publicado no caminho COMPLETED, payload inalterado.
- Dados sensíveis: nenhum log novo; `reason` só traz o valor do limite.
- Testes: nenhum teste existente apagado ou enfraquecido (`src/test` não existia). Teste de horário usa `Clock`
  controlado, sem dependência do relógio real.

## Contratos

system-graph indisponível nesta sessão (tool `impact_of_change` não encontrada), mesma situação do refinamento e
do desenvolvimento. Pelo diff: `POST /payments` mantém status `201` e formato; só surge um novo valor de `reason`
(`above daily pix limit of <limite>`). `payment-completed` e `GET /payments*` sem alteração. Bate com a seção
"Contratos afetados" do refinamento. Fica pendente confirmar, com o system-graph disponível, que nenhum consumidor
faz parse de `reason`.

veredito: aprovado
</details>

<details>
<summary>Testes integrados</summary>

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
</details>
