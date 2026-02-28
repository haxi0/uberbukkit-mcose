package org.bukkit.command;

import net.minecraft.server.EntityItem;
import net.minecraft.server.EntityPainting;
import net.minecraft.server.registry.BlockRegistry;
import net.minecraft.server.registry.EntityTypeRegistry;
import net.minecraft.server.registry.ItemRegistry;
import net.minecraft.server.registry.Registries;
import net.minecraft.server.registry.StructureTypes;
import net.minecraft.server.util.ResourceLocation;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.command.defaults.VanillaCommand;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Syntax-aware command autocomplete registry used by packet203 and custom payload command APIs.
 */
public final class CommandAutocompleteRegistry {
    public static final int PROTOCOL_VERSION = 1;
    public static final String CHANNEL_TREE = "MCOSE|CmdTree";
    public static final String CHANNEL_REQUEST = "MCOSE|CmdReq";
    public static final String CHANNEL_RESPONSE = "MCOSE|CmdRes";

    /** Built-in argument IDs available for command syntax definitions. */
    public static final String ARG_PLAYER = "player";
    public static final String ARG_GAMEMODE = "gamemode";
    public static final String ARG_ITEM_KEY = "item_key";
    public static final String ARG_BLOCK_KEY = "block_key";
    public static final String ARG_ENTITY_KEY = "entity_key";
    public static final String ARG_STRUCTURE_KEY = "structure_key";
    public static final String ARG_WEATHER_TYPE = "weather_type";
    public static final String ARG_TIME_ACTION = "time_action";
    public static final String ARG_TIME_VALUE = "time_value";
    public static final String ARG_GAMERULE_NAME = "gamerule_name";
    public static final String ARG_GAMERULE_VALUE = "gamerule_value";
    public static final String ARG_PROFILE_ACTION = "profile_action";
    public static final String ARG_DEBUG_ACTION = "debug_action";
    public static final String ARG_TICK_RATE = "tick_rate";
    public static final String ARG_INTEGER = "integer";
    public static final String ARG_COORD = "coordinate";
    public static final String ARG_TEXT = "text";

    private static final String BUILTIN_OWNER = "__builtin__";

    private static final String[] BOOLEAN_GAMERULES = new String[] {
        "doDayNightCycle",
        "tntexplodes",
        "mobGriefing",
        "doWeatherCycle",
        "doFireTick",
        "showDeathMessages",
        "sleepEnabled",
        "advertiseAchievements",
        "keepInventory"
    };

    private static final String[] INTEGER_GAMERULES = new String[] { "spawnRadius", "spawnProtectionRadius" };

    private static final String[] GAMEMODE_VALUES = new String[] {
        "survival", "creative", "hardcore", "s", "c", "h", "0", "1", "2"
    };

    private static final String[] WEATHER_VALUES = new String[] {
        "clear", "rain", "thunder", "downfall", "storm"
    };

    private static final String[] TIME_ACTION_VALUES = new String[] { "set", "add" };

    private static final String[] TIME_SET_VALUES = new String[] {
        "sunrise", "day", "noon", "sunset", "night", "midnight"
    };

    private static final String[] PROFILE_ACTION_VALUES = new String[] {
        "start", "stop", "report", "save", "status", "clear", "help"
    };

    private static final Comparator<String> CASE_INSENSITIVE_ORDER = new Comparator<String>() {
        public int compare(String a, String b) {
            if (a == b) {
                return 0;
            }
            if (a == null) {
                return 1;
            }
            if (b == null) {
                return -1;
            }
            int cmp = a.compareToIgnoreCase(b);
            if (cmp != 0) {
                return cmp;
            }
            return a.compareTo(b);
        }
    };

    private static final class Syntax {
        private final List<String> argumentIds;

        private Syntax(String[] argumentIds) {
            ArrayList<String> ids = new ArrayList<String>();
            if (argumentIds != null) {
                for (int i = 0; i < argumentIds.length; i++) {
                    String argumentId = argumentIds[i];
                    if (argumentId != null && argumentId.length() > 0) {
                        ids.add(argumentId);
                    }
                }
            }
            this.argumentIds = Collections.unmodifiableList(ids);
        }
    }

    private static final class CommandSpec {
        private final String canonicalNameLower;
        private final LinkedHashSet<String> aliasesLower = new LinkedHashSet<String>();
        private final List<Syntax> syntaxes = new ArrayList<Syntax>();

        private CommandSpec(String canonicalName) {
            this.canonicalNameLower = normalizeCommandName(canonicalName);
            this.aliasesLower.add(this.canonicalNameLower);
        }
    }

    private static final class CommandTreeEntry {
        private final String canonicalNameLower;
        private final List<String> aliasesLower;
        private final List<Syntax> syntaxes;

        private CommandTreeEntry(String canonicalNameLower, List<String> aliasesLower, List<Syntax> syntaxes) {
            this.canonicalNameLower = canonicalNameLower;
            this.aliasesLower = aliasesLower;
            this.syntaxes = syntaxes;
        }
    }

    /**
     * Supplies syntax-aware command suggestions and token validation.
     */
    public interface ArgumentProvider {
        List<String> suggest(CommandSender sender, String[] args, int argIndex, String prefixLower);

        boolean matches(CommandSender sender, String[] args, int argIndex, String token);
    }

    private static final CommandAutocompleteRegistry INSTANCE = new CommandAutocompleteRegistry();

