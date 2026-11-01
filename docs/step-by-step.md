# RoverQuest MR — Guia de implementação passo a passo

> **Status:** planejamento; implementação ainda não iniciada.  
> **Plataforma:** Android, com testes reais no Galaxy S23 Ultra.  
> **Objetivo:** criar um jogo educativo de robótica em RA móvel com interação espacial: o rover virtual planeja rotas evitando obstáculos físicos identificados a partir de profundidade.  
> **MVP:** posicionar o rover no piso, marcar o destino e fazê-lo contornar **uma caixa real estática** com um mapa local de ocupação.

## 1. Decisões técnicas

| Componente | Tecnologia | Responsabilidade |
| --- | --- | --- |
| Linguagem e build | Kotlin, Gradle | Aplicação nativa, lógica e testes |
| Editor | VS Code/Cursor + terminal; Android Studio opcional | Desenvolvimento assistido por LLM; configurar Android SDK quando necessário |
| Interface | Jetpack Compose | Controles, estados e painéis |
| Rastreamento espacial | Google ARCore SDK para Android | Câmera, planos, poses, hit tests e âncoras |
| Renderização | SceneView (ARSceneView) + Google Filament | Exibir rover, marcadores e rota em 3D |
| Profundidade | ARCore Depth API / Raw Depth | Observar distâncias, detectar obstáculos |
| Navegação | Grade 2D e A* em Kotlin puro | Calcular caminhos sem motor de jogos |
| Qualidade | JUnit, Gradle, adb e testes no aparelho | Testes da lógica e validação da sessão AR |

**Sem Unity, C#, URP ou AR Foundation.** Não é necessário headset, motor de jogos ou robô físico.

- Preferir classes Kotlin pequenas e independentes de Android para grade, ocupação e A*.
- Utilizar o celular real para executar AR: não depender de um emulador Android pesado.
- Manter acesso às classes `Session` e `Frame` do **ARCore** mesmo quando a renderização for abstraída pelo SceneView.
- Usar versões **compatíveis e fixadas** de Kotlin, Compose, SceneView e ARCore, escolhidas após compilar o projeto mínimo; consultar a API instalada, pois o SceneView evolui rapidamente.
- Fazer o diagnóstico da Depth API antes de prometer colisão e desvio de obstáculos físicos.
- Cenário inicial: área pequena, piso plano, boa iluminação e um obstáculo estático.
- Fora do MVP: mapear uma casa inteira, objetos em movimento, multiplayer, reconhecer móveis semanticamente, controlar robô físico e implementar SLAM próprio.

**Estratégia:** primeiro validar um caminho com obstáculo **manual**; depois integrar um obstáculo **detectado** por profundidade.

## 2. Implementação por partes

### Parte 1 — Criar o aplicativo Android mínimo

- [ ] Preparar JDK e Android SDK; verificar `adb` e `./gradlew`.
- [ ] Criar projeto Kotlin/Gradle com Jetpack Compose e configuração Android.
- [ ] Adicionar SceneView/ARSceneView e dependências compatíveis de ARCore.
- [ ] Configurar manifest como **AR Required**, câmera, ARCore, verificações de compatibilidade e permissões.
- [ ] Definir `minSdk` compatível com a versão fixada do SceneView (ARCore exige no mínimo API 24).
- [ ] Exibir a câmera e uma sessão AR de teste no Galaxy S23 Ultra.
- [ ] **Diagnóstico antecipado de profundidade:** verificar `session.isDepthModeSupported(...)` para `AUTOMATIC` e `RAW_DEPTH_ONLY` no aparelho e registrar o resultado. O S23 Ultra não tem sensor ToF; a profundidade é estimada por movimento — confirmar antes de investir nas Partes 4–5.
- [ ] Criar estados claros para recurso indisponível, permissão negada e sessão interrompida.
- [ ] Validar compilação e instalação via Gradle/adb, sem depender do editor gráfico.
- [ ] Criar o módulo Gradle **JVM puro `:core`** (sem plugin Android) para mapeamento, navegação e matemática de projeção.
- [ ] Criar `AGENTS.md` com convenções, comandos de build/teste e critérios de aceite por parte.
- [ ] Adicionar CI que compila o projeto e executa os testes unitários de `:core`.

