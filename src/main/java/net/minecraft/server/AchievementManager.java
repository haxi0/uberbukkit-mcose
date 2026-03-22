package net.minecraft.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;
import net.minecraft.server.registry.AchievementRegistryApi;
import net.minecraft.server.util.ResourceLocation;

/**
 * Centralized Achievement Manager for the server.
 * Handles achievement tracking, unlocking, persistence, and broadcasting.
 */
public final class AchievementManager {

    private static final Logger log = Logger.getLogger("Minecraft");

    private static final int ACHIEVEMENT_STAT_BASE = 5242880;
    private static final int NBT_VERSION_LEGACY = 1;
    private static final int NBT_VERSION_KEYED = 2;
    private static final int NBT_VERSION_PROGRESS = 3;
    private static final String NBT_VERSION = "Version";
    private static final String NBT_ACHIEVEMENTS = "Achievements";
    private static final String NBT_ID = "id";
    private static final String NBT_KEY = "key";
    private static final String NBT_PROGRESS = "Progress";
    private static final String NBT_CRITERIA = "Criteria";
    private static final String NBT_REQUIREMENTS = "Requirements";
    private static final String LEGACY_CRITERION = "legacy_trigger";

    private final EntityPlayer player;
    private final Map<Integer, AchievementProgressState> progressByStatId;
    private final Set<Integer> unresolvedLegacyAchievementIds;
    private final Set<Integer> unlockedAchievementIds;

    public AchievementManager(EntityPlayer player) {
        this.player = player;
        this.progressByStatId = new HashMap<Integer, AchievementProgressState>();
        this.unresolvedLegacyAchievementIds = new HashSet<Integer>();
        this.unlockedAchievementIds = new HashSet<Integer>();
    }

    public boolean hasAchievement(Achievement achievement) {
        if (achievement == null) {
            return false;
        }

        AchievementProgressState state = progressByStatId.get(Integer.valueOf(achievement.e));
        return (state != null && state.isDone()) || unlockedAchievementIds.contains(Integer.valueOf(achievement.e));
    }

    public boolean hasAchievementById(int statId) {
        return getUnlockedAchievementIds().contains(Integer.valueOf(statId));
    }

    public boolean hasAchievement(String namespacedKey) {
        Achievement achievement = findAchievementByKey(namespacedKey);
        return achievement != null && hasAchievement(achievement);
    }

    public boolean canUnlock(Achievement achievement) {
        if (achievement == null) {
            return false;
        }

        return achievement.c == null || hasAchievement(achievement.c);
    }

    public boolean unlock(Achievement achievement) {
        if (achievement == null) {
            return false;
        }

        if (hasAchievement(achievement)) {
            return false;
        }

        if (!canUnlock(achievement)) {
            return false;
        }

        return grantCriterion(achievement, LEGACY_CRITERION);
    }

    public boolean unlock(String namespacedKey) {
        return unlock(findAchievementByKey(namespacedKey));
    }

    public boolean forceUnlock(Achievement achievement) {
        if (achievement == null) {
            return false;
        }

        if (hasAchievement(achievement)) {
            return false;
        }

        return forceGrantCriterion(achievement, LEGACY_CRITERION);
    }

    public boolean forceUnlock(String namespacedKey) {
        return forceUnlock(findAchievementByKey(namespacedKey));
    }

    public boolean grantCriterion(Achievement achievement, String criterion) {
        return grantCriterion(achievement, criterion, false);
    }

    public boolean grantCriterion(String namespacedKey, String criterion) {
        return grantCriterion(findAchievementByKey(namespacedKey), criterion, false);
    }

    public boolean forceGrantCriterion(Achievement achievement, String criterion) {
        return grantCriterion(achievement, criterion, true);
    }

    public boolean forceGrantCriterion(String namespacedKey, String criterion) {
        return grantCriterion(findAchievementByKey(namespacedKey), criterion, true);
    }

