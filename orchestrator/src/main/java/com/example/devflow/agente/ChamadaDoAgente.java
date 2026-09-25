package com.example.devflow.agente;

import java.nio.file.Path;

public record ChamadaDoAgente(Etapa etapa, Path workspace, String prompt, Path log) {
}
