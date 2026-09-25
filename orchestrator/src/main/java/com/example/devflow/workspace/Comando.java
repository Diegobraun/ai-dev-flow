package com.example.devflow.workspace;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

final class Comando {

    private Comando() {
    }

    static String executar(Path diretorio, List<String> comando) {
        try {
            Process processo = new ProcessBuilder(comando)
                    .directory(diretorio.toFile())
                    .redirectErrorStream(true)
                    .start();
            String saida = new String(processo.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
            if (!processo.waitFor(5, TimeUnit.MINUTES)) {
                processo.destroyForcibly();
                throw new IllegalStateException(String.join(" ", comando) + " não terminou em 5 minutos");
            }
            if (processo.exitValue() != 0) {
                throw new IllegalStateException(String.join(" ", comando) + " falhou: " + saida);
            }
            return saida;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(String.join(" ", comando) + " interrompido", e);
        }
    }
}
