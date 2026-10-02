package br.com.sisacao.agents;

import br.com.sisacao.contracts.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentControllerTest {
    @Test void validatesSnapshotBeforeCallingHarnessOrLlm() {
        HarnessStore store = mock(HarnessStore.class);
        LlmClient llm = mock(LlmClient.class);
        var controller = new AgentController(store, llm, new SimpleMeterRegistry());
        var stale = new MarketTick("EURUSD", BigDecimal.ONE, BigDecimal.TEN, Instant.now().minusSeconds(120), MarketTick.Source.SIMULATED);
        assertThrows(ResponseStatusException.class, () -> controller.analyze("market-observer", new AnalysisRequest(UUID.randomUUID(), List.of(stale))));
        assertThrows(ResponseStatusException.class, () -> controller.analyze("unknown", new AnalysisRequest(UUID.randomUUID(), List.of(stale))));
        var simulated = new MarketTick("EURUSD", BigDecimal.ONE, BigDecimal.TEN, Instant.now(), MarketTick.Source.SIMULATED);
        var live = new MarketTick("EURUSD", BigDecimal.ONE, BigDecimal.TEN, Instant.now(), MarketTick.Source.MT5);
        assertThrows(ResponseStatusException.class, () -> controller.analyze("market-observer", new AnalysisRequest(UUID.randomUUID(), List.of(simulated, live))));
        verifyNoInteractions(store, llm);
    }
}
