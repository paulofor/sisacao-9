# Matriz de homologação da versão inicial

Definida antes da primeira execução de testes. Não usa contas de corretora,
inferência paga, buckets remotos ou datasets BigQuery.

| Área | Critério de aceite | Validação local |
| --- | --- | --- |
| Caminho feliz | Bridge simulada → API → PostgreSQL → React; executar ambos os agentes e consultar histórico | Compose + smoke HTTP + Playwright |
| Contratos | Bloquear preço negativo, spread invertido, símbolo inválido, timestamp futuro, token inválido e origem diferente | Testes backend + smoke HTTP |
| Dados | Migração em PostgreSQL real; tick antigo/duplicado não sobrescreve novo; persistência após reinício | Smoke + reinício do backend |
| Agentes | Sem dados ou com dados vencidos: análise bloqueada; desconhecido: 404; saída diferente de HOLD: rejeitada | Testes Java e smoke |
| Codex | initialize/initialized, thread/start, turn/start, correlação de IDs, JSON final; erro/timeout/fim prematuro não viram sucesso | Processo falso JSONL nos testes Java; handshake da CLI local sem inferência |
| Harness | Prompts versionados, SHA-256, sugestões registradas sem promoção automática; caminho fora da raiz bloqueado; subprocesso não herda segredos da aplicação | Testes Java + histórico integrado |
| Bridge Windows | Inicialização, seleção de símbolo, tick ausente, desconexão e fechamento do terminal | Test double MetaTrader5 em unittest; terminal real requer Windows |
| Recuperação | Bridge tenta novamente após falha HTTP/terminal; agentes indisponíveis geram erro visível, sem resultado falso | Testes bridge/backend + Playwright |
| Observabilidade | Actuator health/metrics; contadores com origem/status; runId, duração, snapshot e erro no banco | Smoke HTTP, logs e banco local |
| Segregação | SIMULATED separado de MT5; LLM mock explicitamente identificado; banco e volumes de teste descartáveis | Testes de origem + UI + Compose exclusivo |
| Desktop | Chromium: carregar cotações, analisar, histórico, estados vazio/erro, sem erro de console | Playwright |
| Mobile | Chromium com emulação iPhone 15 Pro: controles e tabela utilizáveis, sem overflow da página | Playwright + screenshot |
| Build | Java, TypeScript e imagens reproduzíveis pelos arquivos versionados; scripts shell válidos | Maven verify, npm build, Docker Compose build, bash -n e ShellCheck |

Safari/WebKit, Firefox, MT5 real, inferência Codex autenticada, autenticação de
usuários e infraestrutura de produção não fazem parte da homologação local desta
estrutura inicial. São gates explícitos para evoluções correspondentes.

## Evidência local — 02/10/2026

- 22 testes Java aprovados: API/validações, snapshots, falhas, harness e processo
  falso do App Server. Maven verify e retestes dos módulos afetados após ajustes.
- 5 testes Python aprovados: simulação, leitura MT5, inicialização/reconexão,
  timestamps, tick inválido e retomada após falha HTTP.
- TypeScript e Vite compilados; imagens dos quatro aplicativos construídas pelos
  Dockerfiles. `bash -n` e ShellCheck aprovados para `scripts/check.sh`.
- Compose completo com PostgreSQL 17: migração aplicada, ingestão, isolamento de
  origem, rejeição de dados inválidos e ticks atrasados, ambos os agentes, métricas
  e histórico persistido verificados por `scripts/smoke.py`.
- Reinício do backend preservou a análise no PostgreSQL e a bridge retomou o envio.
- 8 testes Playwright aprovados: Chromium desktop e emulação iPhone 15 Pro,
  caminhos feliz, vazio, indisponível e falha de análise; screenshots inspecionadas.
- CLI Codex 0.159.2: `initialize` e `thread/start` reais aceitaram os parâmetros
  usados pelo cliente. Nenhum `turn/start` real foi enviado; inferência não homologada.

Na sandbox, a engine Docker é separada do processo de testes. Uma sobreposição
Compose temporária expôs as portas na rede da engine para os testes; os arquivos
versionados continuam publicando somente em loopback. Não foi usado host network,
host de produção, conta de corretora nem bucket cloud.
