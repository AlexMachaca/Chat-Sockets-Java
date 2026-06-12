import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.UpdateOptions;
import org.bson.Document;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Properties;

public class DatabaseManager {
    private MongoClient mongoClient;
    private MongoDatabase database;
    private MongoCollection<Document> messagesCollection;
    private MongoCollection<Document> usersCollection;

    public DatabaseManager() {
        try {
            Properties props = new Properties();
            try (InputStream input = getClass().getClassLoader().getResourceAsStream("config.properties")) {
                if (input == null) throw new IOException("config.properties no encontrado en resources/");
                props.load(input);
            }
            String connectionString = props.getProperty("mongodb.connection_string");
            String databaseName = props.getProperty("mongodb.database_name");

            mongoClient = MongoClients.create(connectionString);
            database = mongoClient.getDatabase(databaseName);
            messagesCollection = database.getCollection("messages");
            usersCollection = database.getCollection("users");
            createIndexes();
            System.out.println("Conexión exitosa a MongoDB Atlas.");
        } catch (Exception e) {
            System.err.println("Error al conectar con MongoDB: " + e.getMessage());
        }
    }

    private void createIndexes() {
        // messages: ordenar por tiempo y buscar conversaciones privadas
        messagesCollection.createIndex(Indexes.descending("timestamp"));
        messagesCollection.createIndex(Indexes.compoundIndex(
                Indexes.ascending("sender"),
                Indexes.ascending("receiver")
        ));

        // users: username único
        usersCollection.createIndex(Indexes.ascending("username"), new IndexOptions().unique(true));
    }

    public void saveMessage(String sender, String receiver, String content, String type) {
        Document message = new Document("sender", sender)
                .append("receiver", receiver)
                .append("content", content)
                .append("type", type)
                .append("timestamp", new Date());
        messagesCollection.insertOne(message);
    }

    public List<Document> getRecentMessages(int limit) {
        List<Document> result = messagesCollection.find(new Document("type", "PUBLIC"))
                .sort(Sorts.descending("timestamp"))
                .limit(limit)
                .into(new ArrayList<>());
        Collections.reverse(result);
        return result;
    }

    public List<Document> getPrivateMessages(String username, int limit) {
        List<Document> result = messagesCollection.find(
                Filters.and(
                        Filters.eq("type", "PRIVATE"),
                        Filters.or(
                                Filters.eq("sender", username),
                                Filters.eq("receiver", username)
                        )
                ))
                .sort(Sorts.descending("timestamp"))
                .limit(limit)
                .into(new ArrayList<>());
        Collections.reverse(result);
        return result;
    }

    public void registerOrUpdateUser(String username) {
        Document userFilter = new Document("username", username);
        Document update = new Document()
                .append("$set", new Document("last_seen", new Date()).append("status", "online"))
                .append("$setOnInsert", new Document("username", username).append("first_connected", new Date()));
        usersCollection.updateOne(userFilter, update, new UpdateOptions().upsert(true));
    }

    public void setUserOffline(String username) {
        Document userFilter = new Document("username", username);
        Document update = new Document("$set", new Document("status", "offline").append("last_seen", new Date()));
        usersCollection.updateOne(userFilter, update);
    }

    public void resetAllUsersOffline() {
        Document update = new Document("$set", new Document("status", "offline"));
        usersCollection.updateMany(new Document(), update);
        System.out.println("Estado de todos los usuarios reseteado a offline.");
    }

    public void close() {
        if (mongoClient != null) {
            mongoClient.close();
        }
    }
}
