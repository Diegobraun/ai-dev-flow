---
name: tarefa
description: Conduz uma tarefa pelo fluxo completo, com um subagente por etapa (refinador, desenvolvedor, revisor, testador) e aprovação humana depois do refinamento e antes do PR. Use quando pedirem para levar uma tarefa do começo ao fim, rodar o fluxo completo ou o devflow.
argument-hint: <descrição da tarefa>
---

# Fluxo completo de uma tarefa

Você é o orquestrador. Não refina, não codifica e não revisa: delega cada etapa ao subagente dela e controla a
passagem entre etapas. Toda informação entre etapas passa pelos arquivos em `.devflow/<tarefa>/`, nunca pelo
resumo que você tem na cabeça.

Os subagentes são `refinador`, `desenvolvedor`, `revisor` e `testador`. Instalados pelo plugin, os nomes são
`devflow:refinador`, `devflow:desenvolvedor`, `devflow:revisor` e `devflow:testador`.

## 0. Preparar

1. Crie o identificador da tarefa: slug curto da descrição (`limite-diario-pix`).
2. Confirme que a árvore de trabalho está limpa (`git status`). Se não estiver, pare e pergunte.
3. Guarde o commit atual como base: `git rev-parse HEAD`.
4. Crie a branch `devflow/<tarefa>`.
5. Garanta que `.devflow/` não entra em commit: adicione ao `.git/info/exclude` se ainda não estiver.

## 1. Refinamento

Chame o `refinador` com: identificador, descrição completa do pedido e, numa segunda rodada, as observações do
usuário.

Depois, leia `.devflow/<tarefa>/refinamento.md` e mostre ao usuário: objetivo, mudanças propostas, contratos
afetados, critérios de aceite e perguntas em aberto.

**Pare e peça aprovação.** Se o usuário pedir ajuste, chame o `refinador` de novo com as observações. Não siga
sem um "pode seguir" explícito.

## 2. Desenvolvimento

Chame o `desenvolvedor` com o identificador e a rodada. Na primeira rodada, a instrução é implementar o
refinamento. Nas seguintes, é corrigir o `review-<n>.md` ou o `testes.md` mais recente.

## 3. Code review

Chame o `revisor` com o identificador, o commit base e o número da rodada. Passe só isso: o revisor não pode
receber resumo do que o desenvolvedor fez, nem a sua opinião sobre o código.

Leia o `veredito` no cabeçalho de `review-<n>.md`:

- `aprovado`: siga para o teste.
- `bloqueado` e esta foi a rodada 1 ou 2: volte para o desenvolvimento.
- `bloqueado` na rodada 3: pare, mostre os bloqueantes ao usuário e pergunte se corrige mais uma vez, se segue
  mesmo assim ou se cancela.

## 4. Teste integrado

Chame o `testador` com o identificador. Leia o `resultado` em `testes.md`:

- `aprovado`: siga.
- `falhou`: mostre as falhas ao usuário e pergunte se volta para o desenvolvimento ou se para aqui.

## 5. Fechamento

Mostre ao usuário:

- `git log --oneline <base>..HEAD`
- resumo de cada etapa, com o caminho de cada documento
- quantas rodadas de review houve

Sugira o comando para abrir o PR com os documentos no corpo, por exemplo
`gh pr create --title "..." --body-file .devflow/<tarefa>/pr.md`, depois de montar o `pr.md` com o objetivo, os
critérios de aceite, o último review e o resultado dos testes.

**Não faça push nem abra PR sem pedido explícito.**
