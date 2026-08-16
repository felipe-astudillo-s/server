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
        Backups de Minecraft a Google Drive.

          java -jar mcbackup.jar auth      conecta tu cuenta de Google (una sola vez)
          java -jar mcbackup.jar backup    hace un backup ahora
          java -jar mcbackup.jar list      muestra los backups que hay en Drive

        La configuracion vive en backup.properties, que se crea solo la primera
        vez que corres 'backup'.""";

    public static void main(String[] args) throws Exception {
        String comando = args.length > 0 ? args[0] : "";
        String[] resto = args.length > 1 ? Arrays.copyOfRange(args, 1, args.length) : new String[0];

        switch (comando) {
            case "auth" -> AuthSetup.main(resto);
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
