import java.io.IOException;
import java.io.OutputStream;
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
        Instant arranque = Instant.now();

        Properties cfg = cargarConfig();
        Path servidor = Path.of(cfg.getProperty("server.dir", "."));
        List<Path> mundos = mundosExistentes(servidor, cfg.getProperty("world.folders", "world"));

        if (mundos.isEmpty()) {
            AuthSetup.exit("No se encontro ninguna carpeta de mundo en " + servidor.toAbsolutePath()
                         + "\nRevisa 'server.dir' y 'world.folders' en " + CONFIG);
        }

        System.out.println("Autenticando con Google Drive...");
        String token = AuthSetup.getAccessToken();

        Path zip = Path.of(cfg.getProperty("temp.dir", "."))
                       .resolve(PREFIJO + LocalDateTime.now().format(FECHA) + ".zip");

        comprimirConServerQuieto(cfg, mundos, zip);

        try {
            System.out.printf("Subiendo %s (%s)...%n", zip.getFileName(), tamano(Files.size(zip)));
            String carpeta = Drive.buscarOCrearCarpeta(token, cfg.getProperty("drive.folder", "Minecraft Backups"));
            Drive.subir(token, zip, carpeta);

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
        boolean usarRcon = Boolean.parseBoolean(cfg.getProperty("rcon.enabled", "false").trim());

        if (!usarRcon) {
            System.out.println("Comprimiendo (RCON desactivado)...");
            comprimir(mundos, zip);
            System.out.println("  ojo: sin RCON el mundo se copia mientras el server escribe.");
            System.out.println("  Para un backup consistente, activa rcon.enabled en " + CONFIG);
            return;
        }

        try (Rcon rcon = new Rcon(cfg.getProperty("rcon.host", "127.0.0.1"),
                                  Integer.parseInt(cfg.getProperty("rcon.port", "25575").trim()),
                                  cfg.getProperty("rcon.password", ""))) {
            try {
                System.out.println("Pausando el guardado del server...");
                rcon.comando("save-off");
                rcon.comando("save-all flush");

                System.out.println("Comprimiendo el mundo...");
                comprimir(mundos, zip);
            } finally {
                // Pase lo que pase, el server tiene que volver a guardar. Si esto
                // no corriera, el mundo dejaria de persistir sin ningun aviso.
                rcon.comando("save-on");
                System.out.println("Guardado reactivado.");
            }
        }
    }

    static void comprimir(List<Path> mundos, Path destino) throws IOException {
        try (OutputStream os = Files.newOutputStream(destino);
             ZipOutputStream zip = new ZipOutputStream(os)) {

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

                # Carpeta donde vive el server (donde esta server.jar).
                server.dir=.

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
                         + "\nRevisalo y volve a correr el backup.\n");
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
