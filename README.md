# Server de Minecraft compartido

Un mundo, varios anfitriones. Cualquiera del grupo puede levantar el server: el
mundo se baja solo antes de jugar y se sube solo al cerrar, así todos siguen
siempre desde donde quedó el anterior.

Reemplaza a mcsync. No hace falta rclone, ni Python, ni crear nada en consolas
de Google.

## Requisitos

Java 17 o superior. Bajalo de [adoptium.net](https://adoptium.net) si no lo tenés.

## Primera vez

1. Descargá `mcbackup.zip` de [Releases](../../releases) y descomprimilo en una
   carpeta vacía.
2. Doble click en **`instalar.bat`** (o `./instalar.sh` en Linux/Mac).

El instalador baja el servidor Fabric, los mods de rendimiento, te pide aceptar
el EULA de Minecraft y conecta Google Drive.

> **Importante:** cuando se abra el navegador, entrá con la **cuenta de Google
> compartida del grupo**, no con la tuya personal. Todos los que hostean usan la
> misma cuenta — es lo que hace que cada uno vea el mundo que dejó el anterior.

## Para jugar

Doble click en **`jugar.bat`**. Eso hace todo:

1. Reserva el mundo, para que nadie más lo levante mientras jugás
2. Baja el mundo más reciente desde Drive
3. Abre el server
4. Cuando cerrás, sube el mundo con tus cambios
5. Libera la reserva

**Para cerrar, escribí `stop` en la consola del server.** No cierres la ventana
con la X ni con Ctrl+C: si lo hacés, el mundo no se sube y tus cambios quedan
solo en tu máquina.

## Comandos

| Comando | Qué hace |
|---|---|
| `java -jar mcbackup.jar host` | Jugar (lo que hace `jugar.bat`) |
| `java -jar mcbackup.jar estado` | Ver si alguien está hosteando ahora |
| `java -jar mcbackup.jar list` | Ver los mundos guardados en Drive |
| `java -jar mcbackup.jar backup` | Subir un backup sin abrir el server |

## Si alguien deja el mundo trabado

Si a quien estaba hosteando se le cortó la luz, la reserva puede quedar tomada.
Primero fijate quién la tiene:

```bash
java -jar mcbackup.jar estado
```

Si confirmaste que esa persona **no** está jugando, podés liberarla:

```bash
java -jar mcbackup.jar host --forzar
```

Las reservas de más de 12 horas se liberan solas.

> Ojo con esto: si forzás mientras alguien realmente está jugando, van a quedar
> dos mundos distintos y el que suba último pisa al otro. Los mundos de
> Minecraft no se pueden fusionar — lo que se pierde, se pierde. Preguntá antes.

## Configuración

Está en `backup.properties`, que crea el instalador:

| Opción | Para qué sirve |
|---|---|
| `player.name` | Tu nombre, para que los demás sepan quién tiene el mundo |
| `server.ram` | Memoria del server. `4G` está bien para 8 GB de RAM |
| `retention` | Cuántas versiones guardar en Drive. Las viejas se borran solas |
| `rcon.*` | Lo usa la herramienta para pausar el guardado antes de copiar |

## Cómo se cuidan tus datos

**El mundo local nunca se borra.** Cuando se baja uno de Drive, el que tenías se
renombra a `world.anterior-<fecha>`. Si algo sale mal, tu partida sigue ahí. Con
el tiempo se acumulan y los podés borrar a mano.

**Los backups se hacen con el guardado pausado.** Antes de comprimir, se le pide
al server que deje de escribir el mundo (por RCON) y se reactiva enseguida. Sin
eso, un backup tomado a mitad de una escritura puede quedar corrupto.

**Las subidas se reanudan.** Si se corta internet a mitad de camino, retoma desde
donde quedó en vez de empezar de cero.

## Preguntas

**¿Puedo jugar sin internet?** No con `jugar.bat`, porque necesita Drive para
reservar y bajar el mundo. Podés arrancar el server a mano, pero después nadie
más va a tener tus cambios.

**¿Y si dos empezamos a la vez?** La reserva lo impide: el segundo recibe un
aviso de quién está hosteando.

**¿Dónde quedan mis credenciales?** En `.auth_tokens.json`, en tu carpeta. No lo
compartas ni lo subas a ningún lado.

**¿Cuánto ocupa en Drive?** Un mundo comprimido va de decenas de MB a un par de
GB. Con `retention=7` guardás siete versiones.
