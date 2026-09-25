---
name: refinamento
description: Refinamento técnico de uma tarefa antes de codar. Lê o código, mapeia arquivos e contratos afetados, define critérios de aceite e plano de teste e grava tudo em .devflow/<tarefa>/refinamento.md. Use quando pedirem para refinar, detalhar, planejar ou quebrar uma tarefa, história ou ticket, ou antes de começar uma mudança que não seja trivial.
---

# Refinamento técnico

O resultado desta etapa é um documento que outra pessoa (ou outro agente) consegue implementar sem precisar
redescobrir o código. Nada de código de produção é alterado aqui.

## Entrada

- Identificador da tarefa (`<tarefa>`). Se não vier, crie um slug curto a partir da descrição, como
  `limite-diario-pix`.
- Descrição do pedido.
- Observações de uma rodada anterior, se houver. Nesse caso, leia o `refinamento.md` existente e ajuste.

## Procedimento

1. **Entenda o pedido.** Separe o que foi pedido do que você está supondo. Suposição que muda o escopo vira
   pergunta em aberto, não decisão.
2. **Ache o ponto de entrada.** Controller, listener, job ou função GraphQL por onde a mudança começa. Siga o
   caminho até onde a regra precisa mudar.
3. **Procure o padrão da casa.** Antes de propor algo, encontre como o projeto já resolve algo parecido
   (validação, erro, client HTTP, evento). A proposta segue esse padrão, a não ser que haja motivo forte.
4. **Verifique contratos.** Se a mudança toca endpoint, schema GraphQL, evento Kafka ou DTO compartilhado:
   - Com as tools do system-graph disponíveis (`impact_of_change`, `service_overview`, `find_contract_issues`),
     consulte quem consome o contrato e registre serviço, arquivo e linha.
   - Sem elas, procure consumidores no próprio repositório e escreva explicitamente "consumidores externos não
     verificados".
5. **Defina critérios de aceite testáveis.** No formato Dado / Quando / Então, cada um verificável por um teste
   automatizado. Inclua pelo menos um caso de erro.
6. **Planeje os testes.** Para cada critério, diga que tipo de teste cobre (unitário, integração com
   `@SpringBootTest`, contrato) e o que precisa ser simulado.
7. **Escreva `.devflow/<tarefa>/refinamento.md`** com o [template](template.md).

## Regras

- Escopo mínimo. Melhoria que não foi pedida vai para "Fora de escopo", não para o plano.
- Cada mudança proposta cita o arquivo. Se o arquivo ainda não existe, diga onde fica e por quê.
- Não invente regra de negócio. Valor limite, mensagem de erro e comportamento em caso de dúvida que não estão no
  pedido nem no código viram pergunta.
- `status: com-perguntas` quando houver pergunta que impede começar. Pergunta que só refina um detalhe pode ficar
  com uma sugestão de resposta e `status: pronto`.
- Seja curto. O documento é lido por quem aprova e por quem implementa, não é relatório.
