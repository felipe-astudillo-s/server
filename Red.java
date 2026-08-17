import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * Diagnostico de red: mide la conexion real al tunel y dice que hacer.
 *
 * Existe porque el ruteo hacia playit no es uniforme. Un mismo proveedor
 * puede mandar un rango de IPs de playit al datacenter local y otro rango al
 * extranjero, y ademas hacerlo distinto por IPv4 y por IPv6. Medido desde una
 * linea VTR en Santiago: el mismo servicio da 14 ms por IPv4 y 230 ms por
 * IPv6 en un dominio, y exactamente al reves en otro.
 *
 * Con eso, "me va lento" puede significar tres cosas muy distintas: el tunel
 * cayo en un rango malo, el cliente esta usando la familia de IP equivocada,
 * o la conexion del jugador esta mal. Cada una se arregla diferente, asi que
 * primero hay que saber cual es.
 *
 * Se mide tiempo de conexion TCP, no ping: es lo que hace el cliente de
 * Minecraft, y no depende de que el ICMP no venga filtrado en el camino.
 */
public class Red {

    static final int PUERTO_POR_DEFECTO = 25565;

    /** Muestras que se conservan por familia. Se hace una extra, ver medir(). */
    static final int INTENTOS_TUNEL = 6;
    static final int INTENTOS_REFERENCIA = 2;

    static final int TIMEOUT_TUNEL = 3000;
    static final int TIMEOUT_REFERENCIA = 2000;

    /**
     * Puntos de referencia de playit, uno por region. Sirven para separar
     * "tu conexion esta mal" de "a este tunel en particular lo estan
     * desviando": si estos responden rapido y el tunel no, el problema es el
     * tunel. Responden en el puerto 80.
     */
    static final String[][] REFERENCIAS = {
        {"Global",       "ping.gl.ply.gg", "gl"},
        {"Sudamerica",   "ping.sa.ply.gg", "sa"},
        {"Norteamerica", "ping.na.ply.gg", "na"},
    };
    static final int PUERTO_REFERENCIA = 80;

    /** Umbrales sobre la mediana, en milisegundos. */
    static final long UMBRAL_BUENO = 60;
    static final long UMBRAL_MALO = 120;

    static final String FLAGS_IPV6 =
        "-Djava.net.preferIPv4Stack=false -Djava.net.preferIPv6Addresses=true";

    enum Calidad { BUENA, REGULAR, MALA, SIN_RESPUESTA }

    // -----------------------------------------------------------------------

    public static void main(String[] args) throws Exception {
        String destino = args.length > 0 ? args[0].trim() : hostnameDeConfig();

        if (destino == null || destino.isBlank()) {
            AuthSetup.exit(SIN_DIRECCION);
            return;
        }

        String host = soloHost(destino);
        int puerto = soloPuerto(destino);

        InetAddress[] direcciones;
        try {
            direcciones = InetAddress.getAllByName(host);
        } catch (UnknownHostException noResuelve) {
            AuthSetup.exit("""

                No se pudo resolver la direccion: %s

                Revisa que este bien escrita. La direccion de playit se ve
                parecida a esto:  algo.gl.at.ply.gg
                """.formatted(host));
            return;
        }

        System.out.println("\nMidiendo " + host + ":" + puerto + " desde tu conexion...");
        System.out.println("Toma unos segundos.\n");

        Medicion v4 = medir(direcciones, puerto, false, INTENTOS_TUNEL, TIMEOUT_TUNEL);
        Medicion v6 = medir(direcciones, puerto, true, INTENTOS_TUNEL, TIMEOUT_TUNEL);

        System.out.println("Tunel del server:");
        imprimir(v4);
        imprimir(v6);

        System.out.println("\nDonde te ubica playit:");
        List<Medicion[]> referencias = new ArrayList<>();
        String datacenterGlobal = null;
        for (String[] ref : REFERENCIAS) {
            Medicion[] par = medirReferencia(ref[1]);
            referencias.add(par);

            Medicion mejorRef = mejorDe(par[0], par[1]);
            String datacenter = mejorRef == null ? null : datacenterDe(mejorRef.ip(), ref[1]);
            if (ref[2].equals("gl")) datacenterGlobal = datacenter;

            System.out.printf("  %-14s %s   %s   %s%n",
                ref[0], breve(par[0]), breve(par[1]), datacenter == null ? "" : datacenter);
        }

        if (sinIPv6(v6, referencias)) {
            System.out.println("\n  (Tu equipo no tiene IPv6 utilizable: solo cuenta la fila IPv4.)");
        }

        veredicto(v4, v6, referencias, datacenterGlobal);
        lineaParaPegar(host, v4, v6, referencias, datacenterGlobal);
    }

