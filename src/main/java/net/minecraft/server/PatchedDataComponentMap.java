package net.minecraft.server;

public final class PatchedDataComponentMap {
    private final DataComponentMap base;
    private final DataComponentPatch patch;

    public PatchedDataComponentMap(DataComponentMap base, DataComponentPatch patch) {
        this.base = base == null ? DataComponentMap.EMPTY : base;
        this.patch = patch == null ? DataComponentPatch.empty() : patch;
    }

    public <T> T get(DataComponentType<T> type) {
        if (type == null) {
            return null;
        }
        if (this.patch.getRemovedTypes().contains(type)) {
            return null;
        }
        T patched = this.patch.get(type);
        if (patched != null) {
            return patched;
        }
        return this.base.get(type);
    }

    public DataComponentMap base() {
        return this.base;
    }

    public DataComponentPatch patch() {
        return this.patch;
    }
}