    public boolean revokeCriterion(Achievement achievement, String criterion) {
        if (achievement == null || criterion == null || criterion.length() == 0) {
            return false;
        }

        AchievementProgressState progress = progressByStatId.get(Integer.valueOf(achievement.e));
        if (progress == null) {
            return false;
        }

        boolean revoked = progress.revoke(criterion);
        if (!revoked) {
            return false;
        }

        rebuildUnlockedSet();
        persistPlayerData();
        return true;
    }

    public boolean revokeCriterion(String namespacedKey, String criterion) {
        return revokeCriterion(findAchievementByKey(namespacedKey), criterion);
    }

    private boolean grantCriterion(Achievement achievement, String criterion, boolean forced) {
        if (achievement == null || criterion == null || criterion.length() == 0) {
            return false;
        }

        if (!forced && !canUnlock(achievement)) {
            return false;
        }

        AchievementProgressState progress = getOrCreateProgress(achievement);
        boolean wasDone = progress.isDone();
        boolean granted = progress.grant(criterion, System.currentTimeMillis());
        boolean doneNow = progress.isDone();
        if (doneNow) {
            unresolvedLegacyAchievementIds.remove(Integer.valueOf(achievement.e));
        }

        if (!granted) {
            return false;
        }

        rebuildUnlockedSet();
        if (!wasDone && doneNow) {
            onAchievementCompleted(achievement, forced);
        } else {
            persistPlayerData();
        }
        return true;
    }

    public Achievement getAchievement(String namespacedKey) {
        return findAchievementByKey(namespacedKey);
    }

    private void onAchievementCompleted(Achievement achievement, boolean forced) {
        persistPlayerData();
        ServerStatistics.getInstance().recordAchievement(player.name, achievement);
        sendAchievementPacket(achievement);
        broadcastAchievement(achievement);
        if (forced) {
            log.info("[Achievement] " + player.name + " earned (forced): " + getAchievementName(achievement));
        } else {
            log.info("[Achievement] " + player.name + " earned: " + getAchievementName(achievement));
        }
    }

    private void sendAchievementPacket(Achievement achievement) {
        if (player.netServerHandler == null) {
            return;
        }

        if (!player.protocol.canReceivePacket(200)) {
            return;
        }

        // Vanilla compatibility boundary: achievements always travel on legacy Packet200Statistic.
        player.netServerHandler.sendPacket(new Packet200Statistic(achievement.e, 1));
    }

    private void broadcastAchievement(Achievement achievement) {
        MinecraftServer server = player.b;
        if (server == null && player.world instanceof WorldServer) {
            server = ((WorldServer) player.world).server;
        }

        if (server == null || server.serverConfigurationManager == null) {
            sendChatToPlayer("\u00A7eYou have just earned the achievement \u00A7a[" + getAchievementName(achievement) + "]");
            return;
        }

        boolean shouldBroadcast = true;
        try {
            WorldServer overworld = server.getWorldServer(0);
            if (overworld != null && overworld.worldData != null) {
                shouldBroadcast = overworld.worldData.getAdvertiseAchievements();
            }
        } catch (Exception ignored) {
            shouldBroadcast = true;
        }

        String achievementName = getAchievementName(achievement);
        String message = "\u00A7e" + player.name + " has just earned the achievement \u00A7a[" + achievementName + "]";

        if (shouldBroadcast) {
            server.serverConfigurationManager.sendAll(new Packet3Chat(message));
        } else {
            sendChatToPlayer(message);
        }
    }

    private void sendChatToPlayer(String message) {
        if (player.netServerHandler != null) {
            player.netServerHandler.sendPacket(new Packet3Chat(message));
        }
    }

    private void persistPlayerData() {
        try {
            if (player == null || player.b == null || player.b.serverConfigurationManager == null) {
                return;
            }

            PlayerFileData playerFileData = player.b.serverConfigurationManager.playerFileData;
            if (playerFileData != null) {
                playerFileData.a(player);
            }
        } catch (Throwable t) {
            log.warning("[AchievementManager] Failed to persist achievements for " + player.name + ": " + t.getMessage());
        }
    }

