package br.com.sisacao.agents;

import br.com.sisacao.contracts.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;

@Configuration
public class LlmConfiguration {
    @Bean
    LlmClient llmClient(ObjectMapper json, @Value("${app.llm-provider}") String provider,
            @Value("${app.codex-command}") String command, @Value("${app.codex-model}") String model,
            @Value("${app.codex-workdir}") String directory, @Value("${app.codex-timeout-seconds}") long timeout) {
        return switch (provider) {
            case "mock" -> new LlmClient() {
                public String provider() { return "mock"; }
                public AnalysisResult analyze(HarnessStore.Harness harness, AnalysisRequest request) {
                    return new AnalysisResult("HOLD", "Simulação: " + request.ticks().size()
                        + " cotações recebidas. Nenhuma ordem foi enviada. Este resultado não usa um LLM.",
                        "Adicionar casos de avaliação com dados ausentes e spreads atípicos antes de promover outro harness.");
                }
            };
            case "codex" -> new CodexAppServerClient(json, List.of(command, "app-server", "--listen", "stdio://"),
                Path.of(directory), Duration.ofSeconds(timeout), model);
            default -> throw new IllegalArgumentException("LLM_PROVIDER deve ser mock ou codex");
        };
    }
}
