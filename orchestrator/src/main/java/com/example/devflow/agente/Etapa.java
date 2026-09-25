package com.example.devflow.agente;

import java.util.List;
import java.util.stream.Stream;

public enum Etapa {

    REFINAMENTO("refinador", "default", Permissoes.LEITURA, Permissoes.DOCUMENTOS),
    DESENVOLVIMENTO("desenvolvedor", "acceptEdits", Permissoes.LEITURA, Permissoes.BUILD, Permissoes.COMMIT,
            List.of("Edit", "Write")),
    REVISAO("revisor", "default", Permissoes.LEITURA, Permissoes.DOCUMENTOS, Permissoes.BUILD),
    TESTE("testador", "default", Permissoes.LEITURA, Permissoes.DOCUMENTOS, Permissoes.BUILD, Permissoes.COMMIT,
            List.of("Edit(src/test/**)", "Write(src/test/**)", "Edit(**/src/test/**)", "Write(**/src/test/**)"));

    private final String agente;
    private final String modoDePermissao;
    private final List<String> ferramentas;

    @SafeVarargs
    Etapa(String agente, String modoDePermissao, List<String>... ferramentas) {
        this.agente = agente;
        this.modoDePermissao = modoDePermissao;
        this.ferramentas = Stream.of(ferramentas).flatMap(List::stream).toList();
    }

    public String agente() {
        return agente;
    }

    public String modoDePermissao() {
        return modoDePermissao;
    }

    public List<String> ferramentas() {
        return ferramentas;
    }

    public String schema() {
        return "schemas/" + name().toLowerCase() + ".json";
    }

    public static final List<String> PROIBIDAS = List.of(
            "Bash(git push *)", "Bash(git remote *)", "Bash(gh *)", "Bash(curl *)", "Bash(wget *)",
            "Bash(rm -rf *)", "WebFetch", "WebSearch");

    private static final class Permissoes {
        static final List<String> LEITURA = List.of(
                "Bash(git log *)", "Bash(git diff *)", "Bash(git show *)", "Bash(git status *)", "Bash(git status)",
                "Bash(ls *)", "Bash(mkdir -p .devflow/*)", "mcp__system-graph");
        static final List<String> DOCUMENTOS = List.of("Edit(.devflow/**)", "Write(.devflow/**)");
        static final List<String> BUILD = List.of("Bash(mvn *)", "Bash(./mvnw *)", "Bash(./gradlew *)", "Bash(gradle *)");
        static final List<String> COMMIT = List.of("Bash(git add *)", "Bash(git commit *)");
    }
}
