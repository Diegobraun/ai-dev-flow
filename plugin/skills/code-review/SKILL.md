---
name: code-review
description: Code review de uma branch ou tarefa com foco em sistemas financeiros. Compara o diff com o refinamento, aplica o checklist (contratos, transação, idempotência, clients HTTP, dados sensíveis, dinheiro) e grava o veredito em .devflow/<tarefa>/review-<n>.md. Use quando pedirem review, revisão de código, revisar PR, branch ou diff.
---

# Code review

Revisão independente. Você não viu como o código foi escrito e não deve tentar adivinhar a intenção de quem
escreveu. Julgue o que está no diff contra o que foi combinado no refinamento.

## Entrada

- Commit base (`<base>`). Se não vier, use `git merge-base HEAD origin/main` (ou `main`/`master`).
- `.devflow/<tarefa>/refinamento.md`, se existir. Sem refinamento, revise só pelo checklist e diga isso no
  documento.
- Número da rodada (`<n>`). Se não vier, use o próximo número livre de `review-<n>.md`.

## Procedimento

1. `git log --oneline <base>..HEAD` e `git diff <base>...HEAD --stat` para ter o tamanho da mudança.
2. Leia o refinamento: critérios de aceite, mudanças propostas, contratos afetados, fora de escopo.
3. Leia o diff arquivo por arquivo. Para cada trecho alterado, abra o arquivo inteiro quando o contexto importar
   (transação, concorrência, tratamento de erro).
4. Aplique o [checklist](checklist.md).
5. Se a mudança toca contrato e as tools do system-graph estiverem disponíveis, rode `impact_of_change` e
   compare com a seção "Contratos afetados" do refinamento. Consumidor que o refinamento não previu é
   bloqueante.
6. Confira se cada critério de aceite tem implementação. Critério sem implementação é bloqueante.
7. Rode o build com testes (`./mvnw -q verify`, `mvn -q verify` ou `./gradlew check`). Build ou teste quebrado é
   bloqueante. Não conserte nada: só registre.
8. Escreva `.devflow/<tarefa>/review-<n>.md` com o [template](template.md).

## Severidade

- **Bloqueante**: o código está errado, quebra contrato, perde dinheiro, vaza dado sensível, não atende critério
  de aceite ou quebra o build. Precisa de correção antes de seguir.
- **Importante**: problema real, mas que não impede seguir (teste frágil, log ruim, nome confuso numa API pública).
- **Sugestão**: preferência ou melhoria. Nunca bloqueia.

`veredito: bloqueado` se houver pelo menos um bloqueante. Senão, `aprovado`.

## Regras

- Todo apontamento tem `arquivo:linha` e um cenário concreto: entrada ou estado que leva ao comportamento errado.
  Sem cenário, não é bloqueante.
- Se não tem certeza, escreva como pergunta, com severidade "importante" no máximo.
- Não aponte estilo que o projeto já usa em outros lugares.
- Não reescreva o código. Descreva o problema e, se for curto, a correção.
- Não edite nenhum arquivo além do `review-<n>.md`.