    private final Map<String, ArgumentProvider> argumentProviders = new HashMap<String, ArgumentProvider>();
    private final Map<String, CommandSpec> commandSpecsByAlias = new HashMap<String, CommandSpec>();
    private final Map<String, String> argumentProviderOwners = new HashMap<String, String>();
    private final Map<String, String> commandSpecOwnersByCanonical = new HashMap<String, String>();
    private final Map<String, LinkedHashSet<String>> commandSpecAliasesByCanonical = new HashMap<String, LinkedHashSet<String>>();
    private final Map<String, LinkedHashSet<String>> pluginArgumentIds = new HashMap<String, LinkedHashSet<String>>();
    private final Map<String, LinkedHashSet<String>> pluginCanonicalSpecs = new HashMap<String, LinkedHashSet<String>>();

    public static CommandAutocompleteRegistry getInstance() {
        return INSTANCE;
    }

    private CommandAutocompleteRegistry() {
        registerBuiltinArguments();
        registerBuiltinCommandSpecs();
    }

    /**
     * Registers or replaces a plugin-owned argument provider.
     *
     * <p>Built-in argument ids are reserved and cannot be overridden.
     * Returns {@code false} if the id is already owned by another plugin.
     */
    public synchronized boolean registerArgumentProvider(Plugin owner, String argumentId, ArgumentProvider provider) {
        return registerArgumentInternal(ownerKey(owner), argumentId, provider, false);
    }

    /**
     * Removes a plugin-owned argument provider by id.
     */
    public synchronized boolean unregisterArgumentProvider(Plugin owner, String argumentId) {
        String key = ownerKey(owner);
        String normalizedId = normalizeArgumentId(argumentId);
        if (key == null || normalizedId.length() == 0) {
            return false;
        }

        String existingOwner = argumentProviderOwners.get(normalizedId);
        if (!key.equals(existingOwner)) {
            return false;
        }

        argumentProviders.remove(normalizedId);
        argumentProviderOwners.remove(normalizedId);
        removePluginArgumentTracking(key, normalizedId);
        return true;
    }

    /**
     * Registers or replaces a plugin-owned command syntax specification.
     *
     * <p>Built-in command specs are reserved and cannot be overridden.
     * Returns {@code false} when aliases collide with another owner.
     */
    public synchronized boolean registerCommandSpec(Plugin owner, String canonicalName, String[] aliases, String[][] syntaxes) {
        return registerSpecInternal(ownerKey(owner), canonicalName, aliases, syntaxes, false);
    }

    /**
     * Removes a plugin-owned command spec by canonical name or alias.
     */
    public synchronized boolean unregisterCommandSpec(Plugin owner, String canonicalOrAlias) {
        String key = ownerKey(owner);
        String normalized = normalizeCommandName(canonicalOrAlias);
        if (key == null || normalized.length() == 0) {
            return false;
        }

        CommandSpec spec = commandSpecsByAlias.get(normalized);
        if (spec == null) {
            return false;
        }

        String canonical = spec.canonicalNameLower;
        String existingOwner = commandSpecOwnersByCanonical.get(canonical);
        if (!key.equals(existingOwner)) {
            return false;
        }

        removeCommandSpecByCanonical(canonical);
        return true;
    }

    /**
     * Removes all plugin-owned autocomplete registrations.
     *
     * <p>Call this on plugin disable to keep autocomplete state clean.
     * Use {@code Bukkit.refreshCommandAutocomplete()} after cleanup if clients need an immediate tree refresh.
     */
    public synchronized void unregisterAll(Plugin owner) {
        String key = ownerKey(owner);
        if (key == null) {
            return;
        }

        LinkedHashSet<String> argumentIds = pluginArgumentIds.get(key);
        if (argumentIds != null) {
            ArrayList<String> snapshot = new ArrayList<String>(argumentIds);
            for (int i = 0; i < snapshot.size(); i++) {
                String argumentId = snapshot.get(i);
                if (!key.equals(argumentProviderOwners.get(argumentId))) {
                    continue;
                }
                argumentProviders.remove(argumentId);
                argumentProviderOwners.remove(argumentId);
                removePluginArgumentTracking(key, argumentId);
            }
        }

        LinkedHashSet<String> canonicals = pluginCanonicalSpecs.get(key);
        if (canonicals != null) {
            ArrayList<String> snapshot = new ArrayList<String>(canonicals);
            for (int i = 0; i < snapshot.size(); i++) {
                String canonical = snapshot.get(i);
                if (!key.equals(commandSpecOwnersByCanonical.get(canonical))) {
                    continue;
                }
                removeCommandSpecByCanonical(canonical);
            }
        }
    }

    private static String ownerKey(Plugin owner) {
        if (owner == null || owner.getDescription() == null || owner.getDescription().getName() == null) {
            return null;
        }
        String name = owner.getDescription().getName().trim().toLowerCase(Locale.ROOT);
        return name.length() == 0 ? null : name;
    }

    private static String normalizeArgumentId(String argumentId) {
        if (argumentId == null) {
            return "";
        }
        return argumentId.trim().toLowerCase(Locale.ROOT);
    }

    public byte[] buildTreePayload(SimpleCommandMap commandMap, CommandSender sender) {
        if (commandMap == null || sender == null) {
            return null;
        }

        List<CommandTreeEntry> entries = collectVisibleCommands(commandMap, sender);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);

