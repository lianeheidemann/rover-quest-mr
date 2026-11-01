# RoverQuest MR — código do jogo

Projeto Android (Kotlin + Gradle) do RoverQuest MR. Visão geral do jogo no [README principal](../README.md).

## Requisitos

- JDK 17 ou superior
- Android SDK com a plataforma definida em `compileSdk` (`app/build.gradle.kts`), com `ANDROID_HOME` configurado ou `local.properties` apontando para o SDK
- Celular Android compatível com ARCore (testado no Galaxy S23 Ultra); o emulador não é suportado

## Estrutura

```
game/
├── settings.gradle.kts        inclui :app e :core
├── gradle/libs.versions.toml  versões fixas de todas as dependências
├── app/   (:app)  Android — câmera, AR, interface
└── core/  (:core) Kotlin puro — regras do jogo + testes JUnit
```

| Módulo | Conteúdo |
| --- | --- |
| `app/` | Aplicativo Android: sessão AR (ARCore + SceneView), interface em Jetpack Compose |
| `core/` | Lógica do jogo em Kotlin puro, sem dependências Android, com testes JUnit |

As versões das dependências ficam em [`gradle/libs.versions.toml`](gradle/libs.versions.toml).

A dependência é em um sentido só: `:app` usa `:core`; `:core` nunca conhece Android nem ARCore.

```mermaid
---
config:
  flowchart:
    curve: basis
    nodeSpacing: 40
    rankSpacing: 50
    padding: 16
---
flowchart LR
    subgraph app [":app · Android"]
        direction TB
        Main("MainActivity") --> App("RoverQuestApp") --> Ar("ArScreen")
    end
    subgraph core [":core · Kotlin puro"]
        direction TB
        startup("startup")
        floor("floor")
        nav("nav")
    end
    ARCore[("ARCore + SceneView")]

    App --> startup
    Ar --> floor
    Ar --> nav
    nav --> floor
    Ar -.-> ARCore

    class Main,App,Ar android
    class startup,floor,nav kotlin
    class ARCore external

    %% Translucent fills and no fixed text color: GitHub picks the light or dark
    %% Mermaid theme from the viewer's setting, and these colors work on both.
    classDef android fill:#3DDC8433,stroke:#3DDC84,stroke-width:2px
    classDef kotlin fill:#7F52FF33,stroke:#7F52FF,stroke-width:2px
    classDef external fill:#94A3B81F,stroke:#94A3B8,stroke-width:1px,stroke-dasharray:4 3
    classDef start fill:#F5A62340,stroke:#F5A623,stroke-width:2px
    style app fill:#3DDC8414,stroke:#3DDC84,stroke-width:1px
    style core fill:#7F52FF14,stroke:#7F52FF,stroke-width:1px
```

## Arquitetura

### `:core` — regras do jogo

Pacote `io.github.lianeheidemann.roverquest.core`, organizado por domínio:

| Pacote | Arquivo | Responsabilidade |
| --- | --- | --- |
| `startup` | `StartupState.kt` | Decide o que mostrar antes da AR (verificando, AR não suportada, câmera negada, falha da sessão, pronto) |
| | `DepthSupport.kt` | Classifica o suporte à profundidade (nenhum, automática, automática + Raw Depth) |
| `floor` | `FloorSetup.kt` | Estado no piso: rover, destino e obstáculo manual, com validações (mesmo piso, distância, sobreposição) e o aviso ao jogador (`setupHint`) |
| `nav` | `OccupancyGrid.kt` | Grade de células de 5 cm em 5 × 5 m centrada no rover: livre, ocupada ou desconhecida |
| | `AStar.kt` | A* com 8 vizinhos, sem cortar quinas, com o motivo quando não há rota |
| | `RoutePlanner.kt` | Monta a grade a partir do `FloorSetup`, roda o A*, simplifica a rota e gera os pontos para desenhar |

Estilo funcional: recebe dados prontos (coordenadas, estados) e devolve objetos imutáveis
(`FloorSetup`, `RoutePlan`). Tudo é testável sem câmera, com os testes em `core/src/test/`.

### `:app` — Android

