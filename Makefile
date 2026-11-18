# ==============================================================================
# 🪵 basquete-app - Makefile de Automação & Ferramentas
# Gerado via jirau (CLI de scaffold nativo multiplataforma)
# ==============================================================================

export ANDROID_HOME ?= $(HOME)/Android/Sdk
export PATH := $(HOME)/.local/bin:$(ANDROID_HOME)/platform-tools:$(ANDROID_HOME)/cmdline-tools/latest/bin:$(PATH)
IOS_DEVICE     ?= $(shell xcrun simctl list devices 2>/dev/null | grep -i booted | grep -oE '[0-9A-F-]{36}' | head -1)
MISE           := $(shell if [ -f "$$HOME/.local/bin/mise" ]; then echo "$$HOME/.local/bin/mise"; elif command -v mise >/dev/null 2>&1; then echo "mise"; else echo ""; fi)
RUN            := $(if $(MISE),$(MISE) exec --,)
CARGO          ?= $(shell command -v cargo 2>/dev/null || echo "$$HOME/.cargo/bin/cargo")
RUSTUP         ?= $(shell command -v rustup 2>/dev/null || echo "$$HOME/.cargo/bin/rustup")
ADB            ?= $(shell command -v adb 2>/dev/null || echo "$$HOME/Android/Sdk/platform-tools/adb")

.PHONY: all help menu setup doctor devices test test-core check format clean

all: menu

help:
	@echo "Execute make para abrir o menu guiado. Alvos diretos são próprios para automação."
	@echo "🪵 basquete-app - Comandos disponíveis:"
	@echo ""
	@echo "  make setup                  - Instala/verifica dependências de ambiente (mise, Java, Rust targets)"
	@echo "  make doctor                 - Executa diagnóstico do ambiente de desenvolvimento"
	@echo "  make devices                - Lista emuladores e dispositivos móveis conectados"
	@echo "  make test                   - Executa todos os testes da workspace (Rust Core + integrações)"
	@echo "  make test-core              - Executa testes unitários do núcleo Rust puro no host"
	@echo "  make test-android           - Executa testes JVM do app Android (sem dispositivo)"
	@echo "  make test-android-device    - Testes instrumentados (Rust real) no dispositivo conectado"
	@echo "  make test-ios               - Executa testes do target iOS via xcodebuild (macOS)"
	@echo "  make check                  - Executa cargo check e cargo clippy"
	@echo "  make format                 - Formata o código Rust (cargo fmt)"
	@echo "  make build-core             - Compila as crates Rust da workspace"
	@echo "  make android-ffi            - Compila o núcleo Rust para Android (.so + bindings Kotlin)"
	@echo "  make apk-debug              - Gera APK Android de Debug"
	@echo "  make apk-release            - Gera APK Android de Release"
	@echo "  make build-android          - Alias para apk-debug"
	@echo "  make ios-ffi                - Compila o núcleo Rust para iOS (XCFramework + bindings Swift)"
	@echo "  make ios-project            - Gera projeto Xcode via XcodeGen (ios/project.yml)"
	@echo "  make build-ios              - Compila o app iOS via xcodebuild (macOS)"
	@echo "  make clean                  - Limpa artefatos de compilação"

menu:
	@MAKE_COMMAND="$(MAKE)" sh scripts/make-menu.sh 'setup|Preparar ambiente (instala ferramentas)' 'doctor|Diagnosticar ambiente' 'devices|Listar dispositivos conectados' 'test-core|Executar testes Rust do workspace' 'check|Verificar código Rust e executar Clippy' 'build-core|Compilar o workspace Rust' 'format|Formatar código Rust (altera arquivos)' 'run-android|Compilar, instalar e abrir Android (requer jirau e dispositivo)' 'test-android|Executar testes Android no host' 'test-android-device|Executar testes Android em dispositivo conectado' 'apk-debug|Gerar APK Android de debug' 'apk-release|Gerar APK Android de release' 'android-ffi|Gerar biblioteca e bindings Android' 'run-ios|Compilar, instalar e abrir iOS (requer jirau e simulador)' 'test-ios|Executar testes iOS (macOS e simulador ativo)' 'build-ios|Compilar app para simulador iOS (macOS)' 'ios-ffi|Gerar biblioteca e bindings iOS' 'clean|Limpar artefatos de build (remove arquivos)'

