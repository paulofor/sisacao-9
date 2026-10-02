# Trabalho neste repositório

- Esta é uma estrutura inicial, de observação. Não implemente operações reais ou
  autoalteração de harness como efeito colateral de uma tarefa.
- Backend e agentes: Java 21, Spring Boot, Maven. Contratos compartilhados em
  `contracts`. Interface React/TypeScript em `frontend`. Bridge em `bridge`.
- Antes de mudar contratos, confira todos os consumidores e a matriz em
  `docs/validation.md`. Teste localmente antes de publicar pelo fluxo de PR.
- Execute `mvn -B verify`, `python3 -m unittest discover -s bridge/tests -v`,
  `npm ci --include=dev --prefix frontend` e `npm run build --prefix frontend`.
  O fluxo integrado está em `scripts/smoke.py` e `frontend/e2e`.
- Ao alterar shell, execute `bash -n` e `shellcheck` nos arquivos alterados.
- Testes usam SIMULATED, LLM mock e banco/volumes descartáveis. Nunca use dados,
  contas ou credenciais de negociação nos testes. Não registre tokens.
- Harness: mantenha versões imutáveis, hash SHA-256, contrato de saída restrito e
  fixtures de falhas. Sugestões do modelo não promovem uma versão automaticamente.
  Alterações de prompts exigem revisão e avaliação antes de promoção.
- Não confunda uma execução mock ou um servidor de protocolo falso com inferência
  real do Codex, nem a bridge simulada com homologação no terminal Windows.
- Imagens são construídas pelos Dockerfiles versionados. Não há deploy produtivo
  configurado nesta versão. Não crie cloud resources para testar este scaffold.
