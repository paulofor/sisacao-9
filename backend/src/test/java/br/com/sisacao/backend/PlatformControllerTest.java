package br.com.sisacao.backend;

import br.com.sisacao.contracts.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.ResourceAccessException;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PlatformControllerTest {
    PlatformRepository repository;
    AgentClient agents;
    MockMvc mvc;
    ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    MarketTick tick = new MarketTick("EURUSD", new BigDecimal("1.1"), new BigDecimal("1.2"), Instant.now(), MarketTick.Source.SIMULATED);

    @BeforeEach void setup() {
        repository = mock(PlatformRepository.class); agents = mock(AgentClient.class);
        mvc = MockMvcBuilders.standaloneSetup(new PlatformController(repository, agents, new SimpleMeterRegistry(), "test-token", MarketTick.Source.SIMULATED))
            .setControllerAdvice(new ApiErrors()).build();
        when(agents.agents()).thenReturn(List.of(new AgentDefinition("market-observer", "Observer", "", "v1")));
        when(repository.ticks(any())).thenReturn(List.of(tick));
    }

    @Test void requiresBridgeTokenAndAcceptsValidTick() throws Exception {
        mvc.perform(post("/api/market/ticks").contentType("application/json").content(json.writeValueAsBytes(tick)))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(repository);
        mvc.perform(post("/api/market/ticks").header("X-Bridge-Token", "test-token").contentType("application/json").content(json.writeValueAsBytes(tick)))
            .andExpect(status().isAccepted());
        verify(repository).saveTick(tick);
    }

    @Test void rejectsInvalidPriceSymbolSourceAndFuture() throws Exception {
        var cases = List.of(
            new MarketTick("EURUSD", BigDecimal.ZERO, tick.ask(), tick.observedAt(), tick.source()),
            new MarketTick("../prompt", tick.bid(), tick.ask(), tick.observedAt(), tick.source()),
            new MarketTick("EURUSD", tick.ask(), tick.bid(), tick.observedAt(), tick.source()),
            new MarketTick("EURUSD", tick.bid(), tick.ask(), tick.observedAt(), MarketTick.Source.MT5),
            new MarketTick("EURUSD", tick.bid(), tick.ask(), Instant.now().plusSeconds(100), tick.source()));
        for (var bad : cases)
            mvc.perform(post("/api/market/ticks").header("X-Bridge-Token", "test-token").contentType("application/json").content(json.writeValueAsBytes(bad)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(repository);
    }

    @Test void blocksUnknownAgentMissingAndStaleData() throws Exception {
        mvc.perform(post("/api/agents/unknown/runs")).andExpect(status().isNotFound());
        when(repository.ticks(any())).thenReturn(List.of());
        mvc.perform(post("/api/agents/market-observer/runs")).andExpect(status().isConflict());
        when(repository.ticks(any())).thenReturn(List.of(new MarketTick(tick.symbol(), tick.bid(), tick.ask(), Instant.now().minusSeconds(61), tick.source())));
        mvc.perform(post("/api/agents/market-observer/runs")).andExpect(status().isConflict());
        verify(agents, never()).analyze(any(), any());
    }

    @Test void recordsFailureWithoutInventingAnalysis() throws Exception {
        when(agents.analyze(any(), any())).thenThrow(new ResourceAccessException("offline"));
        mvc.perform(post("/api/agents/market-observer/runs")).andExpect(status().isBadGateway());
        verify(repository).saveRun(any(), eq("market-observer"), eq(tick.source()), any(), anyLong(), eq(List.of(tick)), isNull(), contains("indisponível"));
    }

    @Test void persistsCorrelatedResultAndSnapshot() throws Exception {
        when(agents.analyze(any(), any())).thenAnswer(call -> new AnalysisResponse(((AnalysisRequest) call.getArgument(1)).runId(),
            "market-observer", "mock", "v1", "a".repeat(64), new AnalysisResult("HOLD", "Observação simulada", "Avaliar limites")));
        mvc.perform(post("/api/agents/market-observer/runs")).andExpect(status().isOk()).andExpect(jsonPath("$.result.action").value("HOLD"));
        verify(repository).saveRun(any(), eq("market-observer"), eq(tick.source()), any(), anyLong(), eq(List.of(tick)), notNull(), isNull());
    }

    @Test void rejectsResponseForAnotherRun() throws Exception {
        when(agents.analyze(any(), any())).thenReturn(new AnalysisResponse(UUID.randomUUID(), "market-observer", "mock", "v1", "a".repeat(64), new AnalysisResult("HOLD", "x", "")));
        mvc.perform(post("/api/agents/market-observer/runs")).andExpect(status().isBadGateway());
    }
}
