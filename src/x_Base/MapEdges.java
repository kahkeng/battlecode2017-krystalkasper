package x_Base;

import battlecode.common.GameActionException;
import battlecode.common.MapLocation;

public strictfp class MapEdges {

    static enum Symmetry {
        UNKNOWN, ROTATIONAL, REFLECTION_X, REFLECTION_Y;
    }

    public static final float EPSILON = 0.01f;
    public final BotBase bot;
    public float minX = 0, minY = 0, maxX = 600, maxY = 600;
    public boolean foundMinX, foundMinY, foundMaxX, foundMaxY;
    public final MapLocation enemyInitialCentroid;
    public final MapLocation myInitialCentroid;
    public final MapLocation mapCentroid; // not necessarily map center if reflected map
    public Symmetry symmetry = Symmetry.UNKNOWN;

    public MapEdges(final BotBase bot) {
        this.bot = bot;
        float enemyX = 0, enemyY = 0;
        for (final MapLocation loc : bot.enemyInitialArchonLocs) {
            enemyX += loc.x;
            enemyY += loc.y;
        }
        float myX = 0, myY = 0;
        for (final MapLocation loc : bot.myInitialArchonLocs) {
            myX += loc.x;
            myY += loc.y;
        }
        this.enemyInitialCentroid = new MapLocation(enemyX / bot.numInitialArchons, enemyY / bot.numInitialArchons);
        this.myInitialCentroid = new MapLocation(myX / bot.numInitialArchons, myY / bot.numInitialArchons);
        final int numArchons = 2 * bot.numInitialArchons;
        this.mapCentroid = new MapLocation((myX + enemyX) / numArchons, (myY + enemyY) / numArchons);
        // detect symmetry
        if (Math.abs(enemyInitialCentroid.x - myInitialCentroid.x) > 5
                && Math.abs(enemyInitialCentroid.y - myInitialCentroid.y) > 5) {
            symmetry = Symmetry.ROTATIONAL;
        }
    }

    public final void detectMapEdges() throws GameActionException {
        final MapLocation myLoc = bot.rc.getLocation();
        final float sensorRange = bot.myType.sensorRadius - 0.01f;
        if (!foundMinX) {
            final MapLocation testLoc = new MapLocation(myLoc.x - sensorRange, myLoc.y);
            if (!bot.rc.onTheMap(testLoc)) {
                findAndPublishMinX(testLoc.x, myLoc.x, myLoc.y);
            }
        }
        if (!foundMaxX) {
            final MapLocation testLoc = new MapLocation(myLoc.x + sensorRange, myLoc.y);
            if (!bot.rc.onTheMap(testLoc)) {
                findAndPublishMaxX(myLoc.x, testLoc.x, myLoc.y);
            }
        }
        if (!foundMinY) {
            final MapLocation testLoc = new MapLocation(myLoc.x, myLoc.y - sensorRange);
            if (!bot.rc.onTheMap(testLoc)) {
                findAndPublishMinY(testLoc.y, myLoc.y, myLoc.x);
            }
        }
        if (!foundMaxY) {
            final MapLocation testLoc = new MapLocation(myLoc.x, myLoc.y + sensorRange);
            if (!bot.rc.onTheMap(testLoc)) {
                findAndPublishMaxY(myLoc.y, testLoc.y, myLoc.x);
            }
        }
    }

    public final void findAndPublishMinX(float low, float high, float y) throws GameActionException {
        while (high - low > EPSILON) {
            final float mid = (high + low) / 2;
            if (bot.rc.onTheMap(new MapLocation(mid, y))) {
                high = mid;
            } else {
                low = mid;
            }
        }
        minX = high;
        foundMinX = true;
        Messaging.broadcastMapMinX(bot);
        if (symmetry == Symmetry.ROTATIONAL && !foundMaxX) {
            maxX = mapCentroid.x * 2 - minX;
            foundMaxX = true;
            Messaging.broadcastMapMaxX(bot);
        }
    }

    public final void findAndPublishMaxX(float low, float high, float y) throws GameActionException {
        while (high - low > EPSILON) {
            final float mid = (high + low) / 2;
            if (bot.rc.onTheMap(new MapLocation(mid, y))) {
                low = mid;
            } else {
                high = mid;
            }
        }
        maxX = low;
        foundMaxX = true;
        Messaging.broadcastMapMaxX(bot);
        if (symmetry == Symmetry.ROTATIONAL && !foundMinX) {
            minX = mapCentroid.x * 2 - maxX;
            foundMinX = true;
            Messaging.broadcastMapMinX(bot);
        }
    }

    public final void findAndPublishMinY(float low, float high, float x) throws GameActionException {
        while (high - low > EPSILON) {
            final float mid = (high + low) / 2;
            if (bot.rc.onTheMap(new MapLocation(x, mid))) {
                high = mid;
            } else {
                low = mid;
            }
        }
        minY = high;
        foundMinY = true;
        Messaging.broadcastMapMinY(bot);
        if (symmetry == Symmetry.ROTATIONAL && !foundMaxY) {
            maxY = mapCentroid.y * 2 - minY;
            foundMaxY = true;
            Messaging.broadcastMapMaxY(bot);
        }
    }

    public final void findAndPublishMaxY(float low, float high, float x) throws GameActionException {
        while (high - low > EPSILON) {
            final float mid = (high + low) / 2;
            if (bot.rc.onTheMap(new MapLocation(x, mid))) {
                low = mid;
            } else {
                high = mid;
            }
        }
        maxY = low;
        foundMaxY = true;
        Messaging.broadcastMapMaxY(bot);
        if (symmetry == Symmetry.ROTATIONAL && !foundMinY) {
            minY = mapCentroid.y * 2 - maxY;
            foundMinY = true;
            Messaging.broadcastMapMinY(bot);
        }
    }

}
