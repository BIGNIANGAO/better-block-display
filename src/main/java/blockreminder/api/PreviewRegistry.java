package blockreminder.api;

import blockreminder.core.BlockEvent;
import blockreminder.core.CombatSnapshot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.function.Consumer;

/** Snapshot-based extension seam; one faulty extension does not break combat. */
public final class PreviewRegistry {
    private static final Map<String, PreviewContributor> REGISTERED = new LinkedHashMap<>();
    private PreviewRegistry() { }

    public static synchronized void register(PreviewContributor contributor) {
        if (contributor == null || contributor.id() == null || contributor.id().isEmpty()) {
            throw new IllegalArgumentException("A contributor needs a stable ID");
        }
        REGISTERED.put(contributor.id(), contributor);
    }

    public static synchronized void unregister(String id) { REGISTERED.remove(id); }

    public static synchronized List<PreviewContributor> contributors() {
        return Collections.unmodifiableList(new ArrayList<>(REGISTERED.values()));
    }

    public static final class Result {
        public final List<BlockEvent> events;
        public final List<String> warnings;
        public final Set<String> coveredClasses;
        private Result(List<BlockEvent> events, List<String> warnings, Set<String> covered) {
            this.events = Collections.unmodifiableList(events);
            this.warnings = Collections.unmodifiableList(warnings);
            this.coveredClasses = Collections.unmodifiableSet(covered);
        }
    }

    public static Result evaluate(CombatSnapshot snapshot, Consumer<Throwable> onError) {
        List<BlockEvent> events = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Set<String> covered = new LinkedHashSet<>();
        for (PreviewContributor contributor : contributors()) {
            try {
                List<BlockEvent> candidate = contributor.preview(snapshot);
                if (candidate == null) throw new IllegalStateException("Null preview events");
                for (BlockEvent e : candidate)
                    if (e == null) throw new IllegalStateException("Null preview event");
                Set<String> claims = new LinkedHashSet<>();
                for (CombatSnapshot.Entity entity : snapshot.entities)
                    if (contributor.supports(entity)) claims.add(entity.className);
                events.addAll(candidate);
                covered.addAll(claims);
            } catch (RuntimeException | LinkageError ex) {
                warnings.add("Extension failed: " + contributor.id());
                onError.accept(ex);
            }
        }
        return new Result(events, warnings, covered);
    }

    public static List<BlockEvent> collect(CombatSnapshot snapshot,
                                          List<String> warnings, Consumer<Throwable> onError) {
        Result result = evaluate(snapshot, onError);
        warnings.addAll(result.warnings);
        return result.events;
    }
}
