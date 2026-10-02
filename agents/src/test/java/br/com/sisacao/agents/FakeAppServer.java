package br.com.sisacao.agents;

import com.fasterxml.jackson.databind.*;
import java.io.*;
import java.util.*;

/** JSONL protocol fixture: never performs inference or accesses external services. */
public class FakeAppServer {
    static final ObjectMapper JSON = new ObjectMapper();
    static void send(Object message) throws Exception { System.out.println(JSON.writeValueAsString(message)); System.out.flush(); }

    public static void main(String[] args) throws Exception {
        String mode = args[0];
        var input = new BufferedReader(new InputStreamReader(System.in));
        boolean initialized = false;
        for (String line; (line = input.readLine()) != null;) {
            JsonNode request = JSON.readTree(line);
            String method = request.path("method").asText();
            int id = request.path("id").asInt();
            if ("initialize".equals(method)) {
                if (!request.path("params").path("clientInfo").path("name").asText().equals("sisacao_agents")) throw new AssertionError();
                send(Map.of("id", id, "result", Map.of("userAgent", "fixture")));
            } else if ("initialized".equals(method)) initialized = true;
            else if ("thread/start".equals(method)) {
                if (!initialized || !request.path("params").path("sandbox").asText().equals("read-only")
                    || !request.path("params").path("approvalPolicy").asText().equals("never")) throw new AssertionError();
                if (mode.equals("rpc-error")) { send(Map.of("id", id, "error", Map.of("code", -1))); return; }
                send(Map.of("method", "thread/started", "params", Map.of("thread", Map.of("id", "thread-1"))));
                send(Map.of("id", id, "result", Map.of("thread", Map.of("id", "thread-1"))));
            } else if ("turn/start".equals(method)) {
                if (mode.equals("eof")) return;
                if (mode.equals("timeout")) { Thread.sleep(60000); return; }
                if (!request.path("params").path("threadId").asText().equals("thread-1")
                    || !request.path("params").path("outputSchema").path("properties").path("action").path("enum").get(0).asText().equals("HOLD")) throw new AssertionError();
                if (mode.equals("approval")) { send(Map.of("id", "approval-1", "method", "item/commandExecution/requestApproval", "params", Map.of())); return; }
                // Notifications may precede the turn/start response; the client must buffer them.
                send(Map.of("method", "item/completed", "params", Map.of("threadId", "other-thread", "turnId", "turn-1",
                    "item", Map.of("type", "agentMessage", "text", "must be ignored"))));
                send(Map.of("method", "item/completed", "params", Map.of("threadId", "thread-1", "turnId", "turn-1",
                    "item", Map.of("type", "agentMessage", "phase", "commentary", "text", "still working"))));
                if (!mode.equals("missing-final")) {
                    String text = JSON.writeValueAsString(Map.of("action", mode.equals("buy") ? "BUY" : "HOLD", "summary", "Fixture observation", "harnessSuggestion", "Evaluate stale data"));
                    if (mode.equals("malformed")) text = "not json";
                    if (mode.equals("extra-field")) text = text.substring(0, text.length() - 1) + ",\"order\":true}";
                    send(Map.of("method", "item/completed", "params", Map.of("threadId", "thread-1", "turnId", "turn-1",
                        "item", Map.of("type", "agentMessage", "phase", "final_answer", "text", text))));
                }
                send(Map.of("id", id, "result", Map.of("turn", Map.of("id", "turn-1"))));
                send(Map.of("method", "turn/completed", "params", Map.of("threadId", "thread-1",
                    "turn", Map.of("id", "turn-1", "status", mode.equals("failed") ? "failed" : mode.equals("interrupted") ? "interrupted" : "completed"))));
            }
        }
    }
}
