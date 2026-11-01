# RoverQuest MR — Guia de implementação passo a passo

> **Status:** Parte 1 implementada; falta validar no aparelho (sessão AR e suporte a profundidade).  
> **Plataforma:** Android, com testes reais no Galaxy S23 Ultra.  
> **Objetivo:** criar um jogo divertido em RA móvel com interação espacial: o rover virtual planeja rotas evitando obstáculos físicos identificados a partir de profundidade.  
> **MVP:** posicionar o rover no piso, marcar o destino e fazê-lo contornar **uma caixa real estática** com um mapa local de ocupação.

## 1. Decisões técnicas

| Componente | Tecnologia | Responsabilidade |
| --- | --- | --- |
| Linguagem e build | Kotlin, Gradle | Aplicação nativa, lógica e testes |
| Editor | VS Code/Cursor + terminal; Android Studio opcional | Desenvolvimento assistido por LLM |
| Interface | Jetpack Compose | Controles, estados e painéis |
| Rastreamento espacial | Google ARCore SDK para Android | Câmera, planos, poses, hit tests e âncoras |
| Renderização | SceneView (ARSceneView) + Google Filament | Exibir rover, marcadores e rota em 3D |
| Profundidade | ARCore Depth API | Detectar obstáculos |
| Navegação | Grade 2D e A* em Kotlin puro | Calcular caminhos |
| Qualidade | JUnit, Gradle, adb e testes no aparelho | Testes da lógica e validação da sessão AR |

- **Sem Unity, C#, URP ou AR Foundation.** Sem headset, motor de jogos ou robô físico.
- Testar AR no celular real, não em emulador.
- Fixar versões de dependências. O SceneView muda rápido: consultar o `llms.txt` e a documentação da versão instalada.
- Fora do MVP: mapear uma casa inteira, objetos em movimento, multiplayer, reconhecimento semântico, robô físico e SLAM próprio.

**Estratégia:** primeiro validar um caminho com obstáculo **manual**; depois integrar um obstáculo **detectado** por profundidade.

## 2. Implementação por partes

### Parte 1 — Aplicativo mínimo

- [x] Projeto Kotlin/Compose com dois módulos: `:app` (Android) e `:core` (Kotlin puro).
- [ ] Sessão AR com câmera funcionando no S23 Ultra.
- [ ] Verificar no aparelho se a profundidade do ARCore é suportada e registrar o resultado.
- [ ] Mensagens claras para permissão negada e AR indisponível.
- [x] Comandos de build/teste no `AGENTS.md` e CI que compila e roda os testes: em todo PR para a `main` e após cada merge nela, com cache do Gradle.

**Concluído quando:** o APK instala via `adb`, abre a sessão AR no aparelho e o suporte a profundidade está documentado.

### Parte 2 — Piso, rover e destino

- [ ] Detectar o piso e indicar onde é possível posicionar.
- [ ] Tocar para posicionar o rover (forma simples) e o destino.
- [ ] Manter ambos fixos ao piso enquanto o celular se move.
- [ ] Indicar quando o rastreamento é perdido.

**Concluído quando:** rover e destino permanecem alinhados ao piso ao movimentar o celular.

### Parte 3 — Rota com A*

- [ ] Grade local com células livres, ocupadas e desconhecidas.
- [ ] Obstáculo manual para testes.
- [ ] A* no módulo `:core`, tratando ausência de rota.
- [ ] Exibir a rota sobre o piso.
- [ ] Testes JUnit da lógica.

**Concluído quando:** a rota contorna o obstáculo manual e os testes rodam sem câmera/ARCore.

### Parte 4 — Ler profundidade

- [ ] Ler e visualizar a profundidade em um modo de diagnóstico.
- [ ] Avisar quando a leitura estiver indisponível ou incerta.
- [ ] Gravar uma sessão do ARCore para repetir testes sem reposicionar o celular.

**Concluído quando:** o app mostra a profundidade estimada de piso, caixa e parede.

### Parte 5 — Mapa de obstáculos

- [ ] Converter profundidade em células ocupadas e livres, de forma estável.
- [ ] Considerar o tamanho do rover ao marcar obstáculos.
- [ ] Célula não observada continua desconhecida, nunca livre.
- [ ] Sobreposição visual da grade para depuração.
- [ ] Obstáculo manual continua disponível.

**Concluído quando:** uma caixa real aparece de forma estável como bloqueada e o A* calcula o desvio.

### Parte 6 — Navegação do rover

- [ ] Rover segue a rota com movimento suave.
- [ ] Para quando o rastreamento ou o mapa ficam pouco confiáveis.
- [ ] Replaneja quando o mapa muda de forma estável.

**Concluído quando:** o rover chega ao destino contornando a caixa real. **(MVP)**

### Parte 7 — Jogo

- [ ] Definir a mecânica principal antes de começar esta parte.
- [ ] Controles mínimos e fim de partida.

**Concluído quando:** alguém joga sem precisar entender ARCore ou algoritmos.

### Parte 8 — Validar e publicar

- [ ] Testar em condições ruins de iluminação e rastreamento.
- [ ] Medir desempenho no aparelho e corrigir travamentos.
- [ ] Documentar versões, instalação e limitações no README.

**Concluído quando:** o APK pode ser instalado e demonstrado de modo reproduzível.

## 3. Marcos

| Marco | Partes | Resultado |
| --- | --- | --- |
| M1 — Base Android/AR | 1–2 | Rover e destino sobre piso real |
| M2 — Navegação | 3 | A* e obstáculo manual com testes |
| M3 — Visão espacial | 4–5 | Mapa de obstáculos estimado por profundidade |
| **M4 — MVP** | **6** | **Rover desvia de caixa real** |
| M5 — Jogo completo | 7–8 | Mecânica de jogo, validação e documentação |

## 4. Estrutura

Todo o código fica em `game/`, com dois módulos: `:app` (Android, AR e interface) e `:core` (Kotlin puro). As pastas internas surgem conforme a implementação.

## 5. Limitações

- **Profundidade é estimativa.** Superfícies reflexivas, pequenas, transparentes, sem textura ou mal iluminadas podem causar erros.
- Detectar um plano não significa reconhecer "mesa", "caixa" ou "cadeira".
- A área de navegação é pequena, estática e escaneada antes.
- Se a profundidade falhar, avisar e oferecer obstáculo manual; não afirmar que obstáculos foram detectados.
- O rover é **simulado**.

## 6. Referências

- [Google — ARCore Android SDK](https://developers.google.com/ar/develop/java/quickstart)
- [Google — Depth API para Android](https://developers.google.com/ar/develop/java/depth/overview)
- [Google — Dispositivos compatíveis](https://developers.google.com/ar/devices)
- [Google — ARCore Recording & Playback (Android)](https://developers.google.com/ar/develop/java/recording-and-playback/introduction)
- [SceneView — Android AR, exemplos e `llms.txt`](https://github.com/sceneview/sceneview)
- [Jetpack Compose](https://developer.android.com/compose)

> **Próxima ação:** executar **apenas a Parte 1**. Não implementar todas as partes ao mesmo tempo.
