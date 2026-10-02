# Integrações

## Bridge MT5 no Windows

Instale Python 64 bits e MetaTrader 5 no mesmo Windows. Abra o terminal e conecte
uma conta de demonstração. O adaptador usa a sessão do terminal; não recebe senha
de corretora em código ou no frontend.

```powershell
py -m venv .venv
.venv\Scripts\python -m pip install -r bridge\requirements-windows.txt
$env:BRIDGE_MODE = "mt5"
$env:BACKEND_URL = "https://seu-backend-privado"
# Defina BRIDGE_TOKEN pelo gerenciador de segredos, igual ao backend.
$env:SYMBOLS = "EURUSD,GBPUSD"
$env:MT5_TERMINAL_PATH = "C:\Program Files\MetaTrader 5\terminal64.exe"
.venv\Scripts\python bridge\bridge.py --once
.venv\Scripts\python bridge\bridge.py
```

O backend deve usar `MARKET_SOURCE=MT5`. Pare a bridge simulada. O Compose padrão
só publica portas locais e **não** configura uma rota Windows → backend: é preciso
um acesso privado/HTTPS com controle de acesso antes de conectar um host remoto.
Use os nomes exatos de símbolos da corretora (inclusive sufixos).

`initialize`, `terminal_info`, `symbol_select`, `symbol_info_tick` e `shutdown`
seguem a [API oficial MetaTrader5](https://www.mql5.com/en/docs/python_metatrader5/mt5symbolinfotick_py).
Timestamp de mercado vem de `time_msc`, preservado em UTC. Falhas fecham a conexão
e permitem nova inicialização no ciclo seguinte; HTTP falho também recebe nova
tentativa com espera crescente, sem fila histórica. Não há fallback para simulação
quando MT5 falha e não existe chamada de execução de ordens.

## Codex App Server

O cliente segue a [documentação oficial](https://learn.chatgpt.com/docs/app-server)
e o schema gerado pela CLI `0.159.2`. O transporte stdio usa um JSON por linha,
sem campo `jsonrpc`, com o fluxo:

```text
initialize → resposta → initialized
thread/start → thread.id
turn/start (outputSchema) → turn.id
item/completed (agentMessage final) → turn/completed (status=completed)
```

O cliente correlaciona thread/turn, armazena notificações que chegam antes da
resposta RPC, ignora mensagens de comentário e valida o JSON final em Java.
Erros RPC, EOF, timeout, falhas/interrupções do turno e pedidos interativos falham
explicitamente. O processo é encerrado após cada análise e em qualquer erro.
O limite é 90s no Codex, 100s no cliente backend e 105s no proxy/frontend.

Para usar o provedor real, instale/autentique Codex numa **conta de sistema dedicada**,
sem plugins, MCPs, hooks, credenciais de corretora ou ferramentas de negociação.
Use a CLI autenticada pela conta apropriada; credenciais não vão ao banco nem à UI.
O Dockerfile padrão usa mock e não instala Codex. Execute o serviço de agentes no
host onde a CLI está instalada e autenticada:

```bash
# Na raiz; backend e agentes na mesma máquina, ambos em loopback:
LLM_PROVIDER=codex CODEX_MODEL=seu-modelo-habilitado java -jar agents/target/agents-0.1.0-SNAPSHOT.jar
```

`CODEX_COMMAND` muda o caminho do executável, `CODEX_WORKDIR` indica o diretório
isolado (padrão `.local/codex-workspace`) e `HARNESS_ROOT` indica a raiz dos prompts.
Se `CODEX_MODEL` ficar vazio, a CLI usa seu modelo configurado. A thread usa
`sandbox=read-only`, `approvalPolicy=never`, `ephemeral=true` e pesquisa web desativada.
O subprocesso recebe apenas variáveis do sistema, proxy/certificados e autenticação
Codex/OpenAI; tokens da bridge, banco e cloud não são herdados do ambiente.
O harness solicita observação sem uso de ferramentas; isso não substitui o isolamento
do usuário/configuração da CLI. Não execute com configuração pessoal privilegiada.

Os testes de protocolo usam um processo Java falso e não gastam inferência. A
homologação real exige autenticação e uma execução completa com o modelo escolhido;
handshake ou `thread/start` isolados não comprovam acesso ao modelo.

## API inicial

| Método | Caminho | Uso |
| --- | --- | --- |
| GET | `/api/status` | Origem selecionada e execução de ordens desabilitada |
| POST | `/api/market/ticks` | Ingestão, exige `X-Bridge-Token`; retorna 202 |
| GET | `/api/market/ticks` | Última cotação por símbolo, até 50 símbolos |
| GET | `/api/agents` | Catálogo de agentes |
| POST | `/api/agents/{id}/runs` | Análise com snapshot recente; retorna resultado e ID |
| GET | `/api/runs` | Últimas 20 análises da origem selecionada |
| GET | `/actuator/health` | Saúde do serviço (backend inclui banco) |
| GET | `/actuator/metrics` | Métricas; `sisacao.ticks.received`, `sisacao.runs`, `sisacao.agent.analyses` |

Payload de tick:

```json
{"symbol":"EURUSD","bid":1.10001,"ask":1.10016,"observedAt":"2026-10-02T12:00:00Z","source":"SIMULATED"}
```

Atualize o horário ao testar. Preços devem ser positivos, ask ≥ bid, precisão até
8 casas. O backend recusa horário mais de 30s no futuro e outra origem. Ticks
vencidos podem aparecer no painel, mas impedem análise. Não há replay automático de
análises: uma nova tentativa do usuário cria um novo ID.

Stack Java: [requisitos Spring Boot 3.5](https://docs.spring.io/spring-boot/3.5/system-requirements.html).
