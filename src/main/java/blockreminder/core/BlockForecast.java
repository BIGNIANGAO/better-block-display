package blockreminder.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class BlockForecast {
    public static final class Line {
        public final String source;
        public final int delta;
        public Line(String source, int delta) { this.source = source; this.delta = delta; }
    }

    public final int initialBlock, finalBlock, delta;
    public final List<Line> lines;
    public final List<String> uncertainties;

    public BlockForecast(int initialBlock, int finalBlock, List<Line> lines, List<String> uncertainties) {
        this.initialBlock = initialBlock;
        this.finalBlock = finalBlock;
        this.delta = finalBlock - initialBlock;
        this.lines = Collections.unmodifiableList(new ArrayList<>(lines));
        this.uncertainties = Collections.unmodifiableList(new ArrayList<>(uncertainties));
    }

    public boolean isExact() { return uncertainties.isEmpty(); }

    public String display(boolean total) {
        return total ? Integer.toString(finalBlock)
                : (delta >= 0 ? "+" : "") + delta;
    }
}
