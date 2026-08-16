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

## Que entren tus amigos

Necesitan tu IP pública y que tu router redirija el puerto **25565**.

**Nunca redirijas el 25575.** Ese es RCON, y da acceso completo a la consola del
server a cualquiera que adivine la contraseña.
