package com.example.devflow.painel;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

public class CamundaRest {

    static final int POR_PAGINA = 500;
    private static final int LIMITE_DE_PAGINAS = 20;

    private final RestClient http;

    public CamundaRest(RestClient http) {
        this.http = http;
    }

    public List<JsonNode> buscar(String recurso, Map<String, Object> filtro, String campoDeOrdem, String ordem, int limite) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("filter", filtro);
        if (campoDeOrdem != null) {
            corpo.put("sort", List.of(Map.of("field", campoDeOrdem, "order", ordem)));
        }
        corpo.put("page", Map.of("limit", limite));
        return itens(pesquisar(recurso, corpo));
    }

    public List<JsonNode> buscarTodos(String recurso, Map<String, Object> filtro) {
        List<JsonNode> todos = new ArrayList<>();
        String cursor = null;
        for (int pagina = 0; pagina < LIMITE_DE_PAGINAS; pagina++) {
            Map<String, Object> page = new LinkedHashMap<>();
            page.put("limit", POR_PAGINA);
            if (cursor != null) {
                page.put("after", cursor);
            }
            JsonNode resposta = pesquisar(recurso, Map.of("filter", filtro, "page", page));
            List<JsonNode> itens = itens(resposta);
            todos.addAll(itens);
            cursor = resposta.path("page").path("endCursor").asText(null);
            if (itens.size() < POR_PAGINA || cursor == null) {
                break;
            }
        }
        return todos;
    }

    public JsonNode obter(String caminho, Object... parametros) {
        return http.get().uri("/v2/" + caminho, parametros).retrieve().body(JsonNode.class);
    }

    public void concluir(String userTaskKey, Map<String, Object> variaveis) {
        http.post()
                .uri("/v2/user-tasks/{chave}/completion", userTaskKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("variables", variaveis))
                .retrieve()
                .toBodilessEntity();
    }

    private JsonNode pesquisar(String recurso, Map<String, Object> corpo) {
        return http.post()
                .uri("/v2/" + recurso + "/search")
                .contentType(MediaType.APPLICATION_JSON)
                .body(corpo)
                .retrieve()
                .body(JsonNode.class);
    }

    private static List<JsonNode> itens(JsonNode resposta) {
        List<JsonNode> itens = new ArrayList<>();
        if (resposta != null) {
            resposta.path("items").forEach(itens::add);
        }
        return itens;
    }
}