setup:
	@echo "==> Configurando ambiente do projeto..."
	@if [ -f scripts/setup-android-env.sh ]; then ./scripts/setup-android-env.sh; fi
	@$(RUSTUP) target add aarch64-linux-android x86_64-linux-android
	@$(RUSTUP) target add aarch64-apple-ios aarch64-apple-ios-sim
	@echo "Setup concluído!"

doctor:
	@if command -v jirau >/dev/null 2>&1; then jirau doctor; else echo "Erro: jirau não encontrado no PATH; instale a CLI para diagnosticar o ambiente." >&2; exit 1; fi

devices:
	@echo "==> Dispositivos detectados:"
	@command -v adb >/dev/null 2>&1 && adb devices -l || echo "adb não encontrado no PATH"
	@if [ "$$(uname -s)" = "Darwin" ]; then \
		echo "--- iOS Simulators Booted ---"; \
		xcrun simctl list devices 2>/dev/null | grep -i booted || echo "Nenhum simulador iOS ativo"; \
	fi

test-core:
	@echo "==> Executando testes do núcleo Rust no host..."
	@$(CARGO) test --workspace

test: test-core

check:
	@echo "==> Executando cargo check e cargo clippy..."
	@$(CARGO) check --workspace
	@$(CARGO) clippy --all-targets -- -D warnings

format:
	@echo "==> Formatando código Rust..."
	@$(CARGO) fmt

build-core:
	@echo "==> Compilando crates Rust..."
	@$(CARGO) build --workspace

clean:
	@echo "==> Limpando artefatos de build..."
	@$(CARGO) clean
	@if [ -d android ]; then cd android && rm -rf .gradle build app/build; fi

android-ffi:
	@$(RUN) ./scripts/build-android-ffi.sh debug

run-android:
	@command -v jirau >/dev/null 2>&1 || { echo "Erro: instale a CLI jirau para executar o app." >&2; exit 1; }
	@jirau run android --no-watch

test-android:
	@cd android && $(RUN) ./gradlew :app:testDebugUnitTest

test-android-device:
	@cd android && $(RUN) ./gradlew :app:connectedDebugAndroidTest

apk-debug:
	@cd android && $(RUN) ./gradlew assembleDebug

apk-release:
	@cd android && $(RUN) ./gradlew assembleRelease

build-android: apk-debug

ios-ffi:
	@if [ "$$(uname -s)" != "Darwin" ]; then echo "Erro: iOS requer macOS com Xcode." >&2; exit 1; fi
	@$(RUN) ./scripts/build-ios-ffi.sh debug

ios-project: ios-ffi
	@cd ios && xcodegen generate

run-ios:
	@command -v jirau >/dev/null 2>&1 || { echo "Erro: instale a CLI jirau para executar o app." >&2; exit 1; }
	@jirau run ios --no-watch

build-ios: ios-project
	@if [ "$$(uname -s)" = "Darwin" ]; then \
		cd ios && xcodebuild -project BasqueteApp.xcodeproj -scheme BasqueteApp -configuration Debug -destination 'generic/platform=iOS Simulator' build CODE_SIGNING_ALLOWED=NO; \
	else \
		echo "Erro: build iOS requer macOS com Xcode." >&2; exit 1; \
	fi

test-ios: ios-project
	@if [ "$$(uname -s)" != "Darwin" ]; then \
		echo "Erro: testes iOS requerem macOS com Xcode." >&2; exit 1; \
	elif [ -z "$(IOS_DEVICE)" ]; then \
		echo "❌ Nenhum simulador iOS ligado. Inicie um com: xcrun simctl boot <UDID>"; exit 1; \
	else \
		cd ios && xcodebuild test -project BasqueteApp.xcodeproj -scheme BasqueteApp -destination 'platform=iOS Simulator,id=$(IOS_DEVICE)'; \
	fi