    public static String getAchievementName(Achievement achievement) {
        if (achievement == null) {
            return "Unknown Achievement";
        }

        if (achievement.f != null && !achievement.f.startsWith("achievement.")) {
            return achievement.f;
        }

        ResourceLocation key = AchievementRegistryApi.getKey(achievement);
        if (key != null) {
            return formatAchievementName(key.getPath());
        }

        return "Unknown Achievement";
    }

    private static String formatAchievementName(String keyPath) {
        if (keyPath == null || keyPath.length() == 0) {
            return "Unknown Achievement";
        }

        StringBuilder out = new StringBuilder();
        boolean nextUpper = true;
        for (int i = 0; i < keyPath.length(); i++) {
            char c = keyPath.charAt(i);
            if (c == '_') {
                out.append(' ');
                nextUpper = true;
                continue;
            }

            if (nextUpper && c >= 'a' && c <= 'z') {
                out.append((char) (c - 32));
            } else {
                out.append(c);
            }
            nextUpper = false;
        }

        return out.toString();
    }

    public void saveToNBT(NBTTagCompound nbt) {
        nbt.a(NBT_VERSION, NBT_VERSION_PROGRESS);

        NBTTagList achievementList = new NBTTagList();
        List<Integer> statIdsToWrite = collectPersistedStatIds();
        for (Integer statIdBoxed : statIdsToWrite) {
            int statId = statIdBoxed.intValue();
            NBTTagCompound achievementTag = new NBTTagCompound();
            Achievement achievement = findAchievementByStatId(statId);
            AchievementProgressState progressState;

            if (achievement != null) {
                ResourceLocation key = AchievementRegistryApi.getKey(achievement);
                if (key != null) {
                    achievementTag.setString(NBT_KEY, key.toString());
                }
                statId = achievement.e;
                progressState = getOrCreateProgress(achievement);
            } else {
                progressState = AchievementProgressState.completedDefault();
            }

            achievementTag.a(NBT_ID, statId);
            NBTTagCompound progressTag = new NBTTagCompound();
            progressState.writeToNBT(progressTag);
            achievementTag.a(NBT_PROGRESS, progressTag);
            achievementList.a(achievementTag);
        }

        nbt.a(NBT_ACHIEVEMENTS, achievementList);
    }

    public void loadFromNBT(NBTTagCompound nbt) {
        progressByStatId.clear();
        unresolvedLegacyAchievementIds.clear();
        unlockedAchievementIds.clear();

        if (nbt == null || !nbt.hasKey(NBT_ACHIEVEMENTS)) {
            log.fine("[AchievementManager] Loaded 0 achievements for " + player.name);
            return;
        }

        int version = nbt.hasKey(NBT_VERSION) ? nbt.e(NBT_VERSION) : NBT_VERSION_LEGACY;
        NBTTagList achievementList = nbt.l(NBT_ACHIEVEMENTS);
        int resolved = 0;

        for (int i = 0; i < achievementList.c(); i++) {
            NBTBase entry = achievementList.a(i);
            if (!(entry instanceof NBTTagCompound)) {
                continue;
            }

            NBTTagCompound achievementTag = (NBTTagCompound) entry;
            Achievement achievement = resolveAchievement(achievementTag, version);
            AchievementProgressState loadedProgress = readProgressFromEntry(achievementTag, version);

            if (achievement != null) {
                AchievementProgressState state = getOrCreateProgress(achievement);
                state.mergeFrom(loadedProgress);
                state.syncRequirements(getRequirements(achievement));
                if (state.isDone()) {
                    ++resolved;
                }
                continue;
            }

            if (achievementTag.hasKey(NBT_ID)) {
                int statId = achievementTag.e(NBT_ID);
                if (statId >= ACHIEVEMENT_STAT_BASE && loadedProgress.isDone()) {
                    unresolvedLegacyAchievementIds.add(Integer.valueOf(statId));
                }
            }
        }

        rebuildUnlockedSet();
        log.fine("[AchievementManager] Loaded " + unlockedAchievementIds.size() + " achievements for " + player.name + " (" + resolved + " registry-resolved)");
    }

