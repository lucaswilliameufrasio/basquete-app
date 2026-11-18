# Direção visual — Basquete App

Referências escolhidas pelo usuário e consultadas nos anexos da sessão em
16/11/2026: CourtX e a referência de times e placares. Não guardar suas
imagens no projeto; origem em `product-and-design.md`.

## Papéis das referências

- **Revisão de 17/11/2026:** o usuário rejeitou a execução visual atual.
  Os dois anexos originais foram recuperados da sessão mencionada em
  `product-and-design.md` e reinspecionados, sem adicioná-los ao repositório.
  Os tokens e a implementação abaixo não são aprovação do resultado. A
  prioridade é corrigir a composição de Jogos, não multiplicar verificações
  de um layout rejeitado.

- **CourtX:** tema escuro próximo de preto, superfícies cinza-escuras, laranja
  forte na ação selecionada, foco no confronto e navegação inferior compacta.
  A imagem combina um cartão destacado de placar com seções verticais.
- **Times e placares:** modo claro, nomes de times legíveis, confrontos e
  números alinhados, superfície clara e cartões brancos que se distinguem do
  fundo. Sua seleção de times demonstra uma lista com estado destacado.

### Auditoria visual das referências e do estado anterior

- CourtX, tela central: o confronto em destaque ocupa um painel horizontal
  compacto no topo, com os dois lados equilibrados, nome do confronto e estado
  no centro, e placares em posição visualmente equivalente. A seguir há uma
  faixa horizontal de categorias, conteúdo em blocos visuais e uma navegação
  inferior compacta. A referência usa fotografias e escudos que não podemos
  reaproveitar; a hierarquia pode ser adaptada sem inventar esses dados.
- Referência clara, tela de placares: a informação esportiva é organizada em
  linhas brancas compactas; equipes ficam nas extremidades, os números estão
  alinhados e o estado da partida está no centro. A área de seleção de times
  usa uma lista com seleção claramente destacada, não uma sequência de campos
  de formulário.
- Evidências anteriores inspecionadas: `target/android-status.png` apresenta
  título, botão de data e dois rádios empilhados antes dos jogos; os cartões
  privilegiam o alinhamento vertical de nomes e ações, não o confronto.
  `target/ios-status-encerrado-fixture.png` agrupa data e liga como formulário
  e várias partidas em um bloco único. Ambos demonstram a ausência da hierarquia
  compacta de confronto das referências; não são capturas do novo redesign.
- Direção para a próxima implementação: preservar controles e acessibilidade
  nativos, mas diminuir a dominância visual dos filtros; apresentar cada
  partida como unidade independente, com equipes e placares em posições
  comparáveis e estado/horário legíveis. Em tamanhos acessíveis, permitir
  quebra e empilhamento em vez de cortar nomes. Nunca fabricar escudos,
  fotografia, jogos, estatísticas ou transmissões para preencher o layout.
- Essa auditoria não comprova fidelidade: comparar capturas reais dos dois
  temas com os anexos após implementar, inclusive estados vazio/erro e nomes
  longos. Não copiar marca, artes ou composição identificável.

As referências mostram ilustrações, fotografias, atletas, logotipos e dados
que não são ativos do app: **não reutilizá-los nem inventar estatísticas,
placares, times ou transmissões**. Não imitar os layouts identificáveis como
marcas. Usar componentes nativos de navegação, data, seleção e acessibilidade.

## Conceito e princípios

**Placar em primeiro plano:** abrir o app deve tornar fácil identificar partidas,
horários e resultados, sem tomar a identidade de nenhuma liga.

1. Conteúdo antes de decoração: hierarquia para o confronto, sem fotos ou
   informações falsas apenas para preencher espaço.
2. Estado explícito: status, hora UTC, carregamento, vazio e erro não podem
   depender apenas de cor.
3. Nativo nas interações: navegação, seletores e acessibilidade seguem a
   plataforma, sem transformar Android e iOS em cópias idênticas.
4. Preferência do usuário prevalece: Sistema, Claro e Escuro são persistidos,
   assim como os favoritos.

## Tokens semânticos

Valores definidos para implementação, inspirados nas referências, **não
extraídos por amostragem de pixels**. Manter nomes semânticos no código; não
usar cores literais nos componentes. Os números são cores opacas sRGB.

| Token | Escuro | Claro | Uso |
| --- | --- | --- | --- |
| `background` | `#101010` | `#F4F4F4` | fundo principal |
| `surface` | `#1C1C1C` | `#FFFFFF` | partida e grupos |
| `textPrimary` | `#F5F5F5` | `#171717` | nomes e placares |
| `textSecondary` | `#BCBCBC` | `#595959` | estado, fonte e detalhes |
| `accent` | `#FF7A1A` | `#A93F00` | ação e seleção |
| `onAccent` | `#111111` | `#FFFFFF` | conteúdo sobre ação preenchida |

