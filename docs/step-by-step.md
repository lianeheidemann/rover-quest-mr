# RoverQuest — Guia de implementação passo a passo

> **Status:** Partes 1 a 4 validadas no S23 Ultra; Partes 5 e 6 implementadas, com problemas vistos no aparelho (ver pendências nas partes).  
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

### Parte 1 — Aplicativo mínimo ✅

- [x] Projeto Kotlin/Compose com dois módulos: `:app` (Android) e `:core` (Kotlin puro).
- [x] Sessão AR com câmera funcionando no S23 Ultra.
- [x] Verificar no aparelho se a profundidade do ARCore é suportada e registrar o resultado.
  - **Resultado (S23 Ultra):** profundidade suportada, **com Raw Depth**; rastreamento ativo e piso detectado.
- [x] Mensagens claras para permissão negada e AR indisponível (implementadas; AR indisponível não é testável no S23 Ultra).
- [x] Comandos de build/teste no `AGENTS.md` e CI que compila e roda os testes: em todo PR para a `main` e após cada merge nela, com cache do Gradle.

**Concluído quando:** o APK instala via `adb`, abre a sessão AR no aparelho e o suporte a profundidade está documentado.

### Parte 2 — Piso, rover e destino ✅

- [x] Detectar o piso e indicar onde é possível posicionar.
- [x] Tocar para posicionar o rover (forma simples) e o destino.
- [x] Manter ambos fixos ao piso enquanto o celular se move.
- [x] Indicar quando o rastreamento é perdido.
  - **Validado no S23 Ultra:** piso detectado, rover e destino posicionados por toque e mantidos no
    lugar ao andar ao redor e ver de outros ângulos. O rover cria a âncora do piso e o destino fica
    relativo a ela; regras de posicionamento e avisos em `:core` (`core/floor/FloorSetup.kt`).

**Concluído quando:** rover e destino permanecem alinhados ao piso ao movimentar o celular.

### Parte 3 — Rota com A* ✅

- [x] Grade local com células livres, ocupadas e desconhecidas.
- [x] Obstáculo manual para testes.
- [x] A* no módulo `:core`, tratando ausência de rota.
- [x] Exibir a rota sobre o piso.
- [x] Testes JUnit da lógica.
  - **Validado no S23 Ultra:** a rota em pontos contorna o obstáculo virtual e continua presa ao piso
    vista de vários ângulos. Grade de 5 cm em 5 × 5 m centrada no rover
    (`core/nav/`); A* com 8 vizinhos, sem cortar quinas. O obstáculo manual é uma caixa virtual de
    30 cm, ampliada pelo raio do rover na grade. Sem profundidade ainda, as células desconhecidas são
    atravessadas como livres; só o obstáculo manual bloqueia.

**Concluído quando:** a rota contorna o obstáculo manual e os testes rodam sem câmera/ARCore.

### Parte 4 — Ler profundidade ✅

- [x] Ler e visualizar a profundidade em um modo de diagnóstico.
- [x] Avisar quando a leitura estiver indisponível ou incerta.
- [x] Gravar uma sessão do ARCore para repetir testes sem reposicionar o celular.
  - **Validado no S23 Ultra:** leituras confiáveis do piso a 0,68 m, 0,92 m e 1,26 m (confiança
    82–94%, cobertura 100%), cores na orientação certa e leitura incerta sinalizada (2,09 m com
    confiança 0%). Limitação vista: o reflexo de luz no piso brilhante é lido como "longe". A gravação
    e a repetição de sessão **ainda não foram testadas no aparelho**.
    Botão "Profundidade": liga a
    Depth API (automática), sobrepõe a profundidade em cores (vermelho = perto, azul = longe) e mostra a
    distância no centro da tela com a confiança do Raw Depth. A leitura é marcada como incerta com pouca
    cobertura, sem dado no centro, confiança baixa ou fora de 0,3–5 m (`core/depth/`). "Gravar sessão"
    salva um MP4 do ARCore em pasta privada do app; "Repetir gravação" reabre a sessão a partir dele.