    private AchievementProgressState readProgressFromEntry(NBTTagCompound achievementTag, int version) {
        if (achievementTag == null) {
            return AchievementProgressState.completedDefault();
        }

        if (version >= NBT_VERSION_PROGRESS && achievementTag.hasKey(NBT_PROGRESS)) {
            return AchievementProgressState.readFromNBT(achievementTag.k(NBT_PROGRESS));
        }

        return AchievementProgressState.completedDefault();
    }

    private Achievement resolveAchievement(NBTTagCompound achievementTag, int version) {
        if (achievementTag == null) {
            return null;
        }

        if (achievementTag.hasKey(NBT_KEY)) {
            String keyString = achievementTag.getString(NBT_KEY);
            Achievement byKey = findAchievementByKey(keyString);
            if (byKey != null) {
                return byKey;
            }
        }

        if (achievementTag.hasKey(NBT_ID)) {
            Achievement byId = findAchievementByStatId(achievementTag.e(NBT_ID));
            if (byId != null) {
                return byId;
            }
        }

        if (version <= NBT_VERSION_LEGACY && achievementTag.hasKey(NBT_KEY)) {
            Achievement byLegacyKey = findAchievementByKey(achievementTag.getString(NBT_KEY));
            if (byLegacyKey != null) {
                return byLegacyKey;
            }
        }

        return null;
    }

    public void syncAllToClient() {
        if (player.netServerHandler == null) {
            return;
        }

        if (!player.protocol.canReceivePacket(200)) {
            return;
        }

        for (Integer statId : new TreeSet<Integer>(getUnlockedAchievementIds())) {
            player.netServerHandler.sendPacket(new Packet200Statistic(statId.intValue(), 1));
        }

        log.fine("[AchievementManager] Synced " + unlockedAchievementIds.size() + " achievements to " + player.name);
    }

    public Set<Integer> getUnlockedAchievementIds() {
        rebuildUnlockedSet();
        return new HashSet<Integer>(unlockedAchievementIds);
    }

    public Set<String> getUnlockedAchievementKeys() {
        Set<String> keys = new TreeSet<String>();
        for (Integer statId : getUnlockedAchievementIds()) {
            Achievement achievement = findAchievementByStatId(statId.intValue());
            if (achievement == null) {
                continue;
            }

            ResourceLocation key = AchievementRegistryApi.getKey(achievement);
            if (key != null) {
                keys.add(key.toString());
            }
        }

        return keys;
    }

    public int getUnlockedCount() {
        return getUnlockedAchievementIds().size();
    }

    public static Achievement findAchievementByStatId(int statId) {
        if (statId < ACHIEVEMENT_STAT_BASE) {
            return null;
        }

        return AchievementRegistryApi.getByStatId(statId);
    }

    public static Achievement findAchievementByKey(ResourceLocation key) {
        return AchievementRegistryApi.get(key);
    }

    public static Achievement findAchievementByKey(String keyString) {
        ResourceLocation key = parseKey(keyString);
        return key == null ? null : findAchievementByKey(key);
    }