| Arquivo | Responsabilidade |
| --- | --- |
| `MainActivity.kt` | Ponto de entrada; só abre a interface |
| `ui/RoverQuestApp.kt` | Pede a permissão da câmera, verifica o ARCore e escolhe a tela via `resolveStartupState` (`:core`) |
| `ui/ArScreen.kt` | Sessão AR (SceneView): converte o toque no piso para coordenadas da âncora, chama o `FloorSetup` e desenha rover, destino, obstáculo e rota como filhos da âncora; painéis de status e instruções |
| `assets/models/` | Modelos 3D do rover. O jogo usa `rover-mini-lite.glb` (58 mil vértices, texturas de 1024 px), gerado a partir de `rover-mini.glb`; `rover.glb` e `rover-mini.glb` são os originais, guardados para a futura troca de rover |
| `res/` | Ícone e textos |

### Fluxo de um toque

```mermaid
---
config:
  flowchart:
    curve: basis
    nodeSpacing: 40
    rankSpacing: 50
    padding: 16
---
flowchart TD
    T(["👆 Toque na tela"])
    subgraph app [":app · Android"]
        R("ReticleNode<br/>acerto no piso · ARCore")
        C("ArScreen<br/>coordenadas da âncora do piso")
        D("ArScreen<br/>desenha a rota e o aviso")
    end
    subgraph core [":core · Kotlin puro"]
        F("FloorSetup<br/>placeDestination · placeObstacle")
        P("planRoute")
        G("OccupancyGrid")
        A("findPath · A*")
    end

    T --> R --> C --> F --> P --> G --> A --> D

    class T start
    class R,C,D android
    class F,P,G,A kotlin

    %% Translucent fills and no fixed text color: GitHub picks the light or dark
    %% Mermaid theme from the viewer's setting, and these colors work on both.
    classDef android fill:#3DDC8433,stroke:#3DDC84,stroke-width:2px
    classDef kotlin fill:#7F52FF33,stroke:#7F52FF,stroke-width:2px
    classDef external fill:#94A3B81F,stroke:#94A3B8,stroke-width:1px,stroke-dasharray:4 3
    classDef start fill:#F5A62340,stroke:#F5A623,stroke-width:2px
    style app fill:#3DDC8414,stroke:#3DDC84,stroke-width:1px
    style core fill:#7F52FF14,stroke:#7F52FF,stroke-width:1px
```

### Princípios

- Posições do jogo são relativas à **âncora do piso**, criada onde o rover é colocado; por isso tudo fica preso ao chão quando o celular se move.
- A interface só exibe estado e envia ações; regras ficam no `:core`.
- Profundidade: a leitura entra no `:app` (Parte 4) e a conversão em células da grade no `:core` (Parte 5), conforme o [plano](../docs/step-by-step.md).

### Modelo 3D otimizado

Modelos gerados por IA costumam ser pesados demais para rodar junto com a câmera e a profundidade.
O `rover-mini-lite.glb` foi gerado assim (fora do projeto, sem dependência no build):

```bash
npx gltfpack@1.3.0 -i rover-mini.glb -o rover-mini-lite.glb -si 0.05 -cc -noq
```

Depois, as texturas foram reduzidas de 4096 para 1024 px. `-noq` mantém as posições em float: com
posições quantizadas, o SceneView calcula a escala do modelo de forma errada.

## Comandos

Execute dentro desta pasta:

```bash
./gradlew :core:test           # testes da lógica
./gradlew :app:assembleDebug   # gera o APK de debug
./gradlew :app:installDebug    # instala no celular conectado via adb
adb logcat -s RoverQuest       # logs do app
```

O APK gerado fica em `app/build/outputs/apk/debug/app-debug.apk`.

APKs prontos para instalar ficam em [Releases](https://github.com/lianeheidemann/rover-quest-mr/releases), publicados pela CI a cada merge na `main`.

Para testar outra branch (ex.: `develop`) sem merge: **Actions → APK de teste → Run workflow**, escolha a branch. O APK aparece em Releases como `<branch> (teste)`, substituído a cada build.

APK de teste mais recente da `develop` (link fixo): [RoverQuestMR-develop.apk](https://github.com/lianeheidemann/rover-quest-mr/releases/download/test-develop/RoverQuestMR-develop.apk). Para outra branch, troque `develop` pelo nome dela (com `/` trocado por `-`) nas duas partes do link.
