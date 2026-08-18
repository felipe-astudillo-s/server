import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Puente local: elige solo la mejor ruta al server y deja el juego apuntando
 * siempre a localhost.
 *
 * Por que hace falta un proceso aparte y no alcanza con dar una IP mejor: Java
 * prefiere IPv4 salvo que le pasen -Djava.net.preferIPv6Addresses=true, y esa
 * bandera hay que ponerla a mano en el launcher. Sin ella, cuando la ruta buena
 * es IPv6 -- y desde Chile pasa seguido -- el cliente ni la intenta. Este
 * puente corre en su propia JVM, donde la preferencia la elegimos nosotros
 * segun lo que hayan dado las mediciones.
 *
 * (Se suele decir que el launcher ademas fuerza preferIPv4Stack=true, lo que
 * apagaria IPv6 del todo. No lo pudimos comprobar, y da igual: sea o no asi,
 * el puente cubre los dos casos.)
 *
 * De regalo, la direccion dentro de Minecraft pasa a ser 'localhost' para
 * siempre: deja de importar quien hospeda hoy.
 *
 * El salto extra es por loopback, sub-milisegundo. No se nota al lado de los
 * 180 ms que ahorra elegir bien la ruta.
 */
public class Puente {

    static final int PUERTO_PREFERIDO = 25565;

    /** Si el 25565 esta tomado (por ejemplo, porque tu tambien hosteas). */
    static final int PUERTOS_A_PROBAR = 10;

    static final int INTENTOS_MEDICION = 4;
    static final int TIMEOUT_MEDICION = 3000;
    static final int TIMEOUT_CONEXION = 8000;
    static final int BUFFER = 16 * 1024;

    static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");

    /** Solo para numerar las conexiones en pantalla. */
    static final AtomicInteger CONTADOR = new AtomicInteger();

    static volatile InetAddress rutaElegida;
    static volatile InetAddress rutaAlternativa;
    static volatile int puertoRemoto;

    /** true cuando se abrio con doble clic: no hay consola, hay ventana. */
    static volatile boolean grafico;

    // -----------------------------------------------------------------------

    /**
     * Entrada cuando alguien hace doble clic en el jar.
     *
     * Windows lo abre con javaw, que no da consola: sin ventana, la persona no
     * veria absolutamente nada. Se abre una y se redirige la salida ahi, asi
     * el resto del programa sigue usando println sin enterarse.
     */
    public static void desdeDobleClic() throws Exception {
        grafico = true;
        PrintStream aLaVentana = Ventana.abrirConsola("Puente al server de Minecraft");
        System.setOut(aLaVentana);
        System.setErr(aLaVentana);
        main(new String[0]);
    }

    /** Corta con un mensaje, por el canal que corresponda. */
    static void cortar(String mensaje) {
        if (grafico) {
            System.out.println(mensaje);
            Ventana.error(mensaje);
            System.exit(1);
        }
        AuthSetup.exit(mensaje);
    }

    public static void main(String[] args) throws Exception {
        String destino = limpiar(args.length > 0 ? args[0] : descubrirDestino());

        if (destino == null || destino.isBlank()) {
            cortar(SIN_DESTINO);
            return;
        }

        String host = Red.soloHost(destino);
        puertoRemoto = Red.soloPuerto(destino);

        InetAddress[] direcciones;
        try {
            direcciones = InetAddress.getAllByName(host);
        } catch (UnknownHostException noResuelve) {
            cortar("\nNo se pudo resolver " + host + ". Revisa que este bien escrita.\n");
            return;
        }

        System.out.println("\nMidiendo las rutas hacia " + host + ":" + puertoRemoto + "...");

        Red.Medicion v4 = Red.medir(direcciones, puertoRemoto, false, INTENTOS_MEDICION, TIMEOUT_MEDICION);
        Red.Medicion v6 = Red.medir(direcciones, puertoRemoto, true, INTENTOS_MEDICION, TIMEOUT_MEDICION);
        Red.imprimir(v4);
        Red.imprimir(v6);

        Red.Medicion mejor = Red.mejorDe(v4, v6);
        if (mejor == null) {
            cortar(NO_RESPONDE);
            return;
        }

        Red.Medicion otra = mejor == v6 ? v4 : v6;
        rutaElegida = mejor.ip();
        rutaAlternativa = otra.respondio() ? otra.ip() : null;

        System.out.printf("%nRuta elegida: %s, %d ms.%n", mejor.familia(), mejor.mediana());
        avisarSiEsMala(mejor);

        generarLanzador();

        abrirPuente();
    }

