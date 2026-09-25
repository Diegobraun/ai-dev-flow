package com.example.devflow.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record NovaTarefa(
        @Pattern(regexp = "[a-z0-9][a-z0-9-]{1,60}") String tarefa,
        @NotBlank String descricao,
        @NotBlank String repositorio,
        String branchBase) {
}
