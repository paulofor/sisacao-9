package br.com.sisacao.agents;

import br.com.sisacao.contracts.*;
import com.fasterxml.jackson.databind.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** One isolated JSONL conversation per analysis; no shared thread history across agents. */
public class CodexAppServerClient implements LlmClient {
    private final ObjectMapper json;
    private final List<String> command;
    private final Path workdir;
    private final Duration timeout;
    private final String model;
    private final Semaphore slots = new Semaphore(2);

    public CodexAppServerClient(ObjectMapper json, List<String> command, Path workdir, Duration timeout, String model) {
        if (timeout.isNegative() || timeout.isZero() || timeout.toSeconds() > 90)
            throw new IllegalArgumentException("Timeout Codex deve ser positivo e no máximo 90s");
        this.json = json.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.command = List.copyOf(command); this.workdir = workdir.toAbsolutePath(); this.timeout = timeout; this.model = model;
    }

    public String provider() { return "codex"; }

    public AnalysisResult analyze(HarnessStore.Harness harness, AnalysisRequest request) {
        if (!slots.tryAcquire()) throw new IllegalStateException("Limite de análises simultâneas atingido");
        Process process = null;
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            Files.createDirectories(workdir);
            var builder = new ProcessBuilder(command).directory(workdir.toFile()).redirectError(ProcessBuilder.Redirect.DISCARD);
            filterEnvironment(builder.environment());
            process = builder.start();
            Process running = process;
            return executor.submit(() -> converse(running, harness, request)).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Análise interrompida", e);
        } catch (IOException | ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Codex App Server falhou ou excedeu o prazo", e);
        } finally {
            if (process != null) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
            }
            executor.shutdownNow();
            slots.release();
        }
    }

    static void filterEnvironment(Map<String, String> environment) {
        // The LLM process does not need database, bridge, broker or cloud credentials.
        var allowed = Set.of("PATH", "HOME", "USERPROFILE", "SYSTEMROOT", "WINDIR", "TEMP", "TMP", "TMPDIR",
            "CODEX_HOME", "OPENAI_API_KEY", "LANG", "LC_ALL", "HTTP_PROXY", "HTTPS_PROXY", "NO_PROXY",
            "SSL_CERT_FILE", "SSL_CERT_DIR");
        environment.keySet().removeIf(key -> !allowed.contains(key.toUpperCase(Locale.ROOT)));
    }

    private AnalysisResult converse(Process process, HarnessStore.Harness harness, AnalysisRequest request) throws IOException {
        var session = new Session(process);
        session.call(1, "initialize", Map.of("clientInfo", Map.of("name", "sisacao_agents", "title", "Sisacao Agents", "version", "0.1.0")));
        session.send(Map.of("method", "initialized", "params", Map.of()));
        Map<String, Object> params = new HashMap<>(Map.of("cwd", workdir.toString(), "approvalPolicy", "never", "sandbox", "read-only",
            "ephemeral", true, "baseInstructions", harness.prompt(), "config", Map.of("web_search", "disabled")));
        if (!model.isBlank()) params.put("model", model);
        String thread = requiredId(session.call(2, "thread/start", params).path("thread").path("id"));
        String turn = requiredId(session.call(3, "turn/start", Map.of("threadId", thread,
            "input", List.of(Map.of("type", "text", "text", json.writeValueAsString(request))), "outputSchema", outputSchema()))
            .path("turn").path("id"));
        String finalText = null;
        while (true) {
            JsonNode event = session.next();
            JsonNode payload = event.path("params");
            if (!thread.equals(payload.path("threadId").asText())) continue;
            String method = event.path("method").asText();
            if ("item/completed".equals(method) && turn.equals(payload.path("turnId").asText())) {
                JsonNode item = payload.path("item");
                if ("agentMessage".equals(item.path("type").asText()) && !"commentary".equals(item.path("phase").asText()))
                    finalText = item.path("text").asText();
            }
            if ("turn/completed".equals(method) && turn.equals(payload.path("turn").path("id").asText())) {
                if (!"completed".equals(payload.path("turn").path("status").asText()) || finalText == null)
                    throw new IOException("Turno terminou sem resposta válida");
                return json.readValue(finalText, AnalysisResult.class);
            }
        }
    }

    private static String requiredId(JsonNode node) throws IOException {
        if (!node.isTextual() || node.asText().isBlank()) throw new IOException("ID ausente no protocolo");
        return node.asText();
    }

    static Map<String, Object> outputSchema() {
        return Map.of("type", "object", "properties", Map.of(
            "action", Map.of("type", "string", "enum", List.of("HOLD")),
            "summary", Map.of("type", "string"), "harnessSuggestion", Map.of("type", "string")),
            "required", List.of("action", "summary", "harnessSuggestion"), "additionalProperties", false);
    }

    private class Session {
        private final Reader reader;
        private final Writer writer;
        private final Deque<JsonNode> notifications = new ArrayDeque<>();
        Session(Process process) {
            reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            writer = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8);
        }
        void send(Object value) throws IOException { writer.write(json.writeValueAsString(value) + "\n"); writer.flush(); }
        JsonNode call(int id, String method, Object params) throws IOException {
            send(Map.of("id", id, "method", method, "params", params));
            while (true) {
                JsonNode message = read();
                if (message.path("id").isInt() && message.path("id").asInt() == id && !message.has("method")) {
                    if (message.has("error") || !message.has("result")) throw new IOException("Erro RPC em " + method);
                    return message.path("result");
                }
                if (notifications.size() >= 1000) throw new IOException("Excesso de eventos durante handshake");
                notifications.add(message);
            }
        }
        JsonNode next() throws IOException { return notifications.isEmpty() ? read() : notifications.removeFirst(); }
        JsonNode read() throws IOException {
            var line = new StringBuilder();
            int c;
            while ((c = reader.read()) != -1 && c != '\n') {
                if (line.length() >= 1_048_576) throw new IOException("Evento excede o limite");
                line.append((char) c);
            }
            if (c == -1 && line.isEmpty()) throw new EOFException("App Server encerrou antes do resultado");
            JsonNode message = json.readTree(line.toString());
            if (message == null || !message.isObject()) throw new IOException("Evento inválido");
            if (message.has("id") && message.has("method")) {
                send(Map.of("id", message.get("id"), "error", Map.of("code", -32601, "message", "Interactive requests are not supported")));
                throw new IOException("App Server solicitou interação não suportada");
            }
            return message;
        }
    }
}
