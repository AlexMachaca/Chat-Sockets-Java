import java.io.*;
import java.net.*;
import java.util.Scanner;

public class ChatClient {
    private static final String SERVER_IP = "localhost";
    private static final int SERVER_PORT = 5000;

    public static void main(String[] args) {
        try (Socket socket = new Socket(SERVER_IP, SERVER_PORT)) {
            System.out.println("Conectado al servidor de chat en " + SERVER_IP + ":" + SERVER_PORT);

            // Hilo para recibir mensajes del servidor
            new Thread(new IncomingMessagesHandler(socket)).start();

            // Hilo principal para enviar mensajes al servidor
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            Scanner scanner = new Scanner(System.in);

            System.out.println("Escribe tu mensaje y presiona Enter (Escribe 'salir' para terminar):");
            while (true) {
                String userInput = scanner.nextLine();
                if ("salir".equalsIgnoreCase(userInput)) {
                    break;
                }
                out.println(userInput);
            }

        } catch (IOException e) {
            System.err.println("Error de conexión: " + e.getMessage());
        }
    }

    /**
     * Tarea interna para escuchar mensajes del servidor de forma asíncrona.
     */
    private static class IncomingMessagesHandler implements Runnable {
        private Socket socket;

        public IncomingMessagesHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                String serverMessage;
                while ((serverMessage = in.readLine()) != null) {
                    System.out.println("\nMensaje del chat: " + serverMessage);
                    System.out.print("> "); // Indicador de escritura para el usuario
                }
            } catch (IOException e) {
                System.out.println("Conexión cerrada por el servidor.");
            }
        }
    }
}
