package nl.profileservice;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public final class ProfilePlugin extends JavaPlugin implements Listener {

    private static final long AUTOSAVE_TICKS = 20 * 60;

    private ProfileService profiles;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        profiles = new ProfileService(
                getConfig().getString("mongo.uri"),
                getConfig().getString("mongo.database"),
                getLogger());
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTaskTimer(this, profiles::autosave, AUTOSAVE_TICKS, AUTOSAVE_TICKS);
    }

    @Override
    public void onDisable() {
        if (profiles != null)
            profiles.close();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED)
            return;
        try {
            profiles.load(event.getUniqueId(), event.getName());
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to load profile of " + event.getName(), e);
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    Component.text("Profiel kan niet geladen worden, probeer het opnieuw."));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        profiles.unload(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Profile victimProfile = profiles.get(victim.getUniqueId());
        if (victimProfile != null)
            victimProfile.addDeath();

        Player killer = victim.getKiller();
        if (killer == null || killer == victim)
            return;
        Profile killerProfile = profiles.get(killer.getUniqueId());
        if (killerProfile != null)
            killerProfile.addKill();
    }

    public ProfileService profiles() {
        return profiles;
    }
}
