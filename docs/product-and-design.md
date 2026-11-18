# Basquete App — produto e direção visual

Nome e identificadores provisórios. Exemplo open source gerado pelo Jirau, sem
afiliação com ligas ou times. Não publicar imagens, marcas ou dados de terceiros
sem conferir direitos e termos.

## Jornadas

- Jogos: consultar agenda e placares; distinguir carregamento, falha, lista
  vazia e dados indisponíveis. A consulta experimental permite filtrar a liga
  norte-americana; "todas" e o filtro continuam limitados a três resultados
  por data. A liga brasileira não é oferecida sem cobertura comprovada.
- Favoritos: selecionar times apresentados nos jogos e consultar ou remover
  a seleção offline, armazenada no aparelho por identificador da fonte. Sem
  identificador, o botão não é exibido. A seleção não é sincronizada entre
  aparelhos e depende da cobertura limitada da lista de jogos.
- Configurações: escolher Sistema (padrão), Claro ou Escuro, persistido no
  aparelho. Android usa preferências locais; iOS usa AppStorage.
- Transmissão: mostrar país e provedor apenas quando dados confiáveis estiverem
  disponíveis. Enquanto nenhuma consulta for feita, informar "não consultada",
  que não significa que não exista transmissão.

## Referências visuais

Imagens fornecidas pelo usuário em 2026-11-16: conceito CourtX (fundo escuro,
laranja, destaque para o jogo) e conceito de seleção de times e estatísticas
(superfícies claras e placares comparáveis). São referências de hierarquia e
clima, não autorização para reutilizar fotos, escudos ou layouts. Tema claro e
escuro seguem o sistema por padrão, com escolha manual. Componentes de navegação
seguem Material 3 no Android e SwiftUI no iOS. A implementação visual ainda
precisa de comparação em aparelhos com as referências.

As imagens foram consultadas nos anexos da sessão original; nenhuma cópia
é mantida no projeto, conforme pedido do usuário.
O conceito CourtX orienta fundo escuro, laranja, hierarquia do placar e
navegação; a segunda referência orienta legibilidade dos times e placares e
a hierarquia sobre superfície clara. Não reutilizar marcas, fotos ou escudos.

## Fonte de dados

Android e iOS usam provisoriamente TheSportsDB v1 para consultar jogos por data em UTC.
O endpoint gratuito limita a resposta a três jogos por dia, por isso a tela
avisa que é incompleta. Não há integração de transmissões. TheSportsDB permite API gratuita para
desenvolvimento, mas seus termos consultados em 2026-11-16 exigem assinatura
paga para publicar em lojas, mesmo para apps gratuitos; dados e imagens podem
ter direitos adicionais. Cobertura de basquete brasileiro e transmissões por
região não foi verificada. Não incluir credenciais em código ou no app.

Fonte: https://www.thesportsdb.com/docs_terms_of_use.php

### Investigação de cobertura (2026-11-16)

- SportsEvents24: API beta pública documentada sem cadastro. Uma consulta em
  16/11/2026 a `/api/v1/events?tournament_id=150914&limit=3` retornou jogos
  reais da temporada brasileira 2026/27, incluindo São José Basketball x Mogi
  Basquete. O esquema não fornece transmissões nem IDs de times para conectar
  aos favoritos existentes. A consulta de teste não comprova completude ou
  direitos de redistribuição. Os IDs são de temporada; a próxima precisa ser
  descoberta, não fixada indefinidamente.
- Restrições anunciadas do beta SportsEvents24: 100 requisições/dia/IP,
  10/minuto/IP, 5.000 registros/mês/IP compartilhados, até 100 por página,
  paginação por cursor e janela entre sete dias atrás e 30 dias à frente.
  Campo `category` não confirma encerramento; horários podem usar fallback
  e exigem inspeção de `timeZoneConfidence`. Não integrado: falta verificar
  termos de uso/distribuição, tratar paginação, limite, fuso e identidade.
