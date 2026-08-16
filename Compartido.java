import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * La configuracion que viaja con el mundo.
 *
 * No alcanza con sincronizar la carpeta del mundo: si cada anfitrion tiene su
 * propia whitelist, los jugadores pueden entrar cuando hostea uno y quedar
 * afuera cuando hostea otro. Lo mismo con los operadores, los baneos y reglas
 * como la dificultad o el PvP.
 *
 * Pero no todo se puede compartir. El puerto, la contrasena de RCON y la IP son
 * de cada maquina: copiarlos romperia la instalacion del que recibe. Por eso hay
 * una lista explicita de que viaja y todo lo demas se queda quieto.
 */
public class Compartido {

    /** Carpeta dentro del zip donde va todo esto. */
    static final String EN_ZIP = "_compartido/";

    /** Archivos que se copian tal cual. */
    static final String[] ARCHIVOS = {
        "whitelist.json",
        "ops.json",
        "banned-players.json",
        "banned-ips.json",
    };

    static final String PROPS_COMPARTIDAS = "server.properties.compartido";

    /**
     * Opciones de server.properties que definen como se juega, y por lo tanto
     * tienen que ser iguales sin importar quien hostee.
     *
     * Todo lo que no este aca es de la maquina: puerto, RCON, IP, memoria.
     */
    static final Set<String> CLAVES = Set.of(
        "motd", "level-name", "gamemode", "difficulty", "hardcore",
        "pvp", "allow-flight", "allow-nether", "spawn-protection",
        "max-players", "online-mode", "white-list", "enforce-whitelist",
        "force-gamemode", "spawn-monsters", "enable-command-block",
        "view-distance", "simulation-distance"
    );

    // -----------------------------------------------------------------------
    // Guardar
    // -----------------------------------------------------------------------

    /** Mete la configuracion compartida dentro del zip del backup. */
    static void agregarAlZip(ZipOutputStream zip, Path servidor) throws IOException {
        for (String nombre : ARCHIVOS) {
            Path archivo = servidor.resolve(nombre);
            if (!Files.exists(archivo)) continue;

            zip.putNextEntry(new ZipEntry(EN_ZIP + nombre));
            Files.copy(archivo, zip);
            zip.closeEntry();
        }

        Path props = servidor.resolve("server.properties");
        if (!Files.exists(props)) return;

        zip.putNextEntry(new ZipEntry(EN_ZIP + PROPS_COMPARTIDAS));
        zip.write(soloLasCompartidas(Files.readAllLines(props)).getBytes());
        zip.closeEntry();
    }

    /** Filtra server.properties dejando unicamente las claves que viajan. */
    static String soloLasCompartidas(List<String> lineas) {
        var salida = new StringBuilder();
        for (String linea : lineas) {
            int igual = linea.indexOf('=');
            if (igual <= 0 || linea.startsWith("#")) continue;

            if (CLAVES.contains(linea.substring(0, igual).trim())) {
                salida.append(linea).append('\n');
            }
        }
        return salida.toString();
    }

    // -----------------------------------------------------------------------
    // Aplicar
    // -----------------------------------------------------------------------

    /**
     * Instala la configuracion que vino en el zip y borra la carpeta temporal.
     *
     * Se llama despues de descomprimir, cuando _compartido/ quedo suelto en la
     * carpeta del server.
     */
    static void aplicar(Path servidor) throws IOException {
        Path origen = servidor.resolve(EN_ZIP.replace("/", ""));
        if (!Files.isDirectory(origen)) return;   // backup viejo, sin esta parte

        var aplicados = new ArrayList<String>();

        for (String nombre : ARCHIVOS) {
            Path archivo = origen.resolve(nombre);
            if (!Files.exists(archivo)) continue;

            Files.copy(archivo, servidor.resolve(nombre), StandardCopyOption.REPLACE_EXISTING);
            aplicados.add(nombre);
        }

        Path props = origen.resolve(PROPS_COMPARTIDAS);
        if (Files.exists(props)) {
            int cambios = fusionarProperties(servidor.resolve("server.properties"),
                                             Files.readAllLines(props));
            if (cambios > 0) aplicados.add(cambios + " opciones del server");
        }

        borrarCarpeta(origen);

        if (!aplicados.isEmpty()) {
            System.out.println("  configuracion del grupo aplicada: " + String.join(", ", aplicados));
        }
    }

    /**
     * Copia las claves compartidas dentro del server.properties local.
     *
     * Se reescribe linea por linea en vez de volcar un archivo nuevo, para no
     * pisar lo que es de esta maquina: el puerto, la contrasena de RCON, la IP.
     */
    static int fusionarProperties(Path destino, List<String> compartidas) throws IOException {
        if (!Files.exists(destino)) return 0;

        var nuevosValores = new java.util.LinkedHashMap<String, String>();
        for (String linea : compartidas) {
            int igual = linea.indexOf('=');
            if (igual > 0) nuevosValores.put(linea.substring(0, igual).trim(), linea.substring(igual + 1));
        }

        var resultado = new ArrayList<String>();
        int cambios = 0;

        for (String linea : Files.readAllLines(destino)) {
            int igual = linea.indexOf('=');
            String clave = (igual > 0 && !linea.startsWith("#")) ? linea.substring(0, igual).trim() : null;

            if (clave != null && nuevosValores.containsKey(clave)) {
                String nueva = clave + "=" + nuevosValores.remove(clave);
                if (!nueva.equals(linea)) cambios++;
                resultado.add(nueva);
            } else {
                resultado.add(linea);
            }
        }

        // Claves que el archivo local no tenia: se agregan al final.
        nuevosValores.forEach((k, v) -> resultado.add(k + "=" + v));
        cambios += nuevosValores.size();

        Files.write(destino, resultado);
        return cambios;
    }

    private static void borrarCarpeta(Path carpeta) throws IOException {
        try (var contenido = Files.walk(carpeta)) {
            for (Path p : contenido.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        }
    }
}
