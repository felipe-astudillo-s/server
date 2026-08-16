#!/bin/sh
# Abre el server. Baja el mundo antes y lo sube al cerrar.
# Para cerrar bien, escribi 'stop' en la consola del server.
cd "$(dirname "$0")" || exit 1

if ! command -v java >/dev/null 2>&1; then
    echo "No encuentro Java. Instalalo desde https://adoptium.net"
    exit 1
fi

exec java -jar mcbackup.jar host "$@"
