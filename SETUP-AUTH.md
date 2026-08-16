# Registrar el cliente OAuth (solo el dueno del proyecto)

> Si vos vas a **hostear** un server, no necesitas nada de esto: lo tuyo esta
> en el [README](README.md) y son dos comandos. Este documento es para quien
> mantiene el proyecto.

## Una sola vez

Esto se hace **una vez**, no por cada hoster. Despues queda commiteado en
`AuthSetup.java` y nadie mas lo toca.

### 1. Crear el proyecto y habilitar la API

1. Entrar a [console.cloud.google.com](https://console.cloud.google.com) y crear un proyecto.
2. **APIs y servicios -> Biblioteca** -> buscar "Google Drive API" -> **Habilitar**.

### 2. Configurar la pantalla de consentimiento

3. Ir a la pantalla de consentimiento de OAuth (segun la version de la consola
   puede aparecer como "Google Auth Platform" o "Pantalla de consentimiento").
4. Tipo de usuario: **Externo**. Completar nombre de la app y email de soporte.
5. En **Permisos / Scopes**, agregar unicamente:

   ```
   https://www.googleapis.com/auth/drive.file
   ```

### 3. Publicar la app  <- el paso que mas se olvida

6. En estado de publicacion, pasar de **Prueba (Testing)** a **Produccion**.

**Por que importa:** mientras la app este en modo Prueba, Google hace que los
refresh tokens **expiren a los 7 dias**. Un backup automatico andaria una
semana y despues fallaria para todos los hosters al mismo tiempo, sin razon
aparente. Ademas el modo Prueba tiene un tope de 100 usuarios.

### 4. Crear las credenciales

7. **Credenciales -> Crear credenciales -> ID de cliente de OAuth**.
8. Tipo de aplicacion: **App de escritorio** (importante: es lo que permite el
   redirect a `127.0.0.1` en cualquier puerto, que es lo que usa el script).
9. Copiar el **Client ID** y el **Client secret** y pegarlos en `AuthSetup.java`,
   en el bloque `"google"`.

### 5. Compilar y publicar

```bash
git tag v1.0.0
git push --tags
```

El workflow compila el `.jar` y lo sube a Releases. Los hosters lo descargan de
ahi, sin autenticacion de por medio.

---

## Por que `drive.file` y no `drive`

`drive.file` da acceso **solo a los archivos que crea la propia app**. Alcanza
de sobra para subir backups, y es un scope **no sensible**: no dispara el
proceso de verificacion de Google.

El scope `drive` completo, en cambio, es *restringido*: obliga a pasar una
auditoria de seguridad con un tercero, que es cara y lenta. No hay ningun
motivo para pedirlo si lo unico que haces es subir tus propios backups.

## Sobre el client ID compartido

Todos los hosters usan el mismo client ID, y eso esta bien: en apps de
escritorio el client ID no es una credencial secreta, y por eso el flujo usa
PKCE. Es exactamente lo que hacen rclone, gcloud y el `gh` CLI.

Dos consecuencias a tener en cuenta:

- **La cuota de la API es del proyecto, compartida entre todos.** Para unas
  pocas subidas por dia sobra muy holgadamente.
- El client ID identifica a la *aplicacion*, no al usuario. Como la pantalla de
  consentimiento es Externa y esta en Produccion, cualquier cuenta de Google
  puede autorizarla: no hay que registrar un client nuevo por grupo.

## Por que hace falta una cuenta de Google compartida

El scope `drive.file` solo da acceso a los archivos que la app creo **para esa
cuenta**. Si cada jugador entrara con su cuenta personal, cada uno veria unicamente
su propio mundo, y compartir la carpeta no alcanzaria: la app del otro tampoco
podria verla.

Por eso todos los que hostean se autentican con **la misma cuenta del grupo**.
Es lo que hace que el mundo sea uno solo.

La alternativa seria pedir el scope `drive` completo, que si permite carpetas
compartidas — pero es un scope restringido y obliga a la auditoria de seguridad
que justamente estamos evitando.

## Seguridad

El archivo `.auth_tokens.json` que genera el script tiene el refresh token del
hoster. Ya esta en `.gitignore` y el script le pone permisos `600` en Linux.
Nunca debe subirse a ningun repo.
