package com.pixelcatt.simplegraves;

import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;


public class CommandHandler implements CommandExecutor {

    private final SimpleGraves plugin;
    private final GraveManager manager;


    public CommandHandler(SimpleGraves plugin, GraveManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }


    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player)) {
            plugin.getMessageManager().sendMessage(sender, "cmd.player_only");
            return true;
        }

        Player player = (Player) sender;

        manager.saveOfflinePlayer(player.getUniqueId(), player.getName());

        String cmd = command.getName().toLowerCase();

        switch (cmd) {
            case "graveinfo":
                if (!player.hasPermission("simplegraves.graveinfo")) {
                    plugin.getMessageManager().sendMessage(player, "cmd.no_permission");
                    return true;
                }

                if (!(args.length == 1)) {
                    plugin.getMessageManager().sendMessage(player, "cmd.usage_graveinfo");
                    return true;
                }

                return  handleGraveInfo(player, args);

            case "graveitems":
                if (!player.hasPermission("simplegraves.graveitems")) {
                    plugin.getMessageManager().sendMessage(player, "cmd.no_permission");
                    return true;
                }

                if (!(args.length == 1)) {
                    plugin.getMessageManager().sendMessage(player, "cmd.usage_graveitems");
                    return true;
                }

                return  handleGraveItems(player, args);

            case "graveadmin":
                if (!player.hasPermission("simplegraves.graveadmin.show")) {
                    plugin.getMessageManager().sendMessage(player, "cmd.no_permission");
                    return true;
                }

                if (!(args.length == 2 || args.length == 3)) {
                    plugin.getMessageManager().sendMessage(player, "cmd.usage_graveadmin");
                    return true;
                }

                return handleGraveAdmin(player, args);

            default:
                plugin.getMessageManager().sendMessage(player, "cmd.usage_graveadmin");
                return true;
        }
    }

    private boolean handleGraveInfo(Player player, String[] args) {
        UUID targetUUID = player.getUniqueId();
        int graveNumber;

        try {
            graveNumber = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            plugin.getMessageManager().sendMessage(player, "cmd.grave_must_be_number");
            return true;
        }

        final int finalGraveNumber = graveNumber;
        manager.graveExistsUUID(targetUUID, graveNumber).thenAccept(exists -> {
            if (!exists) {
                FoliaHelper.runOnEntity(plugin, player, () ->
                        plugin.getMessageManager().sendMessage(player, "cmd.no_grave_with_number",
                                "number", String.valueOf(finalGraveNumber))
                );
                return;
            }

            manager.getGraveLocation(targetUUID, finalGraveNumber).thenAccept(location -> {
                if (location == null || location.getWorld() == null) {
                    FoliaHelper.runOnEntity(plugin, player, () ->
                            plugin.getMessageManager().sendMessage(player, "cmd.failed_grave_location")
                    );
                    return;
                }

                String worldName;
                switch (location.getWorld().getName()) {
                    case "world" -> worldName = "The Overworld";
                    case "world_nether" -> worldName = "The Nether";
                    case "world_the_end" -> worldName = "The End";
                    default -> worldName = location.getWorld().getName();
                }

                String graveNumStr = String.valueOf(finalGraveNumber);
                String xStr = String.valueOf(Math.floor(location.getX()));
                String yStr = String.valueOf(Math.floor(location.getY()));
                String zStr = String.valueOf(Math.floor(location.getZ()));

                FoliaHelper.runOnEntity(plugin, player, () ->
                        plugin.getMessageManager().sendMessage(player, "cmd.grave_info",
                                "number", graveNumStr,
                                "world", worldName,
                                "x", xStr,
                                "y", yStr,
                                "z", zStr)
                );
            });
        });

        return true;
    }

    private boolean handleGraveItems(Player player, String[] args) {
        UUID targetUUID = player.getUniqueId();
        int graveNumber;

        try {
            graveNumber = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            plugin.getMessageManager().sendMessage(player, "cmd.grave_must_be_number");
            return true;
        }

        final int finalGraveNumber = graveNumber;
        manager.graveExistsUUID(targetUUID, graveNumber).thenAccept(exists -> {
            if (!exists) {
                FoliaHelper.runOnEntity(plugin, player, () ->
                        plugin.getMessageManager().sendMessage(player, "cmd.no_grave_with_number",
                                "number", String.valueOf(finalGraveNumber))
                );
                return;
            }

            manager.getGraveItems(targetUUID, finalGraveNumber).thenAccept(itemStacks -> {
                if (itemStacks.isEmpty()) {
                    plugin.getMessageManager().sendMessage(player, "cmd.grave_no_items",
                            "number", String.valueOf(finalGraveNumber));
                } else {
                    Map<String, Integer> graveItems = new HashMap<>();
                    for (ItemStack itemStack : itemStacks) {
                        graveItems.merge(itemStack.getType().name(), itemStack.getAmount(), Integer::sum);
                    }

                    if (graveItems.isEmpty()) {
                        FoliaHelper.runOnEntity(plugin, player, () ->
                                plugin.getMessageManager().sendMessage(player, "cmd.grave_no_items",
                                        "number", String.valueOf(finalGraveNumber))
                        );
                        return;
                    }

                    StringBuilder itemsMessage = new StringBuilder("§c");
                    for (Map.Entry<String, Integer> entry : graveItems.entrySet()) {
                        if (!itemsMessage.toString().equals("§c")) {
                            itemsMessage.append(", ");
                        }

                        itemsMessage.append(entry.getKey()).append(" (x").append(entry.getValue()).append(")");
                    }

                    FoliaHelper.runOnEntity(plugin, player, () -> {
                        plugin.getMessageManager().sendMessage(player, "cmd.grave_items_header",
                                "number", String.valueOf(finalGraveNumber));
                        player.sendMessage(itemsMessage.toString());
                    });
                }
            });
        });

        return true;
    }

    private boolean handleGraveAdmin(Player sender, String[] args) {
        String action = args[0].toLowerCase();
        String targetNameArg = args[1];
        String numberStr = (args.length >= 3) ? args[2] : "-1";

        switch (action) {
            case "go":
                if (!sender.hasPermission("simplegraves.graveadmin.go")) {
                    plugin.getMessageManager().sendMessage(sender, "cmd.no_permission");
                    return true;
                }
                break;
            case "list":
                if (!sender.hasPermission("simplegraves.graveadmin.list")) {
                    plugin.getMessageManager().sendMessage(sender, "cmd.no_permission");
                    return true;
                }
                break;
            case "info":
                if (!sender.hasPermission("simplegraves.graveadmin.info")) {
                    plugin.getMessageManager().sendMessage(sender, "cmd.no_permission");
                    return true;
                }
                break;
            case "items":
                if (!sender.hasPermission("simplegraves.graveadmin.items")) {
                    plugin.getMessageManager().sendMessage(sender, "cmd.no_permission");
                    return true;
                }
                break;
            case "remove":
                if (!sender.hasPermission("simplegraves.graveadmin.remove")) {
                    plugin.getMessageManager().sendMessage(sender, "cmd.no_permission");
                    return true;
                }
                break;
            default:
                plugin.getMessageManager().sendMessage(sender, "cmd.usage_graveadmin");
                return true;
        }

        final String targetNameFinal = targetNameArg;
        final String numberStrFinal = numberStr;

        if (targetNameFinal.equals("*")) {
            if (!action.equals("remove")) {
                plugin.getMessageManager().sendMessage(sender, "cmd.cannot_use_star");
                return true;
            }

            if (numberStrFinal.equals("*")) {
                manager.removeEveryGrave();
                FoliaHelper.runOnEntity(plugin, sender, () ->
                        plugin.getMessageManager().sendMessage(sender, "cmd.removed_all_graves_all"));
            } else {
                int graveNumber;
                try {
                    graveNumber = Integer.parseInt(numberStrFinal);
                } catch (NumberFormatException e) {
                    plugin.getMessageManager().sendMessage(sender, "cmd.grave_must_be_number");
                    return true;
                }
                final int finalGraveNumber = graveNumber;
                manager.removeAllGravesWithNumber(finalGraveNumber);
                FoliaHelper.runOnEntity(plugin, sender, () ->
                        plugin.getMessageManager().sendMessage(sender, "cmd.removed_all_graves_number",
                                "number", String.valueOf(finalGraveNumber)));
            }
            return true;
        }

        Player onlineTarget = Bukkit.getPlayerExact(targetNameFinal);
        if (onlineTarget != null) {
            final UUID targetUUID = onlineTarget.getUniqueId();

            int graveNumber = -1;
            if (!numberStrFinal.equals("-1") && !numberStrFinal.equals("*")) {
                try {
                    graveNumber = Integer.parseInt(numberStrFinal);
                } catch (NumberFormatException e) {
                    plugin.getMessageManager().sendMessage(sender, "cmd.grave_must_be_number");
                    return true;
                }
            }
            final int finalGraveNumber = graveNumber;

            switch (action) {
                case "go":
                    manager.graveExistsUUID(targetUUID, finalGraveNumber).thenAccept(exists -> {
                        if (!exists) {
                            FoliaHelper.runOnEntity(plugin, sender, () ->
                                    plugin.getMessageManager().sendMessage(sender, "cmd.no_grave_other",
                                            "player", targetNameFinal,
                                            "number", String.valueOf(finalGraveNumber)));
                            return;
                        }
                        manager.getGraveLocation(targetUUID, finalGraveNumber).thenAccept(location -> {
                            if (location != null) {
                                FoliaHelper.runOnEntity(plugin, sender, () -> {
                                    sender.teleport(location);
                                    plugin.getMessageManager().sendMessage(sender, "cmd.teleported_to_grave",
                                            "player", targetNameFinal,
                                            "number", String.valueOf(finalGraveNumber));
                                });
                            } else {
                                FoliaHelper.runOnEntity(plugin, sender, () ->
                                        plugin.getMessageManager().sendMessage(sender, "cmd.failed_grave_location_other"));
                            }
                        });
                    });
                    break;
                case "list":
                    manager.getGraveNumberListAsync(targetUUID).thenAccept(graveList -> {
                        FoliaHelper.runOnEntity(plugin, sender, () -> {
                            if (graveList.isEmpty()) {
                                plugin.getMessageManager().sendMessage(sender, "cmd.no_graves_player",
                                        "player", targetNameFinal);
                            } else {
                                plugin.getMessageManager().sendMessage(sender, "cmd.has_graves_header",
                                        "player", targetNameFinal);
                                sender.sendMessage("§c#" + String.join(", #", graveList));
                            }
                        });
                    });
                    break;
                case "info":
                    manager.graveExistsUUID(targetUUID, finalGraveNumber).thenAccept(exists -> {
                        if (!exists) {
                            FoliaHelper.runOnEntity(plugin, sender, () ->
                                    plugin.getMessageManager().sendMessage(sender, "cmd.no_grave_other",
                                            "player", targetNameFinal,
                                            "number", String.valueOf(finalGraveNumber)));
                            return;
                        }
                        manager.getGraveLocation(targetUUID, finalGraveNumber).thenAccept(location -> {
                            if (location == null || location.getWorld() == null) {
                                FoliaHelper.runOnEntity(plugin, sender, () ->
                                        plugin.getMessageManager().sendMessage(sender, "cmd.failed_grave_location_other"));
                                return;
                            }
                            String worldName;
                            switch (location.getWorld().getName()) {
                                case "world" -> worldName = "The Overworld";
                                case "world_nether" -> worldName = "The Nether";
                                case "world_the_end" -> worldName = "The End";
                                default -> worldName = location.getWorld().getName();
                            }
                            String graveNumStr = String.valueOf(finalGraveNumber);
                            String xStr = String.valueOf(Math.floor(location.getX()));
                            String yStr = String.valueOf(Math.floor(location.getY()));
                            String zStr = String.valueOf(Math.floor(location.getZ()));
                            FoliaHelper.runOnEntity(plugin, sender, () ->
                                    plugin.getMessageManager().sendMessage(sender, "cmd.grave_info",
                                            "number", graveNumStr,
                                            "world", worldName,
                                            "x", xStr,
                                            "y", yStr,
                                            "z", zStr));
                        });
                    });
                    break;
                case "items":
                    manager.graveExistsUUID(targetUUID, graveNumber).thenAccept(exists -> {
                        if (!exists) {
                            FoliaHelper.runOnEntity(plugin, sender, () ->
                                    plugin.getMessageManager().sendMessage(sender, "cmd.no_grave_other",
                                            "player", targetNameFinal,
                                            "number", String.valueOf(finalGraveNumber))
                            );
                            return;
                        }

                        manager.getGraveItems(targetUUID, finalGraveNumber).thenAccept(itemStacks -> {
                            if (itemStacks.isEmpty()) {
                                plugin.getMessageManager().sendMessage(sender, "cmd.grave_no_items",
                                        "number", String.valueOf(finalGraveNumber));
                            } else {
                                Map<String, Integer> graveItems = new HashMap<>();
                                for (ItemStack itemStack : itemStacks) {
                                    graveItems.merge(itemStack.getType().name(), itemStack.getAmount(), Integer::sum);
                                }

                                if (graveItems.isEmpty()) {
                                    FoliaHelper.runOnEntity(plugin, sender, () ->
                                            plugin.getMessageManager().sendMessage(sender, "cmd.grave_no_items",
                                                    "number", String.valueOf(finalGraveNumber))
                                    );
                                    return;
                                }

                                StringBuilder itemsMessage = new StringBuilder("§c");
                                for (Map.Entry<String, Integer> entry : graveItems.entrySet()) {
                                    if (!itemsMessage.toString().equals("§c")) {
                                        itemsMessage.append(", ");
                                    }

                                    itemsMessage.append(entry.getKey()).append(" (x").append(entry.getValue()).append(")");
                                }

                                FoliaHelper.runOnEntity(plugin, sender, () -> {
                                    plugin.getMessageManager().sendMessage(sender, "cmd.grave_items_header",
                                            "number", String.valueOf(finalGraveNumber));
                                    sender.sendMessage(itemsMessage.toString());
                                });
                            }
                        });
                    });
                    break;
                case "remove":
                    if (numberStrFinal.equals("*")) {
                        manager.removeAllGraves(targetUUID);
                        FoliaHelper.runOnEntity(plugin, sender, () ->
                                plugin.getMessageManager().sendMessage(sender, "cmd.removed_all_graves_player",
                                        "player", targetNameFinal));
                    } else {
                        manager.graveExistsUUID(targetUUID, finalGraveNumber).thenAccept(exists -> {
                            if (!exists) {
                                FoliaHelper.runOnEntity(plugin, sender, () ->
                                        plugin.getMessageManager().sendMessage(sender, "cmd.no_grave_other",
                                                "player", targetNameFinal,
                                                "number", String.valueOf(finalGraveNumber)));
                                return;
                            }
                            manager.removeGrave(targetUUID, finalGraveNumber, false);
                            FoliaHelper.runOnEntity(plugin, sender, () ->
                                    plugin.getMessageManager().sendMessage(sender, "cmd.removed_grave_player",
                                            "player", targetNameFinal,
                                            "number", String.valueOf(finalGraveNumber)));
                        });
                    }
                    break;
            }
            return true;
        }

        // Offline Target handling
        manager.getOfflinePlayerUUIDAsync(targetNameFinal).thenAccept(uuid -> {
            manager.getOfflinePlayerName(uuid).thenAccept(name -> {
                if (uuid == null) {
                    FoliaHelper.runOnEntity(plugin, sender, () ->
                            plugin.getMessageManager().sendMessage(sender, "cmd.player_not_found",
                                    "player", targetNameFinal));
                    return;
                }
                final UUID targetUUID = uuid;
                final String targetName = name;

                int graveNumber = -1;
                if (!numberStrFinal.equals("-1") && !numberStrFinal.equals("*")) {
                    try {
                        graveNumber = Integer.parseInt(numberStrFinal);
                    } catch (NumberFormatException e) {
                        FoliaHelper.runOnEntity(plugin, sender, () ->
                                plugin.getMessageManager().sendMessage(sender, "cmd.grave_must_be_number"));
                        return;
                    }
                }
                final int finalGraveNumber = graveNumber;

                switch (action) {
                    case "go":
                        manager.graveExistsUUID(targetUUID, finalGraveNumber).thenAccept(exists -> {
                            if (!exists) {
                                FoliaHelper.runOnEntity(plugin, sender, () ->
                                        plugin.getMessageManager().sendMessage(sender, "cmd.no_grave_other",
                                                "player", targetName,
                                                "number", String.valueOf(finalGraveNumber)));
                                return;
                            }
                            manager.getGraveLocation(targetUUID, finalGraveNumber).thenAccept(location -> {
                                if (location != null) {
                                    FoliaHelper.runOnEntity(plugin, sender, () -> {
                                        sender.teleport(location);
                                        plugin.getMessageManager().sendMessage(sender, "cmd.teleported_to_grave",
                                                "player", targetName,
                                                "number", String.valueOf(finalGraveNumber));
                                    });
                                } else {
                                    FoliaHelper.runOnEntity(plugin, sender, () ->
                                            plugin.getMessageManager().sendMessage(sender, "cmd.failed_grave_location_other"));
                                }
                            });
                        });
                        break;
                    case "list":
                        manager.getGraveNumberListAsync(targetUUID).thenAccept(graveList -> {
                            FoliaHelper.runOnEntity(plugin, sender, () -> {
                                if (graveList.isEmpty()) {
                                    plugin.getMessageManager().sendMessage(sender, "cmd.no_graves_player",
                                            "player", targetName);
                                } else {
                                    plugin.getMessageManager().sendMessage(sender, "cmd.has_graves_header",
                                            "player", targetName);
                                    sender.sendMessage("§c#" + String.join(", #", graveList));
                                }
                            });
                        });
                        break;
                    case "info":
                        manager.graveExistsUUID(targetUUID, finalGraveNumber).thenAccept(exists -> {
                            if (!exists) {
                                FoliaHelper.runOnEntity(plugin, sender, () ->
                                        plugin.getMessageManager().sendMessage(sender, "cmd.no_grave_other",
                                                "player", targetName,
                                                "number", String.valueOf(finalGraveNumber)));
                                return;
                            }
                            manager.getGraveLocation(targetUUID, finalGraveNumber).thenAccept(location -> {
                                if (location == null || location.getWorld() == null) {
                                    FoliaHelper.runOnEntity(plugin, sender, () ->
                                            plugin.getMessageManager().sendMessage(sender, "cmd.failed_grave_location_other"));
                                    return;
                                }
                                String worldName;
                                switch (location.getWorld().getName()) {
                                    case "world" -> worldName = "The Overworld";
                                    case "world_nether" -> worldName = "The Nether";
                                    case "world_the_end" -> worldName = "The End";
                                    default -> worldName = location.getWorld().getName();
                                }
                                String graveNumStr = String.valueOf(finalGraveNumber);
                                String xStr = String.valueOf(Math.floor(location.getX()));
                                String yStr = String.valueOf(Math.floor(location.getY()));
                                String zStr = String.valueOf(Math.floor(location.getZ()));
                                FoliaHelper.runOnEntity(plugin, sender, () ->
                                        plugin.getMessageManager().sendMessage(sender, "cmd.grave_info",
                                                "number", graveNumStr,
                                                "world", worldName,
                                                "x", xStr,
                                                "y", yStr,
                                                "z", zStr));
                            });
                        });
                        break;
                    case "items":
                        manager.graveExistsUUID(targetUUID, graveNumber).thenAccept(exists -> {
                            if (!exists) {
                                FoliaHelper.runOnEntity(plugin, sender, () ->
                                        plugin.getMessageManager().sendMessage(sender, "cmd.no_grave_other",
                                                "player", targetName,
                                                "number", String.valueOf(finalGraveNumber))
                                );
                                return;
                            }

                            manager.getGraveItems(targetUUID, finalGraveNumber).thenAccept(itemStacks -> {
                                if (itemStacks.isEmpty()) {
                                    plugin.getMessageManager().sendMessage(sender, "cmd.grave_no_items",
                                            "number", String.valueOf(finalGraveNumber));
                                } else {
                                    Map<String, Integer> graveItems = new HashMap<>();
                                    for (ItemStack itemStack : itemStacks) {
                                        graveItems.merge(itemStack.getType().name(), itemStack.getAmount(), Integer::sum);
                                    }

                                    if (graveItems.isEmpty()) {
                                        FoliaHelper.runOnEntity(plugin, sender, () ->
                                                plugin.getMessageManager().sendMessage(sender, "cmd.grave_no_items",
                                                        "number", String.valueOf(finalGraveNumber))
                                        );
                                        return;
                                    }

                                    StringBuilder itemsMessage = new StringBuilder("§c");
                                    for (Map.Entry<String, Integer> entry : graveItems.entrySet()) {
                                        if (!itemsMessage.toString().equals("§c")) {
                                            itemsMessage.append(", ");
                                        }

                                        itemsMessage.append(entry.getKey()).append(" (x").append(entry.getValue()).append(")");
                                    }

                                    FoliaHelper.runOnEntity(plugin, sender, () -> {
                                        plugin.getMessageManager().sendMessage(sender, "cmd.grave_items_header",
                                                "number", String.valueOf(finalGraveNumber));
                                        sender.sendMessage(itemsMessage.toString());
                                    });
                                }
                            });
                        });
                        break;
                    case "remove":
                        if (numberStrFinal.equals("*")) {
                            manager.removeAllGraves(targetUUID);
                            FoliaHelper.runOnEntity(plugin, sender, () ->
                                    plugin.getMessageManager().sendMessage(sender, "cmd.removed_all_graves_player",
                                            "player", targetName));
                        } else {
                            manager.graveExistsUUID(targetUUID, finalGraveNumber).thenAccept(exists -> {
                                if (!exists) {
                                    FoliaHelper.runOnEntity(plugin, sender, () ->
                                            plugin.getMessageManager().sendMessage(sender, "cmd.no_grave_other",
                                                    "player", targetName,
                                                    "number", String.valueOf(finalGraveNumber)));
                                    return;
                                }
                                manager.removeGrave(targetUUID, finalGraveNumber, false);
                                FoliaHelper.runOnEntity(plugin, sender, () ->
                                        plugin.getMessageManager().sendMessage(sender, "cmd.removed_grave_player",
                                                "player", targetName,
                                                "number", String.valueOf(finalGraveNumber)));
                            });
                        }
                        break;
                }
            });
        });

        return true;
    }
}
