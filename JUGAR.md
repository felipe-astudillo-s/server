# Jugar

> ¿Es la primera vez en esta computadora? Empieza por el [README](README.md).

## Abrir el server

Doble clic en **`jugar.bat`**.

Eso es todo. Por detrás hace cinco cosas:

1. **Reserva el mundo**, para que nadie más lo levante mientras juegas
2. **Descarga el mundo más reciente** desde Drive
3. **Abre el server**
4. Cuando cierras, **sube el mundo** con tus cambios
5. **Libera la reserva** para el siguiente

## Cerrar bien

**Escribe `stop` en la consola del server.**

No cierres la ventana con la X ni con Ctrl+C. Si lo haces, el mundo **no se
sube**: tus cambios quedan solo en tu computadora y el próximo que juegue va a
empezar desde antes de tu partida.

Si de todos modos llega a pasar, el programa te avisa y libera la reserva para
que nadie quede bloqueado.

## Ver si el mundo está libre

```bash
java -jar mcbackup.jar estado
```

Te dice si está libre o quién lo está usando y desde cuándo.

---

# Cuando algo no sale

## "El mundo lo esta hosteando <fulano>"

Alguien está jugando en este momento. Espera a que cierre.

Si estás **seguro** de que no está jugando — se le cortó la luz, cerró mal —
puedes liberarlo a la fuerza:

```bash
java -jar mcbackup.jar host --forzar
```

> Cuidado: si fuerzas mientras esa persona *sí* está jugando, van a quedar dos
> mundos distintos y el que suba último reemplaza al otro. Los mundos de
> Minecraft no se pueden fusionar: lo que se pierde, se pierde. **Pregunta
> antes.**

Las reservas de más de 12 horas se liberan solas.

## "Ya hay un server usando el mundo 'world'"

Quedó un server corriendo en tu propia máquina, normalmente porque se cerró la
ventana sin escribir `stop`. Apágalo bien:

```bash
java -jar mcbackup.jar detener
```

Guarda el mundo y lo cierra como corresponde. Después ya puedes jugar.

## "El mundo local NO esta al dia"

Aparece si intentas subir un respaldo manual cuando alguien jugó después que tú.
Tu copia quedó vieja y subirla borraría la partida del otro.

Lo correcto es simplemente jugar, que descarga el mundo actualizado:

```bash
java -jar mcbackup.jar host
```

## Windows bloquea `jugar.bat`

No debería pasarte: el instalador crea ese archivo en tu computadora, y por eso
nace sin la marca que Windows le pone a lo descargado.

Si de todos modos ocurre — por ejemplo, porque copiaste el `.bat` desde otra
parte — este comando hace exactamente lo mismo y nunca se bloquea:

```bash
java -jar mcbackup.jar host
```

Y para dejar el `jugar.bat` limpio otra vez:

```bash
Unblock-File jugar.bat
```

**No desactives el Control inteligente de aplicaciones**: no se puede volver a
activar sin reinstalar Windows.

## El server no arranca

Si cierra a los pocos segundos, el programa lo detecta y **no sube nada**, para
no reemplazar el mundo bueno que hay en Drive. Revisa el error que quedó en
pantalla: casi siempre es falta de memoria (reduce `server.ram` en
`backup.properties`) o un mod incompatible.

---

# Recuperar una partida

## Versiones anteriores en Drive

```bash
java -jar mcbackup.jar list
```

Se guardan las últimas 7 partidas. Para volver a una:

1. Entra al Drive de la cuenta del grupo, carpeta *Minecraft Backups*
2. Descarga el `.zip` que quieras
3. **Avísale al grupo** — vas a hacer retroceder el mundo para todos
4. Con el server cerrado, renombra tu carpeta `world` y descomprime el zip
5. Juega una partida con `jugar.bat` y cierra con `stop`: eso sube esa versión
   como la más nueva

## Tu copia local

Cada vez que descargas un mundo, el que tenías se guarda como
`world.anterior-<fecha>` en tu carpeta. **Nunca se borra nada.**

Si cerraste mal y perdiste tu partida, está ahí. Se van acumulando, así que
puedes borrar las viejas cuando confirmes que el mundo actual está bien.

---

# Quién puede entrar

La lista de jugadores permitidos (*whitelist*) **viaja con el mundo**. Agregas a
alguien una vez, desde cualquier computadora, y queda agregado para todos los
que hospeden.

Con el server abierto, escribe en su consola:

```bash
whitelist add NombreDeMinecraft
```

Otros comandos útiles, en la misma consola:

| Comando | Qué hace |
|---|---|
| `whitelist list` | Ver quiénes están autorizados |
| `whitelist remove <nombre>` | Sacar a alguien |
| `whitelist on` / `off` | Activar o desactivar la lista |
| `op <nombre>` | Dar permisos de administrador |

Cuando cierres con `stop`, esos cambios se suben junto con el mundo y el próximo
que hospede los recibe automáticamente.

## Qué se comparte y qué no

**Viaja con el mundo:** la whitelist, los operadores, los baneos, y las reglas de
juego — `motd`, dificultad, PvP, máximo de jugadores, modo de juego.

**Se queda en cada computadora:** el puerto, la IP y la contraseña de RCON. Son
de cada instalación; copiarlas rompería la del que recibe.

---

# Configuración

En `backup.properties`:

