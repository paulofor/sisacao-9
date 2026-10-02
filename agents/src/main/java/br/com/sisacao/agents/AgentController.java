package br.com.sisacao.agents;

import br.com.sisacao.contracts.*;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import org.slf4j.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/agents")
public class AgentController {
    private static final Logger LOG = LoggerFactory.getLogger(AgentController.class);
    private final HarnessStore harnesses;
    private final LlmClient llm;
    private final MeterRegistry metrics;
    public AgentController(HarnessStore harnesses, LlmClient llm, MeterRegistry metrics) {
        this.harnesses = harnesses; this.llm = llm; this.metrics = metrics;
    }
    @GetMapping
    public List<AgentDefinition> list() { return AgentCatalog.AGENTS; }

    @PostMapping("/{id}/analyze")
    public AnalysisResponse analyze(@PathVariable String id, @Valid @RequestBody AnalysisRequest request) {
        var agent = AgentCatalog.AGENTS.stream().filter(item -> item.id().equals(id)).findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agente não encontrado"));
        var now = Instant.now();
        if (request.ticks().stream().anyMatch(tick -> tick.ask().compareTo(tick.bid()) < 0
                || tick.observedAt().isBefore(now.minusSeconds(60)) || tick.observedAt().isAfter(now.plusSeconds(30)))
                || request.ticks().stream().map(MarketTick::source).distinct().count() != 1)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Snapshot inválido, misturado ou vencido");
        try {
            var harness = harnesses.load(id, agent.harnessVersion());
            var result = llm.analyze(harness, request);
            metrics.counter("sisacao.agent.analyses", "provider", llm.provider(), "status", "COMPLETED").increment();
            return new AnalysisResponse(request.runId(), id, llm.provider(), harness.version(), harness.sha256(), result);
        } catch (RuntimeException e) {
            metrics.counter("sisacao.agent.analyses", "provider", llm.provider(), "status", "FAILED").increment();
            LOG.warn("runId={} agentId={} provider={} errorType={}", request.runId(), id, llm.provider(), e.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Harness ou provedor indisponível");
        }
    }
}
