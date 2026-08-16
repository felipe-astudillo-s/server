import com.sun.net.httpserver.HttpServer;

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Setup de autenticacion en un solo paso.
 *
 *   El hoster corre:   java -jar authsetup.jar
 *   ...se le abre el navegador, hace click en "Permitir", y listo.
 *
 *   Server sin navegador (VPS headless):
 *                      java -jar authsetup.jar --manual
 *
 * No necesita instalar nada: el jar corre en cualquier JRE 17+, asi que si
 * puede correr un server de Minecraft, ya tiene todo lo que hace falta.
 * Sin dependencias externas.
 */
public class AuthSetup {

    // -----------------------------------------------------------------------
    // Configuracion
    //
    // El clientId se registra UNA sola vez (por vos, el dueno del proyecto) y
    // queda commiteado aca. Es un "public client": no es una credencial
    // secreta. Lo que protege el flujo es PKCE, no el secreto.
    //
    // Google igual entrega un "client secret" para apps de escritorio, pero su
    // propia documentacion aclara que no se considera confidencial.
    // -----------------------------------------------------------------------

    record Provider(String authUrl, String tokenUrl, String clientId,
                    String clientSecret, String scopes, Map<String, String> extraParams) {}

    static final Map<String, Provider> PROVIDERS = Map.of(
        "google", new Provider(
            "https://accounts.google.com/o/oauth2/v2/auth",
            "https://oauth2.googleapis.com/token",
            // Ver SETUP-AUTH.md: se registra una vez, tipo "App de escritorio".
            "892491461504-fl566n7o9cc2ve936kh9sl6bfprcmoh9.apps.googleusercontent.com",
            "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",
            // drive.file = solo los archivos que crea esta app. Alcanza para
            // subir backups y es scope NO sensible: evita la auditoria de
            // seguridad que Google exige para el scope 'drive' completo.
            "https://www.googleapis.com/auth/drive.file",
            // access_type=offline es lo que hace que Google mande refresh_token
            Map.of("access_type", "offline", "prompt", "consent")),
        "microsoft", new Provider(
            "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize",
            "https://login.microsoftonline.com/consumers/oauth2/v2.0/token",
            "PEGA_TU_APPLICATION_ID",
            "",  // public client puro, sin secreto
            "XboxLive.signin offline_access",
            Map.of())
    );

    static final String DEFAULT_PROVIDER = "google";
    static final Path TOKEN_FILE = Path.of(".auth_tokens.json");
    static final int MANUAL_PORT = 53682;   // puerto fijo solo para el modo manual
    static final int TIMEOUT_SECONDS = 300;

    static final String DONE_PAGE = """
        <!doctype html><meta charset="utf-8"><title>Listo</title>
        <body style="font-family:system-ui;text-align:center;padding-top:15vh">
        <h2>Autenticacion completada</h2>
        <p>Ya podes cerrar esta pestana y volver a la terminal.</p>
        </body>""";

    static final String FAIL_PAGE = """
        <!doctype html><meta charset="utf-8"><title>Fallo</title>
        <body style="font-family:system-ui;text-align:center;padding-top:15vh">
        <h2>No se pudo completar la autenticacion</h2>
        <p>Volve a la terminal para ver el motivo.</p>
        </body>""";

    // -----------------------------------------------------------------------
    // Main
    // -----------------------------------------------------------------------

