---
tarefa: limite-diario-pix
etapa: desenvolvimento
rodada: 1
build: ok
---

## O que mudou
- `application.yml`: nova seção `app.daily-limits` (`LOW: 20000`, `MEDIUM: 8000`, `HIGH: 2000`).
- `ClockConfig.java` (novo): bean `Clock` em `America/Sao_Paulo`.
- `PaymentService.java`: recebe `Clock` no construtor; todos os `Instant.now()` viraram `Instant.now(clock)`;
  lê `app.daily-limits` com `Binder`; `DEFAULT_DAILY_LIMIT = 1000`; limite diário resolvido a partir do mesmo
  `Optional<RiskProfile>` (uma chamada só ao customer-service); novo `completedTodayTotal(accountId)`; novo
  `else if` depois do limite por transação e antes do fraud-service com `reason = "above daily pix limit of " + limite`;
  lock por conta (`ConcurrentHashMap<Long, Object>` + `synchronized`) envolvendo a cadeia de validações, o
  pix-gateway, o `save` do `COMPLETED` e a publicação do evento.
- `PaymentServiceDailyLimitTest.java` (novo): 11 testes unitários cobrindo os critérios 1 a 9, mais tier não
  mapeado (usa 1000). `Clock` mutável em São Paulo e teste concorrente com `CountDownLatch` e pix-gateway com atraso.

## Desvios do refinamento
- `PaymentDailyLimitIT.java` não foi criado nesta etapa: pela skill, testes integrados dos critérios de aceite
  são da etapa de teste integrado. Os critérios 1 e 3 também ganharam teste unitário.
- `impact_of_change` não foi executado: as ferramentas do MCP `system-graph` não estavam disponíveis nesta
  sessão. O formato da resposta e o status HTTP não mudam, só surge um novo valor de `reason`.
- Pergunta em aberto (tier não mapeado): segui a sugestão, usa 1000.
- O `synchronized` também envolve as validações de status, saldo e limite por transação (que vêm antes da soma
  do dia na cadeia). Isso não muda o comportamento e deixa o bloco mais simples.

## Pontos do review tratados
- não se aplica (rodada 1)
