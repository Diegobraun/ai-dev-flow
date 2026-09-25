package com.example.devflow.painel;

import com.fasterxml.jackson.databind.JsonNode;

public record AreaAfetada(
        String area,
        JsonNode times,
        JsonNode servicos,
        JsonNode contratos,
        String status,
        String motivo,
        String userTaskKey) {
}
