import java.io.IOException;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Hace un backup del mundo y lo sube a Google Drive.
 *
 * El orden importa: se le pide al server que deje de escribir, se comprime, se
 * le devuelve el control lo antes posible, y recien despues se sube. Asi el
 * server queda con el guardado desactivado los segundos que dura el zip, y no
 * los minutos que puede durar una subida.
 */
public class Backup {

    static final Path CONFIG = Path.of("backup.properties");
    static final String PREFIJO = "backup-";
    // Con segundos: sin ellos, dos backups en el mismo minuto quedan con el
    // mismo nombre y despues no hay forma de distinguirlos al restaurar.
    static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss");

    // Lo bloquea el proceso del server en Windows y no aporta nada al backup.
    static final String ARCHIVO_IGNORADO = "session.lock";

    public static void main(String[] args) throws Exception {
        hacer(cargarConfig(), List.of(args).contains("--forzar"));
    }

    /** El backup en si. Lo reutiliza 'host' para subir el mundo al cerrar. */
    public static void hacer(Properties cfg) throws Exception {
        hacer(cfg, false);
    }

    /**
     * Verifica que el mundo local siga siendo el mas nuevo antes de subirlo.
     *
     * En el modelo rotativo, tu copia queda vieja apenas otro jugador hostea.
     * Subirla la convertiria en el ultimo backup y el proximo que juegue
     * arrancaria desde ahi, perdiendo la partida del que jugo en el medio.
     */
    static void exigirMundoAlDia(String token, String carpetaId, Path servidor) throws Exception {
        List<String[]> backups = Drive.listar(token, carpetaId).stream()
            .filter(a -> a[1].startsWith(PREFIJO) && a[1].endsWith(".zip"))
            .toList();

        if (backups.isEmpty()) return;   // primer backup del mundo: nada que pisar

        String masNuevo = backups.get(backups.size() - 1)[0];
        String origen = Mundo.origenLocal(servidor);

        if (masNuevo.equals(origen)) return;   // nuestro mundo desciende del ultimo

        AuthSetup.exit("""

            El mundo local NO esta al dia: en Drive hay uno mas nuevo (%s).

            Si subis este, el proximo que juegue va a arrancar desde tu copia
            vieja y se pierde lo que jugo el ultimo. Para jugar con el mundo
            actualizado:
                java -jar mcbackup.jar host

            Si de verdad quieres subir esta copia igual, sabiendo que pisa a la
            otra:
                java -jar mcbackup.jar backup --forzar
            """.formatted(backups.get(backups.size() - 1)[1]));
    }

    public static void hacer(Properties cfg, boolean forzar) throws Exception {
        Instant arranque = Instant.now();

        Path servidor = Path.of(cfg.getProperty("server.dir", "."));
        List<Path> mundos = mundosExistentes(servidor, cfg.getProperty("world.folders", "world"));

        if (mundos.isEmpty()) {
            AuthSetup.exit("No se encontro ninguna carpeta de mundo en " + servidor.toAbsolutePath()
                         + "\nRevisa 'server.dir' y 'world.folders' en " + CONFIG);
        }

        System.out.println("Autenticando con Google Drive...");
        String token = AuthSetup.getAccessToken();
        String carpeta = Drive.buscarOCrearCarpeta(token, cfg.getProperty("drive.folder", "Minecraft Backups"));

        // Antes de comprimir nada: si alguien jugo despues que nosotros, subir
        // nuestra copia la pondria como la mas nueva y borraria su partida.
        if (!forzar) exigirMundoAlDia(token, carpeta, servidor);

        Path zip = Path.of(cfg.getProperty("temp.dir", "."))
                       .resolve(PREFIJO + LocalDateTime.now().format(FECHA) + ".zip");

        comprimirConServerQuieto(cfg, mundos, zip);

        try {
            System.out.printf("Subiendo %s (%s)...%n", zip.getFileName(), tamano(Files.size(zip)));
            String nuevoId = Drive.subir(token, zip, carpeta);
            Mundo.anotarOrigen(servidor, nuevoId, zip.getFileName().toString());

            aplicarRetencion(token, carpeta, Integer.parseInt(cfg.getProperty("retention", "7").trim()));
        } finally {
            Files.deleteIfExists(zip);
        }

        System.out.printf("%nBackup completo en %d segundos.%n",
                          Duration.between(arranque, Instant.now()).toSeconds());
    }

    // -----------------------------------------------------------------------
    // Comprimir con el server en pausa de guardado
    // -----------------------------------------------------------------------

    static void comprimirConServerQuieto(Properties cfg, List<Path> mundos, Path zip) throws Exception {
        Path servidor = Path.of(cfg.getProperty("server.dir", "."));
        boolean usarRcon = Boolean.parseBoolean(cfg.getProperty("rcon.enabled", "false").trim());

        if (!usarRcon) {
            System.out.println("Comprimiendo (RCON desactivado)...");
            comprimir(mundos, zip, servidor);
            System.out.println("  ojo: sin RCON el mundo se copia mientras el server escribe.");
            System.out.println("  Para un backup consistente, activa rcon.enabled en " + CONFIG);
            return;
        }

        Rcon rcon;
        try {
            rcon = new Rcon(cfg.getProperty("rcon.host", "127.0.0.1"),
                            Integer.parseInt(cfg.getProperty("rcon.port", "25575").trim()),
                            cfg.getProperty("rcon.password", ""));
        } catch (ConnectException | SocketTimeoutException apagado) {
            // No llegamos al server. Lo mas probable es que este apagado, y con
            // el server apagado nadie escribe el mundo: copiar es seguro.
            // Una contrasena mal puesta no cae aca: esa si corta el backup.
            System.out.println("El server no responde por RCON, parece estar apagado.");
            System.out.println("Se comprime igual: apagado, el mundo no se esta escribiendo.");
            comprimir(mundos, zip, servidor);
            return;
        }

        try (rcon) {
            try {
                System.out.println("Pausando el guardado del server...");
                rcon.comando("save-off");
                rcon.comando("save-all flush");

                System.out.println("Comprimiendo el mundo...");
                comprimir(mundos, zip, servidor);
            } finally {
                // Pase lo que pase, el server tiene que volver a guardar. Si esto
                // no corriera, el mundo dejaria de persistir sin ningun aviso.
                rcon.comando("save-on");
                System.out.println("Guardado reactivado.");
            }
        }
    }