        try {
            out.writeInt(PROTOCOL_VERSION);
            out.writeInt(entries.size());

            for (int i = 0; i < entries.size(); i++) {
                CommandTreeEntry entry = entries.get(i);
                out.writeUTF(entry.canonicalNameLower);

                int aliasCount = Math.min(65535, entry.aliasesLower.size());
                out.writeShort(aliasCount);
                for (int a = 0; a < aliasCount; a++) {
                    out.writeUTF(entry.aliasesLower.get(a));
                }

                int syntaxCount = Math.min(255, entry.syntaxes.size());
                out.writeByte(syntaxCount);
                for (int s = 0; s < syntaxCount; s++) {
                    Syntax syntax = entry.syntaxes.get(s);
                    int argCount = Math.min(255, syntax.argumentIds.size());
                    out.writeByte(argCount);
                    for (int arg = 0; arg < argCount; arg++) {
                        out.writeUTF(syntax.argumentIds.get(arg));
                    }
                }
            }

            return baos.toByteArray();
        } catch (IOException ignored) {
            return null;
        } finally {
            try {
                out.close();
            } catch (IOException ignored) {}
        }
    }

    public List<String> suggest(SimpleCommandMap commandMap, CommandSender sender, String text, int cursorPos) {
        if (commandMap == null || sender == null || text == null) {
            return Collections.emptyList();
        }

        int safeCursor = clamp(cursorPos, 0, text.length());
        String uptoCursor = text.substring(0, safeCursor);
        if (uptoCursor.length() == 0) {
            return Collections.emptyList();
        }

        if (!uptoCursor.startsWith("/")) {
            return suggestPlayerNamesForChat(uptoCursor);
        }

        String commandText = uptoCursor.substring(1);
        String[] parts = commandText.split(" ", -1);
        if (parts.length == 0) {
            return Collections.emptyList();
        }

        String commandLabelLower = safeLower(parts[0]);
        if (parts.length == 1 && commandText.indexOf(' ') < 0) {
            return suggestCommandNames(commandMap, sender, commandLabelLower);
        }

        Command command = resolveAutocompleteCommand(commandMap, commandLabelLower);
        if (command == null || !canUseCommand(sender, command)) {
            return Collections.emptyList();
        }

        String[] args = new String[parts.length - 1];
        if (args.length > 0) {
            System.arraycopy(parts, 1, args, 0, args.length);
        }

        List<String> fromSpec = suggestFromSpec(commandLabelLower, sender, args);
        if (fromSpec != null) {
            return fromSpec;
        }

        String prefixLower = args.length == 0 ? "" : safeLower(args[args.length - 1]);
        return filterStrings(command.tabComplete(sender, commandLabelLower, args), prefixLower);
    }

    private List<CommandTreeEntry> collectVisibleCommands(SimpleCommandMap commandMap, CommandSender sender) {
        HashMap<String, CommandTreeEntry> byCanonical = new HashMap<String, CommandTreeEntry>();

        for (Command command : collectAutocompleteCommands(commandMap)) {
            if (command == null || !canUseCommand(sender, command)) {
                continue;
            }

            String canonical = normalizeCommandName(command.getName());
            if (canonical.length() == 0) {
                continue;
            }
            if (!isLabelRoutableToCommand(commandMap, canonical, command)) {
                continue;
            }

            ArrayList<String> aliases = new ArrayList<String>();
            List<String> commandAliases = command.getAliases();
            if (commandAliases != null) {
                for (int i = 0; i < commandAliases.size(); i++) {
                    String alias = normalizeCommandName(commandAliases.get(i));
                    if (alias.length() == 0 || canonical.equals(alias)) {
                        continue;
                    }
                    Command aliasOwner = commandMap.getCommand(alias);
                    if (aliasOwner != null && aliasOwner != command) {
                        continue;
                    }
                    if (!aliases.contains(alias)) {
                        aliases.add(alias);
                    }
                }
            }

            Collections.sort(aliases, CASE_INSENSITIVE_ORDER);
            CommandSpec spec = resolveSpec(canonical, aliases);
            List<Syntax> syntaxes = spec == null ? Collections.<Syntax>emptyList() : spec.syntaxes;
            byCanonical.put(canonical, new CommandTreeEntry(canonical, aliases, syntaxes));
        }

        ArrayList<CommandTreeEntry> out = new ArrayList<CommandTreeEntry>(byCanonical.values());
        Collections.sort(out, new Comparator<CommandTreeEntry>() {
            public int compare(CommandTreeEntry a, CommandTreeEntry b) {
                return CASE_INSENSITIVE_ORDER.compare(a.canonicalNameLower, b.canonicalNameLower);
            }
        });
        return out;
    }

    private List<String> suggestPlayerNamesForChat(String message) {
        String[] words = message.split(" ", -1);
        String prefixLower = words.length > 0 ? safeLower(words[words.length - 1]) : "";
        ArrayList<String> names = new ArrayList<String>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player == null || player.getName() == null) {
                continue;
            }
            String name = player.getName();
            if (safeLower(name).startsWith(prefixLower)) {
                names.add(name);
            }
        }
        return sortAndDedupe(names);
    }

    private List<String> suggestCommandNames(SimpleCommandMap commandMap, CommandSender sender, String prefixLower) {
        LinkedHashSet<String> names = new LinkedHashSet<String>();
        for (Command command : collectAutocompleteCommands(commandMap)) {
            if (command == null || !canUseCommand(sender, command)) {
                continue;
            }

            String canonical = normalizeCommandName(command.getName());
            if (canonical.startsWith(prefixLower) && isLabelRoutableToCommand(commandMap, canonical, command)) {
                names.add(canonical);
            }

            List<String> aliases = command.getAliases();
            if (aliases != null) {
                for (int i = 0; i < aliases.size(); i++) {
                    String aliasLower = normalizeCommandName(aliases.get(i));
                    if (aliasLower.length() == 0 || canonical.equals(aliasLower)) {
                        continue;
                    }
                    if (aliasLower.startsWith(prefixLower) && isLabelRoutableToCommand(commandMap, aliasLower, command)) {
                        names.add(aliasLower);
                    }
                }
            }
        }
        return sortAndDedupe(names);
    }

    /**
     * Returns null when no explicit syntax exists for this command (caller should fallback).
     */
    private List<String> suggestFromSpec(String commandLabelLower, CommandSender sender, String[] args) {
        CommandSpec spec = commandSpecsByAlias.get(commandLabelLower);
        if (spec == null) {
            return null;
        }

        int argIndex = args.length - 1;
        if (argIndex < 0) {
            argIndex = 0;
        }

        String prefixLower = args.length == 0 ? "" : safeLower(args[argIndex]);

        if (argIndex == 0 && prefixLower.length() == 0) {
            for (int i = 0; i < spec.syntaxes.size(); i++) {
                Syntax syntax = spec.syntaxes.get(i);
                if (!isSyntaxViable(spec, syntax, sender, args, argIndex)) {
                    continue;
                }
                List<String> picks = suggestionsForSyntax(syntax, sender, args, argIndex, prefixLower);
                if (!picks.isEmpty()) {
                    return picks;
                }
            }
            return Collections.emptyList();
        }

        ArrayList<String> merged = new ArrayList<String>();
        for (int i = 0; i < spec.syntaxes.size(); i++) {
            Syntax syntax = spec.syntaxes.get(i);
            if (!isSyntaxViable(spec, syntax, sender, args, argIndex)) {
                continue;
            }
            merged.addAll(suggestionsForSyntax(syntax, sender, args, argIndex, prefixLower));
        }
        return sortAndDedupe(merged);
    }

    private boolean isSyntaxViable(CommandSpec spec, Syntax syntax, CommandSender sender, String[] args, int argIndex) {
        if (syntax == null || argIndex >= syntax.argumentIds.size()) {
            return false;
        }

        for (int i = 0; i < argIndex; i++) {
            if (i >= syntax.argumentIds.size()) {
                return false;
            }

            String token = args.length > i ? args[i] : "";
            if (token == null || token.length() == 0) {
                return false;
            }

            String argumentId = syntax.argumentIds.get(i);
            ArgumentProvider provider = argumentProviders.get(argumentId);
            if (provider == null || !provider.matches(sender, args, i, token)) {
                return false;
            }
        }

        return true;
    }

    private List<String> suggestionsForSyntax(Syntax syntax, CommandSender sender, String[] args, int argIndex, String prefixLower) {
        if (syntax == null || argIndex >= syntax.argumentIds.size()) {
            return Collections.emptyList();
        }
        String argumentId = syntax.argumentIds.get(argIndex);
        ArgumentProvider provider = argumentProviders.get(argumentId);
        if (provider == null) {
            return Collections.emptyList();
        }
        return provider.suggest(sender, args, argIndex, prefixLower);
    }

    private List<Command> collectAutocompleteCommands(SimpleCommandMap commandMap) {
        LinkedHashSet<Command> commands = new LinkedHashSet<Command>();
        commands.addAll(commandMap.getCommands());

        for (VanillaCommand fallback : SimpleCommandMap.fallbackCommands) {
            if (fallback == null) {
                continue;
            }
            String canonical = normalizeCommandName(fallback.getName());
            if (canonical.length() == 0) {
                continue;
            }

            Command existing = commandMap.getCommand(canonical);
            if (existing != null && existing != fallback) {
                continue;
            }
            commands.add(fallback);
        }

        return new ArrayList<Command>(commands);
    }

    private Command resolveAutocompleteCommand(SimpleCommandMap commandMap, String label) {
        String normalized = normalizeCommandName(label);
        if (normalized.length() == 0) {
            return null;
        }

        Command command = commandMap.getCommand(normalized);
        if (command != null) {
            return command;
        }

        for (VanillaCommand fallback : SimpleCommandMap.fallbackCommands) {
            if (isCommandLabel(fallback, normalized)) {
                return fallback;
            }
        }

        return null;
    }

    private boolean isLabelRoutableToCommand(SimpleCommandMap commandMap, String label, Command command) {
        String normalized = normalizeCommandName(label);
        if (normalized.length() == 0 || command == null) {
            return false;
        }

        Command existing = commandMap.getCommand(normalized);
        if (existing != null) {
            return existing == command;
        }

        return isCommandLabel(command, normalized);
    }

    private boolean isCommandLabel(Command command, String label) {
        if (command == null) {
            return false;
        }

        String normalized = normalizeCommandName(label);
        if (normalized.length() == 0) {
            return false;
        }

        if (normalized.equals(normalizeCommandName(command.getName()))) {
            return true;
        }

        List<String> aliases = command.getAliases();
        if (aliases != null) {
            for (int i = 0; i < aliases.size(); i++) {
                if (normalized.equals(normalizeCommandName(aliases.get(i)))) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean canUseCommand(CommandSender sender, Command command) {
        if (sender == null || command == null) {
            return false;
        }

        if (command instanceof PluginCommand) {
            PluginCommand pluginCommand = (PluginCommand) command;
            if (pluginCommand.getPlugin() == null || !pluginCommand.getPlugin().isEnabled()) {
                return false;
            }
        }

        String permission = command.getPermission();
        return permission == null || permission.length() == 0 || sender.hasPermission(permission) || sender.isOp();
    }

    private CommandSpec resolveSpec(String canonical, List<String> aliases) {
        CommandSpec spec = commandSpecsByAlias.get(canonical);
        if (spec != null) {
            return spec;
        }
        if (aliases != null) {
            for (int i = 0; i < aliases.size(); i++) {
                spec = commandSpecsByAlias.get(aliases.get(i));
                if (spec != null) {
                    return spec;
                }
            }
        }
        return null;
    }

    private void registerBuiltinArguments() {
        registerBuiltinLiteralArgument(ARG_GAMEMODE, GAMEMODE_VALUES);
        registerBuiltinLiteralArgument(ARG_WEATHER_TYPE, WEATHER_VALUES);
        registerBuiltinLiteralArgument(ARG_TIME_ACTION, TIME_ACTION_VALUES);
        registerBuiltinLiteralArgument(ARG_PROFILE_ACTION, PROFILE_ACTION_VALUES);
        registerBuiltinLiteralArgument(ARG_DEBUG_ACTION, new String[] { "tickRate" });

        registerBuiltinArgument(ARG_TICK_RATE, new ArgumentProvider() {
            public List<String> suggest(CommandSender sender, String[] args, int argIndex, String prefixLower) {
                return filterStrings(Arrays.asList("reset", "1", "20", "40", "80", "200"), prefixLower);
            }

            public boolean matches(CommandSender sender, String[] args, int argIndex, String token) {
                if (token == null || token.length() == 0) {
                    return false;
                }
                if ("reset".equalsIgnoreCase(token)) {
                    return true;
                }
                try {
                    float value = Float.parseFloat(token);
                    return value > 0.0F && value <= 1000.0F;
                } catch (NumberFormatException ex) {
                    return false;
                }
            }
        });

        registerBuiltinArgument(ARG_PLAYER, new ArgumentProvider() {
            public List<String> suggest(CommandSender sender, String[] args, int argIndex, String prefixLower) {
                return sortAndDedupe(PlayerArgumentResolver.suggest(prefixLower));
            }

            public boolean matches(CommandSender sender, String[] args, int argIndex, String token) {
                return PlayerArgumentResolver.isValidPlayerArgument(sender, token);
            }
        });

        registerBuiltinArgument(ARG_ITEM_KEY, new ArgumentProvider() {
            public List<String> suggest(CommandSender sender, String[] args, int argIndex, String prefixLower) {
                return filterResourceKeys(ItemRegistry.displayKeys(), prefixLower);
            }

            public boolean matches(CommandSender sender, String[] args, int argIndex, String token) {
                return ItemRegistry.normalizeInputIdentifier(token) != null;
            }
        });

        registerBuiltinArgument(ARG_BLOCK_KEY, new ArgumentProvider() {
            public List<String> suggest(CommandSender sender, String[] args, int argIndex, String prefixLower) {
                return filterResourceKeys(BlockRegistry.displayKeys(), prefixLower);
            }

            public boolean matches(CommandSender sender, String[] args, int argIndex, String token) {
                return BlockRegistry.normalizeInputIdentifier(token) != null;
            }
        });

        registerBuiltinArgument(ARG_ENTITY_KEY, new ArgumentProvider() {
            public List<String> suggest(CommandSender sender, String[] args, int argIndex, String prefixLower) {
                ArrayList<ResourceLocation> summonable = new ArrayList<ResourceLocation>();
                for (ResourceLocation key : EntityTypeRegistry.primaryKeys()) {
                    if (isSummonableEntityKey(key)) {
                        summonable.add(key);
                    }
                }
                return filterResourceKeys(summonable, prefixLower);
            }

            public boolean matches(CommandSender sender, String[] args, int argIndex, String token) {
                String normalized = EntityTypeRegistry.normalizeInputIdentifier(token);
                if (normalized == null) {
                    return false;
                }
                return isSummonableEntityKey(new ResourceLocation(normalized));
            }
        });

        registerBuiltinArgument(ARG_STRUCTURE_KEY, new ArgumentProvider() {
            public List<String> suggest(CommandSender sender, String[] args, int argIndex, String prefixLower) {
                try {
                    StructureTypes.initialize();
                } catch (Throwable ignored) {}
                return filterResourceKeys(Registries.STRUCTURE_TYPE.keys(), prefixLower);
            }

            public boolean matches(CommandSender sender, String[] args, int argIndex, String token) {
                try {
                    StructureTypes.initialize();
                } catch (Throwable ignored) {}
                return StructureTypes.normalizeInputIdentifier(token) != null;
            }
        });

        registerBuiltinArgument(ARG_TIME_VALUE, new ArgumentProvider() {
            public List<String> suggest(CommandSender sender, String[] args, int argIndex, String prefixLower) {
                String action = safeLower(argAt(args, 0));
                if ("set".equals(action)) {
                    ArrayList<String> values = new ArrayList<String>(Arrays.asList(TIME_SET_VALUES));
                    values.addAll(Arrays.asList("0", "1000", "6000", "12000", "13000", "18000"));
                    return filterStrings(values, prefixLower);
                }
                return filterStrings(Arrays.asList("0", "1000", "6000", "12000", "13000", "18000"), prefixLower);
            }

            public boolean matches(CommandSender sender, String[] args, int argIndex, String token) {
                String action = safeLower(argAt(args, 0));
                if ("set".equals(action) && containsIgnoreCase(TIME_SET_VALUES, token)) {
                    return true;
                }
                return isIntegerToken(token);
            }
        });

        registerBuiltinArgument(ARG_GAMERULE_NAME, new ArgumentProvider() {
            public List<String> suggest(CommandSender sender, String[] args, int argIndex, String prefixLower) {
                ArrayList<String> values = new ArrayList<String>();
                values.addAll(Arrays.asList(BOOLEAN_GAMERULES));
                values.addAll(Arrays.asList(INTEGER_GAMERULES));
                return filterStrings(values, prefixLower);
            }

            public boolean matches(CommandSender sender, String[] args, int argIndex, String token) {
                return isBooleanGamerule(token) || isIntegerGamerule(token);
            }
        });

        registerBuiltinArgument(ARG_GAMERULE_VALUE, new ArgumentProvider() {
            public List<String> suggest(CommandSender sender, String[] args, int argIndex, String prefixLower) {
                String rule = safeLower(argAt(args, 0));
                if (isBooleanGamerule(rule)) {
                    return filterStrings(Arrays.asList("true", "false"), prefixLower);
                }
                return Collections.emptyList();
            }

            public boolean matches(CommandSender sender, String[] args, int argIndex, String token) {
                String rule = safeLower(argAt(args, 0));
                if (isBooleanGamerule(rule)) {
                    return "true".equalsIgnoreCase(token) || "false".equalsIgnoreCase(token);
                }
                if (isIntegerGamerule(rule)) {
                    return isIntegerToken(token);
                }
                return false;
            }
        });

        registerBuiltinArgument(ARG_INTEGER, new ArgumentProvider() {
            public List<String> suggest(CommandSender sender, String[] args, int argIndex, String prefixLower) {
                return filterStrings(Arrays.asList("1", "8", "16", "32", "64"), prefixLower);
            }

            public boolean matches(CommandSender sender, String[] args, int argIndex, String token) {
                return isIntegerToken(token);
            }
        });

        registerBuiltinArgument(ARG_COORD, new ArgumentProvider() {
            public List<String> suggest(CommandSender sender, String[] args, int argIndex, String prefixLower) {
                if (prefixLower.startsWith("~")) {
                    return filterStrings(Arrays.asList("~", "~1", "~-1"), prefixLower);
                }
                return filterStrings(Arrays.asList("~", "0", "1", "-1"), prefixLower);
            }

            public boolean matches(CommandSender sender, String[] args, int argIndex, String token) {
                return isCoordinateToken(token);
            }
        });

        registerBuiltinArgument(ARG_TEXT, new ArgumentProvider() {
            public List<String> suggest(CommandSender sender, String[] args, int argIndex, String prefixLower) {
                return Collections.emptyList();
            }

            public boolean matches(CommandSender sender, String[] args, int argIndex, String token) {
                return token != null && token.length() > 0;
            }
        });
    }

    private void registerBuiltinCommandSpecs() {
        registerSpecInternal(BUILTIN_OWNER, "admin", new String[] { "openinventory", "openinv" }, new String[][] {
            { ARG_PLAYER }
        }, true);

        registerSpecInternal(BUILTIN_OWNER, "give", new String[0], new String[][] {
            { ARG_PLAYER, ARG_ITEM_KEY },
            { ARG_PLAYER, ARG_ITEM_KEY, ARG_INTEGER }
        }, true);

        registerSpecInternal(BUILTIN_OWNER, "gamerule", new String[0], new String[][] {
            { ARG_GAMERULE_NAME },
            { ARG_GAMERULE_NAME, ARG_GAMERULE_VALUE }
        }, true);

        registerSpecInternal(BUILTIN_OWNER, "weather", new String[] { "toggledownfall" }, new String[][] {
            { ARG_WEATHER_TYPE },
            { ARG_WEATHER_TYPE, ARG_INTEGER }
        }, true);

        registerSpecInternal(BUILTIN_OWNER, "time", new String[0], new String[][] {
            { ARG_TIME_ACTION, ARG_TIME_VALUE }
        }, true);

        registerSpecInternal(BUILTIN_OWNER, "gamemode", new String[0], new String[][] {
            { ARG_PLAYER, ARG_GAMEMODE },
            { ARG_GAMEMODE },
            { ARG_GAMEMODE, ARG_PLAYER }
        }, true);

        registerSpecInternal(BUILTIN_OWNER, "summon", new String[0], new String[][] {
            { ARG_ENTITY_KEY },
            { ARG_ENTITY_KEY, ARG_COORD, ARG_COORD, ARG_COORD },
            { ARG_ENTITY_KEY, ARG_COORD, ARG_COORD, ARG_COORD, ARG_BLOCK_KEY }
        }, true);

        registerSpecInternal(BUILTIN_OWNER, "tp", new String[] { "teleport" }, new String[][] {
            { ARG_PLAYER },
            { ARG_PLAYER, ARG_PLAYER },
            { ARG_COORD, ARG_COORD, ARG_COORD },
            { ARG_PLAYER, ARG_COORD, ARG_COORD, ARG_COORD }
        }, true);

        registerSpecInternal(BUILTIN_OWNER, "tell", new String[0], new String[][] {
            { ARG_PLAYER, ARG_TEXT }
        }, true);

        registerSpecInternal(BUILTIN_OWNER, "locate", new String[0], new String[][] {
            { ARG_STRUCTURE_KEY }
        }, true);

        registerSpecInternal(BUILTIN_OWNER, "profile", new String[] { "profiler" }, new String[][] {
            { ARG_PROFILE_ACTION }
        }, true);

        registerSpecInternal(BUILTIN_OWNER, "debug", new String[0], new String[][] {
            { ARG_DEBUG_ACTION },
            { ARG_DEBUG_ACTION, ARG_TICK_RATE }
        }, true);

        registerSpecInternal(BUILTIN_OWNER, "setworldspawn", new String[0], new String[][] {
            {},
            { ARG_COORD, ARG_COORD, ARG_COORD }
        }, true);
    }

    private boolean registerSpecInternal(String ownerKey, String canonical, String[] aliases, String[][] syntaxes, boolean builtIn) {
        if (ownerKey == null || canonical == null) {
            return false;
        }
        if (builtIn && !BUILTIN_OWNER.equals(ownerKey)) {
            return false;
        }
        if (!builtIn && BUILTIN_OWNER.equals(ownerKey)) {
            return false;
        }

        String canonicalLower = normalizeCommandName(canonical);
        if (canonicalLower.length() == 0) {
            return false;
        }

        LinkedHashSet<String> aliasSet = new LinkedHashSet<String>();
        aliasSet.add(canonicalLower);
        if (aliases != null) {
            for (int i = 0; i < aliases.length; i++) {
                String aliasLower = normalizeCommandName(aliases[i]);
                if (aliasLower.length() > 0) {
                    aliasSet.add(aliasLower);
                }
            }
        }

        for (String aliasLower : aliasSet) {
            CommandSpec existingSpec = commandSpecsByAlias.get(aliasLower);
            if (existingSpec == null) {
                continue;
            }

            String existingCanonical = existingSpec.canonicalNameLower;
            String existingOwner = commandSpecOwnersByCanonical.get(existingCanonical);
            if (!ownerKey.equals(existingOwner)) {
                return false;
            }
            if (!existingCanonical.equals(canonicalLower)) {
                return false;
            }
        }

        String existingCanonicalOwner = commandSpecOwnersByCanonical.get(canonicalLower);
        if (existingCanonicalOwner != null) {
            if (!ownerKey.equals(existingCanonicalOwner)) {
                return false;
            }
            removeCommandSpecByCanonical(canonicalLower);
        }

        CommandSpec spec = new CommandSpec(canonicalLower);
        spec.aliasesLower.clear();
        spec.aliasesLower.addAll(aliasSet);

        if (syntaxes != null) {
            for (int i = 0; i < syntaxes.length; i++) {
                spec.syntaxes.add(new Syntax(syntaxes[i]));
            }
        }

        for (String alias : spec.aliasesLower) {
            commandSpecsByAlias.put(alias, spec);
        }
        commandSpecOwnersByCanonical.put(canonicalLower, ownerKey);
        commandSpecAliasesByCanonical.put(canonicalLower, new LinkedHashSet<String>(spec.aliasesLower));
        trackPluginCommandSpec(ownerKey, canonicalLower);
        return true;
    }

    private void removeCommandSpecByCanonical(String canonical) {
        if (canonical == null || canonical.length() == 0) {
            return;
        }

        LinkedHashSet<String> aliases = commandSpecAliasesByCanonical.remove(canonical);
        if (aliases == null) {
            CommandSpec spec = commandSpecsByAlias.get(canonical);
            if (spec != null) {
                aliases = new LinkedHashSet<String>(spec.aliasesLower);
            }
        }
        if (aliases != null) {
            for (String alias : aliases) {
                commandSpecsByAlias.remove(alias);
            }
        }

        String owner = commandSpecOwnersByCanonical.remove(canonical);
        removePluginCommandSpecTracking(owner, canonical);
    }

    private void trackPluginCommandSpec(String ownerKey, String canonical) {
        if (ownerKey == null || BUILTIN_OWNER.equals(ownerKey)) {
            return;
        }
        LinkedHashSet<String> canonicals = pluginCanonicalSpecs.get(ownerKey);
        if (canonicals == null) {
            canonicals = new LinkedHashSet<String>();
            pluginCanonicalSpecs.put(ownerKey, canonicals);
        }
        canonicals.add(canonical);
    }

    private void removePluginCommandSpecTracking(String ownerKey, String canonical) {
        if (ownerKey == null || BUILTIN_OWNER.equals(ownerKey)) {
            return;
        }
        LinkedHashSet<String> canonicals = pluginCanonicalSpecs.get(ownerKey);
        if (canonicals == null) {
            return;
        }
        canonicals.remove(canonical);
        if (canonicals.isEmpty()) {
            pluginCanonicalSpecs.remove(ownerKey);
        }
    }

    private boolean registerArgumentInternal(String ownerKey, String argumentId, ArgumentProvider provider, boolean builtIn) {
        if (ownerKey == null || provider == null) {
            return false;
        }
        if (builtIn && !BUILTIN_OWNER.equals(ownerKey)) {
            return false;
        }
        if (!builtIn && BUILTIN_OWNER.equals(ownerKey)) {
            return false;
        }

        String normalizedId = normalizeArgumentId(argumentId);
        if (normalizedId.length() == 0) {
            return false;
        }

        String existingOwner = argumentProviderOwners.get(normalizedId);
        if (existingOwner != null && !ownerKey.equals(existingOwner)) {
            return false;
        }

        argumentProviders.put(normalizedId, provider);
        argumentProviderOwners.put(normalizedId, ownerKey);
        trackPluginArgument(ownerKey, normalizedId);
        return true;
    }

    private void trackPluginArgument(String ownerKey, String argumentId) {
        if (ownerKey == null || BUILTIN_OWNER.equals(ownerKey)) {
            return;
        }
        LinkedHashSet<String> argumentIds = pluginArgumentIds.get(ownerKey);
        if (argumentIds == null) {
            argumentIds = new LinkedHashSet<String>();
            pluginArgumentIds.put(ownerKey, argumentIds);
        }
        argumentIds.add(argumentId);
    }

    private void removePluginArgumentTracking(String ownerKey, String argumentId) {
        if (ownerKey == null || BUILTIN_OWNER.equals(ownerKey)) {
            return;
        }
        LinkedHashSet<String> argumentIds = pluginArgumentIds.get(ownerKey);
        if (argumentIds == null) {
            return;
        }
        argumentIds.remove(argumentId);
        if (argumentIds.isEmpty()) {
            pluginArgumentIds.remove(ownerKey);
        }
    }

    private void registerBuiltinArgument(String argumentId, ArgumentProvider provider) {
        registerArgumentInternal(BUILTIN_OWNER, argumentId, provider, true);
    }

    private void registerBuiltinLiteralArgument(final String argumentId, final String[] values) {
        registerBuiltinArgument(argumentId, new ArgumentProvider() {
            public List<String> suggest(CommandSender sender, String[] args, int argIndex, String prefixLower) {
                return filterStrings(Arrays.asList(values), prefixLower);
            }

            public boolean matches(CommandSender sender, String[] args, int argIndex, String token) {
                return containsIgnoreCase(values, token);
            }
        });
    }

    private static boolean isSummonableEntityKey(ResourceLocation key) {
        if (key == null) {
            return false;
        }
        Class<?> entityClass = EntityTypeRegistry.get(key);
        if (entityClass == null) {
            return false;
        }
        return entityClass != EntityItem.class && entityClass != EntityPainting.class;
    }

    private static List<String> filterStrings(Collection<String> values, String prefixLower) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }

        String prefix = safeLower(prefixLower);
        LinkedHashSet<String> out = new LinkedHashSet<String>();
        for (String value : values) {
            if (value == null || value.length() == 0) {
                continue;
            }
            if (safeLower(value).startsWith(prefix)) {
                out.add(value);
            }
        }
        return sortAndDedupe(out);
    }

    private static List<String> filterResourceKeys(Collection<ResourceLocation> keys, String prefixLower) {
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyList();
        }

        String prefix = safeLower(prefixLower);
        boolean namespacedInput = prefix.indexOf(':') >= 0;
        LinkedHashSet<String> out = new LinkedHashSet<String>();

        for (ResourceLocation key : keys) {
            if (key == null) {
                continue;
            }

            String full = key.toString();
            String path = key.getPath();
            if (namespacedInput) {
                if (safeLower(full).startsWith(prefix)) {
                    out.add(full);
                }
            } else {
                if (safeLower(path).startsWith(prefix)) {
                    out.add(path);
                }
            }
        }

        return sortAndDedupe(out);
    }

    private static List<String> sortAndDedupe(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }

        ArrayList<String> out = new ArrayList<String>();
        LinkedHashSet<String> seen = new LinkedHashSet<String>();
        for (String value : values) {
            if (value == null || value.length() == 0) {
                continue;
            }
            if (seen.add(value)) {
                out.add(value);
            }
        }
        Collections.sort(out, CASE_INSENSITIVE_ORDER);
        return out;
    }

    private static String normalizeCommandName(String name) {
        if (name == null) {
            return "";
        }
        String trimmed = name.trim().toLowerCase(Locale.ROOT);
        if (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        int space = trimmed.indexOf(' ');
        if (space >= 0) {
            trimmed = trimmed.substring(0, space);
        }
        return trimmed;
    }

    private static String safeLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static String argAt(String[] args, int index) {
        if (args == null || index < 0 || index >= args.length) {
            return "";
        }
        String value = args[index];
        return value == null ? "" : value;
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    private static boolean containsIgnoreCase(String[] values, String token) {
        if (values == null || token == null) {
            return false;
        }
        for (int i = 0; i < values.length; i++) {
            String value = values[i];
            if (value != null && value.equalsIgnoreCase(token)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isIntegerToken(String token) {
        if (token == null || token.length() == 0) {
            return false;
        }
        try {
            Integer.parseInt(token);
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static boolean isCoordinateToken(String token) {
        if (token == null || token.length() == 0) {
            return false;
        }
        if ("~".equals(token)) {
            return true;
        }
        if (token.startsWith("~")) {
            String remainder = token.substring(1);
            if (remainder.length() == 0) {
                return true;
            }
            try {
                Double.parseDouble(remainder);
                return true;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        try {
            Double.parseDouble(token);
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static boolean isBooleanGamerule(String value) {
        return containsIgnoreCase(BOOLEAN_GAMERULES, value);
    }

    private static boolean isIntegerGamerule(String value) {
        return containsIgnoreCase(INTEGER_GAMERULES, value);
    }
}
