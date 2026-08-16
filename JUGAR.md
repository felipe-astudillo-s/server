# Jugar

> ¿Es la primera vez en esta computadora? Empezá por el [README](README.md).

## Abrir el server

Doble click en **`jugar.bat`**.

Eso es todo. Por detrás hace cinco cosas:

1. **Reserva el mundo**, para que nadie más lo levante mientras jugás
2. **Baja el mundo más reciente** desde Drive
3. **Abre el server**
4. Cuando cerrás, **sube el mundo** con tus cambios
5. **Libera la reserva** para el siguiente

## Cerrar bien

**Escribí `stop` en la consola del server.**

No cierres la ventana con la X ni con Ctrl+C. Si lo hacés, el mundo **no se
sube**: tus cambios quedan solo en tu computadora y el próximo que juegue va a
arrancar desde antes de tu partida.

Si igual llegara a pasar, el programa te avisa y libera la reserva para que
nadie quede trabado.

## Ver si el mundo está libre

```bash
java -jar mcbackup.jar estado
```

Te dice si está libre o quién lo está usando y desde cuándo.

---

# Cuando algo no sale

## "El mundo lo esta hosteando <fulano>"

Alguien está jugando ahora. Esperá a que cierre.

Si estás **seguro** de que no está jugando — se le cortó la luz, cerró mal —
podés liberarlo a la fuerza:

```bash
java -jar mcbackup.jar host --forzar
```

> Cuidado: si forzás mientras esa persona *sí* está jugando, van a quedar dos
> mundos distintos y el que suba último pisa al otro. Los mundos de Minecraft
> no se pueden fusionar: lo que se pierde, se pierde. **Preguntá antes.**

Las reservas de más de 12 horas se liberan solas.

## "Ya hay un server usando el mundo 'world'"

Quedó un server corriendo en tu propia máquina, normalmente porque se cerró la
ventana sin escribir `stop`. Apagalo bien:

```bash
java -jar mcbackup.jar detener
```

Guarda el mundo y lo cierra como corresponde. Después ya podés jugar.

## "El mundo local NO esta al dia"

Aparece si intentás subir un backup manual cuando alguien jugó después que vos.
Tu copia quedó vieja y subirla borraría la partida del otro.

Lo correcto es simplemente jugar, que baja el mundo actualizado:

```bash
java -jar mcbackup.jar host
```

## El server no arranca

Si cierra a los pocos segundos, el programa lo detecta y **no sube nada**, para
no reemplazar el mundo bueno que hay en Drive. Mirá el error que quedó en
pantalla: casi siempre es falta de memoria (bajá `server.ram` en
`backup.properties`) o un mod incompatible.

---

# Recuperar una partida

## Versiones anteriores en Drive

```bash
java -jar mcbackup.jar list
```

Se guardan las últimas 7 partidas. Para volver a una:

1. Entrá al Drive de la cuenta del grupo, carpeta *Minecraft Backups*
2. Descargá el `.zip` que querés
3. **Avisale al grupo** — vas a hacer retroceder el mundo para todos
4. Con el server cerrado, renombrá tu carpeta `world` y descomprimí el zip
5. Jugá una partida con `jugar.bat` y cerrá con `stop`: eso sube esa versión
   como la más nueva

## Tu copia local

Cada vez que bajás un mundo, el que tenías se guarda como
`world.anterior-<fecha>` en tu carpeta. **Nunca se borra nada.**

Si cerraste mal y perdiste tu partida, está ahí. Se van acumulando, así que
podés borrar las viejas cuando confirmes que el mundo actual está bien.

---

# Configuración

En `backup.properties`:

| Opción | Para qué sirve |
|---|---|
| `player.name` | Tu nombre, para que los demás sepan quién tiene el mundo |
| `server.ram` | Memoria del server. `4G` va bien con 8 GB en la máquina |
| `retention` | Cuántas partidas guardar en Drive |

## Que entren tus amigos

Necesitan tu IP pública y que tu router redirija el puerto **25565**.

**Nunca redirijas el 25575.** Ese es RCON, y da acceso completo a la consola
del server a cualquiera que adivine la contraseña.
