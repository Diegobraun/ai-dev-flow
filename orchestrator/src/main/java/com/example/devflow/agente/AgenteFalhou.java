package com.example.devflow.agente;

public class AgenteFalhou extends RuntimeException {

    public AgenteFalhou(String mensagem) {
        super(mensagem);
    }

    public AgenteFalhou(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
