package br.com.sisacao.agents;

import br.com.sisacao.contracts.AnalysisResult;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class HarnessTest {
    @TempDir Path root;

    @Test void loadsVersionedPromptsWithStableHash() {
        var store = new FileHarnessStore("../harnesses");
        for (var agent : AgentCatalog.AGENTS) {
            var artifact = store.load(agent.id(), agent.harnessVersion());
            assertEquals(64, artifact.sha256().length());
            assertEquals(artifact, store.load(agent.id(), agent.harnessVersion()));
            assertTrue(artifact.prompt().contains("HOLD"));
            assertTrue(artifact.prompt().contains("harnessSuggestion"));
        }
    }

    @Test void preventsTraversalAndSymlinkEscape() throws Exception {
        var store = new FileHarnessStore(root.toString());
        assertThrows(IllegalArgumentException.class, () -> store.load("../outside", "v1"));
        assertThrows(IllegalArgumentException.class, () -> store.load("agent", "../../file"));
        Path outside = Files.createTempFile("sisacao-harness-test-", ".md");
        try {
            Path target = Files.createDirectories(root.resolve("agent/v1")).resolve("prompt.md");
            Files.createSymbolicLink(target, outside);
            assertThrows(IllegalArgumentException.class, () -> store.load("agent", "v1"));
        } finally { Files.deleteIfExists(outside); }
    }

    @Test void cannotReturnOrderOrUnboundedText() {
        assertThrows(IllegalArgumentException.class, () -> new AnalysisResult("BUY", "buy now", ""));
        assertThrows(IllegalArgumentException.class, () -> new AnalysisResult("HOLD", " ", ""));
        assertThrows(IllegalArgumentException.class, () -> new AnalysisResult("HOLD", "a".repeat(4001), ""));
        assertThrows(IllegalArgumentException.class, () -> new AnalysisResult("HOLD", "ok", null));
    }
}
