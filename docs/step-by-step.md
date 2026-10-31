# RoverQuest MR — Guia de implementação passo a passo

> **Status:** planejamento, sem implementação iniciada.  
> **Plataforma inicial:** Android (Galaxy S23 Ultra).  
> **Objetivo:** criar um jogo educativo de robótica em realidade aumentada móvel com interação espacial: o robô virtual planeja um caminho em torno de obstáculos reais detectados com profundidade.  
> **MVP:** posicionar um rover no piso, escolher o destino e fazê-lo contornar **uma caixa física estática**, usando um mapa local de obstáculos estimado pela câmera.

## 1. Escopo e decisões

- **Motor:** Unity 6, C# e Universal Render Pipeline (URP).
- **XR:** AR Foundation + Google ARCore XR Plugin (versões compatíveis com a instalação do Unity).
- **Dispositivo de teste:** Galaxy S23 Ultra com Google Play Services para RA atualizado.
- **Posicionamento:** ARPlaneManager e ARRaycastManager.
- **Profundidade:** AROcclusionManager / ARCore Depth API.
- **Navegação:** grade de ocupação 2D e algoritmo A*; não é necessário treinar uma IA.
- **Movimento:** robô 3D simulado. Nenhum hardware de robótica real.
- **Interface:** Escanear, Posicionar, Definir destino, Iniciar e Reiniciar.
- **Cenário:** área pequena, piso aproximadamente plano e bem iluminado, objetos estáticos.
- **Fora do MVP:** reconstrução completa da casa, objetos em movimento, multiplayer, reconhecimento semântico de móveis e SLAM próprio.

**Princípio:** validar cada parte no celular antes de avançar. Começar com um obstáculo **manual** e só depois substituí-lo por um obstáculo **detectado**.

## 2. Implementação por partes

### Parte 1 — Configurar Unity e Android

- [ ] Criar projeto Unity 6 (3D URP).
- [ ] Instalar e configurar AR Foundation, ARCore XR Plugin e XR Plug-in Management.
- [ ] Configurar Android, ARM64, permissões e requisitos das versões de pacotes escolhidas.
- [ ] Criar cena com AR Session, XR Origin e AR Camera.
- [ ] Gerar APK e testar abertura de sessão AR no Galaxy S23 Ultra.
- [ ] Exibir mensagem quando AR estiver indisponível.

**Critério de conclusão:** a câmera e o rastreamento espacial funcionam no APK.

### Parte 2 — Detectar piso, rover e destino

- [ ] Ativar ARPlaneManager para planos horizontais.
- [ ] Usar ARRaycastManager para escolher pontos do piso por toque.
- [ ] Adicionar rover 3D leve, em escala real, com orientação correta.
- [ ] Mostrar marcador de posicionamento (reticle) e destino.
- [ ] Exibir estados: procurando piso, rover posicionado, destino definido e rastreamento perdido.

**Critério de conclusão:** rover e destino permanecem associados à mesma área do piso ao movimentar o celular, respeitando as limitações do rastreamento.

### Parte 3 — Planejamento A* em mapa simulado

- [ ] Criar grade 2D local (ex.: 2 × 2 m, células de 10–20 cm).
- [ ] Representar células livres, ocupadas e desconhecidas.
- [ ] Permitir marcar **manualmente** um obstáculo retangular para depuração.
- [ ] Implementar A* com heurística admissível e custo coerente com os movimentos permitidos.
- [ ] Exibir a rota e tratar casos sem caminho, fora da área ou em célula ocupada.
- [ ] Testar caminho livre, desvio, destino bloqueado e ausência de rota.

**Critério de conclusão:** A* encontra uma rota alternativa em torno do obstáculo artificial, sem depender da Depth API.

### Parte 4 — Captura e diagnóstico de profundidade

- [ ] Adicionar AROcclusionManager à câmera.
- [ ] Verificar disponibilidade da Depth API em tempo de execução.
- [ ] Obter dados de profundidade compatíveis com o provedor e mostrar um mapa colorido para diagnóstico.
- [ ] Testar chão, caixa e parede em diferentes distâncias e condições de iluminação.
- [ ] Filtrar amostras inválidas; se disponível, utilizar os valores de confiança de Raw Depth.
- [ ] Limitar frequência de leitura e descartar corretamente os buffers/imagens capturados.

**Critério de conclusão:** é possível visualizar a profundidade estimada e identificar quando ela não está disponível ou é pouco confiável.

### Parte 5 — Transformar profundidade em mapa de obstáculos

- [ ] Fixar um referencial local alinhado ao piso.
- [ ] Converter amostras de profundidade em pontos 3D usando pose e parâmetros de câmera adequados.
- [ ] Ignorar pontos no nível do piso, fora da área e valores inválidos.
- [ ] Projetar obstáculos acima do piso na grade 2D.
- [ ] Inflar as regiões bloqueadas por margem compatível com o tamanho do rover.
- [ ] Reduzir ruído com filtragem e observações repetidas.
- [ ] Distinguir células desconhecidas de células confirmadamente livres; não assumir que área sem profundidade está livre.
- [ ] Manter o modo manual para depuração.

