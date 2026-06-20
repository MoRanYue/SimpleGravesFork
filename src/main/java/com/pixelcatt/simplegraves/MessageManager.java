package com.pixelcatt.simplegraves;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;


public class MessageManager {

    private final SimpleGraves plugin;
    private final Map<String, String> defaults = new HashMap<>();


    public MessageManager(SimpleGraves plugin) {
        this.plugin = plugin;
        registerDefaults();
    }


    public void reload() {
        plugin.reloadConfig();
    }


    // ------------------------------------------------------------ \\
    //  Public API
    // ------------------------------------------------------------ \\

    /**
     * Get a formatted message string from config.
     * Placeholders are specified as key-value pairs in the varargs:
     * e.g. getMessage("cmd.no_grave_with_number", "number", "5")
     */
    public String getMessage(String path, String... placeholders) {
        String msg = plugin.getConfig().getString("messages." + path);
        if (msg == null) {
            msg = defaults.get(path);
        }

        if (msg == null) {
            return "§cMissing message: " + path;
        }

        return applyPlaceholders(msg, placeholders);
    }

    /**
     * Send a formatted message to a Player.
     */
    public void sendMessage(Player player, String path, String... placeholders) {
        String msg = getMessage(path, placeholders);
        if (msg != null && !msg.isEmpty()) {
            player.sendMessage(msg);
        }
    }

    /**
     * Send a formatted message to a CommandSender.
     */
    public void sendMessage(CommandSender sender, String path, String... placeholders) {
        String msg = getMessage(path, placeholders);
        if (msg != null && !msg.isEmpty()) {
            sender.sendMessage(msg);
        }
    }

    /**
     * Get a tellraw JSON string with placeholder replacement,
     * then execute via console command.
     */
    public void sendTellrawMessage(Player player, String path, String... placeholders) {
        String json = plugin.getConfig().getString("messages." + path);
        if (json == null) {
            json = defaults.get(path);
        }
        if (json == null) {
            player.sendMessage("§cMissing tellraw message: " + path);
            return;
        }

        json = applyPlaceholders(json, placeholders);
        plugin.executeConsoleCommand("tellraw " + player.getName() + " " + json);
    }

    /**
     * Get a plain JSON string from config (for complex component-based messages).
     */
    public String getRawJson(String path, String... placeholders) {
        String json = plugin.getConfig().getString("messages." + path);
        if (json == null) {
            json = defaults.get(path);
        }
        if (json == null) return null;
        return applyPlaceholders(json, placeholders);
    }


    // ------------------------------------------------------------ \\
    //  Internal
    // ------------------------------------------------------------ \\

    private String applyPlaceholders(String message, String... placeholders) {
        if (placeholders == null || placeholders.length == 0) {
            return message;
        }

        for (int i = 0; i < placeholders.length - 1; i += 2) {
            String key = placeholders[i];
            String value = placeholders[i + 1];
            if (key != null && value != null) {
                message = message.replace("%" + key + "%", value);
            }
        }

        return message;
    }