    /**
     * Si la mejor ruta igual es mala, no se corta: se avisa y se sigue. Jugar
     * con lag es peor que jugar bien, pero mucho mejor que no jugar, y la
     * decision es del jugador. Ademas el arreglo real es del anfitrion.
     */
    static void avisarSiEsMala(Red.Medicion mejor) {
        if (Red.calidad(mejor) != Red.Calidad.MALA) return;

        System.out.println("""

            Ojo: hasta la mejor ruta esta lenta. El puente igual te va a
            conectar, pero esto no lo arregla ningun ajuste de tu lado.

            Para saber si es el tunel o tu conexion, corre:
              java -jar mcbackup.jar red
            """);
    }

    // -----------------------------------------------------------------------
    // Lanzador de doble clic
    // -----------------------------------------------------------------------

    /** Nombre del lanzador ya creado, para nombrarlo en las instrucciones. */
    static volatile String lanzador;

    /**
     * Deja un conectar.bat al lado del jar, para no volver a abrir la terminal.
     *
     * Se genera aca y no se distribuye ya hecho por la misma razon que
     * jugar.bat (ver Instalar.regenerarLanzadores): Windows le pone la marca de
     * la web a todo script descargado y el Control inteligente de aplicaciones
     * lo bloquea. Un archivo escrito por la propia maquina no la lleva.
     *
     * No lleva la direccion adentro a proposito: el anfitrion cambia, asi que
     * el lanzador la vuelve a resolver cada vez (Drive, o preguntando con la
     * ultima ya escrita para que alcance con dar Enter).
     */
    static void generarLanzador() {
        Path carpeta = carpetaDelJar();
        String jar = nombreDelJar();
        if (jar == null) return;   // corriendo desde clases sueltas: no aplica

        try {
            Files.writeString(carpeta.resolve("conectar.bat"), String.join("\r\n",
                "@echo off",
                "rem Abre el puente al server y elige la mejor ruta.",
                "rem Despues, en Minecraft, conectate a: localhost",
                "cd /d \"%~dp0\"",
                "set MCJAVA=java",
                "",
                "java -version >nul 2>&1",
                "if not errorlevel 1 goto ejecutar",
                "",
                "rem Sin Java en el PATH: sirve el que ya trae Minecraft.",
                "for /f \"delims=\" %%J in ('dir /b /s \"%APPDATA%\\.minecraft\\runtime\\java.exe\" 2^>nul') do set MCJAVA=%%J",
                "if \"%MCJAVA%\"==\"java\" (",
                "    echo.",
                "    echo No encuentro Java en esta computadora.",
                "    echo Instalalo desde https://adoptium.net y vuelve a intentar.",
                "    echo.",
                "    pause",
                "    exit /b 1",
                ")",
                "",
                ":ejecutar",
                "\"%MCJAVA%\" -jar " + jar + " conectar",
                "echo.",
                "pause",
                ""));

            Path sh = carpeta.resolve("conectar-linux.sh");
            Files.writeString(sh, String.join("\n",
                "#!/bin/sh",
                "# Abre el puente al server. Despues, en Minecraft: localhost",
                "cd \"$(dirname \"$0\")\" || exit 1",
                "",
                "if ! command -v java >/dev/null 2>&1; then",
                "    echo \"No encuentro Java. Instalalo desde https://adoptium.net\"",
                "    exit 1",
                "fi",
                "",
                "exec java -jar " + jar + " conectar",
                ""));

            try {
                sh.toFile().setExecutable(true);
            } catch (SecurityException sinPermisos) {
                // En Windows no aplica y no importa.
            }

            lanzador = "conectar.bat";

        } catch (IOException noSePudoEscribir) {
            // Carpeta de solo lectura: el comando funciona igual, solo que hay
            // que seguir escribiendolo a mano.
        }
    }