    // -----------------------------------------------------------------------
    // Veredicto
    // -----------------------------------------------------------------------

    /**
     * Traduce los numeros a una sola recomendacion. La idea es que el jugador
     * no tenga que interpretar nada: o le decimos que no haga nada, o le
     * damos el texto exacto para copiar.
     */
    static void veredicto(Medicion v4, Medicion v6, List<Medicion[]> referencias,
                          String datacenterGlobal) {
        System.out.println("\n" + "-".repeat(64));
        System.out.println("QUE HACER\n");

        Medicion mejor = mejorDe(v4, v6);

        if (mejor == null) {
            System.out.println("""
                No se pudo conectar al tunel por ninguna via.

                Casi siempre es una de estas tres, en este orden:

                  1. El programa de playit no esta abierto en la computadora
                     de quien hospeda.
                  2. Nadie esta hosteando en este momento.
                  3. La direccion esta mal escrita.

                Ninguna es un problema de ruteo: no toques la configuracion
                del launcher todavia.""");
            return;
        }

        Calidad calidad = calidad(mejor);
        boolean esIPv6 = mejor == v6;

        if (calidad == Calidad.MALA) {
            if (hayReferenciaBuena(referencias)) {
                if (datacenterGlobal != null) {
                    System.out.println("Tu proveedor te ubica en el datacenter "
                                     + datacenterGlobal + ", que esta cerca.\n");
                }
                System.out.printf("""
                    Tu conexion llega bien a playit, pero a ESTE tunel lo estan
                    desviando al extranjero (%d ms por la mejor via).

                    Le pasa al anfitrion, no a ti: al tunel le toco un rango de
                    IPs que tu proveedor manda por Estados Unidos en vez de
                    dejarlo dentro de Chile.

                    Solucion, y es gratis: quien hospeda entra al panel de
                    playit, borra el tunel y crea uno nuevo apuntando al 25565.
                    Sale con otra direccion y, casi seguro, en otro rango.
                    Despues vuelvan a correr este comando con la direccion nueva.

                    Puede hacer falta repetirlo un par de veces hasta que quede.
                    %n""", mejor.mediana());
            } else {
                System.out.println("""
                    Todo dio alto, incluidas las mediciones de referencia.

                    Eso apunta a tu propia conexion a internet, no al tunel ni
                    al server: cualquier cosa que hagas en el launcher o en la
                    configuracion de playit no va a cambiar nada.

                    Prueba con cable en vez de wifi, o mide de nuevo mas tarde.""");
            }
            return;
        }

        String estado = calidad == Calidad.BUENA ? "bien" : "aceptable";
        System.out.printf("Tu mejor via es %s: %d ms, y esta %s.%n%n",
            mejor.familia(), mejor.mediana(), estado);

        if (esIPv6) {
            System.out.printf("""
                Minecraft trae IPv6 apagado, asi que hoy NO la estas usando.
                Para activarla, en el launcher oficial:

                  Instalaciones -> tu perfil -> los tres puntos -> Editar
                  -> Mas opciones -> Argumentos de JVM

                y agrega esto al final de lo que ya diga ahi:

                  %s

                Guarda y vuelve a entrar al server.
                %n""", FLAGS_IPV6);
        } else {
            System.out.println("""
                Es la via que Minecraft usa por defecto, asi que no tienes que
                configurar nada. Si alguien te paso los argumentos de IPv6 para
                el launcher, sacalos: en tu caso empeoran la conexion.
                """);
        }

        Medicion otra = esIPv6 ? v4 : v6;
        if (otra.respondio() && otra.mediana() - mejor.mediana() > UMBRAL_BUENO) {
            System.out.printf("  (Por %s tu trafico se va al extranjero: %d ms. Evitala.)%n",
                otra.familia(), otra.mediana());
        }

        if (mejor.fallidos() > 0) {
            System.out.printf("""

                  Ojo: %d de %d intentos no llegaron. La latencia esta bien pero
                  la conexion se corta a ratos, que es justo lo que produce las
                  desconexiones en pleno juego. Si se repite al volver a medir,
                  conviene recrear el tunel igual.
                %n""", mejor.fallidos(), INTENTOS_TUNEL);
        }
    }

