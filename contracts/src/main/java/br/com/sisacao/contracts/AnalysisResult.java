package br.com.sisacao.contracts;

public record AnalysisResult(String action, String summary, String harnessSuggestion) {
    public AnalysisResult {
        if (!"HOLD".equals(action) || summary == null || summary.isBlank() || summary.length() > 4000
                || harnessSuggestion == null || harnessSuggestion.length() > 2000) {
            throw new IllegalArgumentException("Resposta do agente fora do contrato de observação");
        }
    }
}