    /** La carpeta donde vive el jar, que es donde tiene que quedar el .bat. */
    static Path carpetaDelJar() {
        try {
            Path codigo = Path.of(Puente.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
            if (Files.isRegularFile(codigo)) return codigo.getParent();
        } catch (Exception noSePudoAveriguar) {
            // Sin permisos o con un cargador raro: vale la carpeta actual.
        }
        return Path.of("").toAbsolutePath();
    }

    /** El nombre real del jar, o null si esto no se esta ejecutando desde uno. */
    static String nombreDelJar() {
        try {
            Path codigo = Path.of(Puente.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
            if (Files.isRegularFile(codigo)) return codigo.getFileName().toString();
        } catch (Exception noSePudoAveriguar) {
            // Igual que arriba.
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // El puente
    // -----------------------------------------------------------------------

    static void abrirPuente() throws Exception {
        List<ServerSocket> oidos = List.of();
        int puertoLocal = 0;

        for (int i = 0; i < PUERTOS_A_PROBAR; i++) {
            puertoLocal = PUERTO_PREFERIDO + i;
            oidos = escucharEn(puertoLocal);
            if (!oidos.isEmpty()) break;
        }

        if (oidos.isEmpty()) {
            cortar("\nNo hay ningun puerto libre entre " + PUERTO_PREFERIDO
                         + " y " + (PUERTO_PREFERIDO + PUERTOS_A_PROBAR - 1) + ".\n");
            return;
        }

        String direccionLocal = puertoLocal == PUERTO_PREFERIDO
            ? "localhost"
            : "localhost:" + puertoLocal;

        System.out.println("\n" + "=".repeat(58));
        System.out.println("  En Minecraft conectate a:   " + direccionLocal);
        System.out.println("=".repeat(58));
        System.out.println("""

            Guardalo en tu lista de servidores: esta direccion no cambia
            nunca, ni cuando hospeda otra persona.

            Deja esta ventana abierta mientras juegas. Para cerrar el
            puente, cierrala o aprieta Ctrl+C.
            """);

        if (lanzador != null) {
            System.out.println("Te deje un " + lanzador + " en esta carpeta:");
            System.out.println("la proxima vez dale doble clic y listo, sin terminal.\n");
        }

        System.out.println("Esperando a que abras Minecraft...\n");

        // Todos menos el primero en hilos aparte; el primero se queda con el
        // hilo principal, para que la JVM no se apague al terminar main().
        for (int i = 1; i < oidos.size(); i++) {
            ServerSocket extra = oidos.get(i);
            Thread hilo = new Thread(() -> aceptar(extra));
            hilo.setDaemon(true);
            hilo.start();
        }
        aceptar(oidos.get(0));
    }

    /**
     * Abre un socket en cada punta del loopback, las dos en el mismo puerto.
     *
     * Hacen falta las dos. 'localhost' resuelve a 127.0.0.1 y a ::1, y cual de
     * las dos elige el juego depende de si le pusieron los argumentos de IPv6
     * al launcher: con preferIPv6Addresses=true agarra ::1 primero. Escuchando
     * en una sola, al jugador que tenga la otra le da conexion rechazada.
     *
     * Nunca 0.0.0.0: si escuchara en toda la red, cualquiera en tu wifi podria
     * entrar al server a traves de tu maquina.
     *
     * Si alguna de las dos esta ocupada se sueltan las dos y se prueba el
     * puerto siguiente: quedar escuchando a medias es justo el bug que esto
     * viene a evitar.
     */
    static List<ServerSocket> escucharEn(int puerto) {
        List<ServerSocket> abiertos = new ArrayList<>();

        for (String loopback : loopbacksDisponibles()) {
            try {
                abiertos.add(new ServerSocket(puerto, 50, InetAddress.getByName(loopback)));
            } catch (IOException ocupado) {
                for (ServerSocket abierto : abiertos) cerrar(abierto);
                return List.of();
            }
        }
        return abiertos;
    }

    /**
     * Las puntas del loopback que este equipo soporta. Un equipo con IPv6
     * apagado no puede abrir ::1, y no es un error: se usa solo IPv4.
     */
    static List<String> loopbacksDisponibles() {
        List<String> disponibles = new ArrayList<>();
        disponibles.add("127.0.0.1");

        // Se prueba con un puerto efimero para no pisar el que vamos a usar.
        try (ServerSocket prueba = new ServerSocket(0, 1, InetAddress.getByName("::1"))) {
            if (prueba.isBound()) disponibles.add("::1");
        } catch (IOException sinIPv6) {
            // El equipo no tiene IPv6: con 127.0.0.1 alcanza.
        }
        return disponibles;
    }

    static void aceptar(ServerSocket oido) {
        try (ServerSocket servidor = oido) {
            while (true) {
                Socket juego = servidor.accept();
                Thread hilo = new Thread(() -> atender(juego));
                hilo.setDaemon(true);
                hilo.start();
            }
        } catch (IOException seCerro) {
            // El puente se esta cerrando.
        }
    }

    static void cerrar(ServerSocket socket) {
        try {
            socket.close();
        } catch (IOException yaEstaba) {
            // Cerrar dos veces no es un problema.
        }
    }

    /** Une una conexion del juego con una conexion nueva al server. */
    static void atender(Socket juego) {
        int numero = CONTADOR.incrementAndGet();
        Socket server = null;

        try {
            afinar(juego);
            server = conectarAlServer();

            if (server == null) {
                registrar(numero, "no se pudo llegar al server, se corta");
                cerrar(juego);
                return;
            }

            registrar(numero, "conectado por " + familiaDe(server.getInetAddress()));

            Socket destino = server;
            Thread ida = new Thread(() -> bombear(juego, destino));
            ida.setDaemon(true);
            ida.start();

            bombear(server, juego);          // la vuelta se bombea en este hilo
            registrar(numero, "conexion cerrada");

        } catch (Exception falla) {
            registrar(numero, "se corto: " + falla.getMessage());
            cerrar(juego);
            cerrar(server);
        }
    }

    /**
     * Abre la conexion al server por la ruta elegida, y si esa falla prueba la
     * otra familia. El ruteo puede cambiar en medio de una partida: mejor
     * entrar por la ruta lenta que quedarse afuera.
     */
    static Socket conectarAlServer() {
        Socket socket = intentar(rutaElegida);
        if (socket != null) return socket;

        if (rutaAlternativa != null) {
            System.out.println("  La ruta habitual no responde, probando la otra...");
            socket = intentar(rutaAlternativa);
            if (socket != null) {
                // La buena dejo de andar: la alternativa pasa a ser la principal
                // para las proximas conexiones de esta sesion.
                InetAddress caida = rutaElegida;
                rutaElegida = socket.getInetAddress();
                rutaAlternativa = caida;
            }
        }
        return socket;
    }

    static Socket intentar(InetAddress ip) {
        if (ip == null) return null;
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(ip, puertoRemoto), TIMEOUT_CONEXION);
            afinar(socket);
            return socket;
        } catch (IOException noLlego) {
            cerrar(socket);
            return null;
        }
    }

    /**
     * TCP_NODELAY en los dos extremos. Sin esto, Nagle junta los paquetes
     * chicos antes de mandarlos y agrega hasta 40 ms: justo lo que este
     * comando existe para evitar. Minecraft manda muchisimo paquete chico.
     */
    static void afinar(Socket socket) {
        try {
            socket.setTcpNoDelay(true);
            socket.setKeepAlive(true);
        } catch (IOException daIgual) {
            // Si el sistema no deja tocarlo, el puente funciona igual.
        }
    }

    /** Copia todo lo que venga de un lado hacia el otro hasta que se corte. */
    static void bombear(Socket desde, Socket hacia) {
        byte[] buffer = new byte[BUFFER];
        try {
            InputStream in = desde.getInputStream();
            OutputStream out = hacia.getOutputStream();
            int leidos;
            while ((leidos = in.read(buffer)) != -1) {
                out.write(buffer, 0, leidos);
                out.flush();
            }
        } catch (IOException seCorto) {
            // Es lo normal cuando cualquiera de las dos puntas cierra.
        } finally {
            cerrar(desde);
            cerrar(hacia);
        }
    }

    static void cerrar(Socket socket) {
        if (socket == null) return;
        try {
            socket.close();
        } catch (IOException yaEstaba) {
            // Cerrar dos veces no es un problema.
        }
    }

    static String familiaDe(InetAddress ip) {
        return ip instanceof java.net.Inet6Address ? "IPv6" : "IPv4";
    }

    static void registrar(int numero, String mensaje) {
        System.out.printf("  [%s] conexion %d: %s%n", LocalTime.now().format(HORA), numero, mensaje);
    }

    // -----------------------------------------------------------------------
    // De donde sale la direccion
    // -----------------------------------------------------------------------

    /**
     * Busca la direccion sin molestar al jugador: primero backup.properties,
     * despues el candado en Drive, que es el que sabe quien hostea ahora.
     */
    static String descubrirDestino() {
        // Drive va primero, y el orden importa: el candado es lo unico que sabe
        // quien hostea AHORA. playit.hostname es la direccion PROPIA de quien
        // hostea, asi que en la maquina de alguien que a veces hospeda apuntaria
        // a su propio tunel en vez de al anfitrion de turno.
        String deDrive = destinoSegunDrive();
        if (deDrive != null) return deDrive;

        String deConfig = Red.hostnameDeConfig();
        if (deConfig != null && !deConfig.isBlank()) {
            System.out.println("  Uso la direccion de backup.properties: " + deConfig);
            return deConfig;
        }
        return preguntarDireccion();
    }

    /**
     * Ultimo recurso: preguntarsela a la persona.
     *
     * Se ofrece la del ultimo uso ya escrita, asi lo normal es dar Enter y
     * seguir. Cuando cambia el anfitrion, se pega la nueva encima.
     */
    static String preguntarDireccion() {
        String ultima = leerUltimaDireccion();
        try {
            String elegida = limpiar(grafico
                ? Ventana.pedirDireccion(ultima)
                : preguntarEnConsola(ultima));

            if (elegida != null && !elegida.isBlank()) guardarUltimaDireccion(elegida);
            return elegida;

        } catch (Exception noSePudoPreguntar) {
            return null;
        }
    }

    /**
     * Deja la direccion como para resolverla.
     *
     * Estas direcciones viajan por WhatsApp y Discord y vuelven con sorpresas:
     * espacios, comillas de un copiar y pegar apurado, y sobre todo caracteres
     * invisibles como el BOM o el espacio de ancho cero, que no se ven en
     * pantalla pero rompen la resolucion del nombre. Como un hostname valido
     * solo usa ASCII imprimible, se tira todo lo demas.
     */
    static String limpiar(String crudo) {
        if (crudo == null) return null;
        return crudo.replaceAll("[^\\x21-\\x7E]", "")
                    .replaceAll("^[\"']+|[\"']+$", "");
    }

    static String preguntarEnConsola(String sugerida) throws IOException {
        if (sugerida == null) {
            System.out.print("\nPega la direccion del server (algo.gl.at.ply.gg): ");
        } else {
            System.out.println("\nLa ultima vez jugaste en: " + sugerida);
            System.out.print("Direccion del server (Enter para repetir esa): ");
        }

        var lector = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String linea = lector.readLine();
        if (linea == null) return sugerida;   // sin stdin: se usa la de antes
        linea = linea.trim();
        return linea.isEmpty() ? sugerida : linea;
    }

    static Path archivoUltimaDireccion() {
        return carpetaDelJar().resolve("ultimo-server.txt");
    }

    static String leerUltimaDireccion() {
        try {
            Path archivo = archivoUltimaDireccion();
            if (!Files.exists(archivo)) return null;
            String guardada = Files.readString(archivo).trim();
            return guardada.isEmpty() ? null : guardada;
        } catch (IOException noSePudoLeer) {
            return null;
        }
    }

    static void guardarUltimaDireccion(String direccion) {
        try {
            Files.writeString(archivoUltimaDireccion(), direccion + System.lineSeparator());
        } catch (IOException noSePudoEscribir) {
            // Carpeta de solo lectura: la va a tener que escribir de nuevo.
        }
    }

    /** null si no hay credenciales, si nadie hostea o si Drive no contesta. */
    static String destinoSegunDrive() {
        // getAccessToken() corta el programa si no hay credenciales, asi que se
        // comprueba antes: un jugador puede no haber conectado Drive nunca.
        if (!Files.exists(AuthSetup.TOKEN_FILE)) return null;

        try {
            System.out.println("\nBuscando quien esta hosteando...");
            String token = AuthSetup.getAccessToken();
            Properties cfg = Red.configSiExiste();
            String nombreCarpeta = cfg == null
                ? "Minecraft Backups"
                : cfg.getProperty("drive.folder", "Minecraft Backups");

            String carpeta = Drive.buscarOCrearCarpeta(token, nombreCarpeta);
            String duenio = Sesion.duenioActual(token, carpeta);

            if (duenio == null) {
                System.out.println("  Nadie esta hosteando en este momento.");
                return null;
            }

            String direccion = Sesion.direccionActual(token, carpeta);
            if (direccion == null) {
                System.out.println("  Hostea " + duenio + ", pero no publico su direccion.");
                return null;
            }

            System.out.println("  Hostea " + duenio);
            System.out.println("  Direccion: " + direccion);
            return direccion;

        } catch (Exception noSePudo) {
            System.out.println("  No se pudo consultar Drive: " + noSePudo.getMessage());
            return null;
        }
    }

    static final String SIN_DESTINO = """

        No se pudo averiguar a que server conectarse.

        Pasa la direccion como argumento:

          java -jar mcbackup.jar conectar algo.gl.at.ply.gg

        O dejala fija en backup.properties para no escribirla nunca mas:

          playit.hostname=algo.gl.at.ply.gg

        Si el grupo tiene la cuenta de Drive conectada, quien hospeda publica
        su direccion sola al abrir el server: en ese caso alcanza con correr
        'conectar' sin nada, pero primero alguien tiene que estar hosteando.
        """;

    static final String NO_RESPONDE = """

        El server no responde por ninguna ruta.

        Casi siempre es una de estas:

          1. Nadie esta hosteando en este momento.
          2. Quien hospeda tiene el programa de playit cerrado.
          3. La direccion esta mal escrita.

        No es un problema de rutas: no hay nada que ajustar de tu lado.
        """;
}