- O OpenAPI especifica `from` inclusivo a partir de hoje UTC menos 7 dias e
  `to` exclusivo até hoje UTC mais 30 dias. O transporte experimental local
  valida esses limites na criação e antes de cada requisição, usando relógio
  UTC injetável. Após um 429, instâncias locais que compartilham a mesma
  `SharedQuota` bloqueiam requisições até o `Retry-After` em segundos; ausência
  ou valor inválido mantém o bloqueio dessa instância de controle. Não há
  repetição automática. Instâncias com controles diferentes, processos e
  aparelhos não são coordenados; reiniciar perde esse estado. Não é um
  limitador completo das cotas por IP. Continua restrito a loopback, sem
  transporte de produção.
- Sondagem adicional em 16/11/2026: `tournaments?country=BR&sport_id=2&limit=100`
  retornou 46 torneios, incluindo `NBB 2026/2027` (ID 150914), e
  `nextCursor: null`. Uma consulta com esse ID, `from=2026-11-16`,
  `to=2026-11-17` e `limit=10` retornou dois jogos, ambos com confiança de
  fuso `medium`, e `nextCursor: null`. Essas respostas são uma amostra, não
  garantem descoberta em temporadas futuras ou completude.
- A consulta genérica brasileira para a mesma data com `limit=5` retornou
  cinco partidas de outra competição e um cursor: não usar a primeira página
  para afirmar ausência de NBB. Nela, dois eventos tinham `startsAt` em UTC no
  dia 16 e `localStartsAt` no dia 15; alguns tinham `timeZoneConfidence: null`.
  Portanto a seleção por data UTC não equivale automaticamente ao dia local e
  confiança ausente não deve virar horário local garantido. Respeitar o limite
  de linhas e o cursor, indicando explicitamente qualquer truncamento.
- TheSportsDB v1 `eventsday.php` filtrado pela liga americana retornou três
  jogos reais, ainda sujeito ao limite gratuito; o filtro geral também retornou
  três jogos internacionais. Não há garantia de cobertura completa.
- `lookuptv.php` com um jogo consultado e com o exemplo da documentação
  retornou HTTP 403 usando o acesso público. Não tratar 403 como lista vazia
  nem embutir credenciais pagas no aplicativo.
- O cadastro público da Basketball Champions League Americas lista times
  brasileiros, mas não comprova cobertura da liga nacional nem de sua agenda.
  A página de um time brasileiro o lista como sem liga nacional.
- `nbb_api` é um pacote MIT que coleta dados do site da liga via scraping;
  a licença do pacote não autoriza por si só redistribuição dos dados nem
  estabelece estabilidade ou permissão de acesso ao site. Não integrado.
- API-Sports e Entity Sport anunciam cobertura brasileira; condições de uso,
  acesso ao plano gratuito e abrangência de transmissões ainda precisam ser
  confirmados por fornecedor. Nenhuma credencial foi solicitada ou armazenada.
- SportsAPI (sportsapi.com.br) anuncia basquete e canais por país, mas exige
  chave e anuncia teste de 48 horas e planos pagos. Cobertura efetiva da liga
  brasileira e direitos de distribuição em projeto aberto não foram
  comprovados. Não integrado nem cadastrado.
- Entity Sport informa cobertura da liga brasileira no catálogo, mas sua
  documentação descreve a conta de demonstração como limitada à temporada
  americana 2017/18; isso não confirma acesso de teste à liga brasileira.
  Highlightly também anuncia cobertura da liga brasileira; acesso efetivo,
  aplicabilidade dos termos à implantação aberta e transmissões não foram
  comprovados. Ver verificação complementar abaixo.

### Verificação complementar de fornecedores

- Highlightly anuncia NBB e plano Basic gratuito de 100 requisições/dia. Sua
  documentação exige chave e informa que alguns resultados podem ser ocultados
  nos planos gratuitos; a cobertura efetiva do NBB nesse plano ainda não foi
  testada. Os termos de 03/11/2026, seção 6.1, permitem armazenar e mostrar
  dados aos usuários de produto próprio, mas proíbem fornecer dados a terceiros
  para seus próprios produtos por API, feed, arquivo, proxy ou espelho sem
  autorização escrita, mesmo sem cobrança. Não estabelece por si só que uma
  implantação compartilhada deste projeto aberto seja permitida em qualquer
  configuração. Confirmar limites do plano, publicação e direitos de imagens.
  O índice público dos endpoints de basquete não lista transmissão ao vivo;
  highlights não são prova de canal de transmissão.
