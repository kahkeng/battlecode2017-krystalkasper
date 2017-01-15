package x_Base;

import java.util.ArrayList;
import java.util.List;

import battlecode.common.MapLocation;

public strictfp class Debug {
    public static final void debug_print(MapLocation[] locs) {
        List<MapLocation> locs1 = new ArrayList<MapLocation>();
        for (final MapLocation loc : locs) {
            locs1.add(loc);
        }
        System.out.println(locs1.toString());
    }

    public static final void debug_print(final BotBase bot, final String s) {
        if (bot.DEBUG) {
            System.out.println(s);
        }
    }
}
