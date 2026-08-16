import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Hostear una partida, de principio a fin.
 *
 *   1. reserva el mundo para que nadie mas lo levante
 *   2. baja el mundo mas reciente desde Drive
 *   3. arranca el server y espera a que cierres
 *   4. sube el mundo con tus cambios
 *   5. libera la reserva
 *
 * Es el reemplazo de mcsync: el jugador corre un comando y no piensa en nada mas.
 */
public class Host {

    public static void main(String[] args) throws Exception {
        boolean forzar = List.of(args).contains("--forzar");

        Properties cfg = Backup.cargarConfig();
        Path servidor = Path.of(cfg.getProperty("server.dir", "."));
        Path jar = servidor.resolve(cfg.getProperty("server.jar", "server.jar"));

        if (!Files.exists(jar)) {
            AuthSetup.exit("No encuentro " + jar.toAbsolutePath()
                         + "\nRevisa 'server.dir' y 'server.jar' en backup.properties.");
        }

        // Antes de tocar nada: si ya hay un server con este mundo abierto,
        // bajar otro mundo encima le cambiaria los archivos por debajo y lo
        // corromperia. Se corta aca, antes incluso de reservar en Drive.
        exigirMundoLibre(servidor, cfg);

        String token = AuthSetup.getAccessToken();
        String carpeta = Drive.buscarOCrearCarpeta(token, cfg.getProperty("drive.folder", "Minecraft Backups"));
        String jugador = cfg.getProperty("player.name", System.getProperty("user.name", "alguien"));

        String candado = Sesion.tomar(token, carpeta, jugador, forzar);

        // Si cierran con Ctrl+C, al menos que el mundo no quede trabado para todos.
        // Se usa el token de recien: pedir uno nuevo aca es riesgoso, porque un
        // System.exit dentro de un shutdown hook cuelga la JVM.
        Thread emergencia = new Thread(() -> {
            System.out.println("\nCierre abrupto: el mundo NO se subio a Drive.");
            System.out.println("Tus cambios quedan solo en la carpeta local.");
            Sesion.soltar(token, candado);
        });
        Runtime.getRuntime().addShutdownHook(emergencia);

        try {
            Mundo.traerDeDrive(token, carpeta, cfg);

            long inicio = System.currentTimeMillis();
            int codigo = arrancarServer(cfg, servidor, jar);
            long segundos = (System.currentTimeMillis() - inicio) / 1000;

            // Fabric devuelve 0 aunque el server no haya podido arrancar, asi que
            // el codigo de salida no alcanza para saber si la partida existio.
            // Una partida real dura minutos; si murio en segundos, fue un error.
            if (segundos < MINIMO_PARTIDA_SEGUNDOS) {
                System.out.printf("""

                    El server cerro a los %d segundos: no llego a arrancar bien.
                    NO se sube nada, para no pisar el mundo que hay en Drive.
                    Revisa el error de arriba y volve a intentar.
                    %n""", segundos);
                return;
            }

            System.out.println("\nEl server cerro (codigo " + codigo + "). Subiendo el mundo...\n");
            Backup.hacer(cfg);
        } finally {
            quitarHook(emergencia);
            // Token nuevo: una partida puede durar horas y el anterior ya vencio.
            Sesion.soltar(AuthSetup.getAccessToken(), candado);
        }
    }

    /** Debajo de esto se asume que el server fallo al arrancar, no que jugaste. */
    static final int MINIMO_PARTIDA_SEGUNDOS = 30;

    /**
     * Corta si algun proceso ya tiene abierto el mundo.
     *
     * Minecraft marca el mundo en uso tomando un candado sobre session.lock, y
     * lo suelta al cerrar. Si no podemos tomarlo nosotros, hay un server vivo:
     * puede ser otra ventana que quedo abierta, o el mismo mundo levantado a mano.
     */
    static void exigirMundoLibre(Path servidor, Properties cfg) {
        for (Path mundo : Backup.mundosExistentes(servidor, cfg.getProperty("world.folders", "world"))) {
            if (enUso(mundo)) {
                AuthSetup.exit("""

                    Ya hay un server usando el mundo '%s'.

                    Cerra esa ventana escribiendo 'stop' en su consola y volve a
                    intentar. Si no encontras la ventana, busca el proceso java
                    en el Administrador de tareas.
                    """.formatted(mundo.getFileName()));
            }
        }
    }

    static boolean enUso(Path mundo) {
        Path lock = mundo.resolve("session.lock");
        if (!Files.exists(lock)) return false;   // nunca se abrio: esta libre

        try (FileChannel canal = FileChannel.open(lock, StandardOpenOption.WRITE)) {
            FileLock candado = canal.tryLock();
            if (candado == null) return true;    // lo tiene otro proceso
            candado.release();
            return false;
        } catch (OverlappingFileLockException yaLoTenemos) {
            return true;
        } catch (IOException noSePudoAbrir) {
            // En Windows, un archivo tomado por otro proceso puede ni siquiera
            // abrirse. Ante la duda se asume ocupado: es el lado seguro.
            return true;
        }
    }

    /** Levanta el server de Minecraft y espera a que termine. */
    static int arrancarServer(Properties cfg, Path servidor, Path jar) throws Exception {
        var comando = new ArrayList<String>();
        comando.add(rutaDeJava());
        comando.add("-Xmx" + cfg.getProperty("server.ram", "4G"));
        comando.add("-jar");
        comando.add(jar.getFileName().toString());
        comando.add("nogui");

        System.out.println("\nArrancando el server. Para cerrar, escribi 'stop' en la consola.\n");

        // inheritIO conecta la consola del server con esta terminal: se ve el log
        // y se le pueden escribir comandos como si se hubiera arrancado a mano.
        return new ProcessBuilder(comando)
            .directory(servidor.toFile())
            .inheritIO()
            .start()
            .waitFor();
    }

    /** El mismo java que corre esto, para no depender de que este en el PATH. */
    static String rutaDeJava() {
        Path java = Path.of(System.getProperty("java.home"), "bin", "java");
        return Files.exists(java) ? java.toString()
             : Files.exists(Path.of(java + ".exe")) ? java + ".exe"
             : "java";
    }

    static void quitarHook(Thread hook) {
        try {
            Runtime.getRuntime().removeShutdownHook(hook);
        } catch (IllegalStateException yaEstaCerrando) {
            // La JVM ya se esta apagando: el hook corre igual y hace lo suyo.
        }
    }
}