**Concluído quando:** o APK abre, solicita permissões corretamente, mostra a sessão AR com rastreamento no aparelho e o suporte a profundidade do S23 Ultra está documentado.

### Parte 2 — Detectar piso, posicionar rover e destino

- [ ] Consultar planos horizontais rastreados pelo ARCore (`Plane`).
- [ ] Aplicar hit test com `Frame.hitTest(...)` para selecionar pontos de posicionamento.
- [ ] Exibir reticle enquanto o plano é encontrado.
- [ ] Começar com um rover primitivo (caixa/cilindro); depois trocar por um modelo 3D leve, em escala adequada (GLB/glTF), registrando fonte e licença.
- [ ] Fixar rover e destino a referências espaciais usando `Anchor`, gerenciando seu ciclo de vida.
- [ ] Exibir estados: procurando piso, posicionando, pronto e rastreamento perdido.

**Concluído quando:** o rover e o destino permanecem visualmente alinhados ao piso ao movimentar o celular, dentro dos limites do ARCore.

### Parte 3 — Implementar A* sem depender de AR

- [ ] Criar grade local 2D (exemplo: 2 × 2 m, células de 10–20 cm).
- [ ] Distinguir células **livres**, **ocupadas** e **desconhecidas**.
- [ ] Permitir um obstáculo retangular **manual** para testes.
- [ ] Implementar A* em Kotlin puro, no módulo `:core`, com heurística consistente e regras de vizinhança definidas.
- [ ] Definir a política para células **desconhecidas**: bloqueadas no modo Missão; custo alto (mas transitáveis) no modo Laboratório.
- [ ] Converter o caminho em pontos 3D visíveis sobre o piso.
- [ ] Tratar ausência de rota, início/destino bloqueado e coordenadas fora da grade.
- [ ] Escrever testes JUnit para caminho livre, desvio, bloqueio, menor caminho e tratamento de células desconhecidas em cenários simples.

**Concluído quando:** o caminho contorna o obstáculo manual e os testes da lógica executam sem câmera/ARCore.

### Parte 4 — Ler e visualizar dados reais de profundidade

- [ ] Verificar compatibilidade e suporte ao `Config.DepthMode` em execução.
- [ ] Configurar `AUTOMATIC` ou `RAW_DEPTH_ONLY` conforme o experimento e suporte do aparelho.
- [ ] Acessar quadros ARCore e obter profundidade por APIs como `Frame.acquireDepthImage16Bits()` ou `Frame.acquireRawDepthImage16Bits()`, de acordo com o modo configurado.
- [ ] Exibir um mapa de profundidade colorido em um modo de diagnóstico.
- [ ] Tratar ausência de atualização, medidas inválidas e, quando disponíveis, imagens de confiança Raw Depth.
- [ ] Encerrar corretamente cada `Image` adquirida e evitar processamento pesado na thread da interface.
- [ ] Testar piso, caixa e parede em iluminação/diferentes distâncias.
- [ ] Usar **ARCore Recording & Playback** (`Session.startRecording`, `Session.setPlaybackDatasetUri`) para gravar o cenário da caixa e reprocessá-lo de forma reprodutível, sem reposicionar o celular a cada teste.
- [ ] Opcional: exportar amostras (profundidade + intrínsecos + pose) como fixtures para testes JVM do módulo `:core`.

**Concluído quando:** o celular mostra profundidade estimada e o aplicativo informa quando essa leitura é indisponível ou incerta.

### Parte 5 — Converter profundidade em mapa de obstáculos

- [ ] Definir referencial de mapa **ancorado ao piso** e coerente com rover e destino.
- [ ] Projetar amostras de profundidade em pontos 3D usando intrínsecos de câmera, resolução da imagem, orientação e pose correspondentes:
  - escalar `camera.imageIntrinsics` para a resolução da imagem de profundidade;
  - usar a pose da câmera do **mesmo instante** da imagem de profundidade (comparar `Image.timestamp` com o do frame);
  - Depth16 está em milímetros; valor `0` significa medida inválida.
