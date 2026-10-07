package blockreminder.game;

import com.evacipated.cardcrawl.modthespire.lib.SpireField;

/** Reads known mod-added fields without importing or executing their effects. */
final class OptionalFields {
    private OptionalFields() { }

    static Integer spireInteger(Object instance, String owner, String name) {
        try {
            Class<?> type = Class.forName(owner, false, instance.getClass().getClassLoader());
            Object spireField = type.getField(name).get(null);
            // MTS replaces these objects with generated subclasses. Its public
            // get accessor reads the injected field without invoking the
            // default supplier, set, or a combat callback.
            if (!(spireField instanceof SpireField)) return null;
            Object value = ((SpireField<?>) spireField).get(instance);
            return value == null ? null : ((Number) value).intValue();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            return null; // missing mod/changed API: caller marks a partial preview
        }
    }
}
