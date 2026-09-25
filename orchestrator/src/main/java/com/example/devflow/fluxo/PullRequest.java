package com.example.devflow.fluxo;

import java.util.Locale;

final class PullRequest {

    private PullRequest() {
    }

    static String titulo(String tarefa, String descricao) {
        String primeiraLinha = descricao.lines().findFirst().orElse(tarefa).strip();
        return primeiraLinha.length() > 72 ? primeiraLinha.substring(0, 69) + "..." : primeiraLinha;
    }

    static String corpo(String descricao, String refinamento, String review, String testes, int rodadas, double custo) {
        return """
                %s

                Gerado pelo devflow: %d rodada(s) de code review, custo dos agentes US$ %s.

                <details>
                <summary>Refinamento</summary>

                %s
                </details>

                <details>
                <summary>Último code review</summary>

                %s
                </details>

                <details>
                <summary>Testes integrados</summary>

                %s
                </details>
                """.formatted(descricao.strip(), rodadas, String.format(Locale.ROOT, "%.2f", custo),
                semCabecalho(refinamento), semCabecalho(review), semCabecalho(testes));
    }

    static String semCabecalho(String documento) {
        String texto = documento.strip();
        if (texto.startsWith("---")) {
            int fim = texto.indexOf("\n---", 3);
            if (fim > 0) {
                texto = texto.substring(fim + 4).strip();
            }
        }
        return texto.isEmpty() ? "_não gerado_" : texto;
    }
}
