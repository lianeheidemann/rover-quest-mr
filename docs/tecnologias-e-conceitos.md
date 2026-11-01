# Tecnologias e conceitos do RoverQuest MR

O **RoverQuest MR** é um jogo Android em que um rover virtual é colocado no piso, recebe um destino e precisa encontrar uma rota. A ideia de **realidade misturada** é fazer essa rota considerar também o ambiente físico, como uma caixa real no caminho — não apenas objetos desenhados na tela.

Para entender o projeto, vale seguir o processo: **localizar o celular, descobrir onde o jogador tocou, fixar o rover no espaço, interpretar obstáculos e calcular a rota**.

## 1. Conceitos fundamentais de realidade misturada

### Rastreamento espacial (*tracking*) e SLAM

Para manter um objeto virtual no mesmo lugar enquanto você anda pela sala, o aplicativo precisa estimar **onde o celular está e para onde aponta**. O ARCore combina imagens da câmera e sensores de movimento para atualizar essa estimativa continuamente. Esse tipo de localização e mapeamento do ambiente está relacionado ao **SLAM** (*Simultaneous Localization and Mapping*).

A posição e a orientação de um objeto formam sua **pose**. Em RA, o rastreamento trabalha com seis graus de liberdade (**6DoF**): três para a posição (x, y, z) e três para a orientação. Se o rastreamento for perdido, o posicionamento dos elementos virtuais deixa de ser confiável.

### Detecção de planos (*plane detection*)

O ARCore procura regiões que correspondem a superfícies aproximadamente planas, como pisos, mesas e paredes. O RoverQuest utiliza **planos horizontais voltados para cima** para escolher onde colocar o rover.

Detectar um plano **não é reconhecer semanticamente o chão** nem garantir que sua superfície está livre: uma caixa pode estar apoiada sobre o plano detectado.

### Hit test (*raycast*): como um toque encontra o piso

