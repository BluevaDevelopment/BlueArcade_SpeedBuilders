package net.blueva.arcade.modules.speed_builders.game;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * How many items one placed block is made of: a double slab is two slabs, a block of three
 * candles is three candles, and so on.
 */
public final class BlockItemCount {

    private static final Pattern STACKED = Pattern.compile("[\\[,](?:candles|pickles|eggs|layers)=(\\d+)");

    private BlockItemCount() {
    }

    /** Takes block data as a string, e.g. {@code minecraft:oak_slab[type=double,waterlogged=false]}. */
    public static int of(String blockData) {
        if (blockData == null) {
            return 1;
        }
        String data = blockData.toLowerCase();
        if (data.contains("_slab[") && data.contains("type=double")) {
            return 2;
        }
        Matcher matcher = STACKED.matcher(data);
        if (matcher.find()) {
            return Math.max(1, Integer.parseInt(matcher.group(1)));
        }
        return 1;
    }
}
