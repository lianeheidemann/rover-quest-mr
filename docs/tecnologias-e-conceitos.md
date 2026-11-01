# Tecnologias e conceitos

Resumo das tecnologias do RoverQuest MR e de como elas permitem posicionar o rover e planejar rotas no ambiente real. Versões em [`game/gradle/libs.versions.toml`](../game/gradle/libs.versions.toml).

## Tecnologias

| Tecnologia | Papel no projeto |
| --- | --- |
| **Kotlin** | Linguagem usada no aplicativo Android e nas regras do jogo. |
| **Gradle** | Organiza a compilação em dois módulos: `:app` (Android) e `:core` (lógica independente). |
| **Jetpack Compose** | Cria os botões, painéis e avisos exibidos sobre a imagem da câmera. |
| **ARCore** | Estima onde o celular está, reconhece o piso e mantém objetos virtuais posicionados no ambiente. |
| **ARCore Depth API** | Estima a distância entre a câmera e as superfícies. O **Raw Depth** oferece dados menos processados e um mapa de confiança. |
| **SceneView (ARSceneView)** | Conecta a sessão ARCore à cena 3D, facilitando a exibição do rover, destino, obstáculos e rota. |
| **Filament** | Motor gráfico usado pelo SceneView para renderizar modelos, materiais e iluminação 3D. |
| **JUnit** | Testa as regras do `:core` sem precisar abrir a câmera ou executar o aplicativo. |
| **GitHub Actions** | Automatiza testes, compilação do APK e publicação de releases. |

## Conceitos

- **Realidade aumentada (RA):** sobrepõe objetos virtuais à imagem da câmera, alinhando-os ao ambiente físico.
- **Realidade misturada (MR):** vai além da sobreposição: o conteúdo virtual considera elementos do espaço real. No RoverQuest, a integração de obstáculos reais ao cálculo da rota é a funcionalidade de MR prevista.
- **Rastreamento (tracking):** o ARCore combina imagens da câmera e sensores para estimar posição e orientação do celular. Isso faz o rover parecer parado no chão mesmo quando a câmera se move.
- **Plano detectado:** região reconhecida pelo ARCore como uma superfície plana. O jogo usa planos horizontais voltados para cima para localizar o piso; detectar um plano não significa que ele esteja livre de obstáculos.
- **Âncora:** referência espacial acompanhada pelo ARCore. O rover cria uma âncora no piso; destino, rota e obstáculos usam coordenadas relativas a ela para permanecer alinhados ao mesmo local.
- **Hit test:** testa onde um raio projetado da tela encontra uma superfície detectada, permitindo transformar um toque em uma posição no piso.
- **Profundidade:** distância estimada da câmera até pontos da cena. Pode ser imprecisa em vidro, reflexos, pouca luz ou superfícies sem textura.
- **Projeção da profundidade:** usa a distância de cada pixel e os parâmetros da câmera (modelo *pinhole*) para obter pontos 3D. Convertidos para as coordenadas da âncora, esses pontos permitem estimar a altura dos objetos acima do piso.
- **Grade de ocupação:** divide uma região do piso em células de **5 cm**, classificadas como **livres**, **ocupadas** ou **desconhecidas**. Áreas ainda não observadas não são tratadas como livres.
- **Histerese:** exige leituras consistentes antes de mudar o estado de uma célula, evitando que o mapa oscile por erros de medição.
- **Inflação de obstáculos:** aumenta virtualmente o espaço ocupado por obstáculos de acordo com o raio do rover. Assim, a rota calculada para seu centro considera a largura do robô.
- **A\* (A-star):** busca um caminho de menor custo na grade. Considera até oito direções por célula, sem permitir atravessar diagonalmente as quinas de obstáculos.
- **Obstáculo manual (virtual):** objeto inserido pelo jogador que bloqueia células e força o recálculo da rota; também permite testar o jogo sem dados de profundidade.
- **Gravação e repetição de sessão:** recurso do ARCore que registra vídeo e dados da sessão em MP4 para reproduzir o rastreamento e testar o aplicativo sem refazer o percurso físico.

## Arquitetura em uma frase

O `:app` cuida da câmera, do ARCore e da interface; o `:core` recebe coordenadas e informações do ambiente para decidir posicionamento, mapa e rota, **sem depender do Android**. Veja [`game/README.md`](../game/README.md).