- Enetpulse documenta agenda de canais vinculada a eventos, incluindo indicador
  ao vivo/reprise e provedores filtráveis por país. O exemplo exige usuário e
  token; cobertura de NBB e canais brasileiros, preço, teste e direitos de
  exibição ainda não foram confirmados. Nenhuma chamada autenticada realizada.
- Watchmode documenta fontes de transmissão ao vivo apenas para Estados Unidos,
  Reino Unido, Canadá e Austrália; não é uma solução demonstrada para informar
  onde assistir no Brasil. Sua cobertura é variável e o plano limita acesso.
- Nenhum fornecedor foi integrado ou declarado aprovado. Antes de escolher,
  validar exemplos reais de jogos e canais brasileiros, autorização para o
  uso planejado e custo; não publicar nem embutir credenciais no app.

Fontes: https://highlightly.net/basketball-api/,
https://highlightly.net/basketball-api/documentation/getting-started/pagination/,
https://highlightly.net/terms/,
https://eclient.enetpulse.com/docs/eapi/media-schedule-service,
https://api.watchmode.com/use-cases/live-sports-tv-guide.

### Reavaliação pública de API-Sports (2026-11-17)

- A página de basquete anuncia plano gratuito de 100 consultas por dia; os
  termos indexados informam que a conta gratuita acessa endpoints, mas possui
  dados limitados e que a disponibilidade varia por competição e não é
  garantida. A documentação exige chave. Isso não demonstra que a temporada
  brasileira atual ou transmissões para o Brasil estejam disponíveis nesse plano.
- Os trechos dos termos disponíveis na busca dizem que as contas são
  individuais, proíbem múltiplas contas para ampliar o limite gratuito e a
  revenda direta dos dados sem permissão, mas citam aplicativos e sites como
  projetos possíveis. Esses trechos não resolvem, por si só, a publicação
  gratuita deste app aberto, redistribuição em servidor compartilhado ou
  direitos sobre marcas e imagens. Confirmar os casos de uso com o fornecedor.
- Limitação da verificação: o acesso direto às páginas de termos e cobertura
  retornou HTTP 403 nesta sessão; os pontos acima vieram dos trechos indexados
  pela busca e não substituem leitura integral dos termos vigentes ou resposta
  do fornecedor. Nenhuma conta, chave ou integração foi criada.
- Fontes: https://api-sports.io/terms,
  https://api-sports.io/sports/basketball,
  https://api-sports.io/documentation/basketball/v1.

### Candidata Sportradar (2026-11-17)

- **Cobertura:** a Sportradar anunciou em abril de 2026 parceria plurianual
  com a LNB abrangendo NBB, Copa Super 8 e outras competições. O anúncio trata
  especificamente de direitos audiovisuais para apostas e de coleta e
  distribuição de dados para apostas; não comprova que o pacote de mídia
  Global Basketball inclua NBB nem a cobertura disponível a este projeto.
  A documentação do Global Basketball descreve agendas, times, resultados e
  estatísticas conforme a cobertura, mas exige autenticação. Consultar a
  matriz e confirmar competição, temporada, campos e condições de teste.
- **Transmissões:** a documentação do endpoint de boxscore da API específica
  da liga norte-americana descreve campos de canal e rede. Isso não comprova
  canais brasileiros para o NBB ou disponibilidade desses campos na API
  Global Basketball; permanece pendente uma amostra autorizada por país.
- **Direitos e operação:** os direitos anunciados pertencem à relação entre
  fornecedor e liga, não são autorização para este app publicar os dados ou
  vídeo. Confirmar por escrito licença para app open source gratuito,
  implantação compartilhada, cache, marcas, preço e limites. A documentação
  caracteriza as APIs como B2B e não destinadas a chamadas diretas do cliente;
  nenhuma conta, credencial ou integração foi criada.
