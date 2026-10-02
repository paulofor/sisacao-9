package br.com.sisacao.contracts;

import java.util.UUID;

public record AnalysisResponse(UUID runId, String agentId, String provider, String harnessVersion,
        String harnessSha256, AnalysisResult result) {}
