package com.example.devflow.fluxo;

import java.util.List;

final class Prompts {

    private Prompts() {
    }

    static String refinamento(String tarefa, String descricao, String observacoes, List<String> recusas,
                              boolean aceitarSugestoes) {
        String prompt = """
                Tarefa: %s
                Pasta de trabalho: .devflow/%s/

                Pedido:
                %s
                """.formatted(tarefa, tarefa, descricao);
        if (observacoes != null && !observacoes.isBlank()) {
            prompt += """

                    Esta é uma nova rodada. Leia o refinamento.md atual com Read antes de alterá-lo.
                    Observações de quem revisou:
                    %s

                    Ajuste o refinamento.md para responder a essas observações.
                    """.formatted(observacoes);
        }
        if (aceitarSugestoes) {
            prompt += """

                    O time aprovou o refinamento.md atual (leia com Read antes de alterar), mas ele ainda tem perguntas em aberto. Para cada pergunta sem
                    resposta, adote a sua sugestão, registre a decisão no documento e tire a pergunta da lista. Só
                    continue com status com-perguntas se alguma pergunta não tiver sugestão possível.
                    """;
        }
        if (!recusas.isEmpty()) {
            prompt += """

                    Áreas afetadas recusaram o refinamento (leia o refinamento.md com Read antes de alterar). Motivos:
                    %s

                    Ajuste o refinamento.md para responder a cada área, de preferência mantendo a mudança compatível
                    com o que elas usam hoje.
                    """.formatted(String.join("\n", recusas.stream().map(r -> "- " + r).toList()));
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

    static String revisao(String tarefa, String commitBase, int rodada, List<String> areasAprovadas) {
        String aprovadas = areasAprovadas.isEmpty() ? "nenhuma" : String.join(", ", areasAprovadas);
        return """
                Tarefa: %s
                Commit base: %s
                Rodada: %d
                Pasta de trabalho: .devflow/%s/

                Áreas que aprovaram a mudança: %s

                Revise o diff %s...HEAD contra o refinamento.md e escreva review-%d.md.
                """.formatted(tarefa, commitBase, rodada, tarefa, aprovadas, commitBase, rodada);
    }

    static String teste(String tarefa) {
        return """
                Tarefa: %s
                Pasta de trabalho: .devflow/%s/

                Escreva e rode os testes integrados dos critérios de aceite do refinamento.md e registre em testes.md.
                """.formatted(tarefa, tarefa);
    }
}
