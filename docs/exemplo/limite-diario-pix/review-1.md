---
tarefa: limite-diario-pix
etapa: code-review
rodada: 1
veredito: aprovado
bloqueantes: 0
---

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
