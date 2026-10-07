package blockreminder.api;

import blockreminder.core.BlockEvent;
import blockreminder.core.CombatSnapshot;
import java.util.List;

/**
 * Extensions only receive immutable values. Return projected events; never
 * invoke live game callbacks or read/mutate AbstractDungeon from an extension.
 * Register once during your mod initialization.
 */
public interface PreviewContributor {
    String id();
    boolean supports(CombatSnapshot.Entity entity);
    List<BlockEvent> preview(CombatSnapshot snapshot);
}
