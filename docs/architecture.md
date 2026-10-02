# Arquitetura inicial

O monorepositório usa Maven multimódulo para compartilhar o contrato sem acoplar
a execução dos serviços. Backend e agentes são dois processos Spring Boot. Dois
agentes (`market-observer` e `risk-reviewer`) compartilham o runtime inicial;
o catálogo permite acrescentar papéis e versões. A execução é manual e síncrona,
com limite de dois processos Codex simultâneos. Não há scheduler de negociação.

PostgreSQL guarda o último tick de cada símbolo/origem e o histórico das análises:
ID, agente, fonte, horário, duração, snapshot de entrada, provedor, versão/hash do
harness, saída ou erro. Flyway aplica `V1__initial_schema.sql`. Não é um histórico
de todos os ticks. Uma atualização atrasada/duplicada não substitui uma mais nova.

Cada instância do backend escolhe uma fonte (`SIMULATED` ou `MT5`) e recusa ingestão
da outra. Consultas e histórico são filtrados pela fonte. Homologação usa instâncias
e volumes separados: não conecte o smoke a um banco com dados reais. Trocar a fonte
não apaga dados; a chave composta mantém origens distintas.

## Harness e ciclo de melhoria

A lacuna inicial era a ausência de um contrato que distinguisse saída válida,
sugestão de melhoria e modificação efetiva de um agente. A fundação inclui:

1. Artefato em `harnesses/{agentId}/{version}/prompt.md`, resolvido por `HarnessStore`.
2. SHA-256 calculado sobre os bytes efetivamente lidos e registrado com cada sucesso.
3. Saída tipada com somente `action=HOLD`, `summary` e `harnessSuggestion`.
4. Fixtures de erro, timeout, aprovação interativa, JSON inválido e saída de negociação.
5. Proposta de melhoria armazenada para revisão. Promoção exige novo diretório de
   versão, avaliação e PR; um agente não reescreve o próprio harness.

As avaliações atuais são de contratos e segurança do fluxo, não de qualidade de
estratégias ou rentabilidade. Ao introduzir modelos reais, acrescentar um conjunto
versionado de snapshots com critérios de qualidade antes de promover prompts.

Um futuro adaptador S3/GCS implementará `HarnessStore.load` usando a mesma chave
imutável, validação de tamanho, checksum e erro explícito se o objeto faltar.
Buckets devem guardar artefatos; o banco mantém metadados e referência à versão.
Não foi adicionado SDK/cloud para uma integração que ainda não será utilizada.

## Limites deliberados

- Bridge coleta o último tick por polling (2s padrão). Não garante capturar todos
  os ticks e não substitui um pipeline de dados históricos.
- Agentes recebem somente o snapshot validado. Nesta versão não há ferramentas de
  negociação, endpoint de ordens, posições, carteira nem treinamento automático.
- API e Actuator são locais e ainda não têm login de usuário. Bridge exige token;
  serviço de agentes deve permanecer em rede privada.
- Métricas Actuator reiniciam com o processo; o histórico no banco é durável.
- BigQuery poderá receber eventos históricos para análise em lote, após estudo de
  volume, custo, retenção e segregação. Nenhum pipeline analítico foi antecipado.
- Os Dockerfiles versionados produzem imagens locais/CI. Publicação produtiva,
  destino e gestão de segredos devem ser definidos numa etapa específica.
