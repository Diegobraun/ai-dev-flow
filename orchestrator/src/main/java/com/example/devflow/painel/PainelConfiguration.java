package com.example.devflow.painel;

import com.example.devflow.DevFlowProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
class PainelConfiguration {

    @Bean
    CamundaRest camundaRest(RestClient.Builder builder,
                            @Value("${camunda.client.rest-address:http://localhost:8080}") String endereco) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(3));
        fabrica.setReadTimeout(Duration.ofSeconds(20));
        return new CamundaRest(builder
                .baseUrl(endereco)
                .requestFactory(fabrica)
                .build());
    }

    @Bean
    Painel painel(CamundaRest camunda, ObjectMapper json, DevFlowProperties properties) {
        return new Painel(camunda, json, properties);
    }
}