**Concluído quando:** o app mostra a profundidade estimada de piso, caixa e parede.

### Parte 5 — Mapa de obstáculos

- [ ] Converter profundidade em células ocupadas e livres, de forma estável.
- [x] Considerar o tamanho do rover ao marcar obstáculos.
- [x] Célula não observada continua desconhecida, nunca livre.
- [x] Sobreposição visual da grade para depuração.
- [x] Obstáculo manual continua disponível.
  - **Testado no S23 Ultra: falhou.** A profundidade vira pontos no frame da âncora
    do piso (`core/depth/DepthProjection.kt`). Pontos entre 5 e 40 cm de altura votam "obstáculo";
    pontos no piso (±3 cm) votam "livre", até 2,5 m da câmera; reflexos abaixo do piso e o que fica acima de 40 cm (tampo de
    mesa) não votam. Uma célula só muda de estado após 3 votos líquidos, com histerese
    (`core/nav/ObstacleMap.kt`). Obstáculos reais são ampliados pelo raio do rover; o A* só atravessa
    piso visto como livre e, se faltar ver parte do caminho, pede para apontar a câmera. Chip "Mapa":
    verde = livre, vermelho = obstáculo visto, laranja = folga do rover ou obstáculo virtual. Sem
    suporte a profundidade, só o obstáculo virtual bloqueia, com aviso.
  - **Corrigido, aguardando validação no S23 Ultra:** longe do rover o chão era marcado como
    obstáculo, porque a altura era medida em relação à âncora, e alguns centímetros de diferença
    (âncora fora do chão real, erro da profundidade com a distância) bastavam. Agora, a cada leitura,
    o jogo ajusta um plano ao chão que a profundidade vê (mediana + mínimos quadrados, ignorando
    caixas e móveis) e mede as alturas a partir dele (`core/depth/FloorLevel.kt`). Planos muito
    inclinados ou com poucos pontos são ignorados, mantendo o anterior.

**Concluído quando:** uma caixa real aparece de forma estável como bloqueada e o A* calcula o desvio.

### Parte 6 — Navegação do rover

- [ ] Rover segue a rota com movimento suave.
- [ ] Para quando o rastreamento ou o mapa ficam pouco confiáveis.
- [ ] Replaneja quando o mapa muda de forma estável.
  - **Implementado, não validado:** o rover ainda não completou um trajeto contornando uma caixa
    real no S23 Ultra, porque depende da Parte 5. Botão "Ir" inicia o trajeto; o rover anda a
    15 cm/s, gira no lugar quando precisa virar mais de 20° e passa exatamente pelos cantos da rota
    (`core/nav/RoverMotion.kt`). Pausa se a câmera ou a âncora perdem o rastreamento, ou se não há
    rota por piso visto como livre, e retoma sozinho. Quando o mapa (com histerese) ou o destino/obstáculo
    mudam, a rota é recalculada a partir de onde o rover está. ■ para; ao chegar, marca-se outro destino.
    O rover para 18 cm antes do centro do destino (bandeira 3D), sem subir no marcador, e faz uma dancinha de
    vitória: balança, gira uma volta e dá dois pulinhos (`core/nav/RoverDance.kt`, não testado no
    S23 Ultra).
  - **Extra (pedido da autora), não testado no S23 Ultra:** ao colocar o rover pela primeira vez na
    partida, ele dá meia-volta, vem até perto do celular (para 40 cm antes, no máximo 1,2 m), balança
    como quem cumprimenta, dá outra meia-volta e volta ao ponto inicial (`core/nav/RoverGreeting.kt`).
    É só animação: não desvia de obstáculos e os toques esperam ela terminar.

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

Ver [references.md](references.md).

> **Próxima ação:** corrigir a altura do chão no mapa (Parte 5) e validar as Partes 5 e 6 no aparelho. Não implementar todas as partes ao mesmo tempo.
