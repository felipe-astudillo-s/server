import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Cliente del protocolo RCON de Minecraft (Source RCON).
 *
 * Sirve para mandarle ordenes al server sin tocar la consola. En un backup se
 * usa para desactivar el guardado un momento: si copiamos la carpeta del mundo
 * mientras el server escribe, el zip puede quedar corrupto y uno no se entera
 * hasta que necesita restaurarlo.
 *
 * Formato del paquete (todos los enteros son little-endian):
 *   int32 largo | int32 id | int32 tipo | cuerpo ASCII | 0x00 | 0x00
 */
public class Rcon implements AutoCloseable {

    private static final int TIPO_AUTH = 3;
    private static final int TIPO_AUTH_RESPUESTA = 2;
    private static final int TIPO_COMANDO = 2;

    private static final int TIMEOUT_MS = 10_000;
    private static final int LARGO_MAXIMO = 4096;

    private final Socket socket;
    private final DataOutputStream salida;
    private final DataInputStream entrada;
    private int siguienteId = 1;

    public Rcon(String host, int puerto, String password) throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, puerto), TIMEOUT_MS);
        socket.setSoTimeout(TIMEOUT_MS);
        salida = new DataOutputStream(socket.getOutputStream());
        entrada = new DataInputStream(socket.getInputStream());
        autenticar(password);
    }

    private void autenticar(String password) throws IOException {
        int id = siguienteId++;
        enviar(id, TIPO_AUTH, password);

        // Algunos servers mandan un paquete vacio antes de la respuesta real.
        while (true) {
            Paquete p = recibir();
            if (p.tipo() != TIPO_AUTH_RESPUESTA) continue;
            // La contrasena incorrecta se responde con id = -1.
            if (p.id() == -1) {
                throw new IOException("RCON rechazo la contrasena (revisa rcon.password)");
            }
            return;
        }
    }

    /** Manda un comando y devuelve lo que el server haya respondido. */
    public String comando(String cmd) throws IOException {
        int id = siguienteId++;
        enviar(id, TIPO_COMANDO, cmd);
        return recibir().cuerpo();
    }

    private void enviar(int id, int tipo, String cuerpo) throws IOException {
        byte[] bytes = cuerpo.getBytes(StandardCharsets.US_ASCII);
        int largo = 4 + 4 + bytes.length + 2;   // id + tipo + cuerpo + dos nulos

        ByteBuffer buf = ByteBuffer.allocate(4 + largo).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(largo).putInt(id).putInt(tipo).put(bytes).put((byte) 0).put((byte) 0);

        salida.write(buf.array());
        salida.flush();
    }

    private Paquete recibir() throws IOException {
        int largo = leerIntLE();
        if (largo < 10 || largo > LARGO_MAXIMO) {
            throw new IOException("RCON devolvio un paquete de largo invalido: " + largo);
        }
        byte[] resto = new byte[largo];
        entrada.readFully(resto);

        ByteBuffer buf = ByteBuffer.wrap(resto).order(ByteOrder.LITTLE_ENDIAN);
        int id = buf.getInt();
        int tipo = buf.getInt();
        // El cuerpo va hasta los dos bytes nulos del final.
        String cuerpo = new String(resto, 8, largo - 8 - 2, StandardCharsets.US_ASCII);
        return new Paquete(id, tipo, cuerpo);
    }

    private int leerIntLE() throws IOException {
        byte[] b = new byte[4];
        entrada.readFully(b);
        return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException ignorado) {
            // cerrando de todas formas
        }
    }

    private record Paquete(int id, int tipo, String cuerpo) {}
}
