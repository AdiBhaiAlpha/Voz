#!/bin/sh
set -eu
umask 077
: "${TURN_SECRET:?TURN_SECRET required}"
config=$(mktemp /tmp/voz-turn.XXXXXX)
printf 'static-auth-secret=%s\n' "$TURN_SECRET" > "$config"
if [ -n "${TURN_TLS_CERT:-}" ] && [ -n "${TURN_TLS_KEY:-}" ]; then
    exec turnserver -c "$config" "$@" --cert="$TURN_TLS_CERT" --pkey="$TURN_TLS_KEY" --tls-listening-port="${TURN_TLS_PORT:-5349}"
else
    exec turnserver -c "$config" "$@" --no-tls --no-dtls
fi
