---
tarefa: <tarefa>
etapa: refinamento
status: pronto | com-perguntas
---

# <título curto da tarefa>

## Objetivo

Uma ou duas frases: o que muda para quem usa o sistema.

## Contexto no código

- Ponto de entrada: `src/main/java/.../XController.java` (`POST /recurso`)
- Onde a regra mora hoje: `...`
- Padrão existente a seguir: `...` (como o projeto já faz algo parecido)

## Mudanças propostas

| Arquivo | Mudança |
|---|---|
| `src/main/java/.../Service.java` | ... |
| `src/main/java/.../NovoErro.java` (novo) | ... |

## Contratos afetados

| Contrato | Consumidores | Compatível? |
|---|---|---|
| `POST /payments` (resposta 422 nova) | `account-service` em `.../PaymentClient.java:31` | sim, status novo tratado como erro genérico |

Escreva "nenhum" se a mudança não altera contrato. Escreva "consumidores externos não verificados" se não foi
possível consultar.

## Áreas afetadas

| Área | Times | Serviços | O que muda para eles |
|---|---|---|---|
| credito | Crédito | loan-service | passa a receber `monthlyIncome` em `account-opened`; campo novo, compatível |

Escreva "nenhuma" se só a própria área é afetada. Cada área listada aprova o refinamento antes do desenvolvimento.

## Critérios de aceite

1. **Dado** ..., **quando** ..., **então** ...
2. **Dado** ..., **quando** ..., **então** ...

## Plano de teste

| Critério | Tipo de teste | Observação |
|---|---|---|
| 1 | integração (`@SpringBootTest` + MockMvc) | client externo simulado |
| 2 | unitário | |

## Riscos

- ...

## Fora de escopo

- ...

## Perguntas em aberto

- ... (sugestão: ...)