    /** La familia con menor mediana entre las que respondieron. */
    static Medicion mejorDe(Medicion v4, Medicion v6) {
        if (!v4.respondio()) return v6.respondio() ? v6 : null;
        if (!v6.respondio()) return v4;
        return v6.mediana() < v4.mediana() ? v6 : v4;
    }

    static Calidad calidad(Medicion m) {
        if (!m.respondio()) return Calidad.SIN_RESPUESTA;
        long mediana = m.mediana();
        if (mediana < UMBRAL_BUENO) return Calidad.BUENA;
        if (mediana <= UMBRAL_MALO) return Calidad.REGULAR;
        return Calidad.MALA;
    }

    static boolean hayReferenciaBuena(List<Medicion[]> referencias) {
        for (Medicion[] par : referencias) {
            for (Medicion m : par) {
                if (m.respondio() && m.mediana() < UMBRAL_BUENO) return true;
            }
        }
        return false;
    }

    /** Sin una sola medicion IPv6 exitosa en ningun destino, no hay IPv6. */
    static boolean sinIPv6(Medicion v6, List<Medicion[]> referencias) {
        if (v6.respondio()) return false;
        for (Medicion[] par : referencias) {
            if (par[1].respondio()) return false;
        }
        return true;
    }

    /**
     * Una linea sola con todo, para pegar en el chat del grupo. Juntando las
     * de varios jugadores se ve de una si el problema es de un proveedor, del
     * tunel, o de una sola persona.
     */
    static void lineaParaPegar(String host, Medicion v4, Medicion v6,
                               List<Medicion[]> referencias, String datacenterGlobal) {
        var linea = new StringBuilder("[red] tunel=").append(host);
        linea.append(" v4=").append(resumen(v4));
        linea.append(" v6=").append(resumen(v6));

        for (int i = 0; i < REFERENCIAS.length; i++) {
            Medicion[] par = referencias.get(i);
            Medicion mejor = mejorDe(par[0], par[1]);
            linea.append(' ').append(REFERENCIAS[i][2]).append('=')
                 .append(mejor == null ? "-" : mejor.mediana() + "ms");
            if (REFERENCIAS[i][2].equals("gl") && datacenterGlobal != null) {
                linea.append('/').append(datacenterGlobal);
            }
        }

        Medicion mejor = mejorDe(v4, v6);
        linea.append(" -> ").append(mejor == null ? "SIN CONEXION"
            : calidad(mejor) == Calidad.MALA ? "RECREAR TUNEL"
            : "usar " + mejor.familia());

        System.out.println("\nPega esto en el chat del grupo:\n");
        System.out.println("  " + linea);
        System.out.println();
    }

    static String resumen(Medicion m) {
        if (!m.hayDireccion()) return "-";
        if (!m.respondio()) return "corta";
        return m.mediana() + "ms/j" + m.jitter() + (m.fallidos() > 0 ? "/x" + m.fallidos() : "");
    }

