# Server de Minecraft compartido

Un mundo, varios anfitriones. Cualquiera del grupo puede levantar el server: el
mundo se descarga solo antes de jugar y se sube solo al cerrar, así todos siguen
siempre desde donde quedó el anterior.

> **¿Solo quieres entrar a jugar?** No necesitas nada de esto. Pídele la
> dirección a quien esté hospedando y conéctate desde Minecraft como a cualquier
> server. Si te va lento o te desconecta, salta a
> [¿Problemas de conexión?](#problemas-de-conexión).
>
> **¿Ya instalaste esto y te toca hospedar?** Ve a **[JUGAR.md](JUGAR.md)**.

---

# Instalación (una sola vez)

Esto es para quien va a **hospedar**. Cada persona del grupo que quiera tener el
turno de anfitrión lo hace una vez y no lo repite nunca más.

## Lo único que necesitas

**Java 17 o superior.** Si no lo tienes, descárgalo de
[adoptium.net](https://adoptium.net) y dale siguiente a todo.

Nada más: ni Python, ni rclone, ni crear nada en consolas de Google.

## 1. Descarga el programa

Descarga **`mcbackup.jar`** de [Releases](../../releases) y ponlo en una carpeta
vacía. Esa carpeta va a ser tu server.

Es un solo archivo: no hay nada que descomprimir.

> Ponla en un disco con espacio y **fuera de OneDrive o Dropbox**: si una de esas
> carpetas sincroniza el mundo mientras el server escribe, lo corrompe.

## 2. Ejecuta el instalador

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

## 3. Entra con la cuenta del grupo ← el paso que importa

Cuando se abra el navegador, **inicia sesión con la cuenta de Google compartida
del grupo, no con la tuya personal.**

Todos los que hospedan usan esa misma cuenta. Es lo que hace que veas el mundo
que dejó el anterior. Si entras con tu cuenta personal, vas a tener un mundo
tuyo, aislado, y nadie va a ver tus partidas.

Si el navegador inicia sesión solo con tu cuenta, ciérrala primero o usa una
ventana de incógnito.

## 4. Arma tu túnel de playit

Para que otros se conecten a tu server, tu computadora tiene que ser alcanzable
desde internet. **playit.gg** es gratis, te da una dirección fija y no te obliga
a tocar el router.

1. Entra a [playit.gg](https://playit.gg) y crea una cuenta.
2. Descarga el programa para Windows e instálalo.
3. Al abrirlo, te va a pedir vincularlo con tu cuenta desde el navegador. Acepta.
4. En el panel, crea un túnel de tipo **Minecraft Java** apuntando al puerto
   **25565** de tu computadora.
5. Te va a dar una dirección parecida a `algo.gl.at.ply.gg`. **Esa es tu
   dirección de servidor.**

> Los nombres de los botones pueden cambiar con las actualizaciones de la página,
> pero el orden es siempre ese: cuenta → programa → vincular → túnel al 25565.

El puerto 25565 es el que deja configurado el instalador, así que no hay que
ajustar nada más.

**El programa de playit tiene que estar abierto mientras hospedas.** Si el server
está arriba pero playit cerrado, nadie va a poder entrar. Es la causa más común
de "a mí no me entra".

## 5. Comprueba el túnel antes de repartirlo

No todos los túneles rinden igual, y no es cuestión de suerte del momento: a cada
túnel le toca un rango de IPs, y algunos proveedores chilenos mandan ciertos
rangos por **Estados Unidos** en vez de dejarlos dentro de Chile. El mismo
servicio puede darte 14 ms o 200 ms según qué rango te tocó.

Mídelo:

```bash
java -jar mcbackup.jar red algo.gl.at.ply.gg
```

Te dice en qué datacenter caes (`Santiago_1`, `Miami_1`, ...) y si conviene
borrar el túnel y crear otro. **Rehacerlo es gratis y toma segundos**, y sale con
otra dirección en otro rango. Repite hasta que dé bien.

Hazlo **ahora**, antes de pasarle la dirección al grupo. Es mucho más fácil que
descubrirlo después con todos adentro quejándose del lag.

> Tu medición solo habla de **tu** línea. Un túnel puede ser bueno desde VTR y
> malo desde Movistar. Cuando repartas la dirección, pide que dos o tres corran
> el mismo comando y peguen en el chat la línea que imprime al final: en treinta
> segundos ves si le sirve a todos.

## 6. Guarda tu dirección

Abre `backup.properties` (lo creó el instalador) y pon:

```
playit.hostname=algo.gl.at.ply.gg
```

No es cosmético. Con eso, cada vez que hospedes, tu dirección se publica sola
junto con el candado del mundo, y quien tenga conectada la cuenta de Drive del
grupo entra sin que le avises nada. Si lo dejas vacío el server funciona igual,
pero los demás van a tener que escribir tu dirección a mano.

> **Si `red` te dijo que tu vía buena es IPv6**, tu agente de playit también
> debería salir por ahí, o esa penalización se la comen todos los que se conecten
> a ti. Se fija agregando esta línea a `playit.toml` (necesita el agente
> 0.8.1-beta o superior):
>
> ```toml
> control_address = '[2602:fbaf::1]:5523'
> ```
>
> Si `red` te dijo que tu vía buena es IPv4, **no toques esto**.

## Listo

La carpeta queda con todo configurado y no hay que repetir nada de esto.

**Continúa en [JUGAR.md](JUGAR.md).**

---

# ¿Problemas de conexión?

Si entras al server y te va con lag, te tironea o te desconecta —**y a otros del
grupo no les pasa**— casi seguro no es tu internet ni tu computadora: es por
dónde te está mandando tu proveedor.

Pasa sobre todo con **VTR**, aunque no es exclusivo. Tu proveedor decide por
dónde sale cada rango de IPs, y algunos rangos de playit se van por Estados
Unidos aunque el server esté a diez cuadras. Eso agrega unos 180 ms y cortes.

## Haz esto

1. Baja **`mcbackup.jar`** de [Releases](../../releases) a una carpeta
   cualquiera. No instalas el server ni conectas Drive ni nada: solo el archivo.
2. **Doble clic al `mcbackup.jar`.** Se abre una ventana y te pregunta la
   dirección del server: pega la que te pasó quien está hospedando.
3. Abre Minecraft y conéctate a **`localhost`**.

Eso es todo. La ventana prueba las rutas disponibles hacia el server, se queda
con la más rápida, y hace de puente mientras juegas. Déjala abierta.

**No hay que tocar nada dentro de Minecraft**: ni argumentos de JVM, ni
configuración, ni nada. Solo `localhost`.

La próxima vez, doble clic otra vez: te pregunta de nuevo pero con la última
dirección ya escrita, así que aceptas y sigues. Si cambió el anfitrión, pegas la
nueva encima.

> **¿El doble clic no hace nada?** Tu Java no quedó asociado a los archivos
> `.jar`. Abre una terminal en esa carpeta y corre esto una vez:
>
> ```bash
> java -jar mcbackup.jar conectar
> ```
>
> Te pregunta la dirección igual, y además te deja un **`conectar.bat`** para que
> de ahí en adelante sí funcione el doble clic.

## ¿Por qué hace falta un programa aparte?

Porque la ruta se elige **antes** de que el server sepa que existes: cuando tu
Minecraft abre la conexión, los paquetes ya salieron. Ni el server ni playit
pueden moverlos a otro camino después.

El puente decide antes de que el juego se conecte, y además puede usar rutas que
el cliente de Minecraft no alcanza por cómo arranca su Java. El salto extra es
dentro de tu propia máquina: menos de un milisegundo, contra los ~180 ms que
ahorra elegir bien.

## Si le pasa a varios, el arreglo es del anfitrión

El puente es un parche del lado del jugador. Si a media sala le va mal, lo que
falla es el túnel: quien hospeda tiene que rehacerlo (paso 5 de la instalación).
Es gratis y toma segundos.

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
