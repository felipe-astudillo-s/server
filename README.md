# Backups de Minecraft a Google Drive

Sube el mundo de tu server a tu propio Google Drive, solo, todos los días.

No hay que crear nada en ninguna consola de Google, ni instalar Python, ni
compilar nada. Si tu server de Minecraft arranca, esto también.

## Requisitos

Java 17 o superior — el mismo que ya necesitás para correr el server.

## Instalación

1. Descargá `mcbackup.jar` de la sección [Releases](../../releases).
2. Ponelo en la carpeta del server, al lado de `server.jar`.

## Paso 1: conectar tu Google Drive

```bash
java -jar mcbackup.jar auth
```

Se abre el navegador, elegís tu cuenta, hacés click en **Permitir**, y listo.
Esto se hace **una sola vez**: después se renueva solo.

Los backups van a **tu** Drive, no al de nadie más. La app solo puede ver y
tocar los archivos que ella misma crea: no tiene acceso al resto de tu Drive.

¿El server es un VPS sin navegador?

```bash
java -jar mcbackup.jar auth --manual
```

Te da un link para abrir desde el celular o cualquier otra computadora.

## Paso 2: configurar

```bash
java -jar mcbackup.jar backup
```

La primera vez crea `backup.properties` y se detiene para que lo revises. Los
valores que importan:

| Opción | Para qué sirve |
|---|---|
| `server.dir` | Carpeta del server. `.` si el jar está adentro. |
| `world.folders` | Mundos a respaldar. Vanilla usa solo `world`; Paper y Spigot separan las dimensiones. Los que no existan se ignoran. |
| `retention` | Cuántos backups conservar. Los más viejos se borran solos. |
| `rcon.*` | Ver abajo. Muy recomendado. |

### Activá RCON

Sin RCON, el mundo se copia **mientras el server está escribiendo**, y el
backup puede salir corrupto — de esos que parecen estar bien hasta el día que
los necesitás. Con RCON, la herramienta le pide al server que pause el guardado
durante la compresión y lo reactiva enseguida.

En el `server.properties` del server:

```
enable-rcon=true
rcon.port=25575
rcon.password=poné-algo-difícil
```

Reiniciá el server. Después, en `backup.properties`, poné `rcon.enabled=true` y
la misma contraseña.

## Paso 3: probarlo

```bash
java -jar mcbackup.jar backup
```

Y para ver lo que hay guardado en Drive:

```bash
java -jar mcbackup.jar list
```

## Paso 4: que corra solo

### Linux

```bash
crontab -e
```

Agregá esta línea para un backup diario a las 4 de la mañana (cambiá la ruta):

```
0 4 * * * cd /ruta/a/tu/server && java -jar mcbackup.jar backup >> backup.log 2>&1
```

### Windows

En PowerShell **como administrador**, cambiando la ruta:

```bash
schtasks /create /tn "Backup Minecraft" /sc daily /st 04:00 /tr "cmd /c cd /d C:\ruta\a\tu\server && java -jar mcbackup.jar backup >> backup.log 2>&1"
```

Elegí un horario con el server vacío: la compresión ocupa disco y CPU.

## Restaurar un backup

Un backup que nunca probaste restaurar no es un backup. Probá esto una vez,
con calma, antes de necesitarlo de verdad:

1. **Pará el server.** Sin excepciones — si está corriendo, va a pisar lo que
   restaures.
2. Descargá el `.zip` desde tu Google Drive, carpeta *Minecraft Backups*.
3. Renombrá la carpeta `world` actual a `world-roto` (no la borres todavía).
4. Descomprimí el zip en la carpeta del server: recrea `world/` tal cual estaba.
5. Arrancá el server y verificá que todo esté en su lugar.
6. Recién ahí borrá `world-roto`.

## Preguntas

**¿Cuánto ocupa en mi Drive?** Un mundo comprimido suele ir de decenas de MB a
un par de GB. Con `retention=7` guardás una semana. Si te queda justo, bajá el
número.

**¿Puedo tener varios servers en la misma cuenta?** Sí, pero cambiales el
`drive.folder` a cada uno, o van a competir por la misma retención.

**Se cortó internet a mitad de la subida.** No pasa nada: la subida es
resumible, retoma desde donde quedó en vez de empezar de cero.

**¿Y el archivo `.auth_tokens.json`?** Son tus credenciales. No lo subas a
ningún lado ni lo compartas.
