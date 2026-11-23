# Basquete App

Aplicativo de exemplo para acompanhar partidas de basquete em Android e iOS.
O projeto ainda está em desenvolvimento e não está pronto para publicação em
lojas.

## Funcionalidades

- Consulta de jogos por data UTC e liga, com favoritos locais.
- Temas claro, escuro e do sistema.
- Indicação explícita quando status, horário ou placar não estão disponíveis.
- A transmissão é apresentada como não consultada; não há integração ativa
  para consulta de canais.

A fonte provisória, TheSportsDB, limita a consulta gratuita a três jogos por
data; a lista pode estar incompleta. Os termos e direitos precisam ser
verificados antes da distribuição. O adaptador brasileiro é experimental e
está desativado.

## Desenvolvimento e validação

Consulte [as instruções Android](docs/setup-android.md), o
[padrão de testes](TESTING_STANDARD.md) e o [Makefile](Makefile). A validação
visual e de acessibilidade permanece parcial; alterações iOS recentes ainda
precisam ser compiladas e inspecionadas em um ambiente Apple.

## Licença

O código do projeto está disponível sob a [Apache License 2.0](LICENSE).
Marcas, imagens e dados de terceiros não são licenciados pelo projeto.
