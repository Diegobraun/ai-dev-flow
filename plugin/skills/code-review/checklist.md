# Checklist de review

Nem todo item se aplica a toda mudança. Pule o que não tem relação com o diff.

## Correção

- A regra faz o que o critério de aceite diz, inclusive nos limites (`<` vs `<=`, lista vazia, valor nulo).
- Caminhos de erro retornam o status e a mensagem previstos, e não um 500 genérico.
- Não há exceção engolida (`catch` vazio, `catch` que só loga e segue como se tivesse dado certo).

## Contratos

- Endpoint, evento ou schema mudou de forma incompatível? Campo removido, renomeado, tipo alterado, campo novo
  obrigatório na entrada.
- Status HTTP novo ou mudado: os consumidores tratam?
- Evento Kafka: o payload antigo ainda é aceito por quem consome? Mensagens já publicadas no tópico continuam
  sendo lidas?
- Consumidores listados no refinamento batem com o `impact_of_change` do system-graph.

## Dinheiro

- Valor monetário em `BigDecimal` (ou centavos em `long`), nunca `double` ou `float`.
- Comparação com `compareTo`, não `equals` (`10.0` e `10.00` não são `equals`).
- Arredondamento explícito (`RoundingMode`) onde há divisão ou percentual.

## Transação e concorrência

- Operação que altera mais de uma coisa está numa transação só, ou há compensação.
- Leitura seguida de escrita no mesmo dado (saldo, limite) tem proteção: lock otimista (`@Version`), lock
  pessimista ou operação atômica no banco.
- Evento publicado dentro da transação: o que acontece se o commit falhar depois? (outbox, `@TransactionalEventListener`)

## Mensageria

- Consumer é idempotente: a mesma mensagem processada duas vezes não duplica efeito (débito, e-mail, cadastro).
- Erro no consumer não trava a partição para sempre: há retry limitado e DLT, ou a exceção é tratada.
- `group.id` novo ou alterado foi intencional.

## Clients HTTP e GraphQL

- Timeout de conexão e de leitura configurados.
- Retry só em operação idempotente, com limite.
- Falha do serviço externo tem comportamento definido (fallback, erro de negócio, circuit breaker).
- URL vem de configuração, não está fixa no código.

## Dados sensíveis e segurança

- CPF, número de conta, cartão, token, senha e dados pessoais não aparecem em log, mensagem de erro ou evento que
  não precisa deles.
- Entrada validada (`@Valid`, limites de tamanho e de valor).
- Endpoint novo tem a mesma proteção de autenticação e autorização que os vizinhos.
- Nada de SQL, JPQL ou filtro montado por concatenação com entrada do usuário.

## Testes

- Cada critério de aceite tem pelo menos um teste.
- Caso de erro testado, não só o caminho feliz.
- Teste existente não foi apagado nem enfraquecido para passar.
- Teste não depende de horário, ordem de execução ou rede externa.

## Observabilidade

- Erro relevante é logado uma vez, com contexto (id da operação), sem dado sensível.
- Métrica ou log de negócio, se o projeto já tem esse padrão para operações parecidas.
