# RoverQuest — Guia de implementação passo a passo

> **Status:** Partes 1 a 5 validadas no celular de teste; **MVP validado** (o rover contornou uma caixa real e chegou ao destino). Falta validar a pausa da Parte 6.  
> **Plataforma:** Android, com testes reais no Galaxy S23 Ultra (o "celular de teste" deste guia).  
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
- [x] Sessão AR com câmera funcionando no celular de teste.
- [x] Verificar no aparelho se a profundidade do ARCore é suportada e registrar o resultado.
  - **Resultado (celular de teste):** profundidade suportada, **com Raw Depth**; rastreamento ativo e piso detectado.
- [x] Mensagens claras para permissão negada e AR indisponível (implementadas; AR indisponível não é testável no celular de teste).
- [x] Comandos de build/teste no `AGENTS.md` e CI que compila e roda os testes: em todo PR para a `main` e após cada merge nela, com cache do Gradle.

**Concluído quando:** o APK instala via `adb`, abre a sessão AR no aparelho e o suporte a profundidade está documentado.

### Parte 2 — Piso, rover e destino ✅

- [x] Detectar o piso e indicar onde é possível posicionar.
- [x] Tocar para posicionar o rover (forma simples) e o destino.
- [x] Manter ambos fixos ao piso enquanto o celular se move.
- [x] Indicar quando o rastreamento é perdido.
  - **Validado no celular de teste:** piso detectado, rover e destino posicionados por toque e mantidos no
    lugar ao andar ao redor e ver de outros ângulos. O rover cria a âncora do piso e o destino fica
    relativo a ela; regras de posicionamento e avisos em `:core` (`core/floor/FloorSetup.kt`).

**Concluído quando:** rover e destino permanecem alinhados ao piso ao movimentar o celular.

### Parte 3 — Rota com A* ✅

- [x] Grade local com células livres, ocupadas e desconhecidas.
- [x] Obstáculo manual para testes.
- [x] A* no módulo `:core`, tratando ausência de rota.
- [x] Exibir a rota sobre o piso.
- [x] Testes JUnit da lógica.
  - **Validado no celular de teste:** a rota em pontos contorna o obstáculo virtual e continua presa ao piso
    vista de vários ângulos. Grade de 5 cm em 5 × 5 m centrada no rover
    (`core/nav/`); A* com 8 vizinhos, sem cortar quinas. O obstáculo manual é uma caixa virtual de
    30 cm, ampliada pelo raio do rover na grade. Sem profundidade ainda, as células desconhecidas são
    atravessadas como livres; só o obstáculo manual bloqueia.

**Concluído quando:** a rota contorna o obstáculo manual e os testes rodam sem câmera/ARCore.

### Parte 4 — Ler profundidade ✅

- [x] Ler e visualizar a profundidade em um modo de diagnóstico.
- [x] Avisar quando a leitura estiver indisponível ou incerta.
- [x] Gravar uma sessão do ARCore para repetir testes sem reposicionar o celular.
  - **Validado no celular de teste:** leituras confiáveis do piso a 0,68 m, 0,92 m e 1,26 m (confiança
    82–94%, cobertura 100%), cores na orientação certa e leitura incerta sinalizada (2,09 m com
    confiança 0%). Limitação vista: o reflexo de luz no piso brilhante é lido como "longe". A gravação
    e a repetição de sessão **ainda não foram testadas no aparelho**.
    Botão "Profundidade": liga a
    Depth API (automática), sobrepõe a profundidade em cores (vermelho = perto, azul = longe) e mostra a
    distância no centro da tela com a confiança do Raw Depth. A leitura é marcada como incerta com pouca
    cobertura, sem dado no centro, confiança baixa ou fora de 0,3–5 m (`core/depth/`). "Gravar sessão"
    salva um MP4 do ARCore em pasta privada do app; "Repetir gravação" reabre a sessão a partir dele.

**Concluído quando:** o app mostra a profundidade estimada de piso, caixa e parede.

### Parte 5 — Mapa de obstáculos ✅

- [x] Converter profundidade em células ocupadas e livres, de forma estável.
- [x] Considerar o tamanho do rover ao marcar obstáculos.
- [x] Célula não observada continua desconhecida, nunca livre.
- [x] Sobreposição visual da grade para depuração.
- [x] Obstáculo manual continua disponível.

**Como funciona**
- A profundidade vira pontos no frame da âncora do piso (`core/depth/DepthProjection.kt`), até 2,5 m da câmera.
- A cada leitura, o jogo ajusta um plano ao chão visto (mediana + mínimos quadrados, ignorando caixas e
  móveis) e mede as alturas a partir dele, não da âncora (`core/depth/FloorLevel.kt`). Planos muito
  inclinados ou com poucos pontos são ignorados.
