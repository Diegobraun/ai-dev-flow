package com.example.devflow;

import io.camunda.client.annotation.Deployment;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
@Deployment(resources = {"classpath*:processos/*.bpmn", "classpath*:processos/*.form"})
public class DevFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(DevFlowApplication.class, args);
    }
}