- Fontes:
  https://sportradar.com/content-hub/news/sportradar-to-power-future-of-brazils-top-basketball-league-lnb/,
  https://developer.sportradar.com/basketball/reference/global-basketball-overview,
  https://developer.sportradar.com/basketball/reference/nba-game-boxscore,
  https://developer.sportradar.com/getting-started/docs/coverage-information.

Fontes: https://www.thesportsdb.com/documentation,
https://sportsevents24.com/api, https://sportsevents24.com/api/openapi.json,
https://www.thesportsdb.com/league/5273-basketball-champions-league-americas,
https://github.com/GabrielPastorello/nbb_api,
https://api-sports.io/sports/basketball,
https://entitysport.com/basketball-api-coverage,
https://highlightly.net/basketball-api/,
https://sportsapi.com.br/integracao.

## Verificação inicial (2026-11-16)

- Fixture brasileiro sintético: no Android, montagem de `:app:assembleDebug`
  e `:app:assembleDebugAndroidTest` com `buildRustCoreDebug` excluído passou;
  a suíte instrumentada no emulador remoto x86_64 retornou `OK (9 tests)`.
  No iOS, `xcodebuild test` no simulador retornou `TEST SUCCEEDED`: 12 testes
  unitários e 3 de interface passaram. Em ambas as plataformas, o teste da
  partida fictícia verifica liga e time, “Transmissão não consultada” e
  ausência de ação para favoritar o time sem ID.
- Esses fixtures alimentam os modelos nativos de apresentação diretamente:
  **não exercitam o adaptador Rust brasileiro**, não consultam o fornecedor
  e não comprovam cobertura real, transmissão ou fidelidade visual. O resultado
  iOS está em `target/xcode-test/Logs/Test/Test-BasqueteApp-2026.11.16_08-13-27--0300.xcresult`.
- Android: APK de desenvolvimento sem biblioteca Rust iniciado no emulador
  remoto; três jogos reais exibidos para 16/11/2026, com limite e horários UTC.
  A seleção de tema escuro persistiu após reinício. Área de seleção de tema
  e contraste da barra de status foram ajustados e inspecionados no emulador.
- iOS: oito testes XCTest passaram, incluindo cliente HTTP com transporte
  simulado e ponte Rust. Simulador exibiu jogos reais da mesma consulta.
- Android: 6 testes instrumentados passaram no emulador remoto x86_64,
  incluindo a biblioteca Rust real; a ABI ARM não foi verificada. A biblioteca
  foi compilada com o linker do NDK já instalado, sem cargo-ndk.
- Filtro de liga: no emulador Android, a opção norte-americana retornou
  confrontos e placares reais de 16/11/2026. A consulta envia o filtro de
  liga sem o de esporte, pois a combinação dos dois retornou HTTP 403.
  Os testes iOS após a correção passaram: 11 unitários, incluindo a
  construção da consulta filtrada, e um teste de interface de favoritos.
  O filtro iOS ainda não foi exercitado visualmente com dados reais.
- Favoritos: Android validado no emulador remoto com inclusão, reinício e
  remoção. iOS: 10 testes unitários e um teste de interface passaram; este
  último usa dados locais isolados e verifica inclusão, reinício e remoção.
- O design ainda não reproduz a direção das referências; são telas funcionais
  iniciais. A lista de jogos usa superfícies nativas, times em linhas
  separadas e placares alinhados, inspirados na segunda referência; a
  comparação visual completa ainda está pendente. Em 16/11/2026, a tela
  atualizada foi inspecionada em Android claro e escuro e iOS escuro. Os
  controles de favoritos foram compactados em estrelas junto aos times;
  Android: toque e rótulo de acessibilidade verificados; iOS: apresentação
  inspecionada e jornada de favoritos exercitada por teste de interface.
  Cobertura brasileira,
  transmissão e build Android com biblioteca
  Rust para aparelhos ARM continuam pendentes.
