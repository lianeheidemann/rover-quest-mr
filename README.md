<p align="center">
  <img src="assets/robot/roverquest-app-icon.png" alt="Ícone do app RoverQuest MR" width="15%">
</p>

<h1 align="center">RoverQuest MR</h1>

<p align="center">
  <img src="assets/badge/badge_em-construcao-azul-v1.svg">
  <a href="https://github.com/lianeheidemann/rover-quest-mr/releases/latest"><img src="assets/badge/badge_download-apk-demo-v1.svg" alt="Download APK da versão demo"></a>
  <a href="https://github.com/lianeheidemann/rover-quest-mr/actions/workflows/ci.yml"><img src="https://raw.githubusercontent.com/lianeheidemann/rover-quest-mr/badges/ci.svg" alt="CI"></a>
  <br>
  <br><img width="100%" alt="Faixa de aviso" src="assets/fun/caution-tape/caution-tape-larga-v4.svg" />
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Kotlin-2.4.21-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin 2.4.21">
  <img src="https://img.shields.io/badge/Android-minSdk%2024%20%7C%20compileSdk%2037.2-3DDC84?logo=android&logoColor=white" alt="Android minSdk 24, compileSdk 37.2">
  <img src="https://img.shields.io/badge/ARCore-1.57.0-4285F4?logo=google&logoColor=white" alt="ARCore 1.57.0">
  <img src="https://img.shields.io/badge/SceneView-4.64.0-00897B" alt="SceneView 4.64.0"><br>
  <img src="https://img.shields.io/badge/Jetpack%20Compose-BOM%202026.09.00-4285F4?logo=jetpackcompose&logoColor=white" alt="Jetpack Compose BOM 2026.09.00">
  <img src="https://img.shields.io/badge/Gradle-9.8.2-02303A?logo=gradle&logoColor=white" alt="Gradle 9.8.2">
  <img src="https://img.shields.io/badge/AGP-9.5.0-3DDC84?logo=android&logoColor=white" alt="Android Gradle Plugin 9.5.0">
</p>

<p align="center">
  Jogo de realidade mista para Android em que você guia um pequeno rover virtual pela sua casa: ele enxerga os objetos reais ao redor e encontra sozinho o caminho para desviar deles.
</p>

## O jogo

Sua casa vira a fase. Caixas, livros e almofadas de verdade se tornam obstáculos para um pequeno rover virtual que enxerga o ambiente e encontra o próprio caminho.

| Modo | Como jogar | O que tem de especial |
| --- | --- | --- |
| 🧱 **Monte o labirinto** | Espalhe objetos reais para deixar o caminho do rover o mais longo possível, sem bloqueá-lo totalmente. Quanto maior a rota, mais pontos. | Você é o criador da fase, e cada partida é diferente. |
| 🔋 **Coleta** | Baterias e cristais aparecem pelo chão. Escolha a ordem de coleta e vença o relógio. | Estratégia rápida: o rover obedece, você decide. |
| 👀 **Esconde-esconde** | Itens ficam escondidos atrás dos seus móveis e só aparecem quando você dá a volta neles. | Os objetos virtuais se escondem atrás dos reais, como se estivessem mesmo ali. |

**O rover tem personalidade:** fica curioso ao explorar, se assusta com obstáculos e comemora quando chega.

> 🚧 Em desenvolvimento: os modos acima são a proposta do jogo e ainda não estão implementados.

## Versão beta

Versão **0.1.0**, em testes. Gravação feita no Galaxy S23 Ultra.

<p align="left">
  <img src="assets/interface/interface-v1.webp" alt="Versão beta do RoverQuest MR: piso detectado, rover e destino posicionados e rota desviando de um obstáculo virtual" width="40%">
</p>

| Recurso | Situação |
| --- | --- |
| Detecção do piso | ✅ Disponível |
| Posicionamento do rover e do destino por toque | ✅ Disponível |
| Cálculo de rota (A*) exibido sobre o piso | ✅ Disponível |
| Obstáculo virtual, com recálculo da rota | ✅ Disponível |
| Diagnóstico de profundidade | 🧪 Experimental |
| Obstáculos reais detectados pela câmera | 🚧 Em desenvolvimento |
| Movimento do rover e modos de jogo | 🚧 Em desenvolvimento |

## Requisitos e compatibilidade

O RoverQuest MR é um app Android nativo (Kotlin) que depende do **ARCore**, a plataforma de realidade aumentada do Google. Por isso, o requisito decisivo não é a potência do celular, e sim o modelo estar certificado para ARCore.

| Requisito | Detalhe |
| --- | --- |
| Sistema | Android 7.0 (API 24) ou superior |
| ARCore | **Obrigatório.** O modelo precisa constar na [lista oficial de dispositivos compatíveis](https://developers.google.com/ar/devices) |
| Google Play Services for AR | Instalado e atualizado pela [Play Store](https://play.google.com/store/apps/details?id=com.google.ar.core) |
| Câmera | Câmera traseira, com permissão concedida ao app |
| Gráficos | OpenGL ES 3.0 |
| Depth API | Opcional por enquanto; usada só no diagnóstico. Será necessária para detectar obstáculos reais. Na lista oficial, aparece como *Supports Depth API* |

**Aparelhos testados**

| Aparelho | Resultado |
| --- | --- |
| Samsung Galaxy S23 Ultra | ✅ Funciona (ARCore com Depth API e Raw Depth) |

Outros modelos ainda não foram testados. Se o seu aparelho está na lista do ARCore, o app deve funcionar; se não estiver, ele exibe o aviso *"Este aparelho não é compatível com o ARCore"*.

**Como instalar**

1. Baixe o APK em [Releases](https://github.com/lianeheidemann/rover-quest-mr/releases/latest).
2. Ao abrir o arquivo, autorize a instalação de apps de fontes desconhecidas, se o Android pedir.
3. Abra o RoverQuest MR e permita o uso da câmera.

## Onde jogar

> [!IMPORTANT]
> O rover enxerga o ambiente pela câmera do celular. Escolha bem o lugar para ele não se confundir.

| ✅ Prefira | ❌ Evite |
| --- | --- |
| Ambiente bem iluminado, de dia ou com luz acesa | Penumbra, contraluz ou sol forte direto na câmera |
| Piso com alguma textura (madeira, tapete, piso estampado) | Piso liso de cor única, brilhante ou espelhado |
| Área livre de uns 2 × 2 m, com espaço para você andar ao redor | Corredores apertados ou cantos onde não dá para circular |
| Obstáculos grandes e opacos: caixas, livros empilhados, almofadas | Vidro, espelhos, objetos finos (pés de cadeira, cabos) ou muito pequenos |
| Cena parada enquanto joga | Pessoas e animais passando na frente da câmera |

**Dicas:** antes de posicionar o rover, mova o celular devagar apontando para o piso até aparecerem os pontos brancos; mantenha o celular a cerca de 0,5–3 m dos obstáculos; se aparecer o aviso de rastreamento perdido, volte a apontar para o piso, devagar.
