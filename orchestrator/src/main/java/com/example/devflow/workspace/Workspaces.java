package com.example.devflow.workspace;

import com.example.devflow.DevFlowProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class Workspaces {

    private final Path raiz;

    public Workspaces(DevFlowProperties properties) {
        this.raiz = properties.workspaces().toAbsolutePath().normalize();
    }

    public Workspace preparar(String tarefa, String repositorio, String branchBase) {
        Path diretorio = raiz.resolve(tarefa);
        String branch = "devflow/" + tarefa;
        try {
            Files.createDirectories(raiz);
            if (!Files.isDirectory(diretorio.resolve(".git"))) {
                git(raiz, "clone", "--quiet", "--branch", branchBase, repositorio, diretorio.toString());
            } else {
                git(diretorio, "fetch", "--quiet", "origin");
            }
            git(diretorio, "checkout", "--quiet", "-B", branch, "origin/" + branchBase);
            String commitBase = head(diretorio);
            ignorarDocumentos(diretorio);
            return new Workspace(diretorio, branch, commitBase);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void restaurar(Path diretorio, String commit) {
        git(diretorio, "reset", "--quiet", "--hard", commit);
        git(diretorio, "clean", "-fdq");
    }

    public String head(Path diretorio) {
        return git(diretorio, "rev-parse", "HEAD");
    }

    public int commitsDesde(Path diretorio, String commit) {
        return Integer.parseInt(git(diretorio, "rev-list", "--count", commit + "..HEAD"));
    }

    public String documento(Path diretorio, String tarefa, String nome) {
        Path arquivo = pasta(diretorio, tarefa).resolve(nome);
        try {
            return Files.exists(arquivo) ? Files.readString(arquivo) : "";
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Path escrever(Path diretorio, String tarefa, String nome, String conteudo) {
        Path arquivo = pasta(diretorio, tarefa).resolve(nome);
        try {
            Files.createDirectories(arquivo.getParent());
            return Files.writeString(arquivo, conteudo);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Path log(Path diretorio, String tarefa, String nome) {
        return pasta(diretorio, tarefa).resolve("logs").resolve(nome);
    }

    public String abrirPullRequest(Workspace workspace, String branchBase, String titulo, Path corpo) {
        git(workspace.diretorio(), "push", "--quiet", "--force-with-lease", "-u", "origin", workspace.branch());
        List<String> gh = new ArrayList<>(List.of("gh", "pr", "create", "--base", branchBase, "--head", workspace.branch(),
                "--title", titulo, "--body-file", corpo.toString()));
        return Comando.executar(workspace.diretorio(), gh);
    }

    private static Path pasta(Path diretorio, String tarefa) {
        return diretorio.resolve(".devflow").resolve(tarefa);
    }

    private static void ignorarDocumentos(Path diretorio) throws IOException {
        Path exclude = diretorio.resolve(".git/info/exclude");
        Files.createDirectories(exclude.getParent());
        String atual = Files.exists(exclude) ? Files.readString(exclude) : "";
        if (!atual.lines().anyMatch(".devflow/"::equals)) {
            Files.writeString(exclude, (atual.isEmpty() || atual.endsWith("\n") ? "" : "\n") + ".devflow/\n",
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
    }

    private static String git(Path diretorio, String... argumentos) {
        List<String> comando = new ArrayList<>(List.of("git"));
        comando.addAll(List.of(argumentos));
        return Comando.executar(diretorio, comando);
    }
}