O toque acontece em uma **coordenada 2D da tela**, mas o rover precisa de uma **posição 3D no ambiente**. O **[hit test](https://developers.google.com/ar/develop/hit-test)** resolve essa diferença: o ARCore projeta um raio a partir da câmera na direção correspondente ao ponto tocado e procura interseções com a geometria que conhece.

O resultado de uma interseção (*hit result*) inclui a **pose do ponto atingido**. No RoverQuest, interessa o resultado sobre o **plano do piso**. É assim que um toque pode indicar onde colocar o rover ou seu destino, em vez de simplesmente desenhar um objeto na posição da tela.

O ARCore também pode oferecer resultados em **pontos característicos da imagem** ou em **pontos de profundidade**, quando disponíveis. São formas diferentes de encontrar uma posição espacial; **hit test não é detecção de colisão nem identificação de obstáculos**.

Um **retículo (*reticle*)** é a marca visual que indica ao jogador o local estimado para posicionamento antes de confirmar a ação.

### Âncoras, coordenadas e orientação

A posição encontrada pelo hit test é expressa no **espaço 3D estimado pelo ARCore** (*world space*). Uma **[âncora (*anchor*)](https://developers.google.com/ar/develop/anchors)** permite manter conteúdo virtual associado a essa referência, mesmo quando o ARCore ajusta sua estimativa do ambiente.

No jogo, a âncora é criada ao colocar o rover no piso. Destino, rota e obstáculos virtuais usam **coordenadas relativas à mesma âncora**: por exemplo, "50 cm à frente do rover" continua sendo a mesma posição no sistema local, mesmo quando o celular muda de lugar. A âncora reduz deslocamentos aparentes, mas não elimina erros de rastreamento.

**Em sequência:** o ARCore detecta o plano → o retículo indica uma posição possível → o jogador toca → o hit test encontra o ponto 3D → o aplicativo estabelece a âncora → os elementos são desenhados em relação a ela.

## 2. Como um objeto real vira um obstáculo no mapa?

Detectar o piso não basta para reconhecer uma caixa sobre ele. Para isso, o projeto utiliza a **ARCore Depth API**, que estima a distância da câmera até regiões da imagem. O modo **Raw Depth** disponibiliza dados menos processados e um mapa de confiança, útil para avaliar a qualidade das medições.

Cada pixel com profundidade válida pode ser convertido em um **ponto 3D** usando os parâmetros internos da câmera (modelo *pinhole*). Depois, o ponto é transformado para as coordenadas da âncora do piso. Assim, é possível estimar **a altura do ponto em relação ao chão**, em vez de considerar somente sua distância até o celular.

Na lógica implementada, pontos próximos ao nível do piso (±3 cm) indicam espaço livre; pontos entre **5 e 40 cm acima dele** podem indicar obstáculos. O ambiente é representado por uma **grade de ocupação** de 5 × 5 metros, dividida em células de 5 cm. Cada célula pode estar **livre, ocupada ou desconhecida**.

Uma única leitura pode estar errada. Por isso, o mapa reúne evidências e exige **três votos líquidos** antes de mudar o estado de uma célula. Essa estabilidade, chamada de **histerese**, evita que obstáculos apareçam e desapareçam a cada quadro. Áreas que a câmera ainda não observou continuam desconhecidas; não são automaticamente consideradas seguras.

**Limitação:** vidro, reflexos, pouca textura e iluminação inadequada podem distorcer a profundidade. O aplicativo não identifica semanticamente uma “cadeira” ou uma “caixa”: ele estima quais regiões do espaço estão ocupadas.

## 3. Como o rover encontra uma rota?

Depois de construir a grade, o aplicativo precisa decidir por quais células o rover pode passar. Primeiro, aplica a **inflação de obstáculos**: amplia as áreas bloqueadas conforme o raio do rover. Isso é necessário porque uma rota segura para um ponto não seria necessariamente segura para um robô com largura.

Em seguida, o algoritmo **A\* (*A-star*)** procura uma rota de menor custo entre o início e o destino. Ele considera movimentos em oito direções, mas impede diagonais que atravessariam quinas de obstáculos. O resultado é uma sequência de posições, posteriormente desenhada sobre o piso.

Com o mapa de profundidade ativo, a navegação considera apenas células observadas como livres e pode pedir que o jogador aponte a câmera para uma região ainda desconhecida. Também existe um **obstáculo manual**, inserido pelo jogador, para testar desvios mesmo sem profundidade. Nesse modo de teste, o caminho pode atravessar células ainda desconhecidas; isso **não equivale a confirmar que o chão real está desobstruído**.

## 4. Quando isso se torna realidade misturada?

**Realidade aumentada (RA)** é a sobreposição de conteúdo virtual à visão do mundo físico. **Realidade misturada (MR)** destaca a integração espacial e a interação entre esses dois mundos. Os termos podem se sobrepor; não são tecnologias completamente separadas.

No RoverQuest, o **hit test** permite que uma ação na tela tenha significado no espaço real, e a **âncora** mantém o rover no lugar escolhido. Já a **profundidade** permite estimar se há um objeto real ocupando parte do caminho. O objetivo é que essa informação altere a rota calculada pelo **A\***. É essa resposta do elemento virtual ao ambiente físico que caracteriza a proposta de MR do jogo.

Dois conceitos relacionados ajudam a não confundir as funções:
- **Oclusão:** um objeto físico aparece à frente de um objeto virtual, escondendo a parte que estaria atrás dele. Exige informações de profundidade e tratamento visual; **não é a mesma coisa que evitar obstáculos**.
- **Colisão ou interação física:** um elemento virtual reage à presença de uma superfície ou obstáculo real. No RoverQuest, a interação pretendida é o **desvio por planejamento de rota**, não uma simulação completa de física.

O rover não é um robô físico. A **detecção de obstáculos reais** já possui código implementado, mas ainda depende de validação no aparelho; seu **movimento automático** ao longo da rota é uma etapa futura.

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
