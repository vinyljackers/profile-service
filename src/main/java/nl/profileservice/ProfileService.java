package nl.profileservice;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bukkit.Bukkit;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ProfileService {

    private static final Duration STALE_AFTER = Duration.ofMinutes(5);

    private final MongoClient client;
    private final MongoCollection<Document> collection;
    private final Logger logger;
    private final Map<UUID, Profile> online = new ConcurrentHashMap<>();
    private final ExecutorService db = Executors.newSingleThreadExecutor(r -> new Thread(r, "ProfileService-DB"));

    public ProfileService(String uri, String database, Logger logger) {
        this.client = MongoClients.create(uri);
        this.collection = client.getDatabase(database).getCollection("profiles");
        this.logger = logger;
    }

    public void load(UUID uuid, String name) throws Exception {
        Profile profile = db.submit(() -> {
            Date now = new Date();
            Document doc = collection.findOneAndUpdate(
                    Filters.eq("_id", uuid.toString()),
                    Updates.combine(
                            Updates.set("name", name),
                            Updates.set("lastLogin", now),
                            Updates.inc("logins", 1),
                            Updates.setOnInsert("firstJoin", now),
                            Updates.setOnInsert("kills", 0),
                            Updates.setOnInsert("deaths", 0)),
                    new FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER));
            return new Profile(doc);
        }).get();
        online.put(uuid, profile);
    }

    public Profile get(UUID uuid) {
        return online.get(uuid);
    }

    public void unload(UUID uuid) {
        Profile profile = online.remove(uuid);
        if (profile != null)
            save(profile);
    }

    public void autosave() {
        Instant staleBefore = Instant.now().minus(STALE_AFTER);
        online.forEach((uuid, profile) -> {
            if (Bukkit.getPlayer(uuid) == null && profile.lastLogin().isBefore(staleBefore))
                unload(uuid);
            else if (profile.dirty())
                save(profile);
        });
    }

    public void close() {
        online.values().forEach(this::save);
        online.clear();
        db.shutdown();
        try {
            if (!db.awaitTermination(10, TimeUnit.SECONDS))
                logger.severe("Timed out saving profiles");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        client.close();
    }

    private void save(Profile profile) {
        UUID uuid = profile.uuid();
        Document fields = profile.snapshot().append("lastSeen", new Date());
        db.execute(() -> {
            try {
                collection.updateOne(Filters.eq("_id", uuid.toString()), new Document("$set", fields));
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to save profile " + uuid, e);
            }
        });
    }
}
