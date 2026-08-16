import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deja un server de Minecraft listo para jugar, partiendo de una carpeta vacia.
 *
 * Baja Fabric, los mods de rendimiento, configura el server y conecta Drive.
 * El que hostea por primera vez corre esto y no toca nada mas.
 */
public class Instalar {

    static final String META_FABRIC = "https://meta.fabricmc.net/v2/versions";
    static final String API_MODRINTH = "https://api.modrinth.com/v2/project";

    /** Mods de rendimiento: no cambian el juego, solo lo hacen andar mejor. */
    static final String[][] MODS = {
        {"lithium",     "optimiza el tick sin cambiar el comportamiento vanilla"},
        {"ferrite-core", "reduce el uso de memoria"},
        {"krypton",     "optimiza la pila de red"},
    };

    static final HttpClient CLIENTE = HttpClient.newHttpClient();

    public static void main(String[] args) throws Exception {
        Path destino = Path.of(".");
        String versionPedida = valorDeArgumento(args, "--mc");

        System.out.println("""

            ============================================
              Instalacion del server de Minecraft
            ============================================
            """);

        String mc = versionPedida != null ? versionPedida : ultimaEstable("game");
        String loader = ultimaEstable("loader");
        String instalador = ultimaEstable("installer");

        System.out.println("--- Paso 1 de 5: servidor Fabric ---");
        System.out.printf("Minecraft %s | Fabric loader %s%n", mc, loader);
        bajar(META_FABRIC + "/loader/" + mc + "/" + loader + "/" + instalador + "/server/jar",
              destino.resolve("server.jar"));
        System.out.println("  server.jar listo.\n");

        System.out.println("--- Paso 2 de 5: mods de rendimiento ---");
        instalarMods(destino, mc);

        System.out.println("\n--- Paso 3 de 5: EULA de Minecraft ---");
        aceptarEula(destino);

        System.out.println("\n--- Paso 4 de 5: configuracion del server ---");
        String passwordRcon = configurarServidor(destino);

        System.out.println("\n--- Paso 5 de 5: conectar Google Drive ---");
        System.out.println("""
            Ahora se abre el navegador para conectar la cuenta del mundo.

            IMPORTANTE: entra con la cuenta de Google compartida del grupo, NO
            con la tuya personal. Todos los que hostean usan la misma cuenta:
            asi cada uno ve el mundo que subio el anterior.
            """);
        esperarEnter("Cuando estes listo, apreta Enter...");

        AuthSetup.main(new String[0]);

        escribirBackupProperties(destino, passwordRcon);

        System.out.println("""

            ============================================
              Listo. Para jugar, de ahora en mas:

                  java -jar mcbackup.jar host

              (o hace doble click en jugar.bat)
            ============================================
            """);
    }

    // -----------------------------------------------------------------------
    // Fabric
    // -----------------------------------------------------------------------

    /** Primera version marcada como estable en el listado de Fabric. */
    static String ultimaEstable(String tipo) throws Exception {
        String json = texto(META_FABRIC + "/" + tipo);

        // El listado viene de mas nueva a mas vieja: la primera estable sirve.
        // Fabric devuelve el JSON indentado, asi que hay que tolerar espacios
        // alrededor de los dos puntos.
        Pattern esEstable = Pattern.compile("\"stable\"\\s*:\\s*true");
        Matcher objetos = Pattern.compile("\\{[^{}]*\\}").matcher(json);

        while (objetos.find()) {
            String obj = objetos.group();
            if (esEstable.matcher(obj).find()) {
                String v = Drive.campo(obj, "version");
                if (v != null) return v;
            }
        }
        throw new IOException("Fabric no devolvio ninguna version estable de " + tipo);
    }

    // -----------------------------------------------------------------------
    // Mods
    // -----------------------------------------------------------------------

    static void instalarMods(Path destino, String mc) throws Exception {
        Path mods = destino.resolve("mods");
        Files.createDirectories(mods);

        for (String[] mod : MODS) {
            try {
                String url = urlDelMod(mod[0], mc);
                // El nombre viene url-encodeado (el '+' de las versiones sale
                // como %2B): sin decodificar, el archivo queda con basura.
                String nombre = java.net.URLDecoder.decode(
                    url.substring(url.lastIndexOf('/') + 1), StandardCharsets.UTF_8);
                bajar(url, mods.resolve(nombre));
                System.out.printf("  %s - %s%n", mod[0], mod[1]);
            } catch (Exception e) {
                // Un mod que todavia no salio para esta version no debe frenar
                // la instalacion: el server anda igual, solo un poco mas lento.
                System.out.printf("  %s no esta disponible para %s todavia, se omite.%n", mod[0], mc);
            }
        }
    }

    static String urlDelMod(String slug, String mc) throws Exception {
        String consulta = API_MODRINTH + "/" + slug + "/version"
                        + "?loaders=" + URLEncoder.encode("[\"fabric\"]", StandardCharsets.UTF_8)
                        + "&game_versions=" + URLEncoder.encode("[\"" + mc + "\"]", StandardCharsets.UTF_8);

        String json = texto(consulta);
        Matcher m = Pattern.compile("\"url\"\\s*:\\s*\"(https://cdn\\.modrinth\\.com/[^\"]+\\.jar)\"").matcher(json);
        if (!m.find()) throw new IOException("sin version compatible");
        return m.group(1);
    }