    // -----------------------------------------------------------------------
    // Medicion
    // -----------------------------------------------------------------------

    /** Tiempos de conexion a un destino por una familia de IP. */
    record Medicion(String familia, InetAddress ip, List<Long> tiempos, int fallidos) {

        boolean hayDireccion() { return ip != null; }

        boolean respondio() { return !tiempos.isEmpty(); }

        long mediana() {
            List<Long> ordenados = new ArrayList<>(tiempos);
            Collections.sort(ordenados);
            return ordenados.get(ordenados.size() / 2);
        }

        /** Cuanto se mueve la latencia. Importa tanto como la latencia misma:
         *  es la variacion, no el promedio, la que saca del server. */
        long jitter() {
            var stats = tiempos.stream().mapToLong(Long::longValue).summaryStatistics();
            return stats.getMax() - stats.getMin();
        }
    }

    static Medicion medir(InetAddress[] direcciones, int puerto, boolean ipv6,
                          int intentos, int timeoutMs) {
        String familia = ipv6 ? "IPv6" : "IPv4";
        InetAddress ip = deLaFamilia(direcciones, ipv6);
        if (ip == null) return new Medicion(familia, null, List.of(), 0);

        List<Long> tiempos = new ArrayList<>();
        int fallidos = 0;

        // Se hace un intento extra y se descarta: el primero paga el arranque
        // en frio (ARP/ND, tablas del router) y sale siempre mas lento.
        for (int i = 0; i <= intentos; i++) {
            long ms = unaConexion(ip, puerto, timeoutMs);
            if (i == 0) continue;
            if (ms < 0) fallidos++;
            else tiempos.add(ms);

            // Si no llego ninguna de las dos primeras, no hay nada del otro
            // lado: seguir insistiendo son varios segundos de espera al pedo,
            // y este comando lo corre alguien mirando la pantalla.
            if (tiempos.isEmpty() && fallidos >= 2) break;
        }
        return new Medicion(familia, ip, tiempos, fallidos);
    }

    static Medicion[] medirReferencia(String host) {
        InetAddress[] direcciones;
        try {
            direcciones = InetAddress.getAllByName(host);
        } catch (UnknownHostException noResuelve) {
            return new Medicion[] {
                new Medicion("IPv4", null, List.of(), 0),
                new Medicion("IPv6", null, List.of(), 0),
            };
        }
        return new Medicion[] {
            medir(direcciones, PUERTO_REFERENCIA, false, INTENTOS_REFERENCIA, TIMEOUT_REFERENCIA),
            medir(direcciones, PUERTO_REFERENCIA, true, INTENTOS_REFERENCIA, TIMEOUT_REFERENCIA),
        };
    }