    static void comprimir(List<Path> mundos, Path destino) throws IOException {
        comprimir(mundos, destino, null);
    }

    /**
     * @param servidor si no es null, ademas del mundo se guarda la configuracion
     *                 del grupo (whitelist, ops, baneos y reglas de juego).
     */
    static void comprimir(List<Path> mundos, Path destino, Path servidor) throws IOException {
        try (OutputStream os = Files.newOutputStream(destino);
             ZipOutputStream zip = new ZipOutputStream(os)) {

            if (servidor != null) Compartido.agregarAlZip(zip, servidor);

            for (Path mundo : mundos) {
                Path raiz = mundo.getParent() == null ? mundo : mundo.getParent();

                try (Stream<Path> archivos = Files.walk(mundo)) {
                    for (Path archivo : (Iterable<Path>) archivos.filter(Files::isRegularFile)::iterator) {
                        if (archivo.getFileName().toString().equals(ARCHIVO_IGNORADO)) continue;

                        zip.putNextEntry(new ZipEntry(raiz.relativize(archivo).toString().replace('\\', '/')));
                        try {
                            Files.copy(archivo, zip);
                        } catch (IOException e) {
                            // Un archivo suelto bloqueado no debe tirar abajo el backup entero.
                            System.out.println("  no se pudo leer, se omite: " + archivo.getFileName());
                        }
                        zip.closeEntry();
                    }
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Retencion
    // -----------------------------------------------------------------------

    /** Deja los N backups mas nuevos y borra el resto. */
    static void aplicarRetencion(String token, String carpetaId, int conservar) throws Exception {
        List<String[]> archivos = Drive.listar(token, carpetaId);

        // Doble red de seguridad: solo se borran los que esta herramienta creo.
        // El scope drive.file ya impide ver nada ajeno, pero el nombre confirma.
        List<String[]> propios = archivos.stream()
            .filter(a -> a[1].startsWith(PREFIJO) && a[1].endsWith(".zip"))
            .toList();

        int sobran = propios.size() - conservar;
        if (sobran <= 0) {
            System.out.printf("Hay %d backup%s guardado%s (se conservan %d).%n",
                              propios.size(), propios.size() == 1 ? "" : "s",
                              propios.size() == 1 ? "" : "s", conservar);
            return;
        }

        // El listado viene del mas viejo al mas nuevo, asi que los que sobran van primero.
        for (int i = 0; i < sobran; i++) {
            System.out.println("Borrando backup viejo: " + propios.get(i)[1]);
            Drive.borrar(token, propios.get(i)[0]);
        }
    }

    // -----------------------------------------------------------------------
    // Configuracion
    // -----------------------------------------------------------------------

    static Properties cargarConfig() throws IOException {
        if (!Files.exists(CONFIG)) {
            Files.writeString(CONFIG, """
                # Configuracion de los backups.

                # Tu nombre, para que los demas sepan quien tiene el mundo.
                player.name=

                # Carpeta donde vive el server (donde esta server.jar).
                server.dir=.

                # Archivo del server y memoria a asignarle.
                server.jar=server.jar
                server.ram=4G

                # Carpetas de mundo a respaldar, separadas por coma.
                # Vanilla usa solo 'world'; Paper y Spigot separan las dimensiones.
                # Las que no existan se ignoran solas.
                world.folders=world,world_nether,world_the_end

                # Nombre de la carpeta en tu Google Drive.
                drive.folder=Minecraft Backups

                # Cuantos backups conservar. Los mas viejos se borran solos.
                retention=7

                # Donde dejar el zip temporal mientras se sube.
                temp.dir=.

                # Tu direccion de playit, la que le pasas a los demas.
                # Solo la usa 'mcbackup red' para no tener que escribirla cada
                # vez que midas la conexion. El server no la necesita.
                playit.hostname=

                # --- RCON -------------------------------------------------------
                # Muy recomendado. Sin esto el mundo se copia mientras el server
                # escribe, y el backup puede salir corrupto.
                #
                # Para activarlo, en server.properties del server poner:
                #   enable-rcon=true
                #   rcon.port=25575
                #   rcon.password=algo-dificil
                # y despues reiniciar el server.
                rcon.enabled=false
                rcon.host=127.0.0.1
                rcon.port=25575
                rcon.password=
                """);

            AuthSetup.exit("\nCree " + CONFIG + " con valores por defecto."
                         + "\nRevisalo y vuelve a ejecutar el backup.\n");
        }

        var props = new Properties();
        try (var in = Files.newInputStream(CONFIG)) {
            props.load(in);
        }
        return props;
    }

    static List<Path> mundosExistentes(Path servidor, String lista) {
        var encontrados = new ArrayList<Path>();
        for (String nombre : lista.split(",")) {
            Path p = servidor.resolve(nombre.trim());
            if (Files.isDirectory(p)) encontrados.add(p);
        }
        return encontrados;
    }

    static String tamano(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
