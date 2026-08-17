import java.util.Arrays;
import java.util.List;

/**
 * Punto de entrada unico.
 *
 *   java -jar mcbackup.jar auth      una vez, para conectar el Drive
 *   java -jar mcbackup.jar backup    hace un backup ahora
 *   java -jar mcbackup.jar list      muestra los backups guardados
 */
public class Main {

    static final String AYUDA = """
        Server de Minecraft compartido, sincronizado por Google Drive.

          java -jar mcbackup.jar instalar  arma el server desde cero (primera vez)
          java -jar mcbackup.jar auth      conecta la cuenta del mundo (una sola vez)
          java -jar mcbackup.jar host      JUGAR: baja el mundo, abre el server, lo sube al cerrar
          java -jar mcbackup.jar estado    muestra si alguien esta hosteando ahora
          java -jar mcbackup.jar conectar  ENTRAR A JUGAR: elige la mejor ruta y abre localhost
          java -jar mcbackup.jar red       mide tu conexion al server y te dice que hacer
          java -jar mcbackup.jar detener   apaga un server que quedo colgado sin ventana
          java -jar mcbackup.jar backup    sube un backup sin abrir el server
          java -jar mcbackup.jar list      muestra los backups que hay en Drive

        La configuracion vive en backup.properties, que se crea solo la primera
        vez que ejecutas cualquiera de estos comandos.""";

    public static void main(String[] args) throws Exception {
        String comando = args.length > 0 ? args[0] : "";
        String[] resto = args.length > 1 ? Arrays.copyOfRange(args, 1, args.length) : new String[0];

        switch (comando) {
            case "instalar", "setup" -> Instalar.main(resto);
            case "auth" -> AuthSetup.main(resto);
            case "host", "jugar" -> Host.main(resto);
            case "estado" -> estado();
            case "conectar" -> Puente.main(resto);
            case "red", "ping" -> Red.main(resto);
            case "detener", "stop" -> detener();
            case "backup" -> Backup.main(resto);
            case "list" -> listar();
            case "", "--help", "-h", "help" -> System.out.println(AYUDA);
            default -> {
                System.err.println("Comando desconocido: " + comando + "\n");
                System.err.println(AYUDA);
                System.exit(1);
            }
        }
    }

    /**
     * Apaga por RCON un server que quedo corriendo sin ventana visible.
     *
     * Pasa cuando se cierra la consola sin escribir 'stop': el proceso queda
     * vivo, con el mundo tomado, y no hay forma obvia de llegarle. Matar el
     * proceso funcionaria, pero perderia lo ultimo sin guardar; esto lo apaga
     * como corresponde.
     */
    static void detener() throws Exception {
        var cfg = Backup.cargarConfig();

        if (!Boolean.parseBoolean(cfg.getProperty("rcon.enabled", "false").trim())) {
            AuthSetup.exit("Para usar esto hace falta rcon.enabled=true en backup.properties.");
        }

        try (Rcon rcon = new Rcon(cfg.getProperty("rcon.host", "127.0.0.1"),
                                  Integer.parseInt(cfg.getProperty("rcon.port", "25575").trim()),
                                  cfg.getProperty("rcon.password", ""))) {
            System.out.println("Guardando el mundo...");
            rcon.comando("save-all flush");
            System.out.println("Apagando el server...");
            try {
                rcon.comando("stop");
            } catch (Exception seCorto) {
                // El server cierra la conexion mientras se apaga: es lo normal.
            }
            System.out.println("Listo, el server se esta cerrando.");
        } catch (java.net.ConnectException noHayNadie) {
            System.out.println("No hay ningun server corriendo: no hay nada que apagar.");
        }
    }

    /** Quien tiene el mundo tomado ahora mismo. */
    static void estado() throws Exception {
        String token = AuthSetup.getAccessToken();
        var cfg = Backup.cargarConfig();
        String carpeta = Drive.buscarOCrearCarpeta(token, cfg.getProperty("drive.folder", "Minecraft Backups"));

        String duenio = Sesion.duenioActual(token, carpeta);
        System.out.println(duenio == null
            ? "El mundo esta libre: puedes hostear cuando quieras."
            : "Lo esta hosteando " + duenio);
    }

    static void listar() throws Exception {
        String token = AuthSetup.getAccessToken();
        var cfg = Backup.cargarConfig();
        String carpeta = Drive.buscarOCrearCarpeta(token, cfg.getProperty("drive.folder", "Minecraft Backups"));

        List<String[]> archivos = Drive.listar(token, carpeta);
        if (archivos.isEmpty()) {
            System.out.println("Todavia no hay backups en Drive.");
            return;
        }

        System.out.printf("%d backup(s), del mas viejo al mas nuevo:%n%n", archivos.size());
        for (String[] a : archivos) {
            System.out.println("  " + a[1]);
        }
    }
}
