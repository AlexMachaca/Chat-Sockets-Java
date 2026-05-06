import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChatServer {
    private static final int PORT = 5000;
    // Mapa para asociar nombres de usuario con sus respectivos flujos de salida
    private static Map<String, PrintWriter> connectedClients = new ConcurrentHashMap<>();

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
                
                if (username == null || username.trim().isEmpty()) {
                    socket.close();
                    return;
                }

                System.out.println("¡El usuario '" + username + "' se ha conectado desde " + socket.getInetAddress().getHostAddress() + "!");

                // Agregar al mapa de clientes conectados
                connectedClients.put(username, out);
                broadcast("--- " + username + " se ha unido al chat ---");
                broadcastUserList();

                String message;
                // Leer mensajes del cliente y procesarlos
                while ((message = in.readLine()) != null) {
                    if (message.startsWith("@")) {
                        handlePrivateMessage(message);
                    } else {
                        System.out.println(username + ": " + message);
                        broadcast(username + ": " + message);
                    }
                }
            } catch (IOException e) {
                System.err.println("Error en la conexión con el cliente '" + username + "': " + e.getMessage());
            } finally {
                // Limpiar recursos al desconectarse el cliente
                if (username != null) {
                    connectedClients.remove(username);
                    broadcast("--- " + username + " ha abandonado el chat ---");
                    broadcastUserList();
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
         * Envía la lista actualizada de usuarios a todos los clientes.
         */
        private void broadcastUserList() {
            String userListMessage = "/userlist " + String.join(",", connectedClients.keySet());
            broadcast(userListMessage);
        }

        /**
         * Procesa un mensaje privado con el formato @usuario mensaje
         */
        private void handlePrivateMessage(String message) {
            int firstSpace = message.indexOf(" ");
            if (firstSpace > 1) {
                String targetUser = message.substring(1, firstSpace);
                String privateMsg = message.substring(firstSpace + 1);
                
                PrintWriter targetOut = connectedClients.get(targetUser);
                if (targetOut != null) {
                    targetOut.println("[Privado de " + username + "]: " + privateMsg);
                    out.println("[Privado para " + targetUser + "]: " + privateMsg);
                } else {
                    out.println("--- Error: El usuario '" + targetUser + "' no está conectado ---");
                }
            } else {
                out.println("--- Formato incorrecto. Usa: @usuario mensaje ---");
            }
        }

        /**
         * Envía un mensaje a todos los clientes conectados.
         */
        private void broadcast(String message) {
            for (PrintWriter writer : connectedClients.values()) {
                writer.println(message);
            }
        }
    }
}
