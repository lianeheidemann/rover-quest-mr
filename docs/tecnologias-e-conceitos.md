# Tecnologias e conceitos

Resumo rápido do que o RoverQuest MR usa e por quê. Versões em [`game/gradle/libs.versions.toml`](../game/gradle/libs.versions.toml).

## Tecnologias

| Tecnologia | Para que serve no projeto |
| --- | --- |
| **Kotlin** | Linguagem de todo o código, do app e da lógica. |
| **Gradle** | Build em dois módulos: `:app` (Android) e `:core` (Kotlin puro). |
| **Jetpack Compose** | Interface: painéis, botões e avisos sobre a câmera. |
| **ARCore** | Rastreia o celular no espaço, detecta o piso, cria âncoras e fornece a profundidade. |
| **ARCore Depth API** | Estima a distância de cada pixel da câmera; com **Raw Depth**, informa também a confiança. |
| **SceneView (ARSceneView)** | Integra ARCore e Compose e desenha o 3D (rover, destino, rota, mapa). |
| **Filament** | Motor de renderização 3D usado pelo SceneView. |
| **JUnit** | Testes da lógica do `:core`, sem câmera nem aparelho. |
| **GitHub Actions** | CI: testa, gera o APK e publica releases. |

## Conceitos

- **Realidade aumentada (RA):** objetos virtuais desenhados sobre a imagem real da câmera, no lugar certo do ambiente.
- **Rastreamento (tracking):** o ARCore calcula a posição e a orientação do celular a cada quadro. Sem rastreamento, nada fica no lugar.
- **Plano:** superfície plana detectada pelo ARCore, aqui só o piso (horizontal, voltado para cima).
- **Âncora:** ponto fixo no mundo real que o ARCore acompanha. O rover cria a **âncora do piso**, e tudo (destino, rota, obstáculos, mapa) é guardado **relativo a ela**, por isso fica preso ao chão.
- **Hit test:** "lançar um raio" do toque ou da mira na tela até o piso, para saber onde posicionar algo.
- **Profundidade:** distância da câmera até cada ponto da imagem. É uma **estimativa**: falha em reflexos, vidro, pouca luz e superfícies lisas.
- **Projeção da profundidade:** converte cada pixel e sua distância em um ponto 3D (modelo *pinhole* com as intrínsecas da câmera) e depois no frame da âncora, obtendo a altura acima do piso.
- **Grade de ocupação:** o piso dividido em células de 5 cm, cada uma **livre**, **ocupada** ou **desconhecida**. O que não foi visto continua desconhecido, nunca livre.
- **Histerese:** uma célula só muda de estado depois de várias leituras concordantes, para o mapa não "piscar" com ruído.
- **Inflação:** obstáculos são ampliados pelo raio do rover, para que o centro dele siga a rota sem encostar em nada.
- **A\*:** algoritmo de busca do menor caminho na grade, com 8 direções e sem cortar quinas de obstáculos.
- **Obstáculo manual (virtual):** caixa colocada pelo jogador, usada para testar a rota e como alternativa quando não há profundidade.
- **Gravação e repetição de sessão:** o ARCore grava câmera e sensores em MP4 e depois reproduz a sessão sem o celular se mover.

## Arquitetura em uma frase

O `:app` lê o ARCore e desenha; o `:core` recebe dados prontos (pontos, toques, profundidade) e decide tudo (posicionamento, mapa, rota), sem depender do Android. Detalhes em [`game/README.md`](../game/README.md).