| Opción | Para qué sirve |
|---|---|
| `player.name` | Tu nombre, para que los demás sepan quién tiene el mundo |
| `server.ram` | Memoria del server. `4G` funciona bien con 8 GB en la máquina |
| `retention` | Cuántas partidas guardar en Drive |
| `playit.hostname` | Tu dirección de playit. Se publica sola al hospedar, para que los demás entren con `conectar` sin preguntarte nada |

## Que entren tus amigos

Abre el programa de **playit** antes o durante la partida. Si tienes tu
`playit.hostname` puesto en `backup.properties`, no tienes que avisar nada: tu
dirección se publica sola al reservar el mundo, y los demás entran corriendo
`java -jar mcbackup.jar conectar` y conectándose a `localhost`.

Si no lo tienes puesto, pasa tu dirección (`algo.gl.at.ply.gg`) por el chat del
grupo. Es siempre la misma, así que alcanza con compartirla una vez.

Si playit está cerrado, el server funciona igual pero nadie puede conectarse
desde afuera. Es la causa más común de "a mí no me entra".

> Si prefieres no usar playit, la alternativa es pasar tu IP pública y redirigir
> el puerto **25565** en tu router. En ese caso, **nunca redirijas el 25575**:
> ese es RCON y da acceso completo a la consola del server.

La configuración inicial de playit está en el [README](README.md).

## Si te va lento o te desconecta

**Usa el puente.** Son tres pasos y no hay que escribir ningún comando:

1. Baja **`mcbackup.jar`** de [Releases](../../releases) a una carpeta
   cualquiera. No necesitas instalar el server ni conectar Drive ni nada más.
2. **Doble clic al `mcbackup.jar`.** Se abre una ventana y te pregunta la
   dirección del server: pega la que te pasó quien está hospedando y dale
   aceptar.
3. Abre Minecraft y conéctate a **`localhost`**.

Listo. La ventana mide las rutas disponibles, elige la más rápida y se queda
abierta haciendo de puente mientras juegas.

**No hay que tocar nada dentro de Minecraft**: ni argumentos de JVM, ni
configuración. Solo `localhost`.

La próxima vez, doble clic otra vez: te va a preguntar de nuevo pero con la
última dirección ya escrita, así que le das aceptar y sigues. Si cambió el
anfitrión, pegas la nueva encima.

> **Si el doble clic no hace nada**, es que tu Java no quedó asociado a los
> archivos `.jar`. Abre una terminal en esa carpeta y corre esto una vez:
>
> ```bash
> java -jar mcbackup.jar conectar
> ```
>
> Te va a preguntar la dirección igual, y además te deja un **`conectar.bat`**
> en la carpeta para que de ahí en adelante sí puedas usar doble clic.
>
> Ese `.bat` lo genera tu propia computadora en vez de venir en la descarga,
> por la misma razón que `jugar.bat`: Windows bloquea los scripts bajados de
> internet. Uno creado localmente no lleva esa marca. Y si no tienes Java
> instalado, el `.bat` usa el que ya trae Minecraft.

**Por qué esto arregla algo.** Tu proveedor decide por dónde sale cada rango de
IPs. Algunos mandan ciertos rangos de playit por Estados Unidos aunque el
servidor esté a diez cuadras, y el viaje de ida y vuelta te agrega 180 ms y
cortes. Medido en una línea VTR de Santiago: el mismo servicio da **18 ms por
IPv6 y 196 ms por IPv4** en un dominio, y exactamente al revés en otro. No es tu
wifi ni tu computadora.

Lo importante: **el launcher de Minecraft arranca con
`java.net.preferIPv4Stack=true`, que apaga IPv6 por completo.** Cuando la vía
buena es IPv6, el juego no puede usarla ni pegando la IP a mano. El puente corre
en su propia ventana, sin esa restricción, así que alcanza la ruta que el juego
no alcanza. El salto extra es por dentro de tu máquina: no se nota.

### Si quieres ver los números

```bash
java -jar mcbackup.jar red algo.gl.at.ply.gg
```

Mide lo mismo pero sin abrir el puente, y te nombra el datacenter donde te
ubica tu proveedor (`Santiago_1`, `Miami_1`, ...). Al final imprime una línea
para pegar en el chat: si varios la pegan, se ve de inmediato si el problema es
de un proveedor, del túnel, o de una sola persona.

### Sin puente, a mano

**Si usas el puente, sáltate esto: no hace falta.** Está acá solo para quien
prefiera no dejar una ventana abierta.

En ese caso, y solo si `red` te dice que tu vía buena es IPv6, puedes
habilitarla en el launcher oficial: **Instalaciones → tu perfil → los tres
puntos → Editar → Más opciones → Argumentos de JVM**, y agregas al final de lo
que ya diga:

```
-Djava.net.preferIPv4Stack=false -Djava.net.preferIPv6Addresses=true
```

**Hazlo solo si `red` te lo indicó.** A ciegas puede empeorarte la conexión: hay
direcciones donde IPv6 es justo la vía mala.

### Cuando el arreglo es del anfitrión

Si el diagnóstico dice que el túnel está desviado, no hay nada que puedas hacer
tú: quien hospeda tiene que borrar el túnel en el panel de playit y crear uno
nuevo al 25565. Sale con otra dirección, en otro rango. Es gratis e instantáneo,
y puede hacer falta repetirlo un par de veces.

> **Pagar playit Premium no arregla esto.** Su túnel regional de Sudamérica cae
> en São Paulo: 60 ms medidos desde Santiago, peor que los 12-14 ms que se
> consiguen gratis cuando el túnel queda en un rango bueno. No gasten en eso.
