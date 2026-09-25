---
tarefa: <tarefa>
etapa: code-review
rodada: <n>
veredito: aprovado | bloqueado
bloqueantes: <quantidade>
---

# Review <n>: <tarefa>

Base `<commit base>`, <quantidade> commits, <arquivos> arquivos.

## Critérios de aceite

| Critério | Implementado | Onde |
|---|---|---|
| 1 | sim | `Service.java:42` |
| 2 | não | |

## Bloqueantes

### B1. <título curto>

`src/main/java/.../Arquivo.java:57`

Cenário: dado ..., quando ..., acontece ... em vez de ....

Correção: ...

## Importantes

### I1. <título curto>

`arquivo:linha`. ...

## Sugestões

- `arquivo:linha`: ...

## Contratos

Resultado do `impact_of_change` (ou "system-graph indisponível") comparado com o refinamento.
