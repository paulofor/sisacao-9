package br.com.sisacao.agents;

/** A future bucket adapter must preserve the same immutable key and checksum contract. */
public interface HarnessStore {
    Harness load(String agentId, String version);
    record Harness(String version, String sha256, String prompt) {}
}
