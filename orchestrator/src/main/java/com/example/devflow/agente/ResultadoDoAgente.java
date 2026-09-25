package com.example.devflow.agente;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;

public record ResultadoDoAgente(JsonNode saida, double custoUsd, int turnos, Duration duracao) {

    public String texto(String campo) {
        return saida.path(campo).asText("");
    }

    public boolean booleano(String campo) {
        return saida.path(campo).asBoolean(false);
    }

    public int inteiro(String campo) {
        return saida.path(campo).asInt(0);
    }
}