    // -----------------------------------------------------------------------
    // EULA y configuracion
    // -----------------------------------------------------------------------

    static void aceptarEula(Path destino) throws Exception {
        Path eula = destino.resolve("eula.txt");
        if (Files.exists(eula) && Files.readString(eula).contains("eula=true")) {
            System.out.println("  El EULA ya estaba aceptado.");
            return;
        }

        System.out.println("""
            Para correr un server hay que aceptar el EULA de Minecraft:
            https://aka.ms/MinecraftEULA
            """);

        String respuesta = esperarEnter("Escribi 'acepto' para continuar: ").trim().toLowerCase();
        if (!respuesta.equals("acepto")) {
            AuthSetup.exit("No se acepto el EULA. La instalacion queda a medias.");
        }

        Files.writeString(eula, "# Aceptado durante la instalacion\neula=true\n");
        System.out.println("  EULA aceptado.");
    }

    /** Crea server.properties si no existe. Devuelve la password de RCON. */
    static String configurarServidor(Path destino) throws Exception {
        Path props = destino.resolve("server.properties");

        if (Files.exists(props)) {
            String contenido = Files.readString(props);
            String password = valorDePropiedad(contenido, "rcon.password");

            if (!contenido.contains("enable-rcon=true") || password.isEmpty()) {
                System.out.println("""
                      Ya existe un server.properties, no lo toco.
                      Pero le falta RCON, que es lo que permite hacer backups sin
                      corromper el mundo. Agregale estas lineas y reinicia el server:
                        enable-rcon=true
                        rcon.port=25575
                        rcon.password=elegi-una""");
                return password;
            }
            System.out.println("  server.properties ya estaba configurado, con RCON activo.");
            return password;
        }

        String password = passwordAlAzar();
        Files.writeString(props, """
            # Generado por el instalador.
            motd=Server del grupo
            max-players=10
            online-mode=true
            difficulty=normal
            view-distance=10
            server-port=25565

            # RCON: lo usa la herramienta de backups para pausar el guardado
            # antes de copiar el mundo. Sin esto los backups pueden salir rotos.
            enable-rcon=true
            rcon.port=25575
            rcon.password=%s
            """.formatted(password));

        System.out.println("  server.properties creado, con RCON activado.");
        return password;
    }

    static void escribirBackupProperties(Path destino, String passwordRcon) throws Exception {
        Path config = destino.resolve("backup.properties");
        if (Files.exists(config)) {
            System.out.println("\nbackup.properties ya existia, no lo toco.");
            return;
        }

        System.out.print("\nTu nombre (para que los demas sepan quien tiene el mundo): ");
        String jugador = leerLinea();
        if (jugador.isBlank()) jugador = System.getProperty("user.name", "alguien");

        Files.writeString(config, """
            # Generado por el instalador.

            player.name=%s

            server.dir=.
            server.jar=server.jar
            server.ram=4G

            world.folders=world

            drive.folder=Minecraft Backups
            retention=7
            temp.dir=.

            rcon.enabled=true
            rcon.host=127.0.0.1
            rcon.port=25575
            rcon.password=%s
            """.formatted(jugador.trim(), passwordRcon));

        System.out.println("backup.properties creado.");
    }

    // -----------------------------------------------------------------------
    // Utilidades
    // -----------------------------------------------------------------------

    static void bajar(String url, Path destino) throws Exception {
        HttpResponse<Path> r = CLIENTE.send(
            HttpRequest.newBuilder(URI.create(url)).GET().build(),
            HttpResponse.BodyHandlers.ofFile(destino));

        if (r.statusCode() >= 400) {
            throw new IOException("No se pudo descargar " + url + " (" + r.statusCode() + ")");
        }
    }

    static String texto(String url) throws Exception {
        HttpResponse<String> r = CLIENTE.send(
            HttpRequest.newBuilder(URI.create(url)).GET().build(),
            HttpResponse.BodyHandlers.ofString());

        if (r.statusCode() >= 400) {
            throw new IOException("Respuesta " + r.statusCode() + " de " + url);
        }
        return r.body();
    }

    static String passwordAlAzar() {
        byte[] bytes = new byte[18];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String valorDePropiedad(String contenido, String clave) {
        Matcher m = Pattern.compile("(?m)^" + Pattern.quote(clave) + "=(.*)$").matcher(contenido);
        return m.find() ? m.group(1).trim() : "";
    }

    static String valorDeArgumento(String[] args, String nombre) {
        List<String> lista = new ArrayList<>(List.of(args));
        int i = lista.indexOf(nombre);
        return (i >= 0 && i + 1 < lista.size()) ? lista.get(i + 1) : null;
    }

    static String esperarEnter(String mensaje) throws IOException {
        System.out.print(mensaje);
        return leerLinea();
    }

    // Uno solo para todo el proceso: si se crea un BufferedReader por cada
    // pregunta, el primero se lleva lo que el usuario ya tipeo y el siguiente
    // lee vacio.
    private static final BufferedReader CONSOLA =
        new BufferedReader(new InputStreamReader(System.in));

    static String leerLinea() throws IOException {
        String linea = CONSOLA.readLine();
        return linea == null ? "" : linea;
    }
}
