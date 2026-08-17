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

## Antes de empezar: playit, para que entren tus amigos

Para que otros se conecten a tu server hace falta que tu computadora sea
alcanzable desde internet. La forma más simple es **playit.gg**, un servicio
gratuito que arma un túnel y te da una dirección fija, sin tocar el router.

1. Entra a [playit.gg](https://playit.gg) y crea una cuenta.
2. Descarga el programa para Windows e instálalo.
3. Al abrirlo, te va a pedir vincularlo con tu cuenta desde el navegador. Acepta.
4. En el panel de playit, crea un túnel de tipo **Minecraft Java**, apuntando al
   puerto **25565** de tu computadora.
5. Te va a dar una dirección parecida a `algo.gl.at.ply.gg`. **Esa es tu
   dirección de servidor.**

> Los nombres de los botones pueden cambiar con las actualizaciones de la página,
> pero el orden es siempre ese: cuenta → programa → vincular → túnel al 25565.

### Antes de repartir la dirección, compruébala

No todos los túneles de playit son iguales de rápidos, y no es cuestión de
suerte del momento: a cada túnel le toca un rango de IPs, y algunos proveedores
chilenos mandan ciertos rangos por **Estados Unidos** en vez de dejarlos dentro
de Chile. El mismo túnel puede darte 14 ms o 200 ms según qué rango le tocó.

Una vez que tengas la dirección, mídela:

```bash
java -jar mcbackup.jar red algo.gl.at.ply.gg
```

Te dice si el túnel quedó bien o si conviene borrarlo y crear uno nuevo (es
gratis, y sale con otra dirección en otro rango). Hazlo **antes** de pasarle la
dirección al grupo: es mucho más fácil que descubrirlo después, con todos
adentro quejándose del lag.

Después, **pon tu dirección en `backup.properties`**:

```
playit.hostname=algo.gl.at.ply.gg
```

No es cosmético: con eso, cada vez que hospedes, tu dirección se publica sola
junto con el candado del mundo. Los demás corren `java -jar mcbackup.jar
conectar` sin argumentos y su juego apunta solo a quien esté hospedando, sin que
nadie tenga que repartir nada por el chat. Si lo dejas vacío, el server funciona
igual, pero los demás van a tener que escribir tu dirección a mano.

> **Si el diagnóstico te dice que tu buena vía es IPv6**, tu agente de playit
> también debería salir por ahí, o la penalización te la comen todos los que se
> conecten a ti. Se fija agregando esta línea a `playit.toml` (necesita el
> agente 0.8.1-beta o superior):
>
> ```toml
> control_address = '[2602:fbaf::1]:5523'
> ```
>
> Si `red` te dice que tu buena vía es IPv4, **no toques esto**.

**Tu dirección no cambia nunca.** Cada quien tiene la suya: cuando le toque
hospedar a otro, los demás usan la dirección de esa persona — o directamente
`mcbackup conectar`, que la averigua solo.

**El programa de playit tiene que estar abierto mientras hospedas.** Si el server
está arriba pero playit cerrado, nadie va a poder entrar.

El puerto 25565 es el que deja configurado el instalador, así que no tienes que
ajustar nada más. Ni el server de Minecraft ni esta herramienta necesitan saber
cuál es tu dirección de playit: el túnel funciona por fuera y ellos ni se enteran.

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
