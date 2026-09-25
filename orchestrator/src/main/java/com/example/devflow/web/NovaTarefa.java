package com.example.devflow.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record NovaTarefa(
        @Pattern(regexp = "[a-z0-9][a-z0-9-]{1,60}") String tarefa,
        @NotBlank String descricao,
        @NotBlank String repositorio,
        String branchBase,
        @Min(1) @Max(10) Integer limiteDeRevisoes) {
}
