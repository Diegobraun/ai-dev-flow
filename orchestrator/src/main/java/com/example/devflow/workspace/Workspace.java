package com.example.devflow.workspace;

import java.nio.file.Path;

public record Workspace(Path diretorio, String branch, String commitBase) {
}