- Pontos entre 5 e 40 cm acima do chão votam "obstáculo"; pontos no chão (±3 cm) votam "livre"; reflexos
  abaixo do chão e tampos acima de 40 cm não votam. Uma célula só muda após 3 votos líquidos, com
  histerese (`core/nav/ObstacleMap.kt`).
- Obstáculos reais são ampliados pelo raio do rover. O A* só atravessa chão visto como livre; se faltar
  ver parte do caminho, pede para apontar a câmera.
- Destino colado num móvel: a rota vai ao chão livre alcançável mais próximo, entre 22 e 40 cm da
  bandeira, em vez de recusar (`core/nav/RoutePlanner.kt`).
- Ícone "Mapa": verde = livre, vermelho = obstáculo visto, laranja = folga do rover ou obstáculo virtual,
  sem cor = não visto. Sem suporte a profundidade, só o obstáculo virtual bloqueia, com aviso.

**No celular de teste**
- ✅ Com o ajuste do chão, o piso livre fica verde também longe do rover; sofá e cadeira ficam vermelhos.
- ⚠️ Pequenos furos sem cor no meio do verde às vezes atrasam a rota ("Aponte a câmera para o caminho").
- ✅ Caixa de leite real (~7 cm de largura), sem obstáculo virtual: apareceu vermelha com folga laranja, o chão em volta
  verde, e a rota desviou dela.
- ⚠️ Num momento o chão inteiro ficou vermelho antes de se corrigir (provável reflexo do piso liso).

**Concluído quando:** uma caixa real aparece de forma estável como bloqueada e o A* calcula o desvio.

### Parte 6 — Navegação do rover

- [x] Rover segue a rota com movimento suave.
- [ ] Para quando o rastreamento ou o mapa ficam pouco confiáveis.
- [x] Replaneja quando o mapa muda de forma estável.

**Como funciona** (`core/nav/RoverMotion.kt`)
- Botão "Ir" inicia o trajeto; "Parar" interrompe. O rover anda a 15 cm/s, gira no lugar quando precisa
  virar mais de 20° e passa exatamente pelos cantos da rota.
- Pausa se a câmera ou a âncora perdem o rastreamento, ou se não há rota por chão visto como livre, e
  retoma sozinho. Quando o mapa ou o destino/obstáculo mudam, a rota é recalculada de onde o rover está.
- Para a 22 cm do centro da bandeira, na primeira vez que chega a essa distância em qualquer trecho da
  rota: nunca em cima dela. O destino precisa ficar a pelo menos 30 cm do rover.

**No celular de teste**
- ✅ O rover seguiu a rota contornando o obstáculo virtual e chegou ao destino.
- ✅ **MVP alcançado:** o rover contornou uma caixa real (detectada só pela profundidade) e chegou à bandeira.
- ✅ A rota se refez sozinha conforme o mapa mudou.
- ⏳ Falta: pausa ao perder o rastreamento.

**Concluído quando:** o rover chega ao destino contornando a caixa real. **(MVP)**

### Extras de jogo (pedidos da autora)

Fora do plano original; animações puras em `:core`, sem efeito nas regras.

| Extra | Onde | No celular de teste |
| --- | --- | --- |
| Saudação: ao ser colocado, o rover dá meia-volta, vem até perto do celular, cumprimenta e volta | `core/nav/RoverGreeting.kt` | ⏳ não testado |
| Dancinha de vitória: vira para o jogador, balança, gira e dá dois pulinhos de frente para a tela | `core/nav/RoverDance.kt` | ⏳ não testado |
| Modelos 3D do rover e da bandeira de chegada, otimizados | `app/src/main/assets/models/` | ✅ aparecem no jogo |
| Confirmar a área de jogo, lanterna, troca automática Destino → Obstáculo, ícone girando ao procurar o piso | `app/.../ui/ArScreen.kt` | ✅ confirmar área e lanterna; ⏳ demais |

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
| M1 — Base Android/AR ✅ | 1–2 | Rover e destino sobre piso real |
| M2 — Navegação ✅ | 3 | A* e obstáculo manual com testes |
| M3 — Visão espacial ✅ | 4–5 | Mapa de obstáculos estimado por profundidade |
| **M4 — MVP ✅** | **6** | **Rover desvia de caixa real** |
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

> **Próxima ação:** validar a pausa ao perder o rastreamento (Parte 6) e definir a mecânica principal da Parte 7. Não implementar todas as partes ao mesmo tempo.