    private void registerDefaults() {
        // Death messages
        defaults.put("death.lucky_day", "§aIt's your Lucky Day!");
        defaults.put("death.no_safe_location", "§cSimpleGraves was unable to place your Grave!");
        defaults.put("death.keep_items", "§aBecause of this, you can keep your Items!");
        defaults.put("death.grave_at_respawn", "§aYour grave will be placed at your respawn point!");

        // Grave creation messages
        defaults.put("grave.failed_create", "§cFailed to create Grave!");
        defaults.put("grave.location_tellraw",
                "[{\"text\":\"Your Grave \",\"color\":\"white\"},{\"text\":\"#%number%\",\"color\":\"gold\"},{\"text\":\" is Located at \",\"color\":\"white\"},{\"text\":\"%coords%\",\"color\":\"gold\"},{\"text\":\" in \",\"color\":\"white\"},%world_json%]");
        defaults.put("grave.world_overworld", "{\"text\":\"The Overworld\",\"color\":\"green\"}");
        defaults.put("grave.world_nether", "{\"text\":\"The Nether\",\"color\":\"red\"}");
        defaults.put("grave.world_end", "{\"text\":\"The End\",\"color\":\"#ffffaa\"}");
        defaults.put("grave.world_custom", "{\"text\":\"%name%\",\"color\":\"light_purple\"}");

        // Block break messages
        defaults.put("block_break.cannot_break_others", "§cYou cannot break other Player's Graves!");

        // Command messages
        defaults.put("cmd.player_only", "§cOnly Players can run this Command!");
        defaults.put("cmd.no_permission", "§cYou don't have permission to use this command.");
        defaults.put("cmd.usage_graveinfo", "Usage: /graveinfo <number>");
        defaults.put("cmd.usage_graveitems", "Usage: /graveitems <number>");
        defaults.put("cmd.usage_graveadmin", "Usage: /graveadmin <go|list|info|items|remove> [<player>] [<number>]");
        defaults.put("cmd.grave_must_be_number", "§cGrave must be a Number.");
        defaults.put("cmd.no_grave_with_number", "§cYou don't have a Grave with Number #%number%");
        defaults.put("cmd.failed_grave_location", "§cFailed to retrieve the Grave Location");
        defaults.put("cmd.grave_info", "§aGrave #%number% is Located at:\n§9World: §c%world%\n§9X: §c%x%\n§9Y: §c%y%\n§9Z: §c%z%");
        defaults.put("cmd.grave_no_items", "§cGrave #%number% has no Items!");
        defaults.put("cmd.grave_items_header", "§aGrave #%number% has the following Items:");
        defaults.put("cmd.cannot_use_star", "§cYou can only use Player * with the remove Command.");
        defaults.put("cmd.removed_all_graves_all", "§aRemoved all Graves of all Players.");
        defaults.put("cmd.removed_all_graves_number", "§aRemoved all Graves with Number #%number%.");
        defaults.put("cmd.no_grave_other", "§c%player% doesn't have a Grave with Number #%number%");
        defaults.put("cmd.teleported_to_grave", "§aTeleported to %player%'s Grave #%number%");
        defaults.put("cmd.failed_grave_location_other", "§cFailed to retrieve the grave location or world.");
        defaults.put("cmd.no_graves_player", "§c%player% currently has no Graves.");
        defaults.put("cmd.has_graves_header", "§a%player% has the following Graves:");
        defaults.put("cmd.player_not_found", "§cPlayer '%player%' not found.");
        defaults.put("cmd.removed_all_graves_player", "§aRemoved all Graves of %player%.");
        defaults.put("cmd.removed_grave_player", "§aRemoved %player%'s Grave #%number%");

        // Update notification messages
        // Each message includes the [SimpleGraves] prefix as the first elements
        defaults.put("update.available_json",
                "[{\"text\":\"[\",\"color\":\"red\",\"bold\":true},{\"text\":\"Simple\",\"color\":\"green\",\"bold\":true},{\"text\":\"Graves\",\"color\":\"blue\",\"bold\":true},{\"text\":\"]\",\"color\":\"red\",\"bold\":true},{\"text\":\" \",\"color\":\"white\"},{\"text\":\"A new Update is available!\",\"color\":\"gold\"}]");
        defaults.put("update.click_download_json",
                "[{\"text\":\"[\",\"color\":\"red\",\"bold\":true},{\"text\":\"Simple\",\"color\":\"green\",\"bold\":true},{\"text\":\"Graves\",\"color\":\"blue\",\"bold\":true},{\"text\":\"]\",\"color\":\"red\",\"bold\":true},{\"text\":\" \",\"color\":\"white\"},{\"text\":\"Click here to Download it!\",\"color\":\"aqua\",\"underlined\":true,\"clickEvent\":{\"action\":\"open_url\",\"value\":\"https://modrinth.com/plugin/simple_graves/versions\"}}]");
    }
}
