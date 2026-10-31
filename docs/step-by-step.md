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
- [ ] Configurar manifest, câmera, ARCore, verificações de compatibilidade e permissões.
- [ ] Exibir a câmera e uma sessão AR de teste no Galaxy S23 Ultra.
- [ ] Criar estados claros para recurso indisponível, permissão negada e sessão interrompida.
- [ ] Validar compilação e instalação via Gradle/adb, sem depender do editor gráfico.

**Concluído quando:** o APK abre, solicita permissões corretamente e mostra a sessão AR com rastreamento no aparelho.

### Parte 2 — Detectar piso, posicionar rover e destino

- [ ] Consultar planos horizontais rastreados pelo ARCore (`Plane`).
- [ ] Aplicar hit test com `Frame.hitTest(...)` para selecionar pontos de posicionamento.
- [ ] Exibir reticle enquanto o plano é encontrado.
- [ ] Adicionar modelo 3D do rover leve, em escala adequada (GLB/glTF).
- [ ] Fixar rover e destino a referências espaciais usando `Anchor`, gerenciando seu ciclo de vida.
- [ ] Exibir estados: procurando piso, posicionando, pronto e rastreamento perdido.

**Concluído quando:** o rover e o destino permanecem visualmente alinhados ao piso ao movimentar o celular, dentro dos limites do ARCore.

### Parte 3 — Implementar A* sem depender de AR

- [ ] Criar grade local 2D (exemplo: 2 × 2 m, células de 10–20 cm).
- [ ] Distinguir células **livres**, **ocupadas** e **desconhecidas**.
- [ ] Permitir um obstáculo retangular **manual** para testes.
- [ ] Implementar A* em Kotlin puro, com heurística consistente e regras de vizinhança definidas.
- [ ] Converter o caminho em pontos 3D visíveis sobre o piso.
- [ ] Tratar ausência de rota, início/destino bloqueado e coordenadas fora da grade.
- [ ] Escrever testes JUnit para caminho livre, desvio, bloqueio e menor caminho em cenários simples.

**Concluído quando:** o caminho contorna o obstáculo manual e os testes da lógica executam sem câmera/ARCore.

### Parte 4 — Ler e visualizar dados reais de profundidade

- [ ] Verificar compatibilidade e suporte ao `Config.DepthMode` em execução.
- [ ] Configurar `AUTOMATIC` ou `RAW_DEPTH_ONLY` conforme o experimento e suporte do aparelho.
- [ ] Acessar quadros ARCore e obter profundidade por APIs como `Frame.acquireDepthImage16Bits()` ou `Frame.acquireRawDepthImage16Bits()`, de acordo com o modo configurado.
- [ ] Exibir um mapa de profundidade colorido em um modo de diagnóstico.
- [ ] Tratar ausência de atualização, medidas inválidas e, quando disponíveis, imagens de confiança Raw Depth.
- [ ] Encerrar corretamente cada `Image` adquirida e evitar processamento pesado na thread da interface.
- [ ] Testar piso, caixa e parede em iluminação/diferentes distâncias.

**Concluído quando:** o celular mostra profundidade estimada e o aplicativo informa quando essa leitura é indisponível ou incerta.

### Parte 5 — Converter profundidade em mapa de obstáculos

- [ ] Definir referencial de mapa **ancorado ao piso** e coerente com rover e destino.
- [ ] Projetar amostras de profundidade em pontos 3D usando intrínsecos de câmera, resolução da imagem, orientação e pose correspondentes.
- [ ] Filtrar medidas inválidas/ruidosas e pontos próximos ao piso.
- [ ] Projetar pontos acima do plano do piso nas células da grade.
- [ ] Inflar células ocupadas de acordo com raio/largura do rover e margem de segurança.
- [ ] Combinar observações sucessivas com filtros conservadores, evitando oscilações.
- [ ] Conservar células não observadas como **desconhecidas**, não automaticamente livres.
- [ ] Disponibilizar sobreposição visual de grade, pontos/obstáculos e rota para depuração.
- [ ] Preservar a opção de inserir obstáculo manual.

**Concluído quando:** uma caixa física estática é representada de forma estável como região bloqueada, suficiente para o A* calcular um desvio.

**Atenção:** a Depth API não devolve automaticamente mapa de ocupação, colisores físicos nem identificação semântica de objetos. Essa conversão é uma parte principal do projeto.

### Parte 6 — Fazer o rover navegar

- [ ] Implementar deslocamento do rover ao longo da rota com velocidade limitada e orientação suave.
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
- [ ] Adicionar CI para compilar e executar testes unitários em mudanças de código.

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
rover-quest-mr-/
├── docs/
│   └── step-by-step.md
├── app/
│   └── src/
│       ├── main/
│       │   ├── java/.../roverquest/
│       │   │   ├── ar/           # Sessão, planos, poses, anchors
│       │   │   ├── depth/        # Captura e filtragem
│       │   │   ├── mapping/      # Grade de ocupação
│       │   │   ├── navigation/   # A*, rotas
│       │   │   ├── rover/        # Movimento e renderização
│       │   │   └── ui/           # Jetpack Compose
│       │   ├── assets/models/   # Modelos GLB
│       │   └── AndroidManifest.xml
│       └── test/                 # Testes JVM (A* e mapeamento)
├── gradle/
├── build.gradle.kts
├── settings.gradle.kts
├── AGENTS.md                     # Contratos para agentes; criar futuramente
└── README.md
```

**Estrutura apenas planejada:** não criar código ou pastas vazias antecipadamente. O conteúdo acima não representa implementação concluída.

## 5. Desenvolvimento assistido por LLM

- Dividir solicitações ao agente **por etapa**, sem pedir toda a aplicação de uma vez.
- Manter a lógica A*/mapeamento independente de API Android para permitir testes rápidos.
- Exigir que cada mudança compile e execute testes relevantes antes de seguir.
- Criar futuramente `AGENTS.md` com responsabilidades, convenções e critérios de aceite.
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
- [SceneView — Android AR, exemplos e `llms.txt`](https://github.com/sceneview/sceneview)
- [Jetpack Compose](https://developer.android.com/compose)
- [Android — Testes locais](https://developer.android.com/training/testing/local-tests)

> **Próxima ação:** executar **apenas a Parte 1**, validar sessão AR no celular e fixar dependências que compilam. Não implementar todas as partes ao mesmo tempo.
