package com.example.devflow.fluxo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PullRequestTest {

    @Test
    void tituloVemDoRefinamento() {
        String refinamento = """
                ---
                tarefa: paginar-loans
                status: pronto
                ---

                # Paginar a listagem de empréstimos (GET /v2/loans)

                ## Objetivo
                """;

        assertThat(PullRequest.titulo("paginar-loans", "Paginar a resposta de GET /loans no loan-service: aceitar page e size", refinamento))
                .isEqualTo("Paginar a listagem de empréstimos (GET /v2/loans)");
    }

    @Test
    void semTituloNoRefinamentoUsaADescricao() {
        String descricao = "Paginar a resposta de GET /loans no loan-service: aceitar page e size (padrão 0 e 20)";

        assertThat(PullRequest.titulo("paginar-loans", descricao, "")).hasSize(72).endsWith("...");
        assertThat(PullRequest.titulo("paginar-loans", "Validar CPF", null)).isEqualTo("Validar CPF");
    }
}
