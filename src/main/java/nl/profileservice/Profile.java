package nl.profileservice;

import org.bson.Document;

import java.time.Instant;
import java.util.UUID;

public final class Profile {

    private final UUID uuid;
    private final String name;
    private final Instant firstJoin;
    private final Instant lastLogin;
    private final int logins;
    private int kills;
    private int deaths;
    private boolean dirty;

    Profile(Document doc) {
        uuid = UUID.fromString(doc.getString("_id"));
        name = doc.getString("name");
        firstJoin = doc.getDate("firstJoin").toInstant();
        lastLogin = doc.getDate("lastLogin").toInstant();
        logins = doc.getInteger("logins");
        kills = doc.getInteger("kills");
        deaths = doc.getInteger("deaths");
    }

    Document snapshot() {
        dirty = false;
        return new Document("kills", kills)
                .append("deaths", deaths);
    }

    boolean dirty() {
        return dirty;
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public Instant firstJoin() {
        return firstJoin;
    }

    public Instant lastLogin() {
        return lastLogin;
    }

    public int logins() {
        return logins;
    }

    public int kills() {
        return kills;
    }

    public int deaths() {
        return deaths;
    }

    public void addKill() {
        kills++;
        dirty = true;
    }

    public void addDeath() {
        deaths++;
        dirty = true;
    }
}
