import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * El candado que impide que dos personas hosteen el mundo a la vez.
 *
 * Vive como un archivo LOCK.json en la carpeta de Drive compartida. Mientras
 * exista, alguien esta hosteando; cuando se borra, el mundo queda libre.
 *
 * Por que importa: si dos jugadores levantan el server al mismo tiempo, los dos
 * parten del mismo mundo y los dos suben el suyo al terminar. El segundo pisa
 * al primero y esas horas de juego se pierden. No hay forma de fusionarlas
 * despues: un mundo de Minecraft no se puede mergear.
 */
public class Sesion {

    static final String ARCHIVO = "LOCK.json";

    /** Pasado este tiempo se asume que el candado quedo colgado por un cierre sucio. */
    static final Duration VENCIMIENTO = Duration.ofHours(12);

    /**
     * Toma el candado. Devuelve el id del archivo para poder soltarlo despues.
     *
     * @param direccion la direccion de playit del anfitrion, para que los demas
     *                  la encuentren solos. Puede ir vacia: es opcional.
     * @param forzar    rompe un candado ajeno. Solo para cuando quedo colgado y
     *                  se confirmo que nadie esta jugando.
     */
    public static String tomar(String token, String carpetaId, String jugador,
                               String direccion, boolean forzar) throws Exception {

        List<String[]> existentes = buscarCandados(token, carpetaId);

        if (!existentes.isEmpty()) {
            String contenido = Drive.descargarTexto(token, existentes.get(0)[0]);
            String duenio = valor(contenido, "jugador");
            String desde = valor(contenido, "desdeUtc");
            boolean vencido = estaVencido(desde);

            if (!forzar && !vencido) {
                throw new IOException("""

                    El mundo lo esta hosteando %s desde %s.

                    Espera a que cierre su server. Si estas seguro de que nadie
                    esta jugando (por ejemplo, se le corto la luz), puedes ejecutar:
                      java -jar mcbackup.jar host --forzar
                    """.formatted(duenio, desde));
            }

            System.out.println(vencido
                ? "Habia un candado vencido de " + duenio + " (" + desde + "). Se libera."
                : "Rompiendo el candado de " + duenio + " por pedido explicito.");

            for (String[] viejo : existentes) Drive.borrar(token, viejo[0]);
        }

        String contenido = """
            {"jugador":"%s","desdeUtc":"%s","direccion":"%s"}"""
            .formatted(Drive.escapar(jugador), Instant.now().toString(),
                       Drive.escapar(direccion == null ? "" : direccion.trim()));

        String miId = Drive.subirTexto(token, ARCHIVO, contenido, carpetaId);

        // Dos personas pueden haber visto la carpeta libre al mismo tiempo y
        // haber creado su candado a la vez. Drive permite nombres repetidos, asi
        // que puede haber quedado mas de uno. Gana el de id mas chico: como la
        // regla es la misma en las dos maquinas, ambas llegan al mismo resultado.
        List<String[]> despues = buscarCandados(token, carpetaId);
        if (despues.size() > 1) {
            String ganador = despues.stream().map(a -> a[0]).min(Comparator.naturalOrder()).orElseThrow();
            if (!ganador.equals(miId)) {
                Drive.borrar(token, miId);
                throw new IOException("\nOtro jugador tomo el mundo en el mismo momento. Intenta de nuevo en un rato.\n");
            }
        }

        System.out.println("Mundo reservado para " + jugador + ".");
        return miId;
    }

    /** Suelta el candado. No tira excepcion: se llama desde cierres de emergencia. */
    public static void soltar(String token, String candadoId) {
        try {
            Drive.borrar(token, candadoId);
            System.out.println("Mundo liberado.");
        } catch (Exception e) {
            System.err.println("No se pudo liberar el candado: " + e.getMessage());
            System.err.println("El proximo que hostee va a tener que usar --forzar.");
        }
    }

    /** Quien tiene el mundo ahora mismo, o null si esta libre. */
    public static String duenioActual(String token, String carpetaId) throws Exception {
        List<String[]> candados = buscarCandados(token, carpetaId);
        if (candados.isEmpty()) return null;

        String contenido = Drive.descargarTexto(token, candados.get(0)[0]);
        return valor(contenido, "jugador") + " desde " + valor(contenido, "desdeUtc");
    }

    /**
     * La direccion de playit de quien esta hosteando, o null si no hay nadie o
     * si el anfitrion no la publico.
     *
     * Es lo que le permite a 'mcbackup conectar' apuntar solo, sin que nadie
     * tenga que repartir la direccion cada vez que cambia el anfitrion.
     */
    public static String direccionActual(String token, String carpetaId) throws Exception {
        List<String[]> candados = buscarCandados(token, carpetaId);
        if (candados.isEmpty()) return null;

        String direccion = Drive.campo(Drive.descargarTexto(token, candados.get(0)[0]), "direccion");
        return direccion == null || direccion.isBlank() ? null : direccion.trim();
    }

    private static List<String[]> buscarCandados(String token, String carpetaId) throws Exception {
        return Drive.listar(token, carpetaId).stream()
            .filter(a -> ARCHIVO.equals(a[1]))
            .toList();
    }

    private static boolean estaVencido(String desdeUtc) {
        try {
            return Instant.parse(desdeUtc).isBefore(Instant.now().minus(VENCIMIENTO));
        } catch (Exception fechaIlegible) {
            return false;   // ante la duda, se respeta el candado
        }
    }

    private static String valor(String json, String clave) {
        String v = Drive.campo(json, clave);
        return v == null ? "(desconocido)" : v;
    }
}
