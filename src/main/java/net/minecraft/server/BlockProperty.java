package net.minecraft.server;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class BlockProperty {
    private final String name;
    private final Set<String> allowedValues;

    public BlockProperty(String name, Set<String> allowedValues) {
        this.name = name == null ? "" : name;
        LinkedHashSet<String> copy = new LinkedHashSet<String>();
        if (allowedValues != null) {
            copy.addAll(allowedValues);
        }
        this.allowedValues = Collections.unmodifiableSet(copy);
    }

    public String getName() {
        return this.name;
    }

    public Set<String> getAllowedValues() {
        return this.allowedValues;
    }

    public boolean accepts(String serializedValue) {
        if (this.allowedValues.isEmpty()) {
            return true;
        }
        return this.allowedValues.contains(serializedValue);
    }
}
