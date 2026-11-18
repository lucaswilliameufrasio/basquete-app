# Testes locais do adaptador brasileiro

Este procedimento gera bindings e bibliotecas **somente para testes**. Os dados
são fictícios; não acessa o fornecedor e não habilita jogos brasileiros no
app normal. Requer Mac Apple Silicon, Xcode, XcodeGen, Android SDK/NDK, Rust com
os targets `x86_64-linux-android` e `aarch64-apple-ios-sim` já instalados, e
dependências Cargo disponíveis offline. Não instala ferramentas.

## 1. Gerar os artefatos

Na raiz do projeto, ajuste o caminho para o NDK instalado e execute:

```sh
ANDROID_NDK_HOME="$HOME/Library/Android/sdk/ndk/29.0.13846066" bash scripts/build-brazil-fixture.sh
```

Ao concluir, deve aparecer `Fixture bindings and libraries ready`. Os artefatos
ficam em `target/fixture-bindings` e `target/fixture-cargo`; o script não
substitui os bindings ou bibliotecas normais. Ele mantém diretórios de montagem
temporários em `target/fixture-xcframework.*`.

## 2. Testar Android

Com um emulador x86_64 de teste conectado, execute na raiz:

```sh
cd android
./gradlew :app:connectedDebugAndroidTest -PbrazilFixtureTest=true
```

O opt-in usa a biblioteca experimental e inclui `BrazilAdapterJourneyTest`.
O teste verifica a partida processada pelo adaptador Rust na tela, sem inventar
transmissão ou identificador para favorito. A instalação de debug substitui o
APK de mesmo identificador no emulador, preservando seus dados.

## 3. Testar iOS

Na raiz, gere o projeto separado e rode os testes no simulador disponível:

```sh
cd ios
xcodegen generate --spec project-fixture.yml
xcodebuild test -project BasqueteAppFixture.xcodeproj -scheme BasqueteApp \
  -destination 'platform=iOS Simulator,id=SEU-UDID' \
  -derivedDataPath ../target/xcode-fixture-test CODE_SIGNING_ALLOWED=NO
```

Substitua `SEU-UDID` pelo identificador obtido com `xcrun simctl list devices`.
O resultado esperado é `TEST SUCCEEDED`, incluindo
`testBrazilFixtureDoesNotInventBroadcastOrFavorites`. O projeto experimental
usa o mesmo identificador do app normal no simulador; seus testes isolam os
favoritos em uma suíte de preferências própria.

Estes testes não comprovam acesso, cobertura ou direitos de dados reais, nem
substituem a verificação do app normal ou a comparação visual do redesign.

## 4. Verificar barreiras de release

Na raiz, depois de gerar os artefatos:

```sh
python3 scripts/test-fixture-release-isolation.py
```

O resultado esperado contém três linhas `PASS`: a feature Rust é rejeitada
no perfil release, a variante Android release não existe com o opt-in e o
projeto experimental iOS rejeita a configuração Release. Essas barreiras não
substituem a revisão do artefato final de distribuição.