    /**
     * En que datacenter cayo, por nombre. Null si no se pudo averiguar.
     *
     * Los puntos de referencia de playit contestan por HTTP un texto con una
     * linea 'tunnel_name: Santiago_1'. Preguntar es mucho mejor que deducirlo
     * de los milisegundos: deja de ser "esto parece lejos" y pasa a ser "te
     * estan mandando a Dallas".
     */
    static String datacenterDe(InetAddress ip, String host) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, PUERTO_REFERENCIA), TIMEOUT_REFERENCIA);
            socket.setSoTimeout(TIMEOUT_REFERENCIA);

            socket.getOutputStream().write(
                ("GET / HTTP/1.0\r\nHost: " + host + "\r\nConnection: close\r\n\r\n")
                    .getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();

            String respuesta = new String(socket.getInputStream().readAllBytes(),
                                          StandardCharsets.UTF_8);
            for (String linea : respuesta.split("\n")) {
                if (linea.startsWith("tunnel_name:")) {
                    return linea.substring("tunnel_name:".length()).trim();
                }
            }
        } catch (IOException noSePudo) {
            // Es un extra: sin el nombre quedan los milisegundos, que alcanzan.
        }
        return null;
    }

    /** Milisegundos que tardo el handshake, o -1 si no se pudo conectar. */
    static long unaConexion(InetAddress ip, int puerto, int timeoutMs) {
        long inicio = System.nanoTime();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, puerto), timeoutMs);
            return (System.nanoTime() - inicio) / 1_000_000;
        } catch (IOException noLlego) {
            return -1;
        }
    }

    static InetAddress deLaFamilia(InetAddress[] direcciones, boolean ipv6) {
        for (InetAddress ip : direcciones) {
            if (ip instanceof Inet6Address == ipv6) return ip;
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // Salida
    // -----------------------------------------------------------------------

    static void imprimir(Medicion m) {
        if (!m.hayDireccion()) {
            System.out.printf("  %-5s %-34s no tiene direccion de este tipo%n", m.familia(), "");
            return;
        }
        String ip = "(" + m.ip().getHostAddress() + ")";
        if (!m.respondio()) {
            System.out.printf("  %-5s %-34s sin respuesta%n", m.familia(), ip);
            return;
        }
        System.out.printf("  %-5s %-34s %4d ms   jitter %d ms%s%n",
            m.familia(), ip, m.mediana(), m.jitter(),
            m.fallidos() > 0 ? "   " + m.fallidos() + " sin llegar" : "");
    }

    /** Version corta para la tabla de referencias. */
    static String breve(Medicion m) {
        if (!m.respondio()) return String.format("%-4s %8s", m.familia(), "-");
        return String.format("%-4s %5d ms", m.familia(), m.mediana());
    }

    // -----------------------------------------------------------------------
    // Entrada
    // -----------------------------------------------------------------------

    /**
     * Lee playit.hostname de backup.properties si el archivo ya existe.
     *
     * No usa Backup.cargarConfig() a proposito: esa funcion crea el archivo y
     * corta la ejecucion cuando falta, y este comando lo corren tambien los
     * jugadores, que no tienen carpeta de server ni por que tener una.
     */
    static String hostnameDeConfig() {
        Properties props = configSiExiste();
        return props == null ? null : props.getProperty("playit.hostname", "").trim();
    }

    /** backup.properties ya cargado, o null si no existe o no se pudo leer. */
    static Properties configSiExiste() {
        if (!Files.exists(Backup.CONFIG)) return null;
        try {
            var props = new Properties();
            try (var in = Files.newInputStream(Backup.CONFIG)) {
                props.load(in);
            }
            return props;
        } catch (IOException noSePudoLeer) {
            return null;
        }
    }

    static String soloHost(String destino) {
        int dosPuntos = destino.lastIndexOf(':');
        return dosPuntos > 0 && esNumero(destino.substring(dosPuntos + 1))
            ? destino.substring(0, dosPuntos)
            : destino;
    }

    static int soloPuerto(String destino) {
        int dosPuntos = destino.lastIndexOf(':');
        String cola = dosPuntos > 0 ? destino.substring(dosPuntos + 1) : "";
        return esNumero(cola) ? Integer.parseInt(cola) : PUERTO_POR_DEFECTO;
    }

    static boolean esNumero(String texto) {
        if (texto.isEmpty()) return false;
        for (char c : texto.toCharArray()) {
            if (c < '0' || c > '9') return false;
        }
        return true;
    }

    static final String SIN_DIRECCION = """

        Falta la direccion del server.

        Pasala como argumento:

          java -jar mcbackup.jar red algo.gl.at.ply.gg

        Es la misma que usas para entrar al juego. Si no la tienes, pidesela a
        quien esta hosteando: le aparece en el panel de playit, en su tunel.

        Si tu hospedas, puedes dejarla fija agregando esta linea a
        backup.properties y no volver a escribirla nunca mas:

          playit.hostname=algo.gl.at.ply.gg
        """;
}
