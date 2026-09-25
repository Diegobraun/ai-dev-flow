package com.example.devflow.fluxo;

import java.util.Locale;

final class PullRequest {

    private PullRequest() {
    }

    static String titulo(String tarefa, String descricao, String refinamento) {
        String doRefinamento = semCabecalho(refinamento == null ? "" : refinamento).lines()
                .filter(linha -> linha.startsWith("# "))
                .map(linha -> linha.substring(2).strip())
                .filter(linha -> !linha.isEmpty())
                .findFirst()
                .orElse(null);
        if (doRefinamento != null) {
            return cortar(doRefinamento, 120);
        }
        return cortar(descricao.lines().findFirst().orElse(tarefa).strip(), 72);
    }

    private static String cortar(String texto, int limite) {
        return texto.length() > limite ? texto.substring(0, limite - 3) + "..." : texto;
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
