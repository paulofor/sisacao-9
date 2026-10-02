import { useCallback, useEffect, useState } from "react";

type Tick = {
  symbol: string;
  bid: number;
  ask: number;
  observedAt: string;
  source: string;
};
type Agent = {
  id: string;
  name: string;
  description: string;
  harnessVersion: string;
};
type Analysis = {
  provider: string;
  harnessVersion: string;
  harnessSha256: string;
  result: { action: string; summary: string; harnessSuggestion: string };
};
type Run = {
  id: string;
  agentId: string;
  status: string;
  createdAt: string;
  durationMs: number;
  response: Analysis | null;
  error: string | null;
};
type Dashboard = {
  status: { marketSource: string; tradingEnabled: boolean };
  ticks: Tick[];
  agents: Agent[];
  runs: Run[];
};

async function api<T>(path: string, method = "GET"): Promise<T> {
  const response = await fetch(`/api${path}`, {
    method,
    signal: AbortSignal.timeout(method === "POST" ? 105_000 : 10_000),
  });
  if (!response.ok) {
    const error = await response.json().catch(() => ({}));
    throw new Error(
      error.detail || "Não foi possível conectar ao serviço. Tente novamente.",
    );
  }
  return response.json();
}

const price = (value: number) =>
  value.toLocaleString("pt-BR", {
    minimumFractionDigits: 5,
    maximumFractionDigits: 8,
  });
const time = (value: string) =>
  new Date(value).toLocaleTimeString("pt-BR", { timeZone: "UTC" });

