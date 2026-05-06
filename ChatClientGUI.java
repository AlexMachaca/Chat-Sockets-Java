import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;
import javafx.stage.Stage;

import java.io.*;
import java.net.Socket;
import java.util.Optional;

public class ChatClientGUI extends Application {
    private static final String SERVER_IP = "localhost";
    private static final int SERVER_PORT = 5000;

    private VBox chatBox;
    private ScrollPane scrollPane;
    private TextField messageField;
    private ListView<String> userListView;
    private PrintWriter out;
    private Socket socket;
    private String username;

    @Override
    public void start(Stage primaryStage) {
        if (!promptUsername()) {
            Platform.exit();
            return;
        }

        primaryStage.setTitle("JavaFX Chat - " + username);

        // Sidebar de usuarios
        Label usersLabel = new Label("Usuarios Online");
        usersLabel.setStyle("-fx-font-weight: bold; -fx-padding: 10;");
        userListView = new ListView<>();
        userListView.getStyleClass().add("user-list");
        
        VBox sidebar = new VBox(usersLabel, userListView);
        sidebar.setPrefWidth(180);
        sidebar.getStyleClass().add("sidebar");

        // Area de chat
        chatBox = new VBox(15);
        chatBox.getStyleClass().add("chat-container");
        
        scrollPane = new ScrollPane(chatBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
        // Auto-scroll al final
        chatBox.heightProperty().addListener((obs, oldVal, newVal) -> scrollPane.setVvalue(1.0));

        // Area de entrada
        messageField = new TextField();
        messageField.setPromptText("Escribe algo...");
        messageField.getStyleClass().add("message-input");
        HBox.setHgrow(messageField, Priority.ALWAYS);

        Button sendButton = new Button("Enviar");
        sendButton.getStyleClass().add("send-button");
        sendButton.setOnAction(e -> sendMessage());
        messageField.setOnAction(e -> sendMessage());

        HBox inputArea = new HBox(10, messageField, sendButton);
        inputArea.getStyleClass().add("input-area");
        inputArea.setAlignment(Pos.CENTER);

        // Layout Principal
        BorderPane mainLayout = new BorderPane();
        mainLayout.getStyleClass().add("main-container");
        mainLayout.setLeft(sidebar);
        mainLayout.setCenter(scrollPane);
        mainLayout.setBottom(inputArea);

        Scene scene = new Scene(mainLayout, 800, 550);
        
        // Cargar CSS
        File cssFile = new File("style.css");
        if (cssFile.exists()) {
            scene.getStylesheets().add(cssFile.toURI().toString());
        }

        primaryStage.setScene(scene);
        connectToServer();

        primaryStage.setOnCloseRequest(event -> {
            closeConnection();
            Platform.exit();
            System.exit(0);
        });

        primaryStage.show();
        messageField.requestFocus();
    }

    private boolean promptUsername() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Bienvenido");
        dialog.setHeaderText("Configuracion de Usuario");
        dialog.setContentText("Nombre de usuario:");
        
        File cssFile = new File("style.css");
        if (cssFile.exists()) {
            dialog.getDialogPane().getStylesheets().add(cssFile.toURI().toString());
        }

        Optional<String> result = dialog.showAndWait();
        if (result.isPresent() && !result.get().trim().isEmpty()) {
            username = result.get().trim();
            return true;
        }
        return false;
    }

    private void connectToServer() {
        try {
            socket = new Socket(SERVER_IP, SERVER_PORT);
            out = new PrintWriter(socket.getOutputStream(), true);
            out.println(username);

            new Thread(new IncomingMessagesHandler()).start();
            addSystemMessage("Bienvenido al chat, " + username);

        } catch (IOException e) {
            addSystemMessage("Error de conexion: " + e.getMessage());
        }
    }

    private void sendMessage() {
        String message = messageField.getText().trim();
        if (!message.isEmpty() && out != null) {
            out.println(message);
            messageField.clear();
        }
    }

    private void addSystemMessage(String msg) {
        Platform.runLater(() -> {
            Label label = new Label(msg);
            label.getStyleClass().add("system-message");
            HBox wrapper = new HBox(label);
            wrapper.setAlignment(Pos.CENTER);
            chatBox.getChildren().add(wrapper);
        });
    }

    private void addMessageBubble(String rawMessage) {
        Platform.runLater(() -> {
            boolean isSentByMe = rawMessage.startsWith(username + ":");
            boolean isPrivate = rawMessage.contains("[Privado");
            
            String displayMessage = rawMessage;
            if (isSentByMe && !isPrivate) {
                displayMessage = rawMessage.substring(username.length() + 1).trim();
            }

            VBox bubble = new VBox(5);
            bubble.getStyleClass().add("bubble");
            
            if (isSentByMe) {
                bubble.getStyleClass().add("bubble-sent");
                bubble.setAlignment(Pos.CENTER_RIGHT);
            } else {
                bubble.getStyleClass().add("bubble-received");
                bubble.setAlignment(Pos.CENTER_LEFT);
            }

            if (isPrivate) {
                bubble.getStyleClass().add("bubble-private");
            }

            Text text = new Text(displayMessage);
            text.getStyleClass().add("message-text");
            text.setWrappingWidth(300);
            
            if (isSentByMe && !isPrivate) {
                text.getStyleClass().add("sent-text");
            } else {
                text.getStyleClass().add("received-text");
            }

            bubble.getChildren().add(text);

            HBox wrapper = new HBox(bubble);
            wrapper.setPadding(new Insets(0, 5, 0, 5));
            if (isSentByMe) {
                wrapper.setAlignment(Pos.CENTER_RIGHT);
            } else {
                wrapper.setAlignment(Pos.CENTER_LEFT);
            }

            chatBox.getChildren().add(wrapper);
        });
    }

    private void closeConnection() {
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) { e.printStackTrace(); }
    }

    private class IncomingMessagesHandler implements Runnable {
        @Override
        public void run() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                String serverMessage;
                while ((serverMessage = in.readLine()) != null) {
                    final String msg = serverMessage;
                    if (msg.startsWith("/userlist ")) {
                        String[] users = msg.substring(10).split(",");
                        Platform.runLater(() -> {
                            userListView.setItems(FXCollections.observableArrayList(users));
                        });
                    } else if (msg.startsWith("---")) {
                        addSystemMessage(msg);
                    } else {
                        addMessageBubble(msg);
                    }
                }
            } catch (IOException e) {
                addSystemMessage("Conexion perdida.");
            }
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
