package blockreminder.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Value-only view of combat. No game objects, callbacks, action queues or RNGs
 * can escape into the calculator or an extension.
 */
public final class CombatSnapshot {
    public enum Kind { POWER, RELIC, ORB, CARD, ENEMY_POWER, STANCE, BLIGHT }

    public static final class Entity {
        public final Kind kind;
        public final String className, id, name;
        public final int amount;
        public final Map<String, Integer> values;

        public Entity(Kind kind, String className, String id, String name, int amount,
                      Map<String, Integer> values) {
            this.kind = kind;
            this.className = className;
            this.id = id == null ? "" : id;
            this.name = name == null ? this.id : name;
            this.amount = amount;
            this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        }

        public int value(String key, int fallback) {
            Integer v = values.get(key);
            return v == null ? fallback : v;
        }

        public boolean flag(String key) { return value(key, 0) != 0; }
    }

    public final int block, health, focus, maxOrbs;
    public final String stance;
    public final boolean includeEthereal;
    public final List<Entity> entities;
    public final List<String> uncertainties;

    public CombatSnapshot(int block, int health, int focus, int maxOrbs, String stance,
                          boolean includeEthereal, List<Entity> entities, List<String> uncertainties) {
        this.block = Math.max(0, block);
        this.health = health;
        this.focus = focus;
        this.maxOrbs = maxOrbs;
        this.stance = stance == null ? "" : stance;
        this.includeEthereal = includeEthereal;
        this.entities = Collections.unmodifiableList(new ArrayList<>(entities));
        this.uncertainties = Collections.unmodifiableList(new ArrayList<>(uncertainties));
    }

    public List<Entity> ofKind(Kind kind) {
        List<Entity> found = new ArrayList<>();
        for (Entity e : entities) if (e.kind == kind) found.add(e);
        return Collections.unmodifiableList(found);
    }

    /** Match concrete classes, never a mod that happens to reuse a vanilla ID. */
    public Entity find(String className) {
        for (Entity e : entities) if (e.className.equals(className)) return e;
        return null;
    }

    public boolean has(String className) { return find(className) != null; }
}
