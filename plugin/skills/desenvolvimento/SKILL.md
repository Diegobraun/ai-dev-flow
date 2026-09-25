---
name: desenvolvimento
description: Implementa uma tarefa a partir do refinamento aprovado em .devflow/<tarefa>/refinamento.md e, nas rodadas seguintes, corrige os pontos bloqueantes do code review. Use quando pedirem para implementar ou desenvolver uma tarefa já refinada, ou para corrigir o que o review apontou.
---

# Desenvolvimento

## Entrada

- `.devflow/<tarefa>/refinamento.md`, aprovado. É o contrato desta etapa.
- `.devflow/<tarefa>/review-<n>.md` mais recente, se existir. Nesse caso esta é uma rodada de correção.
- `.devflow/<tarefa>/testes.md`, se existir com falhas. Também é rodada de correção.

## Procedimento

1. Leia o refinamento inteiro antes de abrir qualquer arquivo de código.
2. Em rodada de correção, liste os pontos **bloqueantes** do review (ou os testes que falharam) e trate cada um.
   Pontos "importante" e "sugestão" entram só se forem baratos e dentro do escopo.
3. Implemente seguindo o padrão existente citado no refinamento. Mesmo estilo de nome, de erro, de log e de
   teste que o projeto já usa.
4. Escreva ou ajuste os testes unitários das classes que mudaram. Os testes integrados dos critérios de aceite
   são da etapa seguinte.
5. Rode o build com testes: `./mvnw -q verify`, `mvn -q verify` ou `./gradlew check`, o que o projeto usar. Não
   entregue com build quebrado.
6. Faça commits pequenos com mensagem `devflow(<tarefa>): <o que mudou>`.
7. Escreva `.devflow/<tarefa>/desenvolvimento.md`.

## desenvolvimento.md

```markdown
---
tarefa: <tarefa>
etapa: desenvolvimento
rodada: <n>
build: ok | falhou
---

## O que mudou
- `Arquivo.java`: ...

## Desvios do refinamento
- nenhum | ... e por quê

## Pontos do review tratados
- [B1] ... -> corrigido em `Arquivo.java`
- [I2] ... -> não tratado, fora de escopo
```

## Regras

- Não mude nada fora do que o refinamento lista sem registrar em "Desvios do refinamento".
- Não altere contrato (endpoint, evento, schema) de forma incompatível se o refinamento não disse que pode.
- Não faça `git push`, não abra PR, não mexa em configuração de CI.
- Não apague nem enfraqueça teste existente para o build passar. Se um teste antigo quebrou por causa da
  mudança, explique no `desenvolvimento.md`.
- Dados sensíveis (CPF, número de conta, token) nunca vão para log.