Pares calculados por luminância relativa WCAG: escuro texto/fundo **17,45:1**,
texto/superfície **15,63:1**, secundário/superfície **8,97:1**,
acento/superfície **6,53:1**, conteúdo/acento **7,24:1**; claro
texto/superfície **17,93:1**, secundário/superfície **7,00:1**,
acento/superfície **6,18:1** e conteúdo/acento **6,18:1**. Esses valores não
atestam contraste de outros pares, estados, transparências ou renderização.
Antes de usar um par novo, medir: texto normal ≥4,5:1; texto grande e limites
visuais essenciais ≥3:1. Estados de erro usarão cor nativa acessível **e texto**;
não inventar um tom sem medi-lo.

Tipografia: fontes do sistema, Roboto/Material no Android e SF/SwiftUI no iOS,
com dimensionamento acessível. Escala inicial: título de tela 28, título de
seção 20, nomes 16, corpo 14–16, metadados 12–14 sp/pt; placares 20–24 com
algarismos alinhados. Valores são pontos de partida para os estilos nativos,
não tamanhos rígidos que impeçam ampliação. Nomes longos podem quebrar linha.
Espaçamento base 4, com passos 8/12/16/24; margens de conteúdo 16–24 e raio
de cartões 14–16 dp/pt. A navegação e componentes não devem sobrepor o conteúdo.

## Componentes e estados

| Componente | Anatomia e contrato | Estados |
| --- | --- | --- |
| Navegação | Jogos, Favoritos e Configurações com ícone nativo e rótulo; indicador laranja do destino atual. Android: barra Material; iOS: `TabView`. | selecionado, não selecionado, foco e toque |
| Cabeçalho | título da tela e, quando necessário, subtítulo de contexto; sem logotipo de liga. | normal e texto ampliado |
| Filtros | data UTC com seletor nativo e seleção de liga identificável; fim da consulta não é implícito. | selecionado, foco, aberto e indisponível |
| Cartão de partida | liga e status secundários; dois times e placares alinhados; horário ou ausência; aviso de transmissão; favorito por time apenas com ID válido. | sem placar, encerrado, não iniciado, status desconhecido, nome longo |
| Favorito | estrela com nome acessível existente; seleção visível também por preenchimento do ícone, não só pela cor. | adicionado, não adicionado, sem ID, foco e toque |
| Estado de conteúdo | carregando, erro com “Tentar novamente”, vazio e lista; erro nunca vira vazio. | carregando, falha, vazio, preenchido |
| Configurações | grupo nativo de opções Sistema, Claro, Escuro; texto da fonte e transmissão. | opção selecionada, foco e texto ampliado |
| Fonte e limites | rodapé discreto mas legível com limite de três jogos e referência UTC; ausência de consulta à transmissão explícita. | texto longo e quebra de linha |

Áreas interativas mínimas: Android **48 dp**; iOS **44 pt**; preservar rótulos
de acessibilidade e ordem lógica. Pressionado/foco precisam de indicação
visível com contraste suficiente; animações apenas para feedback ou transição,
preferindo o padrão nativo e respeitando movimento reduzido.

## Regras para a implementação

- Preservar os modos Sistema, Claro e Escuro e os dados persistidos existentes.
- No escuro, trabalhar com fundo quase preto e superfícies elevadas discretas;
  no claro, fundo suave e superfícies brancas. Reservar laranja para ações,
  seleção e ênfase, ajustando tons para contraste acessível.
- O conteúdo principal é a partida: liga e estado em menor destaque, nomes
  legíveis e placares comparáveis. Sem placar ou horário, exibir a ausência
  real. Não substituir a lista por imagens fictícias.
- Separar visualmente filtros, resultados, favoritos e configurações; evitar
  uma lista plana idêntica em todas as telas. Conservar estados de erro, vazio,
  carregamento e os avisos de fonte e transmissão.
- Usar ícones nativos e alvos adequados, nomes acessíveis já existentes,
  suporte a texto ampliado e navegação Material/SwiftUI apropriada à plataforma.
- Aplicar e verificar os tokens nas duas plataformas; comparar capturas reais
  com os anexos originais sem salvar ou republicar as imagens de terceiros.
  Não considerar o redesign concluído antes da comparação e jornadas reais.

## Fontes e pendências

- Referências visuais escolhidas pelo usuário: anexos da sessão citada em
  `product-and-design.md`; consultadas, não copiadas ao repositório.
- [Acessibilidade Android](https://developer.android.com/design/ui/mobile/guides/foundations/accessibility)
  e [WCAG 2.2: contraste](https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html),
  consultados em 16/11/2026. A página Apple de acessibilidade não retornou
  conteúdo na consulta; orientação específica de iOS ainda deve ser conferida.
- **Ainda não comprovado:** tokens aplicados em tela real, contraste de todos
  os estados, leitor de tela, texto ampliado e comparação de capturas. O
  inventário e contratos estão em `redesign-inventory.md`.
