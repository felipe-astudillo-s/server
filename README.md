# Server de Minecraft compartido

Un mundo, varios anfitriones. Cualquiera del grupo puede levantar el server: el
mundo se descarga solo antes de jugar y se sube solo al cerrar, así todos siguen
siempre desde donde quedó el anterior.

> **¿Ya instalaste esto antes y solo quieres jugar?**
> Ve directo a **[JUGAR.md](JUGAR.md)**. Esta página es para la primera vez.

---

# Instalación (una sola vez)

## Lo único que necesitas

**Java 17 o superior.** Si no lo tienes, descárgalo de
[adoptium.net](https://adoptium.net) y dale siguiente a todo.

Nada más: ni Python, ni rclone, ni crear nada en consolas de Google.

## Los pasos

### 1. Descarga el programa

Descarga **`mcbackup.jar`** de [Releases](../../releases) y ponlo en una carpeta
vacía. Esa carpeta va a ser tu server.

Es un solo archivo: no hay nada que descomprimir.

> Ponla en un disco con espacio y **fuera de OneDrive o Dropbox**: si una de esas
> carpetas sincroniza el mundo mientras el server escribe, lo corrompe.

### 2. Ejecuta el instalador

Abre una terminal **en esa carpeta**: clic derecho en un espacio vacío →
**"Abrir en Terminal"**. Después escribe:

```bash
java -jar mcbackup.jar instalar
```

> **¿Por qué un comando y no un doble clic?** Windows bloquea los archivos `.bat`
> descargados de internet, y no hay manera de evitarlo sin comprar un certificado
> de firma digital. Este comando funciona siempre, en cualquier computadora.
>
> Y es la única vez que lo necesitas: el instalador crea un **`jugar.bat`** en tu
> carpeta y, como lo genera tu propia computadora, ese sí abre con doble clic.
>
> **No desactives el Control inteligente de aplicaciones** para esquivar el
> bloqueo: Windows no te deja volver a activarlo sin reinstalar el sistema.

Va a hacer cinco cosas, mostrándote cada una:

1. Descargar el servidor Fabric de la última versión estable
2. Instalar tres mods de rendimiento (lithium, ferritecore, krypton)
3. Pedirte que aceptes el EULA de Minecraft — hay que escribir `acepto`
4. Crear la configuración, con una contraseña de RCON generada al azar
5. Abrir el navegador para conectar Google Drive

### 3. Entra con la cuenta del grupo ← el paso que importa

Cuando se abra el navegador, **inicia sesión con la cuenta de Google compartida
del grupo, no con la tuya personal.**

Todos los que hospedan usan esa misma cuenta. Es lo que hace que veas el mundo
que dejó el anterior. Si entras con tu cuenta personal, vas a tener un mundo
tuyo, aislado, y nadie va a ver tus partidas.

Si el navegador inicia sesión solo con tu cuenta, ciérrala primero o usa una
ventana de incógnito.

### 4. Listo

Cuando termine, te va a decir cómo jugar. La carpeta queda con todo configurado
y no hay que repetir nada de esto nunca más.

**Continúa en [JUGAR.md](JUGAR.md).**

---

## Si algo sale mal

**"No encuentro Java"** — no está instalado o no quedó en el PATH. Instálalo de
[adoptium.net](https://adoptium.net) y vuelve a abrir la ventana.

**Un mod no se pudo descargar** — pasa cuando la versión de Minecraft es muy
nueva y el mod todavía no sale para ella. El instalador lo avisa y continúa; el
server funciona igual, solo un poco menos optimizado.

**Quieres una versión específica de Minecraft** — ejecuta desde la terminal:

```bash
java -jar mcbackup.jar instalar --mc 1.21.4
```

**Ya tenías un server armado** — el instalador no reemplaza tu `server.properties`
si ya existe. Solo te avisa si le falta RCON, que es lo que necesita para hacer
respaldos sin corromper el mundo.

## Para quien mantiene el proyecto

Si eres quien administra el repositorio y necesitas registrar el cliente OAuth,
eso está en [SETUP-AUTH.md](SETUP-AUTH.md). Ningún jugador necesita leerlo.
