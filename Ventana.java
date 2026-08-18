import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

/**
 * La cara grafica del puente, para cuando se abre con doble clic.
 *
 * Al hacer doble clic en el jar, Windows lo abre con javaw, que no trae
 * consola: todo lo que el programa imprima se pierde y el usuario ve que "no
 * pasa nada". Esta clase da una ventana que hace las veces de consola, y un
 * cuadro de dialogo para pedir la direccion.
 *
 * Swing viene con Java, asi que no agrega ninguna dependencia.
 */
public class Ventana {

    static JTextArea texto;

    /**
     * Pide la direccion del server. Devuelve null si la persona cancela.
     *
     * @param sugerida la ultima que uso, para que solo tenga que dar Enter.
     */
    static String pedirDireccion(String sugerida) throws Exception {
        final String[] respuesta = new String[1];

        SwingUtilities.invokeAndWait(() -> respuesta[0] = (String) JOptionPane.showInputDialog(
            null,
            """
            Pega aca la direccion del server.

            Es la que te paso quien esta hosteando, del estilo
            algo.gl.at.ply.gg
            """,
            "Conectar al server",
            JOptionPane.QUESTION_MESSAGE,
            null, null,
            sugerida == null ? "" : sugerida));

        String elegida = respuesta[0];
        return elegida == null || elegida.isBlank() ? null : elegida.trim();
    }

    /**
     * Abre la ventana de estado y devuelve un flujo que escribe en ella.
     *
     * Se engancha en System.out, asi el resto del programa sigue usando
     * println y no se entera de que hay una ventana.
     */
    static PrintStream abrirConsola(String titulo) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            texto = new JTextArea(22, 64);
            texto.setEditable(false);
            texto.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
            texto.setMargin(new java.awt.Insets(8, 8, 8, 8));

            JFrame ventana = new JFrame(titulo);
            ventana.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            ventana.setLayout(new BorderLayout());
            ventana.add(new JScrollPane(texto), BorderLayout.CENTER);
            ventana.setMinimumSize(new Dimension(560, 420));
            ventana.pack();
            ventana.setLocationRelativeTo(null);
            ventana.setVisible(true);
        });

        return new PrintStream(new OutputStream() {
            @Override
            public void write(int b) {
                escribir(new String(new byte[] {(byte) b}, StandardCharsets.UTF_8));
            }

            @Override
            public void write(byte[] datos, int desde, int cuantos) {
                escribir(new String(datos, desde, cuantos, StandardCharsets.UTF_8));
            }
        }, true, StandardCharsets.UTF_8);
    }

    static void escribir(String linea) {
        SwingUtilities.invokeLater(() -> {
            if (texto == null) return;
            texto.append(linea);
            texto.setCaretPosition(texto.getDocument().getLength());
        });
    }

    /** Un cartel de error, para los cortes cuando no hay consola donde avisar. */
    static void error(String mensaje) {
        try {
            SwingUtilities.invokeAndWait(() -> JOptionPane.showMessageDialog(
                null, mensaje, "No se pudo conectar", JOptionPane.WARNING_MESSAGE));
        } catch (Exception noSePudoMostrar) {
            System.err.println(mensaje);
        }
    }

    /** Si esta maquina puede mostrar ventanas (en un server sin pantalla, no). */
    static boolean hayPantalla() {
        return !java.awt.GraphicsEnvironment.isHeadless();
    }
}