export default function App() {
  const [data, setData] = useState<Dashboard | null>(null);
  const [error, setError] = useState("");
  const [runError, setRunError] = useState("");
  const [running, setRunning] = useState<string | null>(null);
  const refresh = useCallback(async () => {
    try {
      const [status, ticks, agents, runs] = await Promise.all([
        api<Dashboard["status"]>("/status"),
        api<Tick[]>("/market/ticks"),
        api<Agent[]>("/agents"),
        api<Run[]>("/runs"),
      ]);
      setData({ status, ticks, agents, runs });
      setError("");
    } catch (e) {
      setError(e instanceof Error ? e.message : "Serviço indisponível");
    }
  }, []);
  useEffect(() => {
    void refresh();
    const timer = setInterval(() => void refresh(), 5000);
    return () => clearInterval(timer);
  }, [refresh]);

  async function analyze(agent: Agent) {
    setRunning(agent.id);
    setRunError("");
    try {
      await api(`/agents/${agent.id}/runs`, "POST");
    } catch (e) {
      setRunError(e instanceof Error ? e.message : "A análise falhou");
    } finally {
      setRunning(null);
      await refresh();
    }
  }
  const fresh =
    !!data?.ticks.length &&
    data.ticks.every(
      (tick) => Date.now() - Date.parse(tick.observedAt) < 60_000,
    );
  return (
    <div className="app">
      <header className="topbar">
        <a className="brand" href="/" aria-label="Sisacao início">
          <span className="brand-mark">S</span>sisacao
          <span className="version">/ 0.1</span>
        </a>
        <span className="environment">Ambiente de pesquisa</span>
      </header>
      <main>
        <div className="intro">
          <div>
            <p className="eyebrow">MERCADO & INTELIGÊNCIA</p>
            <h1>Observatório</h1>
            <p className="subtitle">
              Um ponto de partida para observar o mercado e avaliar agentes.
            </p>
          </div>
          <button className="refresh" onClick={() => void refresh()}>
            Atualizar ↻
          </button>
        </div>
        <div className="status-strip">
          <span className={`connection ${error ? "offline" : ""}`}>
            <i />
            {error
              ? "Conexão indisponível"
              : data
                ? "Conectado"
                : "Conectando…"}
          </span>
          <span>
            Dados:{" "}
            <strong>
              {data?.status.marketSource === "SIMULATED"
                ? "Simulados"
                : data?.status.marketSource === "MT5"
                  ? "MetaTrader 5"
                  : "Aguardando"}
            </strong>
          </span>
          <span>
            Execução de ordens: <strong>Desabilitada</strong>
          </span>
        </div>
        {error && (
          <div className="notice error" role="alert">
            {error}
          </div>
        )}
        <section aria-labelledby="quotes-title">
          <div className="section-heading">
            <h2 id="quotes-title">Cotações</h2>
            <span>Atualização a cada 5 segundos · UTC</span>
          </div>
          <div className="panel table-scroll">
            <table>
              <thead>
                <tr>
                  <th>Ativo</th>
                  <th>Compra (bid)</th>
                  <th>Venda (ask)</th>
                  <th>Spread</th>
                  <th>Último tick</th>
                </tr>
              </thead>
              <tbody>
                {data?.ticks.map((tick) => (
                  <tr key={tick.symbol}>
                    <td>
                      <span className="symbol-icon">↗</span>
                      <strong>{tick.symbol}</strong>
                    </td>
                    <td className="numeric">{price(tick.bid)}</td>
                    <td className="numeric">{price(tick.ask)}</td>
                    <td className="numeric muted">
                      {price(tick.ask - tick.bid)}
                    </td>
                    <td>
                      {time(tick.observedAt)}{" "}
                      <span
                        className={`badge ${Date.now() - Date.parse(tick.observedAt) >= 60_000 ? "stale" : ""}`}
                      >
                        {Date.now() - Date.parse(tick.observedAt) >= 60_000
                          ? "Desatualizado"
                          : "Recente"}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            {!data?.ticks.length && (
              <p className="empty">
                {data
                  ? "Aguardando cotações da bridge."
                  : "Carregando cotações…"}
              </p>
            )}
          </div>
        </section>
        <section aria-labelledby="agents-title">
          <div className="section-heading">
            <h2 id="agents-title">Agentes</h2>
            <span>Análises sob demanda</span>
          </div>
          <div className="agent-grid">
            {data?.agents.map((agent, index) => (
              <article className="panel agent-card" key={agent.id}>
                <div className="agent-top">
                  <span className="agent-number">0{index + 1}</span>
                  <span className="badge">Harness {agent.harnessVersion}</span>
                </div>
                <h3>{agent.name}</h3>
                <p>{agent.description}</p>
                <button
                  aria-label={`Analisar com ${agent.name}`}
                  disabled={!!running || !fresh || !!error}
                  onClick={() => void analyze(agent)}
                >
                  {running === agent.id ? "Analisando…" : "Executar análise"}
                  <span>↗</span>
                </button>
              </article>
            ))}
          </div>
          {data && !fresh && (
            <p className="muted">
              As análises ficam disponíveis quando há cotações recentes.
            </p>
          )}
          {runError && (
            <div className="notice error" role="alert">
              {runError}
            </div>
          )}
        </section>
        <section aria-labelledby="history-title">
          <div className="section-heading">
            <h2 id="history-title">Últimas análises</h2>
            <span>Observações e sugestões para os harnesses</span>
          </div>
          <div className="panel history">
            {!data?.runs.length && (
              <div className="empty">
                <span className="empty-icon">◎</span>
                <h3>O primeiro passo é observar.</h3>
                <p>Execute um agente para registrar sua primeira análise.</p>
              </div>
            )}
            {data?.runs.map((run) => (
              <article className="run" key={run.id}>
                <div className="run-heading">
                  <strong>
                    {data.agents.find((agent) => agent.id === run.agentId)
                      ?.name || run.agentId}
                  </strong>
                  <span className="muted">
                    {time(run.createdAt)} UTC · {run.durationMs} ms
                  </span>
                </div>
                {run.response ? (
                  <>
                    <div className="run-tags">
                      <span className="badge">
                        {run.response.result.action}
                      </span>
                      <span className="badge">
                        {run.response.provider === "mock"
                          ? "Simulação · sem LLM"
                          : "Codex App Server"}
                      </span>
                      <span className="badge">
                        Harness {run.response.harnessVersion}
                      </span>
                    </div>
                    <p>{run.response.result.summary}</p>
                    <details>
                      <summary>Sugestão de melhoria</summary>
                      <p>
                        {run.response.result.harnessSuggestion ||
                          "Nenhuma sugestão nesta análise."}
                      </p>
                      <small>
                        Versão usada · SHA-256{" "}
                        <code>{run.response.harnessSha256}</code>
                      </small>
                    </details>
                  </>
                ) : (
                  <p className="error-text">Falhou: {run.error}</p>
                )}
              </article>
            ))}
          </div>
        </section>
        <footer>
          SISACAO / FUNDAÇÃO <span>Observação → avaliação → evolução</span>
        </footer>
      </main>
    </div>
  );
}
