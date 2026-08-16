# Server de Minecraft compartido

Un mundo, varios anfitriones. Cualquiera del grupo puede levantar el server: el
mundo se baja solo antes de jugar y se sube solo al cerrar, así todos siguen
siempre desde donde quedó el anterior.

> **¿Ya instalaste esto antes y solo querés jugar?**
> Andá directo a **[JUGAR.md](JUGAR.md)**. Esta página es para la primera vez.

---

# Instalación (una sola vez)

## Lo único que necesitás

**Java 17 o superior.** Si no lo tenés, bajalo de [adoptium.net](https://adoptium.net)
y dale siguiente a todo.

Nada más: ni Python, ni rclone, ni crear nada en consolas de Google.

## Los pasos

### 1. Descargá el programa

Bajá **`mcbackup.zip`** de [Releases](../../releases) y descomprimilo en una
carpeta vacía. Esa carpeta va a ser tu server.

> Ponela en un disco con espacio y **fuera de OneDrive o Dropbox**: si una de
> esas carpetas sincroniza el mundo mientras el server escribe, lo corrompe.

### 2. Ejecutá el instalador

Doble click en **`instalar.bat`** (en Linux o Mac: `./instalar.sh`).

Va a hacer cinco cosas, mostrándote cada una:

1. Descargar el servidor Fabric de la última versión estable
2. Instalar tres mods de rendimiento (lithium, ferritecore, krypton)
3. Pedirte que aceptes el EULA de Minecraft — hay que escribir `acepto`
4. Crear la configuración, con una contraseña de RCON generada al azar
5. Abrir el navegador para conectar Google Drive

### 3. Entrá con la cuenta del grupo ← el paso que importa

Cuando se abra el navegador, **iniciá sesión con la cuenta de Google compartida
del grupo, no con la tuya personal.**

Todos los que hostean usan esa misma cuenta. Es lo que hace que veas el mundo
que dejó el anterior. Si entrás con tu cuenta personal, vas a tener un mundo
tuyo, aislado, y nadie va a ver tus partidas.

Si el navegador te loguea solo con tu cuenta, cerrá sesión primero o usá una
ventana de incógnito.

### 4. Listo

Cuando termine, te va a decir cómo jugar. La carpeta queda con todo configurado
y no hay que repetir nada de esto nunca más.

**Seguí por [JUGAR.md](JUGAR.md).**

---

## Si algo sale mal

**"No encuentro Java"** — no está instalado o no quedó en el PATH. Instalalo de
[adoptium.net](https://adoptium.net) y volvé a abrir la ventana.

**Un mod no se pudo descargar** — pasa cuando la versión de Minecraft es muy
nueva y el mod todavía no salió para ella. El instalador lo avisa y sigue; el
server anda igual, solo un poco menos optimizado.

**Querés una versión específica de Minecraft** — corré desde la terminal:

```bash
java -jar mcbackup.jar instalar --mc 1.21.4
```

**Ya tenías un server armado** — el instalador no pisa tu `server.properties`
si ya existe. Solo te avisa si le falta RCON, que es lo que necesita para hacer
backups sin corromper el mundo.

## Para el que mantiene el proyecto

Si sos quien administra el repositorio y necesitás registrar el cliente OAuth,
eso está en [SETUP-AUTH.md](SETUP-AUTH.md). Ningún jugador necesita leerlo.
