package x_Base;

import battlecode.common.Direction;
import battlecode.common.MapLocation;
import battlecode.common.Team;

public strictfp class Meta {
    final BotBase bot;

    public enum TerrainType {
        SPARSE, DENSE;
    }

    public Meta(final BotBase bot) {
        this.bot = bot;
    }

    public final boolean isShortGame() {
        if (bot.rc.getRoundLimit() <= 1600 || bot.formation.separation < 40f) {
            return true;
        }
        return false;
    }

    public final boolean isLongGame() {
        return !isShortGame();
    }

    public final TerrainType getTerrainType(final MapLocation loc, boolean includeRobots) {
        if (getTerrainDensity(loc, includeRobots) > 0.33f) {
            return TerrainType.DENSE;
        } else {
            return TerrainType.SPARSE;
        }
    }

    public final float getTerrainDensity(final MapLocation loc, boolean includeRobots) {
        if (bot.rc.senseNearbyTrees(loc, 1.0f, Team.NEUTRAL).length > 0) {
            return 1.0f;
        }
        Direction dir = Direction.NORTH;
        final float increment;
        final int count;
        if (bot.myType.bodyRadius > 1.5) {
            increment = 40.0f;
            count = 9;
        } else {
            increment = 60.0f;
            count = 6;
        }
        int blocked = 0;
        for (int i = 0; i < count; i++) {
            final MapLocation adjLoc = loc.add(dir, bot.myType.bodyRadius + 1.1f);
            if (bot.rc.senseNearbyTrees(adjLoc, 1.1f, Team.NEUTRAL).length > 0
                    || (includeRobots && bot.rc.senseNearbyRobots(adjLoc, 1.1f, null).length > 0)
                    || bot.mapEdges.isOffMap(adjLoc)) {
                blocked++;
            }

            dir = dir.rotateLeftDegrees(increment);
        }
        return 1.0f * blocked / count;
    }
}