- [ ] Copiar o buffer e fechar a `Image` imediatamente; processar em corrotina de fundo a ~5–10 Hz.
- [ ] Manter a função de projeção pura (arrays + intrínsecos + matrizes) no módulo `:core`, com testes JVM.
- [ ] Filtrar medidas inválidas/ruidosas.
- [ ] Classificar pontos pela altura em relação ao plano do piso (limiares iniciais, ajustáveis):
  - |h| < ~3 cm → evidência de célula **livre**;
  - ~5 cm < h < ~1,5 m → evidência de célula **ocupada**;
  - demais alturas → ignorar.
- [ ] Acumular evidências por célula com contadores hit/miss (ou log-odds) e limiares para trocar de estado.
- [ ] Inflar células ocupadas de acordo com raio/largura do rover e margem de segurança.
- [ ] Combinar observações sucessivas com histerese conservadora, evitando oscilações.
- [ ] Conservar células sem evidência como **desconhecidas**, não automaticamente livres.
- [ ] Disponibilizar sobreposição visual de grade, pontos/obstáculos e rota para depuração.
- [ ] Preservar a opção de inserir obstáculo manual.

**Concluído quando:** uma caixa física estática é representada de forma estável como região bloqueada, suficiente para o A* calcular um desvio.

**Atenção:** a Depth API não devolve automaticamente mapa de ocupação, colisores físicos nem identificação semântica de objetos. Essa conversão é uma parte principal do projeto.

### Parte 6 — Fazer o rover navegar

- [ ] Implementar deslocamento do rover ao longo da rota com velocidade limitada e orientação suave.
- [ ] Expressar rota e posição do rover no referencial da âncora do piso, recalculando a pose de mundo a cada frame (a âncora pode ser ajustada pelo ARCore).
- [ ] Animar as rodas e atualizar estados da missão.
- [ ] Parar ao perder rastreamento ou quando o mapa ficar pouco confiável.
- [ ] Replanejar somente após mudanças relevantes e estáveis, com limite de frequência.
- [ ] Definir tolerância de chegada ao destino.
- [ ] Medir distância aproximada, tempo de cálculo A* e duração da missão.

**Concluído quando:** o rover virtual chega ao destino contornando a caixa real em um cenário controlado.

### Parte 7 — Tornar o projeto útil e divertido

- [ ] Criar **Missão 01 — Desvie do obstáculo**.
- [ ] Exibir controles intuitivos: escanear, posicionar, escolher destino, iniciar e reiniciar.
- [ ] Adicionar conclusão da missão e pontuação simples.
- [ ] Criar dois modos: **Missão** (experiência lúdica) e **Laboratório** (mapa, rota e métricas).
- [ ] Manter interface mobile minimalista, legível e com poucos elementos simultâneos.

**Concluído quando:** alguém consegue completar a missão sem conhecer ARCore ou algoritmos de navegação.

### Parte 8 — Validar, otimizar e documentar

- [ ] Testar piso livre, caixa estática e condições desfavoráveis de iluminação/rastreamento.
- [ ] Registrar falsos obstáculos, falhas de profundidade e estabilidade do posicionamento.
- [ ] Medir FPS, memória, frequência de atualização do mapa e tempo do A* no celular.
- [ ] Ajustar resolução/amostragem e frequência de processamento para evitar travamentos.
- [ ] Tratar pausa, retomada, permissões negadas e recursos indisponíveis.
- [ ] Documentar versões exatas das dependências e comandos para build/instalação.
- [ ] Criar README com requisitos, arquitetura, imagens, demonstração e limitações verificadas.
- [ ] Revisar a CI (criada na Parte 1) e o `AGENTS.md` para refletir o estado final.

**Concluído quando:** o APK pode ser instalado e demonstrado de modo reproduzível, com limitações conhecidas documentadas.

## 3. Marcos

