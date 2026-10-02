package br.com.sisacao.agents;

import br.com.sisacao.contracts.*;

public interface LlmClient {
    String provider();
    AnalysisResult analyze(HarnessStore.Harness harness, AnalysisRequest request);
}
