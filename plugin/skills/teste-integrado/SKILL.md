---
name: teste-integrado
description: Escreve e roda testes integrados que provam cada critério de aceite do refinamento (Spring Boot, MockMvc, Testcontainers, WireMock) e registra o resultado em .devflow/<tarefa>/testes.md. Use quando pedirem teste integrado, teste de aceite, validar os critérios de uma tarefa ou verificar uma branch antes do PR.
---

# Teste integrado

Esta etapa prova que o que foi combinado no refinamento funciona com a aplicação de pé. Não corrige código de
produção: se um teste revela bug, o bug é o resultado.

## Entrada

- `.devflow/<tarefa>/refinamento.md`: critérios de aceite e plano de teste.
- Código já revisado na branch atual.

## Procedimento

1. Leia os critérios de aceite e o plano de teste.
2. Veja como o projeto já escreve teste de integração (pasta, sufixo `IT` ou `Test`, anotações, perfis, classes
   base). Siga esse padrão. Se não houver nenhum, use `@SpringBootTest` com `MockMvc` ou `WebTestClient`.
3. Dependências externas:
   - HTTP ou GraphQL de outro serviço: `MockRestServiceServer`, WireMock ou `@MockitoBean` no client, o que o
     projeto já usa.
   - Banco e Kafka: Testcontainers se o projeto já tem; senão, o que estiver configurado para teste (H2,
     `@EmbeddedKafka`).
   - Nunca rede externa de verdade.
4. Escreva um teste por critério de aceite, com nome que diga o critério. Casos de erro inclusos.
5. Rode a suíte completa (`./mvnw -q verify`, `mvn -q verify` ou `./gradlew check`), não só os testes novos.
6. Commit dos testes com `devflow(<tarefa>): testes de aceite`.
7. Escreva `.devflow/<tarefa>/testes.md`.

## testes.md

```markdown
---
tarefa: <tarefa>
etapa: teste-integrado
resultado: aprovado | falhou
---

| Critério | Teste | Resultado |
|---|---|---|
| 1 | `PaymentLimitIT.rejeitaTransferenciaAcimaDoLimite` | passou |
| 2 | `PaymentLimitIT.aceitaTransferenciaNoLimite` | falhou |

## Falhas

### Critério 2
Esperado ..., obtido .... Trecho do erro:
    ...

## Suíte completa
<n> testes, <f> falhas.
```

## Regras

- Só crie ou altere arquivos de teste (`src/test/**`) e o `testes.md`. Código de produção, não.
- Não marque critério como coberto se o teste não exercita o comportamento de verdade (por exemplo, mockando a
  própria classe que implementa a regra).
- Não use `@Disabled`, não aumente timeout para esconder lentidão, não apague teste que falha.
- `resultado: falhou` se qualquer critério falhar ou se a suíte completa tiver falha nova.