    public static void main(String[] args) throws Exception {
        String providerName = DEFAULT_PROVIDER;
        boolean manual = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--manual" -> manual = true;
                case "--provider" -> providerName = args[++i];
                case "--help", "-h" -> {
                    System.out.println("""
                        Uso: java -jar authsetup.jar [opciones]

                          --provider google|microsoft   proveedor (por defecto: google)
                          --manual                      para servers sin navegador
                          --help                        esto""");
                    return;
                }
                default -> exit("Argumento desconocido: " + args[i]);
            }
        }

        Provider cfg = PROVIDERS.get(providerName);
        if (cfg == null) exit("Proveedor desconocido: " + providerName);
        if (cfg.clientId().startsWith("PEGA_TU")) {
            exit("""

                 Falta configurar el clientId de '%s' en este archivo.
                 Eso lo hace UNA vez el dueno del proyecto, no cada hoster.
                 """.formatted(providerName));
        }

        String tokenJson = authorize(cfg, manual);
        saveTokens(providerName, tokenJson);

        System.out.println("\nListo. Credenciales guardadas en " + TOKEN_FILE);
        System.out.println("No hace falta volver a correr esto: se renueva solo.\n");
    }

    // -----------------------------------------------------------------------
    // Flujo OAuth
    // -----------------------------------------------------------------------

    static String authorize(Provider cfg, boolean manual) throws Exception {
        String verifier = base64url(randomBytes(32));
        String challenge = base64url(sha256(verifier));
        String state = base64url(randomBytes(16));

        String query;
        String redirectUri;

        if (manual) {
            redirectUri = "http://127.0.0.1:" + MANUAL_PORT;
            String url = buildAuthUrl(cfg, redirectUri, challenge, state);

            System.out.println("\nEste server no tiene navegador. Abri este link en");
            System.out.println("cualquier otra computadora o en tu celular:\n");
            System.out.println(url);
            System.out.println("\nDespues de autorizar, el navegador va a intentar ir a una");
            System.out.println("direccion 127.0.0.1 y va a mostrar error. Eso es esperado.");
            System.out.println("Copia la URL COMPLETA de la barra de direcciones y pegala aca.\n");
            System.out.print("URL: ");

            var reader = new BufferedReader(new InputStreamReader(System.in));
            String pasted = reader.readLine();
            if (pasted == null || pasted.isBlank()) exit("No pegaste nada.");
            query = URI.create(pasted.trim()).getRawQuery();
        } else {
            // puerto 0 = el sistema operativo elige uno libre
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            redirectUri = "http://127.0.0.1:" + server.getAddress().getPort();
            var received = new CompletableFuture<String>();

            server.createContext("/", exchange -> {
                String q = exchange.getRequestURI().getRawQuery();

                // El navegador tambien pide /favicon.ico: lo ignoramos.
                if (q == null || (!q.contains("code=") && !q.contains("error="))) {
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }
                // Se valida aca tambien, y no solo abajo, para no mostrarle
                // "listo" al navegador cuando el callback vino mal o forjado.
                Map<String, String> got = parseQuery(q);
                boolean ok = got.containsKey("code") && state.equals(got.get("state"));

                byte[] body = (ok ? DONE_PAGE : FAIL_PAGE).getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
                exchange.sendResponseHeaders(ok ? 200 : 400, body.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(body);
                }
                received.complete(q);  // el motivo exacto lo reporta el main
            });
            server.start();

            String url = buildAuthUrl(cfg, redirectUri, challenge, state);
            System.out.println("\nAbriendo el navegador para que autorices el acceso...");
            System.out.println("Si no se abre solo, entra a este link:\n");
            System.out.println(url + "\n");
            openBrowser(url);

            try {
                query = received.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (Exception e) {
                exit("No llego la respuesta a tiempo. Proba de nuevo, o usa --manual.");
                return null;
            } finally {
                server.stop(1);
            }
        }

        Map<String, String> params = parseQuery(query);
        if (params.containsKey("error")) exit("\nAutorizacion rechazada: " + params.get("error"));
        if (!state.equals(params.get("state"))) exit("\nEl 'state' no coincide. Corta por seguridad.");

        System.out.println("Codigo recibido, canjeando por el token...");
        return exchangeCode(cfg, params.get("code"), redirectUri, verifier);
    }

    static String buildAuthUrl(Provider cfg, String redirectUri, String challenge, String state) {
        var params = new LinkedHashMap<String, String>();
        params.put("client_id", cfg.clientId());
        params.put("redirect_uri", redirectUri);
        params.put("response_type", "code");
        params.put("scope", cfg.scopes());
        params.put("state", state);
        params.put("code_challenge", challenge);
        params.put("code_challenge_method", "S256");
        params.putAll(cfg.extraParams());
        return cfg.authUrl() + "?" + formEncode(params);
    }

    static String exchangeCode(Provider cfg, String code, String redirectUri, String verifier)
            throws Exception {
        var form = new LinkedHashMap<String, String>();
        form.put("client_id", cfg.clientId());
        form.put("code", code);
        form.put("redirect_uri", redirectUri);
        form.put("grant_type", "authorization_code");
        form.put("code_verifier", verifier);
        if (!cfg.clientSecret().isEmpty()) form.put("client_secret", cfg.clientSecret());
        return postForm(cfg.tokenUrl(), form);
    }

    /** Lo que usa el resto del proyecto. Refresca solo si hace falta. */
    public static String getAccessToken() throws Exception {
        if (!Files.exists(TOKEN_FILE)) {
            exit("No hay credenciales. Corre primero: java -jar authsetup.jar");
        }
        String saved = Files.readString(TOKEN_FILE);
        Provider cfg = PROVIDERS.get(jsonField(saved, "provider"));
        String refreshToken = jsonField(saved, "refresh_token");
        if (refreshToken == null) {
            exit("No se guardo refresh_token. Volve a correr: java -jar authsetup.jar");
        }

        var form = new LinkedHashMap<String, String>();
        form.put("client_id", cfg.clientId());
        form.put("refresh_token", refreshToken);
        form.put("grant_type", "refresh_token");
        if (!cfg.clientSecret().isEmpty()) form.put("client_secret", cfg.clientSecret());

        String fresh = postForm(cfg.tokenUrl(), form);

        // Algunos proveedores rotan el refresh_token en cada uso: hay que guardarlo.
        if (jsonField(fresh, "refresh_token") != null) {
            saveTokens(jsonField(saved, "provider"), fresh);
        }
        return jsonField(fresh, "access_token");
    }

    // -----------------------------------------------------------------------
    // Guardado
    // -----------------------------------------------------------------------

    static void saveTokens(String provider, String tokenJson) throws Exception {
        String accessToken = jsonField(tokenJson, "access_token");
        String refreshToken = jsonField(tokenJson, "refresh_token");

        String out = """
            {
              "provider": "%s",
              "access_token": "%s",
              "refresh_token": "%s"
            }
            """.formatted(provider, nullToEmpty(accessToken), nullToEmpty(refreshToken));

        Files.writeString(TOKEN_FILE, out);
        // En Windows esto no aplica, pero en un VPS Linux importa.
        try {
            Files.setPosixFilePermissions(TOKEN_FILE, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException ignored) {
            // sistema de archivos sin POSIX (Windows): nada que hacer
        }
    }

    // -----------------------------------------------------------------------
    // Utilidades
    // -----------------------------------------------------------------------

    static String postForm(String url, Map<String, String> form) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(formEncode(form)))
            .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
            .send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 400) {
            exit("\nEl proveedor rechazo la peticion (" + response.statusCode() + "):\n" + response.body());
        }
        return response.body();
    }

    static String formEncode(Map<String, String> params) {
        var sb = new StringBuilder();
        params.forEach((k, v) -> {
            if (!sb.isEmpty()) sb.append('&');
            sb.append(URLEncoder.encode(k, StandardCharsets.UTF_8))
              .append('=')
              .append(URLEncoder.encode(v, StandardCharsets.UTF_8));
        });
        return sb.toString();
    }

    static Map<String, String> parseQuery(String query) {
        var result = new LinkedHashMap<String, String>();
        if (query == null) return result;
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) continue;
            result.put(
                java.net.URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                java.net.URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
        }
        return result;
    }

    /**
     * Extractor minimo de campos JSON. Alcanza y sobra: las respuestas OAuth
     * son planas y todos los valores que necesitamos son strings.
     */
    static String jsonField(String json, String key) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        return m.find() ? m.group(1) : null;
    }

    static byte[] randomBytes(int n) {
        byte[] bytes = new byte[n];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }

    static byte[] sha256(String input) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.US_ASCII));
    }

    static String base64url(byte[] raw) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    static void openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
            }
        } catch (Exception ignored) {
            // sin entorno grafico: el link ya quedo impreso arriba
        }
    }

    static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    static void exit(String message) {
        System.err.println(message);
        System.exit(1);
    }
}
