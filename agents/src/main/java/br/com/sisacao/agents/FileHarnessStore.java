package br.com.sisacao.agents;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class FileHarnessStore implements HarnessStore {
    private final Path root;
    public FileHarnessStore(@Value("${app.harness-root}") String root) { this.root = Path.of(root).toAbsolutePath().normalize(); }

    public Harness load(String agentId, String version) {
        if (!agentId.matches("[a-z0-9-]{1,64}") || !version.matches("v[0-9]{1,6}"))
            throw new IllegalArgumentException("Chave de harness inválida");
        try {
            Path file = root.resolve(agentId).resolve(version).resolve("prompt.md").toRealPath();
            if (!file.startsWith(root.toRealPath()) || Files.size(file) > 65_536)
                throw new IllegalArgumentException("Harness fora dos limites permitidos");
            byte[] bytes = Files.readAllBytes(file);
            String prompt = new String(bytes, StandardCharsets.UTF_8);
            if (prompt.isBlank()) throw new IllegalArgumentException("Harness vazio");
            return new Harness(version, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)), prompt);
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Harness não disponível: " + agentId + "/" + version, e);
        }
    }
}
