package com.example.devflow.painel;

public class PedidoInvalido extends RuntimeException {

    public PedidoInvalido(String mensagem) {
        super(mensagem);
    }
}
