package br.com.sisacao.agents;

import br.com.sisacao.contracts.AgentDefinition;
import java.util.List;

public final class AgentCatalog {
    private AgentCatalog() {}
    public static final List<AgentDefinition> AGENTS = List.of(
        new AgentDefinition("market-observer", "Observador de mercado", "Resume as cotações recebidas, sem enviar ordens.", "v1"),
        new AgentDefinition("risk-reviewer", "Revisor de risco", "Aponta limitações dos dados e sugere melhorias de avaliação.", "v1"));
}
