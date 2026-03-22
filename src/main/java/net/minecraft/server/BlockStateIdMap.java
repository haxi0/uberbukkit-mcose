package net.minecraft.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public final class BlockStateIdMap {
    private final Map<BlockStateKey, Integer> idByState = new HashMap<BlockStateKey, Integer>();
    private final ArrayList<BlockStateKey> stateById = new ArrayList<BlockStateKey>();

    public synchronized int getOrCreateId(BlockStateKey key) {
        Integer existing = this.idByState.get(key);
        if (existing != null) {
            return existing.intValue();
        }
        int id = this.stateById.size();
        this.stateById.add(key);
        this.idByState.put(key, Integer.valueOf(id));
        return id;
    }

    public synchronized BlockStateKey getState(int id) {
        if (id < 0 || id >= this.stateById.size()) {
            return null;
        }
        return this.stateById.get(id);
    }

    public synchronized int size() {
        return this.stateById.size();
    }
}
