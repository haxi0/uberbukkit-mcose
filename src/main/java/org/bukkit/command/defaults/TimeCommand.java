package org.bukkit.command.defaults;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

public class TimeCommand extends VanillaCommand {
    public TimeCommand() {
        super("time");
        this.description = "Changes the time on each world";
        this.usageMessage = "/time set <value|sunrise|day|noon|sunset|night|midnight>\n/time add <value>";
    }

    @Override
    public boolean execute(CommandSender sender, String currentAlias, String[] args) {
        if (args.length != 2) {
            sender.sendMessage(ChatColor.RED + "Incorrect usage. Correct usage:\n" + usageMessage);
            return false;
        }

        if (args[0].equalsIgnoreCase("add")) {
            int value;
            try {
                value = Integer.parseInt(args[1]);
            } catch (NumberFormatException ex) {
                sender.sendMessage("Unable to convert time value, " + args[1]);
                return true;
            }
            if (!sender.hasPermission("bukkit.command.time.add")) {
                sender.sendMessage(ChatColor.RED + "You don't have permission to add to the time");
            } else {
                for (World world : Bukkit.getWorlds()) {
                    world.setFullTime(world.getFullTime() + value);
                }
                Command.broadcastCommandMessage(sender, "Added " + value + " to time");
            }
        } else if (args[0].equalsIgnoreCase("set")) {
            if (!sender.hasPermission("bukkit.command.time.set")) {
                sender.sendMessage(ChatColor.RED + "You don't have permission to set the time");
            } else {
                Long target = parseNamedTime(args[1]);
                if (target == null) {
                    try {
                        target = Long.parseLong(args[1]);
                    } catch (NumberFormatException ex) {
                        sender.sendMessage("Unable to convert time value, " + args[1]);
                        return true;
                    }
                }
                for (World world : Bukkit.getWorlds()) {
                    world.setTime(target);
                }
                Command.broadcastCommandMessage(sender, "Set time to " + args[1]);
            }
        } else {
            sender.sendMessage("Unknown method, use either \"add\" or \"set\"");
            return true;
        }

        return true;
    }

    private Long parseNamedTime(String name) {
        if (name.equalsIgnoreCase("sunrise")) return 0L;     // first light
        if (name.equalsIgnoreCase("day")) return 1000L;       // sunrise-ish
        if (name.equalsIgnoreCase("noon")) return 6000L;      // midday
        if (name.equalsIgnoreCase("sunset")) return 12000L;   // dusk
        if (name.equalsIgnoreCase("night")) return 13000L;    // sunset-ish
        if (name.equalsIgnoreCase("midnight")) return 18000L; // midnight
        return null;
    }

    @Override
    public boolean matches(String input) {
        return input.startsWith("time ");
    }
    
    @Override
    public java.util.List<String> tabComplete(org.bukkit.command.CommandSender sender, String alias, String[] args) {
        java.util.List<String> completions = new java.util.ArrayList<String>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            String[] subcommands = {"set", "add", "query"};
            for (String sub : subcommands) {
                if (sub.startsWith(prefix)) {
                    completions.add(sub);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            String prefix = args[1].toLowerCase();
            String[] times = {"day", "noon", "night", "midnight", "sunrise", "sunset"};
            for (String time : times) {
                if (time.startsWith(prefix)) {
                    completions.add(time);
                }
            }
        }
        return completions;
    }
}