**Critério de conclusão:** uma caixa real parada no chão produz células ocupadas e pode ser contornada pelo A*.

**Nota:** a Depth API **não fornece automaticamente** um mapa de ocupação pronto nem uma malha precisa de colisões. Esta conversão é uma parte central do desenvolvimento.

### Parte 6 — Navegação e movimento do rover

- [ ] Mover o rover virtual pelos pontos da rota, com velocidade moderada e rotação suave.
- [ ] Animar rodas e mostrar estado da navegação.
- [ ] Parar ou solicitar novo escaneamento se o rastreamento estiver ruim.
- [ ] Replanejar somente após mudanças estáveis no mapa, sem executar A* a cada quadro.
- [ ] Detectar chegada com tolerância de posição.
- [ ] Registrar distância estimada, tempo de cálculo da rota e duração da missão.

**Critério de conclusão:** o rover vai do ponto inicial ao destino evitando uma caixa real detectada, em cenário de teste controlado.

### Parte 7 — Jogo e modo educativo

- [ ] Criar **Missão 01 — Desvie do obstáculo**.
- [ ] Exibir objetivo, progresso, sucesso e opção de reiniciar.
- [ ] Criar pontuação simples por missão concluída e percurso.
- [ ] Adicionar opção **Missão / Laboratório**: em Laboratório, mostrar grade, rota e métricas.
- [ ] Criar interface minimalista, legível e sem excesso de instruções.

**Critério de conclusão:** uma pessoa consegue concluir a missão sem conhecer Unity, ARCore ou A*.

### Parte 8 — Qualidade, testes e documentação

- [ ] Testar em três cenários: piso livre, uma caixa, ambiente de iluminação/rastreamento desfavorável.
- [ ] Registrar falhas de profundidade, falsos obstáculos e estabilidade do posicionamento.
- [ ] Medir FPS, memória e tempo do A* no dispositivo.
- [ ] Tratar retomada do aplicativo, perda de rastreamento, permissão negada e recursos indisponíveis.
- [ ] Criar README com requisitos, instalação, arquitetura, imagens e limitações verificadas.
- [ ] Adicionar testes unitários para grade/A* e, quando adequado, CI de validação.

**Critério de conclusão:** o projeto pode ser instalado, demonstrado e reproduzido com suas limitações documentadas.

## 3. Marcos

| Marco | Partes | Entrega |
| --- | --- | --- |
| M1 — Base AR | 1–2 | Rover e destino no piso |
| M2 — Navegação | 3 | A* com obstáculo manual |
| M3 — Integração espacial | 4–5 | Caixa física representada no mapa |
| **M4 — MVP** | **6** | **Rover contorna a caixa real** |
| M5 — Demonstração completa | 7–8 | Missão, interface, testes e documentação |

## 4. Estrutura futura sugerida

```text
rover-quest-mr-/
├── docs/
│   └── step-by-step.md
├── Assets/
│   ├── Scenes/
│   ├── Scripts/
│   │   ├── AR/
│   │   ├── Mapping/
│   │   ├── Navigation/
│   │   ├── Rover/
│   │   └── UI/
│   ├── Prefabs/
│   └── Tests/
├── Packages/
├── ProjectSettings/
└── README.md
```

**Esta estrutura é apenas planejada:** criar pastas e código Unity somente na etapa correspondente. A inclusão deste documento não significa que funcionalidades foram implementadas.

## 5. Critérios técnicos e limitações

- A Depth API estima profundidade e pode produzir ruído, lacunas e erros; não se deve prometer desvio perfeito de qualquer objeto.
- Identificar a geometria do piso não equivale a reconhecer semanticamente uma “mesa”, “cadeira” ou “caixa”.
- Não usar objetos pequenos, translúcidos, refletivos ou em movimento como requisitos do primeiro MVP.
- Se a profundidade não estiver disponível, apresentar aviso e permitir testar a navegação com obstáculo manual; **não** afirmar que os obstáculos físicos foram detectados.
- Garantir que o mapa, o rover e o destino usem o mesmo referencial espacial.
- “Realidade misturada” descreve a integração das geometrias física e virtual neste conceito; a aplicação é executada como RA móvel, não como headset MR.

## 6. Referências

- [Unity — AR Foundation](https://docs.unity3d.com/Packages/com.unity.xr.arfoundation@6.0/manual/index.html)
- [Unity — AR Foundation Samples](https://github.com/Unity-Technologies/arfoundation-samples)
- [Google — ARCore Depth API](https://developers.google.com/ar/develop/depth)
- [Google — Raw Depth API](https://developers.google.com/ar/develop/unity-arf/raw-depth)
- [Google — dispositivos compatíveis](https://developers.google.com/ar/devices)

> **Próxima ação:** executar apenas a Parte 1. Não implementar todas as partes simultaneamente.
