#!/bin/sh
set -u

while :; do
    printf '\nO que você quer fazer?\n'
    number=1
    for entry in "$@"; do
        printf ' %2s) %s\n' "$number" "${entry#*|}"
        number=$((number + 1))
    done
    printf '  0) Sair\nEscolha: '
    if ! IFS= read -r choice; then
        printf '\n'
        exit 0
    fi
    if [ "$choice" = 0 ]; then
        exit 0
    fi
    number=1
    target=
    for entry in "$@"; do
        if [ "$choice" = "$number" ]; then
            target=${entry%%|*}
            break
        fi
        number=$((number + 1))
    done
    if [ -z "$target" ]; then
        printf 'Opção inválida. Escolha um número do menu.\n'
        continue
    fi
    if [ "$target" = clean ]; then
        printf 'Remover artefatos de build? [s/N]: '
        if ! IFS= read -r confirmation; then
            printf '\n'
            exit 0
        fi
        case "$confirmation" in
            s|S|sim|SIM) ;;
            *) printf 'Limpeza cancelada.\n'; continue ;;
        esac
    fi
    if "${MAKE_COMMAND:-make}" "$target"; then
        printf '\nTarefa concluída: %s\n' "$target"
    else
        status=$?
        printf '\nTarefa falhou: %s (código %s)\n' "$target" "$status" >&2
    fi
done
