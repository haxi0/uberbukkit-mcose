package org.bukkit.command.defaults;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.CraftServer;
import net.minecraft.server.WorldData;
import net.minecraft.server.MinecraftServer;

public class GameruleCommand extends VanillaCommand {
    
    // Boolean gamerules
    private static final String[] BOOLEAN_RULES = {"doDayNightCycle", "tntexplodes", "mobGriefing", "doWeatherCycle", "doFireTick", "showDeathMessages", "sleepEnabled", "advertiseAchievements", "keepInventory"};
    // Integer gamerules
    private static final String[] INTEGER_RULES = {"spawnRadius", "spawnProtectionRadius"};
    
    public GameruleCommand() {
        super("gamerule");
        this.description = "Sets or queries a game rule.";
        this.usageMessage = "/gamerule <rule name> [value]";
        this.setPermission("bukkit.command.gamerule");
    }
    
    private boolean isBooleanRule(String name) {
        for (String rule : BOOLEAN_RULES) {
            if (rule.equalsIgnoreCase(name)) return true;
        }
        return false;
    }
    
    private boolean isIntegerRule(String name) {
        for (String rule : INTEGER_RULES) {
            if (rule.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    private boolean isSpawnProtectionRule(String name) {
        return name.equalsIgnoreCase("spawnradius") || name.equalsIgnoreCase("spawnprotectionradius");
    }

    @Override
    public boolean execute(CommandSender sender, String currentAlias, String[] args) {
        if (!testPermission(sender)) {
            return true;
        }

        if (args.length == 0) {
            // List all gamerules
            sender.sendMessage(ChatColor.YELLOW + "Available gamerules:");
            for (String rule : BOOLEAN_RULES) {
                sender.sendMessage("  " + rule + " (boolean)");
            }
            for (String rule : INTEGER_RULES) {
                sender.sendMessage("  " + rule + " (integer)");
            }
            return true;
        }

        MinecraftServer mcServer = ((CraftServer) Bukkit.getServer()).getServer();
        if (mcServer.worlds.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Error: No worlds loaded on the server.");
            return true;
        }
        WorldData worldData = mcServer.worlds.get(0).worldData;
        if (worldData == null) {
            sender.sendMessage(ChatColor.RED + "Error: Could not retrieve world data for the primary world.");
            return true;
        }

        String ruleName = args[0].toLowerCase();

        if (args.length == 1) {
            // Query a gamerule value
            if (ruleName.equals("dodaynightcycle")) {
                sender.sendMessage(args[0] + " = " + worldData.getDoDayNightCycle());
            } else if (ruleName.equals("tntexplodes")) {
                sender.sendMessage(args[0] + " = " + worldData.getTntexplodes());
            } else if (ruleName.equals("mobgriefing")) {
                sender.sendMessage(args[0] + " = " + worldData.getMobGriefing());
            } else if (ruleName.equals("doweathercycle")) {
                sender.sendMessage(args[0] + " = " + worldData.getDoWeatherCycle());
            } else if (ruleName.equals("dofiretick")) {
                sender.sendMessage(args[0] + " = " + worldData.getDoFireTick());
            } else if (ruleName.equals("showdeathmessages")) {
                sender.sendMessage(args[0] + " = " + worldData.getShowDeathMessages());
            } else if (ruleName.equals("sleepenabled")) {
                sender.sendMessage(args[0] + " = " + worldData.getSleepEnabled());
            } else if (ruleName.equals("advertiseachievements")) {
                sender.sendMessage(args[0] + " = " + worldData.getAdvertiseAchievements());
            } else if (ruleName.equals("keepinventory")) {
                sender.sendMessage(args[0] + " = " + worldData.getKeepInventory());
            } else if (isSpawnProtectionRule(ruleName)) {
                sender.sendMessage(args[0] + " = " + Bukkit.getServer().getSpawnRadius());
            } else {
                sender.sendMessage(ChatColor.RED + "Unknown game rule: " + args[0]);
                return false;
            }
        } else if (args.length == 2) {
            String valueStr = args[1];
            
            // Handle integer gamerules
            if (isIntegerRule(args[0])) {
                int intValue;
                try {
                    intValue = Integer.parseInt(valueStr);
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "Invalid value for game rule. Expected an integer.");
                    return false;
                }
                
                if (isSpawnProtectionRule(ruleName)) {
                    int clampedValue = Math.max(0, intValue);
                    Bukkit.getServer().setSpawnRadius(clampedValue);
                    worldData.setSpawnRadius(clampedValue); // Keep world gamerule data aligned
                    sender.sendMessage("Game rule " + args[0] + " has been set to " + clampedValue);
                    if (!(sender instanceof org.bukkit.command.ConsoleCommandSender)) {
                        Bukkit.getLogger().info("User " + sender.getName() + " set game rule " + args[0] + " to " + clampedValue);
                    }
                } else {
                    sender.sendMessage(ChatColor.RED + "Unknown game rule: " + args[0]);
                    return false;
                }
            } else if (isBooleanRule(args[0])) {
                // Handle boolean gamerules
                boolean value;
                if (valueStr.equalsIgnoreCase("true")) {
                    value = true;
                } else if (valueStr.equalsIgnoreCase("false")) {
                    value = false;
                } else {
                    sender.sendMessage(ChatColor.RED + "Invalid value for game rule. Use 'true' or 'false'.");
                    return false;
                }

                if (ruleName.equals("dodaynightcycle")) {
                    worldData.setDoDayNightCycle(value);
                } else if (ruleName.equals("tntexplodes")) {
                    worldData.setTntexplodes(value);
                } else if (ruleName.equals("mobgriefing")) {
                    worldData.setMobGriefing(value);
                } else if (ruleName.equals("doweathercycle")) {
                    worldData.setDoWeatherCycle(value);
                } else if (ruleName.equals("dofiretick")) {
                    worldData.setDoFireTick(value);
                } else if (ruleName.equals("showdeathmessages")) {
                    worldData.setShowDeathMessages(value);
                } else if (ruleName.equals("sleepenabled")) {
                    worldData.setSleepEnabled(value);
                } else if (ruleName.equals("advertiseachievements")) {
                    worldData.setAdvertiseAchievements(value);
                } else if (ruleName.equals("keepinventory")) {
                    worldData.setKeepInventory(value);
                } else {
                    sender.sendMessage(ChatColor.RED + "Unknown game rule: " + args[0]);
                    return false;
                }
                
                sender.sendMessage("Game rule " + args[0] + " has been set to " + value);
                if (!(sender instanceof org.bukkit.command.ConsoleCommandSender)) {
                    Bukkit.getLogger().info("User " + sender.getName() + " set game rule " + args[0] + " to " + value);
                }
            } else {
                sender.sendMessage(ChatColor.RED + "Unknown game rule: " + args[0]);
                return false;
            }
        } else {
            sender.sendMessage(ChatColor.RED + "Usage: " + usageMessage);
            return false;
        }

        return true;
    }

    @Override
    public boolean matches(String input) {
        if (input == null) return false;
        String lowerInput = input.toLowerCase();
        return lowerInput.startsWith("gamerule ") || lowerInput.equals("gamerule");
    }
    
    @Override
    public java.util.List<String> tabComplete(org.bukkit.command.CommandSender sender, String alias, String[] args) {
        java.util.List<String> completions = new java.util.ArrayList<String>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            // Add all gamerules
            for (String rule : BOOLEAN_RULES) {
                if (rule.toLowerCase().startsWith(prefix)) {
                    completions.add(rule);
                }
            }
            for (String rule : INTEGER_RULES) {
                if (rule.toLowerCase().startsWith(prefix)) {
                    completions.add(rule);
                }
            }
        } else if (args.length == 2) {
            // Suggest true/false for boolean rules, nothing for integer rules
            if (isBooleanRule(args[0])) {
                String prefix = args[1].toLowerCase();
                String[] values = {"true", "false"};
                for (String val : values) {
                    if (val.startsWith(prefix)) {
                        completions.add(val);
                    }
                }
            }
            // For integer rules, no suggestions (user types a number)
        }
        return completions;
    }
}