    private static ResourceLocation parseKey(String keyString) {
        if (keyString == null || keyString.length() == 0) {
            return null;
        }

        try {
            return new ResourceLocation(keyString);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static boolean trigger(EntityPlayer player, Achievement achievement) {
        if (player != null && player.achievementManager != null && achievement != null) {
            return player.achievementManager.unlock(achievement);
        }

        return false;
    }

    public static boolean trigger(EntityPlayer player, String namespacedKey) {
        if (player != null && player.achievementManager != null) {
            return player.achievementManager.unlock(namespacedKey);
        }

        return false;
    }

    public static boolean grantCriterion(EntityPlayer player, Achievement achievement, String criterion) {
        if (player != null && player.achievementManager != null && achievement != null) {
            return player.achievementManager.grantCriterion(achievement, criterion);
        }

        return false;
    }

    public static boolean grantCriterion(EntityPlayer player, String namespacedKey, String criterion) {
        if (player != null && player.achievementManager != null) {
            return player.achievementManager.grantCriterion(namespacedKey, criterion);
        }

        return false;
    }

    public static boolean forceGrantCriterion(EntityPlayer player, Achievement achievement, String criterion) {
        if (player != null && player.achievementManager != null && achievement != null) {
            return player.achievementManager.forceGrantCriterion(achievement, criterion);
        }

        return false;
    }

    public static boolean forceGrantCriterion(EntityPlayer player, String namespacedKey, String criterion) {
        if (player != null && player.achievementManager != null) {
            return player.achievementManager.forceGrantCriterion(namespacedKey, criterion);
        }

        return false;
    }

    public static boolean revokeCriterion(EntityPlayer player, Achievement achievement, String criterion) {
        if (player != null && player.achievementManager != null && achievement != null) {
            return player.achievementManager.revokeCriterion(achievement, criterion);
        }

        return false;
    }

    public static boolean revokeCriterion(EntityPlayer player, String namespacedKey, String criterion) {
        if (player != null && player.achievementManager != null) {
            return player.achievementManager.revokeCriterion(namespacedKey, criterion);
        }

        return false;
    }

    public static boolean hasUnlocked(EntityPlayer player, Achievement achievement) {
        if (player != null && player.achievementManager != null && achievement != null) {
            return player.achievementManager.hasAchievement(achievement);
        }

        return false;
    }

    public static boolean hasUnlocked(EntityPlayer player, String namespacedKey) {
        if (player != null && player.achievementManager != null) {
            return player.achievementManager.hasAchievement(namespacedKey);
        }

        return false;
    }

    private List<Integer> collectPersistedStatIds() {
        TreeSet<Integer> sorted = new TreeSet<Integer>();
        sorted.addAll(unresolvedLegacyAchievementIds);

        for (Map.Entry<Integer, AchievementProgressState> entry : progressByStatId.entrySet()) {
            AchievementProgressState state = entry.getValue();
            if (state != null && state.hasProgress()) {
                sorted.add(entry.getKey());
            }
        }

        return new ArrayList<Integer>(sorted);
    }

    private void rebuildUnlockedSet() {
        if (!unresolvedLegacyAchievementIds.isEmpty()) {
            List<Integer> resolved = new ArrayList<Integer>();
            for (Integer statId : unresolvedLegacyAchievementIds) {
                if (findAchievementByStatId(statId.intValue()) != null) {
                    resolved.add(statId);
                }
            }

            for (Integer statId : resolved) {
                unresolvedLegacyAchievementIds.remove(statId);
                Achievement achievement = findAchievementByStatId(statId.intValue());
                if (achievement != null) {
                    markAchievementComplete(achievement, 0L);
                }
            }
        }

        unlockedAchievementIds.clear();
        unlockedAchievementIds.addAll(unresolvedLegacyAchievementIds);
        for (Map.Entry<Integer, AchievementProgressState> entry : progressByStatId.entrySet()) {
            AchievementProgressState state = entry.getValue();
            if (state != null && state.isDone()) {
                unlockedAchievementIds.add(entry.getKey());
            }
        }
    }

    private RequirementSet getRequirements(Achievement achievement) {
        if (achievement == null) {
            return RequirementSet.EMPTY;
        }
        return RequirementSet.single(LEGACY_CRITERION);
    }

    private AchievementProgressState getOrCreateProgress(Achievement achievement) {
        Integer statId = Integer.valueOf(achievement.e);
        AchievementProgressState progress = progressByStatId.get(statId);
        if (progress == null) {
            progress = new AchievementProgressState();
            progressByStatId.put(statId, progress);
        }
        progress.syncRequirements(getRequirements(achievement));
        return progress;
    }

    private boolean markAchievementComplete(Achievement achievement, long obtainedAtMillis) {
        if (achievement == null) {
            return false;
        }

        AchievementProgressState progress = getOrCreateProgress(achievement);
        boolean wasDone = progress.isDone();
        progress.grant(LEGACY_CRITERION, obtainedAtMillis);
        boolean doneNow = progress.isDone();
        if (doneNow) {
            unresolvedLegacyAchievementIds.remove(Integer.valueOf(achievement.e));
        }
        return !wasDone && doneNow;
    }

    private static final class AchievementProgressState {
        private final Map<String, CriterionProgressState> criteria = new HashMap<String, CriterionProgressState>();
        private RequirementSet requirements = RequirementSet.EMPTY;

        static AchievementProgressState completedDefault() {
            AchievementProgressState state = new AchievementProgressState();
            state.syncRequirements(RequirementSet.single(LEGACY_CRITERION));
            state.grant(LEGACY_CRITERION, 0L);
            return state;
        }

        void syncRequirements(RequirementSet newRequirements) {
            this.requirements = newRequirements == null ? RequirementSet.EMPTY : newRequirements;
            Set<String> names = this.requirements.names();
            criteria.entrySet().removeIf(entry -> entry == null || !names.contains(entry.getKey()));
            for (String criterionName : this.requirements.names()) {
                if (!criteria.containsKey(criterionName)) {
                    criteria.put(criterionName, new CriterionProgressState());
                }
            }
        }

        boolean grant(String criterionName, long obtainedAtMillis) {
            CriterionProgressState progress = criteria.get(criterionName);
            if (progress == null) {
                progress = new CriterionProgressState();
                criteria.put(criterionName, progress);
            }
            return progress.grant(obtainedAtMillis);
        }

        boolean isDone() {
            return requirements.test(criteria);
        }

        boolean hasProgress() {
            for (CriterionProgressState state : criteria.values()) {
                if (state.isDone()) {
                    return true;
                }
            }
            return false;
        }

        void mergeFrom(AchievementProgressState other) {
            if (other == null) {
                return;
            }

            for (Map.Entry<String, CriterionProgressState> entry : other.criteria.entrySet()) {
                String criterionName = entry.getKey();
                if (criterionName == null || criterionName.length() == 0) {
                    continue;
                }

                CriterionProgressState mine = criteria.get(criterionName);
                if (mine == null) {
                    mine = new CriterionProgressState();
                    criteria.put(criterionName, mine);
                }
                mine.mergeFrom(entry.getValue());
            }

            if (requirements.isEmpty() && !other.requirements.isEmpty()) {
                requirements = other.requirements;
            }
        }

        boolean revoke(String criterionName) {
            CriterionProgressState progress = criteria.get(criterionName);
            return progress != null && progress.revoke();
        }

        void writeToNBT(NBTTagCompound progressTag) {
            NBTTagCompound criteriaTag = new NBTTagCompound();
            for (Map.Entry<String, CriterionProgressState> entry : criteria.entrySet()) {
                String criterionName = entry.getKey();
                CriterionProgressState criterionState = entry.getValue();
                if (criterionName == null || criterionName.length() == 0 || criterionState == null || !criterionState.isDone()) {
                    continue;
                }
                criteriaTag.setLong(criterionName, criterionState.obtainedAtMillis);
            }
            progressTag.a(NBT_CRITERIA, criteriaTag);

            NBTTagCompound requirementsTag = new NBTTagCompound();
            requirements.writeToNBT(requirementsTag);
            progressTag.a(NBT_REQUIREMENTS, requirementsTag);
        }

        static AchievementProgressState readFromNBT(NBTTagCompound progressTag) {
            AchievementProgressState state = new AchievementProgressState();
            if (progressTag == null) {
                return state;
            }

            if (progressTag.hasKey(NBT_CRITERIA)) {
                NBTTagCompound criteriaTag = progressTag.k(NBT_CRITERIA);
                for (String criterionName : criteriaTag.getKeys()) {
                    long obtainedAt = criteriaTag.getLong(criterionName);
                    if (obtainedAt > 0L) {
                        CriterionProgressState criterionState = new CriterionProgressState();
                        criterionState.obtainedAtMillis = obtainedAt;
                        state.criteria.put(criterionName, criterionState);
                    }
                }
            }

            if (progressTag.hasKey(NBT_REQUIREMENTS)) {
                state.requirements = RequirementSet.readFromNBT(progressTag.k(NBT_REQUIREMENTS));
            }

            return state;
        }
    }

    private static final class CriterionProgressState {
        private long obtainedAtMillis;

        boolean isDone() {
            return obtainedAtMillis > 0L;
        }

        boolean grant(long obtainedAtMillis) {
            if (isDone()) {
                return false;
            }
            if (obtainedAtMillis <= 0L) {
                obtainedAtMillis = System.currentTimeMillis();
            }
            this.obtainedAtMillis = obtainedAtMillis;
            return true;
        }

        void mergeFrom(CriterionProgressState other) {
            if (other == null || !other.isDone()) {
                return;
            }
            if (!isDone() || other.obtainedAtMillis < this.obtainedAtMillis) {
                this.obtainedAtMillis = other.obtainedAtMillis;
            }
        }

        boolean revoke() {
            if (!isDone()) {
                return false;
            }
            this.obtainedAtMillis = 0L;
            return true;
        }
    }

    private static final class RequirementSet {
        private static final String NBT_GROUP_COUNT = "GroupCount";
        private static final String NBT_GROUP_PREFIX = "Group";
        private static final String NBT_CRITERION_COUNT = "CriterionCount";
        private static final String NBT_CRITERION_PREFIX = "Criterion";

        private static final RequirementSet EMPTY = new RequirementSet(new ArrayList<List<String>>());
        private final List<List<String>> groups;

        RequirementSet(List<List<String>> groups) {
            this.groups = groups;
        }

        static RequirementSet single(String criterionName) {
            List<String> group = new ArrayList<String>();
            group.add(criterionName);
            List<List<String>> groups = new ArrayList<List<String>>();
            groups.add(group);
            return new RequirementSet(groups);
        }

        boolean isEmpty() {
            return groups.isEmpty();
        }

        Set<String> names() {
            Set<String> names = new HashSet<String>();
            for (List<String> group : groups) {
                names.addAll(group);
            }
            return names;
        }

        boolean test(Map<String, CriterionProgressState> criteria) {
            if (groups.isEmpty()) {
                return false;
            }

            for (List<String> group : groups) {
                boolean any = false;
                for (String criterion : group) {
                    CriterionProgressState progress = criteria.get(criterion);
                    if (progress != null && progress.isDone()) {
                        any = true;
                        break;
                    }
                }
                if (!any) {
                    return false;
                }
            }
            return true;
        }

        void writeToNBT(NBTTagCompound tag) {
            tag.a(NBT_GROUP_COUNT, groups.size());
            for (int i = 0; i < groups.size(); ++i) {
                List<String> group = groups.get(i);
                NBTTagCompound groupTag = new NBTTagCompound();
                groupTag.a(NBT_CRITERION_COUNT, group.size());
                for (int j = 0; j < group.size(); ++j) {
                    String criterion = group.get(j);
                    groupTag.setString(NBT_CRITERION_PREFIX + j, criterion == null ? "" : criterion);
                }
                tag.a(NBT_GROUP_PREFIX + i, groupTag);
            }
        }

        static RequirementSet readFromNBT(NBTTagCompound tag) {
            if (tag == null) {
                return EMPTY;
            }

            int groupCount = tag.e(NBT_GROUP_COUNT);
            if (groupCount <= 0) {
                return EMPTY;
            }

            List<List<String>> groups = new ArrayList<List<String>>();
            for (int i = 0; i < groupCount; ++i) {
                String groupKey = NBT_GROUP_PREFIX + i;
                if (!tag.hasKey(groupKey)) {
                    continue;
                }

                NBTTagCompound groupTag = tag.k(groupKey);
                int criterionCount = groupTag.e(NBT_CRITERION_COUNT);
                if (criterionCount <= 0) {
                    continue;
                }

                List<String> group = new ArrayList<String>();
                for (int j = 0; j < criterionCount; ++j) {
                    String criterion = groupTag.getString(NBT_CRITERION_PREFIX + j);
                    if (criterion != null && criterion.length() > 0) {
                        group.add(criterion);
                    }
                }

                if (!group.isEmpty()) {
                    groups.add(group);
                }
            }

            if (groups.isEmpty()) {
                return EMPTY;
            }
            return new RequirementSet(groups);
        }
    }
}
