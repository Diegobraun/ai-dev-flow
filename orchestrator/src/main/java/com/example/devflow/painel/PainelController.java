package com.example.devflow.painel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

@RestController
@RequestMapping("/api")
public class PainelController {

    private final Painel painel;
    private final ObjectMapper json;

    public PainelController(Painel painel, ObjectMapper json) {
        this.painel = painel;
        this.json = json;
    }

    @GetMapping("/tarefas")
    public List<ResumoDaTarefa> tarefas() {
        return painel.tarefas();
    }

    @GetMapping("/tarefas/{chave}")
    public DetalheDaTarefa tarefa(@PathVariable String chave) {
        return painel.tarefa(chave);
    }

    @GetMapping("/pendencias")
    public List<Pendencia> pendencias(@RequestParam(required = false) String grupo) {
        return painel.pendencias(grupo);
    }

    @GetMapping("/pendencias/{chave}")
    public Pendencia pendencia(@PathVariable String chave) {
        return painel.pendencia(chave);
    }

    @PostMapping("/pendencias/{chave}/conclusao")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void concluir(@PathVariable String chave, @RequestBody(required = false) Map<String, Object> variaveis) {
        painel.concluir(chave, variaveis == null ? Map.of() : variaveis);
    }

    @GetMapping("/grupos")
    public List<String> grupos() {
        return painel.grupos();
    }

    @ExceptionHandler(PedidoInvalido.class)
    ResponseEntity<Map<String, String>> pedidoInvalido(PedidoInvalido erro) {
        return ResponseEntity.badRequest().body(Map.of("erro", erro.getMessage()));
    }

    @ExceptionHandler(RestClientResponseException.class)
    ResponseEntity<Map<String, String>> falhaDoCamunda(RestClientResponseException erro) {
        HttpStatus status = erro.getStatusCode().is4xxClientError()
                ? HttpStatus.valueOf(erro.getStatusCode().value())
                : HttpStatus.BAD_GATEWAY;
        return ResponseEntity.status(status).body(Map.of("erro", detalhe(erro)));
    }

    @ExceptionHandler(ResourceAccessException.class)
    ResponseEntity<Map<String, String>> camundaForaDoAr(ResourceAccessException erro) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("erro", "Camunda indisponível: " + erro.getMostSpecificCause().getMessage()));
    }

    private String detalhe(RestClientResponseException erro) {
        try {
            JsonNode corpo = json.readTree(erro.getResponseBodyAsString());
            if (corpo != null && corpo.hasNonNull("detail")) {
                return corpo.path("detail").asText();
            }
        } catch (Exception ignorado) {
            return erro.getStatusText();
        }
        return erro.getStatusText();
    }
}
