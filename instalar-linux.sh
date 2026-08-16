#!/bin/sh
# Instalacion del server. Se corre una sola vez.
cd "$(dirname "$0")" || exit 1

if ! command -v java >/dev/null 2>&1; then
    echo "No encuentro Java. Instalalo desde https://adoptium.net"
    exit 1
fi

exec java -jar mcbackup.jar instalar
