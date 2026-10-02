package br.com.sisacao.agents;

import br.com.sisacao.contracts.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class CodexAppServerClientTest {
    @TempDir Path workdir;
    private CodexAppServerClient client(String mode) {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        return new CodexAppServerClient(new ObjectMapper(), List.of(java, "-cp", System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
            FakeAppServer.class.getName(), mode), workdir, Duration.ofMillis(mode.equals("timeout") ? 800 : 5000), "");
    }
    private AnalysisResult analyze(String mode) {
        return client(mode).analyze(new HarnessStore.Harness("v1", "hash", "Only observe"), new AnalysisRequest(UUID.randomUUID(), List.of()));
    }
    @Test void followsHandshakeAndUsesOnlyFinalMessageFromMatchingTurn() {
        assertEquals("Fixture observation", analyze("success").summary());
    }
    @Test void excludesApplicationSecretsFromChildEnvironment() {
        var environment = new HashMap<>(Map.of("PATH", "/bin", "OPENAI_API_KEY", "test-key",
            "BRIDGE_TOKEN", "test-bridge", "POSTGRES_PASSWORD", "test-db", "AWS_SECRET_ACCESS_KEY", "test-cloud"));
        CodexAppServerClient.filterEnvironment(environment);
        assertEquals(Set.of("PATH", "OPENAI_API_KEY"), environment.keySet());
    }
    @ParameterizedTest
    @ValueSource(strings = {"failed", "interrupted", "missing-final", "malformed", "extra-field", "buy", "rpc-error", "eof", "timeout", "approval"})
    void failsClosed(String mode) { assertThrows(IllegalStateException.class, () -> analyze(mode)); }
}
