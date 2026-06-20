package com.pixelcatt.simplegraves;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;


public class PlayerJoinListener implements Listener {

    private final SimpleGraves plugin;
    private final GraveManager manager;

    private boolean sendUpdateNotification = false;


    public PlayerJoinListener(SimpleGraves plugin, GraveManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    public void setUpdateAvailable(boolean updateAvailable) {
        this.sendUpdateNotification = updateAvailable;
    }


    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        manager.saveOfflinePlayer(player.getUniqueId(), player.getName());

        if (sendUpdateNotification && player.isOp()) {
            MessageManager mm = plugin.getMessageManager();

            // Send prefix + "A new Update is available!" using tellraw
            mm.sendTellrawMessage(player, "update.available_json");

            // Send "Click here to Download it!" using tellraw
            mm.sendTellrawMessage(player, "update.click_download_json");
        }
    }
}
