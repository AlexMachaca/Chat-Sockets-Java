import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChatServer {
    private static final int PORT = 5000;
    private static Map<String, PrintWriter> connectedClients = new ConcurrentHashMap<>();
    private static DatabaseManager dbManager;

    public static void main(String[] args) {
        dbManager = new DatabaseManager();
        dbManager.resetAllUsersOffline();
        System.out.println("Iniciando servidor en el puerto " + PORT + "...");
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Servidor escuchando en el puerto " + PORT + ". Esperando conexiones...");
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Nuevo cliente: " + clientSocket.getInetAddress().getHostAddress());
                new ClientHandler(clientSocket).start();
            }
        } catch (IOException e) {
            System.err.println("Error al iniciar el servidor: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (dbManager != null) dbManager.close();
        }
    }

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
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                username = in.readLine();
                if (username == null || username.trim().isEmpty()) {
                    out.println("ERROR:Username vacio.");
                    socket.close();
                    return;
                }
                username = username.trim();

                if (!username.matches("[a-zA-Z0-9_]{1,20}")) {
                    out.println("ERROR:Username invalido. Solo letras, numeros y _ (max 20 caracteres).");
                    socket.close();
                    return;
                }

                if (connectedClients.containsKey(username)) {
                    out.println("ERROR:El usuario '" + username + "' ya esta conectado.");
                    socket.close();
                    return;
                }

                System.out.println("Usuario '" + username + "' conectado.");
                dbManager.registerOrUpdateUser(username);
                sendHistory();

                connectedClients.put(username, out);
                broadcast("--- " + username + " se ha unido al chat ---");
                broadcastUserList();

                String message;
                while ((message = in.readLine()) != null) {
                    if (message.startsWith("@")) {
                        handlePrivateMessage(message);
                    } else {
                        System.out.println(username + ": " + message);
                        dbManager.saveMessage(username, "ALL", message, "PUBLIC");
                        broadcast(username + ": " + message);
                    }
                }
            } catch (IOException e) {
                System.err.println("Error con el cliente '" + username + "': " + e.getMessage());
            } finally {
                if (username != null) {
                    connectedClients.remove(username);
                    dbManager.setUserOffline(username);
                    broadcast("--- " + username + " ha abandonado el chat ---");
                    broadcastUserList();
                }
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private void sendHistory() {
            out.println("--- Historial publico (ultimos 50 mensajes) ---");
            List<org.bson.Document> publicHistory = dbManager.getRecentMessages(50);
            for (org.bson.Document doc : publicHistory) {
                out.println(doc.getString("sender") + ": " + doc.getString("content"));
            }

            List<org.bson.Document> privateHistory = dbManager.getPrivateMessages(username, 30);
            if (!privateHistory.isEmpty()) {
                out.println("--- Tus mensajes privados recientes ---");
                for (org.bson.Document doc : privateHistory) {
                    String sender = doc.getString("sender");
                    String receiver = doc.getString("receiver");
                    String content = doc.getString("content");
                    if (sender.equals(username)) {
                        out.println("[Privado para " + receiver + "]: " + content);
                    } else {
                        out.println("[Privado de " + sender + "]: " + content);
                    }
                }
            }

            out.println("--- Fin del historial ---");
        }

        private void handlePrivateMessage(String message) {
            int firstSpace = message.indexOf(" ");
            if (firstSpace > 1) {
                String targetUser = message.substring(1, firstSpace);
                String privateMsg = message.substring(firstSpace + 1);

                PrintWriter targetOut = connectedClients.get(targetUser);
                if (targetOut != null) {
                    dbManager.saveMessage(username, targetUser, privateMsg, "PRIVATE");
                    targetOut.println("[Privado de " + username + "]: " + privateMsg);
                    out.println("[Privado para " + targetUser + "]: " + privateMsg);
                } else {
                    out.println("--- Error: El usuario '" + targetUser + "' no esta conectado ---");
                }
            } else {
                out.println("--- Formato incorrecto. Usa: @usuario mensaje ---");
            }
        }

        private void broadcast(String message) {
            for (PrintWriter writer : connectedClients.values()) {
                writer.println(message);
            }
        }

        private void broadcastUserList() {
            String userList = "/userlist " + String.join(",", connectedClients.keySet());
            broadcast(userList);
        }
    }
}