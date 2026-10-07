package blockreminder.core;

/** A change at a defined point before the enemies act. */
public final class BlockEvent {
    public enum Phase { RELIC, POWER, ORB, END_TURN_CARD, LATE_POWER, EXHAUST }
    public enum Operation { GAIN, DAMAGE }
    public final Phase phase;
    public final Operation operation;
    public final String source;
    public final int amount;

    public BlockEvent(Phase phase, Operation operation, String source, int amount) {
        if (phase == null || operation == null || source == null || amount < 0) {
            throw new IllegalArgumentException("Invalid preview event");
        }
        this.phase = phase;
        this.operation = operation;
        this.source = source;
        this.amount = amount;
    }

    public static BlockEvent gain(Phase phase, String source, int amount) {
        return new BlockEvent(phase, Operation.GAIN, source, Math.max(0, amount));
    }

    public static BlockEvent damage(Phase phase, String source, int amount) {
        return new BlockEvent(phase, Operation.DAMAGE, source, Math.max(0, amount));
    }
}