| Marco | Partes | Resultado |
| --- | --- | --- |
| M1 — Base Android/AR | 1–2 | Rover e destino sobre piso real |
| M2 — Navegação | 3 | A* e obstáculo manual com testes |
| M3 — Visão espacial | 4–5 | Mapa de obstáculos estimado por profundidade |
| **M4 — MVP** | **6** | **Rover desvia de caixa real** |
| M5 — Experiência completa | 7–8 | Missão, laboratório, medições e documentação |

## 4. Estrutura futura sugerida

```text
rover-quest-mr/
├── docs/
│   └── step-by-step.md
├── core/                         # Módulo JVM puro, sem Android
│   └── src/
│       ├── main/kotlin/.../roverquest/
│       │   ├── mapping/          # Grade de ocupação e projeção de profundidade
│       │   └── navigation/       # A*, rotas
│       └── test/kotlin/          # Testes JUnit (A*, mapeamento, projeção)
├── app/
│   └── src/
│       ├── main/
│       │   ├── java/.../roverquest/
│       │   │   ├── ar/           # Sessão, planos, poses, anchors
│       │   │   ├── depth/        # Captura, cópia de buffers, gravação/playback
│       │   │   ├── rover/        # Movimento e renderização
│       │   │   └── ui/           # Jetpack Compose
│       │   ├── assets/models/   # Modelos GLB
│       │   └── AndroidManifest.xml
├── gradle/
├── build.gradle.kts
├── settings.gradle.kts           # include(":app", ":core")
├── AGENTS.md                     # Contratos para agentes; criar na Parte 1
└── README.md
```

**Estrutura apenas planejada:** não criar código ou pastas vazias antecipadamente. O conteúdo acima não representa implementação concluída.

## 5. Desenvolvimento assistido por LLM

- Dividir solicitações ao agente **por etapa**, sem pedir toda a aplicação de uma vez.
- Manter a lógica A*/mapeamento/projeção no módulo `:core`, sem API Android, para permitir testes rápidos (`./gradlew :core:test`).
- Exigir que cada mudança compile e execute testes relevantes antes de seguir.
- Criar `AGENTS.md` já na Parte 1, com responsabilidades, convenções e critérios de aceite.
- Usar gravações ARCore (Parte 4) para reproduzir cenários sem depender de testes manuais repetidos.
- Consultar a documentação **da versão instalada** de SceneView; ele oferece `llms.txt` e integração MCP, mas exemplos da internet podem não corresponder à versão fixada.
- Preferir Gradle CLI, VS Code/Cursor e `adb` para iteração; abrir o Android Studio apenas se necessário para configurar SDK, depurar ou inspecionar o aplicativo.

## 6. Limitações

- **Profundidade é estimativa**, não medição perfeita. Superfícies reflexivas, pequenas, transparentes, sem textura ou mal iluminadas podem causar erros.
- Plano detectado não implica reconhecimento semântico de “mesa”, “caixa” ou “cadeira”.
- A área de navegação inicial é pequena, estática e explicitamente escaneada.
- Se a profundidade falhar, mostrar aviso e oferecer o modo de obstáculo manual; não afirmar que os obstáculos físicos foram reconhecidos.
- O rover é **simulado** e não executa movimentação física.
- A aplicação é RA móvel com interação entre geometria física e virtual, no espectro de realidade misturada.

## 7. Referências

- [Google — ARCore Android SDK](https://developers.google.com/ar/develop/java/quickstart)
- [Google — ARCore Depth API](https://developers.google.com/ar/develop/depth)
- [Google — Depth API para Android](https://developers.google.com/ar/develop/java/depth/overview)
- [Google — Raw Depth no Android](https://developers.google.com/ar/develop/java/depth/raw-depth)
- [Google — Dispositivos compatíveis](https://developers.google.com/ar/devices)
- [Google — ARCore Recording & Playback (Android)](https://developers.google.com/ar/develop/java/recording-and-playback/introduction)
- [SceneView — Android AR, exemplos e `llms.txt`](https://github.com/sceneview/sceneview)
- [Jetpack Compose](https://developer.android.com/compose)
- [Android — Testes locais](https://developer.android.com/training/testing/local-tests)

> **Próxima ação:** executar **apenas a Parte 1**, validar sessão AR no celular e fixar dependências que compilam. Não implementar todas as partes ao mesmo tempo.
