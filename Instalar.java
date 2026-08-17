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
        if (!passwordRcon.isEmpty()) {
            protegerRcon(destino);
        }

        System.out.println("\n--- Paso 5 de 5: conectar Google Drive ---");
        System.out.println("""
            Ahora se abre el navegador para conectar la cuenta del mundo.

            IMPORTANTE: entra con la cuenta de Google compartida del grupo, NO
            con la tuya personal. Todos los que hostean usan la misma cuenta:
            asi cada uno ve el mundo que subio el anterior.
            """);
        esperarEnter("Cuando estes listo, presiona Enter...");

        AuthSetup.main(new String[0]);

        escribirBackupProperties(destino, passwordRcon);
        regenerarLanzadores(destino);

        System.out.println("""

            ============================================
              Listo. Para jugar, de ahora en mas:

                  java -jar mcbackup.jar host

              (o haz doble clic en jugar.bat)
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
            Para ejecutar un server hay que aceptar el EULA de Minecraft:
            https://aka.ms/MinecraftEULA
            """);

        String respuesta = esperarEnter("Escribe 'acepto' para continuar: ").trim().toLowerCase();
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
                      corromper el mundo. Agrega estas lineas y reinicia el server:
                        enable-rcon=true
                        rcon.port=25575
                        rcon.password=elige-una""");
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
            simulation-distance=10
            server-port=25565

            # Cuantos bytes tiene que pesar un paquete para que el server lo
            # comprima. 256 es el valor de siempre y esta bien: bajarlo a 64
            # ahorra algo de ancho de banda a costa de CPU. Sirve poco y nada
            # si el problema es de ruteo (ahi mide con: mcbackup red).
            network-compression-threshold=256

            # Lista de jugadores permitidos. Empieza apagada para que puedas
            # entrar; para activarla, en la consola del server: whitelist on
            # Se sincroniza con el resto del grupo junto con el mundo.
            white-list=false
            enforce-whitelist=false

            # RCON: lo usa la herramienta de backups para pausar el guardado
            # antes de copiar el mundo. Sin esto los backups pueden salir rotos.
            enable-rcon=true
            rcon.port=25575
            rcon.password=%s
            """.formatted(password));

        System.out.println("  server.properties creado, con RCON activado.");
        return password;
    }

    // -----------------------------------------------------------------------
    // Firewall
    // -----------------------------------------------------------------------

    static final String REGLA_FIREWALL_RCON = "mcbackup - bloquear RCON externo";

    /**
     * Bloquea en el firewall de Windows las conexiones a RCON que no vengan de
     * la propia computadora.
     *
     * RCON escucha en todas las interfaces de red (no solo localhost), asi que
     * sin esto cualquier otra maquina en la misma red podria intentar hablarle.
     * No afecta a mcbackup ni al juego: mcbackup se conecta por 127.0.0.1 (el
     * firewall de Windows no filtra el trafico loopback) y los jugadores entran
     * por el puerto 25565, que esta regla no toca.
     *
     * Si no se puede (hace falta ser administrador), el server funciona
     * exactamente igual: solo queda un poco menos protegido, y se avisa como
     * cerrarlo a mano.
     */
    static void protegerRcon(Path destino) {
        if (!esWindows()) return;   // en Linux/mac esto se resuelve con iptables/pf, fuera de alcance aca

        String puerto = valorDePropiedad(leerSiExiste(destino.resolve("server.properties")), "rcon.port");
        if (puerto.isEmpty()) puerto = "25575";

        System.out.println("\nProtegiendo RCON (puerto " + puerto + ") de conexiones externas...");

        try {
            if (reglaFirewallYaExiste()) {
                System.out.println("  ya estaba protegido.");
                return;
            }

            int codigo = new ProcessBuilder(
                "netsh", "advfirewall", "firewall", "add", "rule",
                "name=" + REGLA_FIREWALL_RCON,
                "dir=in", "action=block", "protocol=TCP", "localport=" + puerto)
                .redirectErrorStream(true)
                .start()
                .waitFor();

            if (codigo == 0) {
                System.out.println("  listo: ya no se puede alcanzar RCON desde otra computadora.");
            } else {
                avisarFirewallManual(puerto);
            }
        } catch (Exception e) {
            avisarFirewallManual(puerto);
        }
    }

    static boolean reglaFirewallYaExiste() throws Exception {
        Process p = new ProcessBuilder(
            "netsh", "advfirewall", "firewall", "show", "rule", "name=" + REGLA_FIREWALL_RCON)
            .redirectErrorStream(true)
            .start();
        String salida = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        p.waitFor();
        return salida.contains(REGLA_FIREWALL_RCON);
    }

    static void avisarFirewallManual(String puerto) {
        System.out.println("""
              No se pudo agregar la regla (hace falta ser administrador).
              El server arranca igual, sin ningun problema: esto solo hace que
              RCON quede alcanzable desde otras computadoras de tu red, ademas
              de la tuya. Para cerrarlo, abre PowerShell como administrador
              (clic derecho en el boton de inicio -> "Terminal (Admin)") y
              pega esto una sola vez:
            """);
        System.out.printf("  netsh advfirewall firewall add rule name=\"%s\" dir=in action=block protocol=TCP localport=%s%n%n",
            REGLA_FIREWALL_RCON, puerto);
    }

    static boolean esWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    static String leerSiExiste(Path archivo) {
        try {
            return Files.exists(archivo) ? Files.readString(archivo) : "";
        } catch (IOException e) {
            return "";
        }
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

            # Tu direccion de playit, la que te da el panel al crear el tunel.
            # Ponla aca y se publica sola cada vez que hosteas: los demas entran
            # con 'mcbackup conectar' sin que les tengas que avisar nada.
            playit.hostname=

            rcon.enabled=true
            rcon.host=127.0.0.1
            rcon.port=25575
            rcon.password=%s
            """.formatted(jugador.trim(), passwordRcon));

        System.out.println("backup.properties creado.");
    }

    // -----------------------------------------------------------------------
    // Lanzadores
    // -----------------------------------------------------------------------

    /**
     * Reescribe jugar.bat y jugar-linux.sh en la carpeta del server.
     *
     * Windows le pone una "marca de la web" a todo lo que sale de un zip
     * descargado, y el Control inteligente de aplicaciones bloquea los scripts
     * que la tienen. Un archivo que se crea localmente no la lleva, asi que
     * generarlo aca deja el lanzador funcionando sin que nadie tenga que
     * desbloquear nada a mano.
     */
    static void regenerarLanzadores(Path destino) throws IOException {
        Files.writeString(destino.resolve("jugar.bat"), String.join("\r\n",
            "@echo off",
            "rem Abre el server. Descarga el mundo antes y lo sube al cerrar.",
            "rem Para cerrar bien, escribe 'stop' en la consola del server.",
            "cd /d \"%~dp0\"",
            "",
            "java -version >nul 2>&1",
            "if errorlevel 1 (",
            "    echo.",
            "    echo No encuentro Java en esta computadora.",
            "    echo Instalalo desde https://adoptium.net y vuelve a intentar.",
            "    echo.",
            "    pause",
            "    exit /b 1",
            ")",
            "",
            "java -jar mcbackup.jar host %*",
            "echo.",
            "pause",
            ""));

        Files.writeString(destino.resolve("jugar-linux.sh"), String.join("\n",
            "#!/bin/sh",
            "# Abre el server. Descarga el mundo antes y lo sube al cerrar.",
            "cd \"$(dirname \"$0\")\" || exit 1",
            "",
            "if ! command -v java >/dev/null 2>&1; then",
            "    echo \"No encuentro Java. Instalalo desde https://adoptium.net\"",
            "    exit 1",
            "fi",
            "",
            "exec java -jar mcbackup.jar host \"$@\"",
            ""));

        try {
            destino.resolve("jugar-linux.sh").toFile().setExecutable(true);
        } catch (SecurityException sinPermisos) {
            // En Windows no aplica y no importa.
        }

        System.out.println("Lanzadores listos: jugar.bat quedo sin la marca de Windows.");
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
