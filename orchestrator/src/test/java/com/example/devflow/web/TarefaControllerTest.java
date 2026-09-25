package com.example.devflow.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TarefaControllerTest {

    @Test
    void geraIdentificadorSemAcentoEComSufixo() {
        assertThat(TarefaController.slug("Criar limite diário de PIX por conta"))
                .matches("criar-limite-diario-de-pix-por-conta-[a-z0-9]{3}");
    }

    @Test
    void cortaDescricaoLongaNumaPalavraInteira() {
        assertThat(TarefaController.slug("Adicionar validação de valor máximo por transferência PIX para contas jurídicas"))
                .matches("adicionar-validacao-de-valor-maximo-por-[a-z0-9]{3}");
    }
}
