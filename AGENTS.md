# AGENTS.md

## Projeto
RoverQuest MR: jogo de RA para Android (Kotlin), focado em diversão. Um rover virtual usa A* para desviar
de obstáculos reais detectados pela ARCore Depth API. Aparelho de teste: Galaxy S23 Ultra.

O plano oficial é `docs/step-by-step.md`. Leia antes de implementar e siga a ordem das partes.

## Git
- Trabalhe na branch `develop`. Não crie outras branches sem pedido explícito.
- `main` recebe apenas merge da `develop`, via pull request.
- Commits pequenos, em inglês, no formato `tipo: descrição` (`feat`, `fix`, `docs`, `test`, `chore`).

## Como trabalhar
- Implemente **uma parte do plano por vez**; não antecipe partes futuras nem crie pastas vazias.
- Ao concluir uma parte, marque os checkboxes correspondentes em `docs/step-by-step.md`.
- Uma mudança só está pronta quando compila e os testes relevantes passam.
- O que depende de câmera/ARCore só pode ser validado no aparelho: diga que não foi testado
  em vez de afirmar que funciona.
- Detalhes não especificados no plano ficam a critério do código existente; adapte-se a ele.

## Regras técnicas
- Sem Unity, C#, AR Foundation ou motores de jogos.
- Grade, A* e projeção de profundidade ficam no módulo `:core`, Kotlin puro, sem imports Android.
- `:core` recebe dados prontos (profundidade, toques, tempo); nunca acessa ARCore ou Android.
- Posições do jogo (rover, destino, rota, obstáculos) são relativas à âncora do piso, não ao mundo.
- A interface só exibe estado e envia ações; regras de jogo não ficam na UI.
- Fixe versões de dependências; o SceneView muda rápido: consulte a documentação/`llms.txt`
  da versão instalada, não exemplos genéricos da internet.
- Código e identificadores em inglês; documentação em português.

## Comandos
A definir na Parte 1 (build, testes de `:core`, instalação via `adb`).
