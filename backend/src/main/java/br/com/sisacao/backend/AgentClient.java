package br.com.sisacao.backend;

import br.com.sisacao.contracts.*;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AgentClient {
    private final RestClient client;
    public AgentClient(@Value("${app.agents-url}") String url) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofSeconds(100));
        client = RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }
    public List<AgentDefinition> agents() {
        return List.of(client.get().uri("/internal/agents").retrieve().body(AgentDefinition[].class));
    }
    public AnalysisResponse analyze(String agentId, AnalysisRequest request) {
        return client.post().uri("/internal/agents/{id}/analyze", agentId).body(request)
            .retrieve().body(AnalysisResponse.class);
    }
}
