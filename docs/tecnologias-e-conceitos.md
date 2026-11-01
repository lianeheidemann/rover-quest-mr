# Tecnologias e conceitos do RoverQuest MR

O **RoverQuest MR** é um jogo Android em que um rover virtual é colocado no chão de uma sala. O jogador escolhe um destino e o aplicativo calcula uma rota. A proposta é que essa rota considere não apenas obstáculos virtuais, mas também objetos reais, como uma caixa no piso.

Para entender como isso funciona, é útil acompanhar o caminho dos dados: **a câmera identifica o espaço, o aplicativo constrói um mapa e um algoritmo procura por onde o rover pode passar**.

## 1. Como o celular localiza o rover no mundo real?

O **ARCore** combina imagens da câmera com leituras de sensores, como giroscópio e acelerômetro, para estimar continuamente a posição e a orientação do celular. Esse processo é chamado de **rastreamento espacial (*tracking*)**. É ele que permite olhar para o rover de ângulos diferentes sem que o objeto acompanhe o movimento da tela.

O ARCore também identifica **planos**, isto é, regiões que parecem superfícies planas. Neste projeto, interessam os planos horizontais voltados para cima, usados para posicionar o rover no piso. Isso **não significa que o ARCore reconheça o objeto como “chão”** ou saiba, por si só, se há algo bloqueando a passagem.

Quando o jogador toca na tela, um **hit test** verifica onde a direção correspondente ao toque encontra o plano detectado. Nesse ponto é criada uma **âncora (*anchor*)**, uma referência espacial que o ARCore continua acompanhando. Rover, destino, obstáculos virtuais e rota usam posições relativas à mesma âncora, mantendo-se alinhados ao piso. Esse alinhamento é aproximado: erros de rastreamento podem causar pequenos deslocamentos.

## 2. Como um objeto real vira um obstáculo no mapa?

Detectar o piso não basta para reconhecer uma caixa sobre ele. Para isso, o projeto utiliza a **ARCore Depth API**, que estima a distância da câmera até regiões da imagem. O modo **Raw Depth** disponibiliza dados menos processados e um mapa de confiança, útil para avaliar a qualidade das medições.

Cada pixel com profundidade válida pode ser convertido em um **ponto 3D** usando os parâmetros internos da câmera (modelo *pinhole*). Depois, o ponto é transformado para as coordenadas da âncora do piso. Assim, é possível estimar **a altura do ponto em relação ao chão**, em vez de considerar somente sua distância até o celular.

Na lógica implementada, pontos próximos ao nível do piso (±2 cm) indicam espaço livre; pontos entre **3 e 40 cm acima dele** podem indicar obstáculos. O ambiente é representado por uma **grade de ocupação** de 5 × 5 metros, dividida em células de 5 cm. Cada célula pode estar **livre, ocupada ou desconhecida**.

Uma única leitura pode estar errada. Por isso, o mapa reúne evidências e exige **três votos líquidos** antes de mudar o estado de uma célula. Essa estabilidade, chamada de **histerese**, evita que obstáculos apareçam e desapareçam a cada quadro. Áreas que a câmera ainda não observou continuam desconhecidas; não são automaticamente consideradas seguras.

**Limitação:** vidro, reflexos, pouca textura e iluminação inadequada podem distorcer a profundidade. O aplicativo não identifica semanticamente uma “cadeira” ou uma “caixa”: ele estima quais regiões do espaço estão ocupadas.

## 3. Como o rover encontra uma rota?

Depois de construir a grade, o aplicativo precisa decidir por quais células o rover pode passar. Primeiro, aplica a **inflação de obstáculos**: amplia as áreas bloqueadas conforme o raio do rover. Isso é necessário porque uma rota segura para um ponto não seria necessariamente segura para um robô com largura.

Em seguida, o algoritmo **A\* (*A-star*)** procura uma rota de menor custo entre o início e o destino. Ele considera movimentos em oito direções, mas impede diagonais que atravessariam quinas de obstáculos. O resultado é uma sequência de posições, posteriormente desenhada sobre o piso.

Com o mapa de profundidade ativo, a navegação considera apenas células observadas como livres e pode pedir que o jogador aponte a câmera para uma região ainda desconhecida. Também existe um **obstáculo manual**, inserido pelo jogador, para testar desvios mesmo sem profundidade. Nesse modo de teste, o caminho pode atravessar células ainda desconhecidas; isso **não equivale a confirmar que o chão real está desobstruído**.

## 4. Onde entra a realidade misturada?

Na **realidade aumentada (RA)**, elementos digitais são sobrepostos à visão do ambiente, como o rover desenhado sobre o chão. O objetivo de **realidade misturada (MR)** aparece quando características do espaço físico passam a afetar o comportamento desses elementos: por exemplo, uma caixa real faz o algoritmo escolher outro caminho.

Portanto, o diferencial do RoverQuest MR não é apenas mostrar um modelo 3D pela câmera, mas **usar informações do ambiente para orientar a navegação virtual**. Não há um robô físico: o rover e sua movimentação são simulados.

## 5. O papel de cada tecnologia

| Tecnologia | Como participa do funcionamento |
| --- | --- |
| **Kotlin** | Implementa o aplicativo Android e os algoritmos de posicionamento, mapeamento e navegação. |
| **Gradle** | Compila o projeto e separa o aplicativo (`:app`) da lógica independente de Android (`:core`). |
| **Jetpack Compose** | Exibe controles, instruções e informações da sessão sobre a câmera. |
| **ARCore + Depth API** | Fornecem rastreamento, planos, âncoras e estimativas de profundidade. |
| **SceneView + Filament** | Integram a sessão de RA à renderização dos objetos e trajetos 3D. |
| **JUnit** | Verifica os algoritmos do módulo `:core` sem precisar da câmera. |
| **GitHub Actions** | Automatiza testes, compilação e distribuição de APKs. |

A separação entre módulos é intencional: **`:app` recebe dados do ARCore e apresenta a cena; `:core` transforma coordenadas em decisões**, como classificar células ou calcular uma rota. Assim, os algoritmos podem ser testados isoladamente.

**Estado do projeto:** o posicionamento no piso, a rota com obstáculo virtual e a leitura de profundidade já foram validados no celular. O mapa de obstáculos reais foi implementado, mas **ainda precisa ser validado no aparelho**. O movimento automático do rover pela rota é uma etapa futura. A gravação/repetição de sessões também ainda não foi validada em dispositivo.

Para acompanhar as etapas e os detalhes de implementação, consulte o [guia passo a passo](step-by-step.md) e o [README do código](../game/README.md). As versões das bibliotecas estão em [`game/gradle/libs.versions.toml`](../game/gradle/libs.versions.toml).
