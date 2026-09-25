package com.example.devflow.fluxo;

final class Prompts {

    private Prompts() {
    }

    static String refinamento(String tarefa, String descricao, String observacoes) {
        String prompt = """
                Tarefa: %s
                Pasta de trabalho: .devflow/%s/

                Pedido:
                %s
                """.formatted(tarefa, tarefa, descricao);
        if (observacoes != null && !observacoes.isBlank()) {
            prompt += """

                    Esta é uma nova rodada. O refinamento.md atual não foi aprovado. Observações de quem revisou:
                    %s

                    Ajuste o refinamento.md para responder a essas observações.
                    """.formatted(observacoes);
        }
        return prompt;
    }

    static String desenvolvimento(String tarefa, int rodada, String correcao) {
        String prompt = """
                Tarefa: %s
                Rodada: %d
                Pasta de trabalho: .devflow/%s/
                """.formatted(tarefa, rodada, tarefa);
        if (correcao == null || correcao.isBlank()) {
            return prompt + "\nImplemente o refinamento aprovado em .devflow/%s/refinamento.md.\n".formatted(tarefa);
        }
        return prompt + """

                Rodada de correção. Trate o que está em .devflow/%s/%s, seguindo o refinamento.md.
                """.formatted(tarefa, correcao);
    }

    static String revisao(String tarefa, String commitBase, int rodada) {
        return """
                Tarefa: %s
                Commit base: %s
                Rodada: %d
                Pasta de trabalho: .devflow/%s/

                Revise o diff %s...HEAD contra o refinamento.md e escreva review-%d.md.
                """.formatted(tarefa, commitBase, rodada, tarefa, commitBase, rodada);
    }

    static String teste(String tarefa) {
        return """
                Tarefa: %s
                Pasta de trabalho: .devflow/%s/

                Escreva e rode os testes integrados dos critérios de aceite do refinamento.md e registre em testes.md.
                """.formatted(tarefa, tarefa);
    }
}
