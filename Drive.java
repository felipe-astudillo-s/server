import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cliente minimo de la API de Google Drive v3.
 *
 * Solo hace lo que el backup necesita: crear la carpeta, subir, listar y
 * borrar. Todo con el scope drive.file, asi que unicamente ve y toca los
 * archivos que esta misma app creo: no tiene forma de tocar el resto del
 * Drive del hoster, ni siquiera por error.
 */
public class Drive {

    private static final String API = "https://www.googleapis.com/drive/v3/files";
    private static final String API_UPLOAD = "https://www.googleapis.com/upload/drive/v3/files";
    private static final String MIME_CARPETA = "application/vnd.google-apps.folder";

    /** Multiplo de 256 KiB, como exige la API para subidas resumibles. */
    private static final int CHUNK = 8 * 1024 * 1024;
    private static final int REINTENTOS = 5;

    private static final HttpClient CLIENTE = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build();

    // -----------------------------------------------------------------------
    // Carpeta
    // -----------------------------------------------------------------------

    /** Busca la carpeta de backups y, si no existe, la crea. */
    public static String buscarOCrearCarpeta(String token, String nombre) throws Exception {
        String consulta = "mimeType='" + MIME_CARPETA + "'"
                        + " and name='" + nombre.replace("'", "\\'") + "'"
                        + " and trashed=false";

        String url = API + "?q=" + URLEncoder.encode(consulta, StandardCharsets.UTF_8)
                   + "&fields=" + URLEncoder.encode("files(id,name)", StandardCharsets.UTF_8);

        String respuesta = pedir(HttpRequest.newBuilder(URI.create(url)).GET(), token);
        List<String[]> encontradas = extraerArchivos(respuesta);
        if (!encontradas.isEmpty()) return encontradas.get(0)[0];

        String cuerpo = "{\"name\":\"" + escapar(nombre) + "\",\"mimeType\":\"" + MIME_CARPETA + "\"}";
        String creada = pedir(
            HttpRequest.newBuilder(URI.create(API + "?fields=id"))
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo)),
            token);

        String id = campo(creada, "id");
        if (id == null) throw new IOException("Drive no devolvio el id de la carpeta: " + creada);
        return id;
    }

    // -----------------------------------------------------------------------
    // Subida resumible
    // -----------------------------------------------------------------------

    /**
     * Sube un archivo con el protocolo resumible de Drive.
     *
     * Se sube por pedazos y, si la conexion se corta, se le pregunta a Google
     * cuanto recibio y se sigue desde ahi. Para un backup de varios cientos de
     * MB en una conexion domestica esto es la diferencia entre terminar o
     * empezar de cero cada vez.
     */
    public static String subir(String token, Path archivo, String carpetaId) throws Exception {
        long total = java.nio.file.Files.size(archivo);
        String nombre = archivo.getFileName().toString();

        // 1. Abrir la sesion de subida.
        String metadatos = "{\"name\":\"" + escapar(nombre) + "\",\"parents\":[\"" + carpetaId + "\"]}";

        HttpResponse<String> inicio = CLIENTE.send(
            HttpRequest.newBuilder(URI.create(API_UPLOAD + "?uploadType=resumable&fields=id"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("X-Upload-Content-Type", "application/zip")
                .header("X-Upload-Content-Length", String.valueOf(total))
                .POST(HttpRequest.BodyPublishers.ofString(metadatos))
                .build(),
            HttpResponse.BodyHandlers.ofString());

        if (inicio.statusCode() >= 400) {
            throw new IOException("No se pudo iniciar la subida (" + inicio.statusCode() + "): " + inicio.body());
        }
        String sesion = inicio.headers().firstValue("Location")
            .orElseThrow(() -> new IOException("Drive no devolvio la URL de sesion"));

        // 2. Mandar los pedazos.
        long offset = 0;
        int fallosSeguidos = 0;

        try (RandomAccessFile raf = new RandomAccessFile(archivo.toFile(), "r")) {
            while (offset < total) {
                int largo = (int) Math.min(CHUNK, total - offset);
                byte[] pedazo = new byte[largo];
                raf.seek(offset);
                raf.readFully(pedazo);

                String rango = "bytes " + offset + "-" + (offset + largo - 1) + "/" + total;

                try {
                    HttpResponse<String> r = CLIENTE.send(
                        HttpRequest.newBuilder(URI.create(sesion))
                            .header("Content-Range", rango)
                            .PUT(HttpRequest.BodyPublishers.ofByteArray(pedazo))
                            .build(),
                        HttpResponse.BodyHandlers.ofString());

                    int codigo = r.statusCode();

                    if (codigo == 200 || codigo == 201) {
                        System.out.printf("  subido 100%%%n");
                        return campo(r.body(), "id");
                    }
                    if (codigo == 308) {                     // pedazo aceptado, falta el resto
                        offset = confirmado(r, offset + largo);
                        fallosSeguidos = 0;
                        System.out.printf("  subido %d%%%n", offset * 100 / total);
                        continue;
                    }
                    if (codigo >= 500) {                     // error temporal de Google: se reintenta
                        throw new IOException("respuesta " + codigo);
                    }
                    // Un 4xx no se arregla reintentando (token vencido, cuota llena,
                    // sesion expirada). Va como RuntimeException para saltear el
                    // catch de reintentos y cortar con el motivo real.
                    throw new ErrorFatal("Drive rechazo la subida (" + codigo + "): " + r.body());

                } catch (IOException | InterruptedException e) {
                    if (++fallosSeguidos > REINTENTOS) {
                        throw new IOException("Se corto la subida y no se pudo retomar: " + e.getMessage(), e);
                    }
                    System.out.println("  se corto, preguntando cuanto llego...");
                    offset = consultarProgreso(sesion, total);
                }
            }
        }
        return null;
    }

    /** Lee el header Range de un 308 para saber hasta donde llego Google. */
    private static long confirmado(HttpResponse<String> r, long porDefecto) {
        return r.headers().firstValue("Range")
            .map(h -> Long.parseLong(h.substring(h.indexOf('-') + 1)) + 1)
            .orElse(porDefecto);
    }

    /** Le pregunta a Drive cuantos bytes tiene, para retomar desde ahi. */
    private static long consultarProgreso(String sesion, long total) throws Exception {
        HttpResponse<String> r = CLIENTE.send(
            HttpRequest.newBuilder(URI.create(sesion))
                .header("Content-Range", "bytes */" + total)
                .PUT(HttpRequest.BodyPublishers.noBody())
                .build(),
            HttpResponse.BodyHandlers.ofString());

        if (r.statusCode() == 200 || r.statusCode() == 201) return total;   // ya estaba completa
        return confirmado(r, 0);
    }

    // -----------------------------------------------------------------------
    // Listado y borrado
    // -----------------------------------------------------------------------

    /** Backups de la carpeta, del mas viejo al mas nuevo. Cada item: {id, nombre}. */
    public static List<String[]> listar(String token, String carpetaId) throws Exception {
        String consulta = "'" + carpetaId + "' in parents and trashed=false";
        String url = API + "?q=" + URLEncoder.encode(consulta, StandardCharsets.UTF_8)
                   + "&orderBy=createdTime"
                   + "&pageSize=1000"
                   + "&fields=" + URLEncoder.encode("files(id,name)", StandardCharsets.UTF_8);

        return extraerArchivos(pedir(HttpRequest.newBuilder(URI.create(url)).GET(), token));
    }

    public static void borrar(String token, String archivoId) throws Exception {
        pedir(HttpRequest.newBuilder(URI.create(API + "/" + archivoId)).DELETE(), token);
    }

    // -----------------------------------------------------------------------
    // Utilidades
    // -----------------------------------------------------------------------

    private static String pedir(HttpRequest.Builder builder, String token) throws Exception {
        HttpResponse<String> r = CLIENTE.send(
            builder.header("Authorization", "Bearer " + token).build(),
            HttpResponse.BodyHandlers.ofString());

        if (r.statusCode() >= 400) {
            throw new IOException("Drive respondio " + r.statusCode() + ": " + r.body());
        }
        return r.body();
    }

    /**
     * Saca los pares id/nombre de una respuesta de listado.
     *
     * Se puede parsear con una expresion regular porque el 'fields' de la
     * consulta fija la forma exacta de la respuesta: objetos con id y nombre y
     * nada mas. Asi el proyecto sigue sin dependencias externas.
     */
    static List<String[]> extraerArchivos(String json) {
        var resultado = new ArrayList<String[]>();
        Matcher m = Pattern.compile(
            "\\{\\s*\"id\"\\s*:\\s*\"([^\"]*)\"\\s*,\\s*\"name\"\\s*:\\s*\"([^\"]*)\"\\s*\\}").matcher(json);
        while (m.find()) {
            resultado.add(new String[]{m.group(1), m.group(2)});
        }
        return resultado;
    }

    static String campo(String json, String clave) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(clave) + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        return m.find() ? m.group(1) : null;
    }

    static String escapar(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** Error que no tiene sentido reintentar. */
    static class ErrorFatal extends RuntimeException {
        private static final long serialVersionUID = 1L;

        ErrorFatal(String mensaje) {
            super(mensaje);
        }
    }
}
