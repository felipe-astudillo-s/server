import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Trae el mundo desde Drive y lo deja listo para jugar. */
public class Mundo {

    static final DateTimeFormatter SELLO = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /**
     * Baja el ultimo mundo subido y lo instala en la carpeta del server.
     *
     * Drive es la fuente de verdad: el que hostea siempre parte de lo ultimo
     * que subio el anterior. El mundo local que hubiera no se borra nunca, se
     * aparta con otro nombre, porque puede ser una partida que no llego a
     * subirse y es lo unico que queda de ella.
     */
    public static void traerDeDrive(String token, String carpetaId, Properties cfg) throws Exception {
        List<String[]> backups = Drive.listar(token, carpetaId).stream()
            .filter(a -> a[1].startsWith(Backup.PREFIJO) && a[1].endsWith(".zip"))
            .toList();

        if (backups.isEmpty()) {
            System.out.println("No hay ningun mundo en Drive todavia: se juega con el local.");
            return;
        }

        // El listado viene ordenado por fecha de creacion: el ultimo es el mas nuevo.
        String[] ultimo = backups.get(backups.size() - 1);
        Path servidor = Path.of(cfg.getProperty("server.dir", "."));
        Path zip = servidor.resolve(".mundo-descargado.zip");

        System.out.println("Bajando el mundo mas reciente: " + ultimo[1]);
        Drive.descargar(token, ultimo[0], zip);

        try {
            apartarMundoLocal(servidor, cfg);
            System.out.println("Instalando el mundo...");
            descomprimir(zip, servidor);
        } finally {
            Files.deleteIfExists(zip);
        }
    }

    /** Renombra los mundos que ya estaban, sin borrar nada. */
    static void apartarMundoLocal(Path servidor, Properties cfg) throws IOException {
        String sello = LocalDateTime.now().format(SELLO);

        for (Path mundo : Backup.mundosExistentes(servidor, cfg.getProperty("world.folders", "world"))) {
            Path apartado = mundo.resolveSibling(mundo.getFileName() + ".anterior-" + sello);
            Files.move(mundo, apartado, StandardCopyOption.ATOMIC_MOVE);
            System.out.println("  el mundo local quedo guardado como " + apartado.getFileName());
        }
    }

    /**
     * Descomprime el zip en la carpeta del server.
     *
     * Valida que ninguna entrada apunte fuera del destino: un zip preparado con
     * nombres tipo "../../algo" podria sobrescribir archivos de todo el disco.
     * El zip viene de Drive, pero no cuesta nada no confiar.
     */
    static void descomprimir(Path zip, Path destino) throws IOException {
        Path raiz = destino.toAbsolutePath().normalize();

        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zip))) {
            for (ZipEntry entrada; (entrada = zis.getNextEntry()) != null; ) {
                Path salida = raiz.resolve(entrada.getName()).normalize();

                if (!salida.startsWith(raiz)) {
                    throw new IOException("El zip trae una ruta que se escapa de la carpeta: "
                                        + entrada.getName());
                }

                if (entrada.isDirectory()) {
                    Files.createDirectories(salida);
                } else {
                    Files.createDirectories(salida.getParent());
                    copiar(zis, salida);
                }
            }
        }
    }

    private static void copiar(InputStream origen, Path destino) throws IOException {
        Files.copy(origen, destino, StandardCopyOption.REPLACE_EXISTING);
    }
}
