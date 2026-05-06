import java.io.*;
import java.net.*;
import java.util.*;

public class ChatServer {
    private static final int PORT = 5000;
    // Lista para almacenar los flujos de salida de todos los clientes conectados
    private static Set<PrintWriter> clientWriters = Collections.synchronizedSet(new HashSet<>());

    public static void main(String[] args) {
        System.out.println("Iniciando el servidor de chat en el puerto " + PORT + "...");
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Servidor escuchando en el puerto " + PORT + ". Esperando conexiones...");
            
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("¡Nuevo cliente conectado desde: " + clientSocket.getInetAddress().getHostAddress() + "!");
                
                // Crear un nuevo hilo para manejar la comunicación con este cliente
                new ClientHandler(clientSocket).start();
            }
        } catch (IOException e) {
            System.err.println("Error al iniciar el servidor: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Clase interna para manejar la conexión de cada cliente de forma independiente.
     */
    private static class ClientHandler extends Thread {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String username;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try {
                // Configurar los flujos de entrada y salida
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);
                
                // Leer el nombre de usuario (primer mensaje del cliente)
                username = in.readLine();
                System.out.println("¡El usuario '" + username + "' se ha conectado desde " + socket.getInetAddress().getHostAddress() + "!");

                // Agregar el flujo de salida a la lista global
                clientWriters.add(out);
                broadcast("--- " + username + " se ha unido al chat ---");

                String message;
                // Leer mensajes del cliente y retransmitirlos a los demás
                while ((message = in.readLine()) != null) {
                    System.out.println(username + ": " + message);
                    broadcast(username + ": " + message);
                }
            } catch (IOException e) {
                System.err.println("Error en la conexión con el cliente '" + username + "': " + e.getMessage());
            } finally {
                // Limpiar recursos al desconectarse el cliente
                if (out != null) {
                    clientWriters.remove(out);
                }
                if (username != null) {
                    broadcast("--- " + username + " ha abandonado el chat ---");
                }
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                System.out.println("El usuario '" + username + "' se ha desconectado.");
            }
        }

        /**
         * Envía un mensaje a todos los clientes conectados.
         */
        private void broadcast(String message) {
            synchronized (clientWriters) {
                for (PrintWriter writer : clientWriters) {
                    writer.println(message);
                }
            }
        }
    }
}
