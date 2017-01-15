package x_Base;

import battlecode.common.Direction;
import battlecode.common.GameConstants;
import battlecode.common.MapLocation;
import battlecode.common.RobotType;

public strictfp class Formations {

    public final BotBase bot;
    public final MapLocation enemyInitialCentroid;
    public final MapLocation myInitialHomeBase;
    public final MapLocation mapCentroid; // not necessarily map center if reflected map
    public final Direction baseDir; // direction from base to center
    public final float separation;

    public Formations(final BotBase bot) {
        this.bot = bot;
        // get enemy centroid
        float x = 0, y = 0;
        for (final MapLocation loc : bot.enemyInitialArchonLocs) {
            x += loc.x;
            y += loc.y;
        }
        this.enemyInitialCentroid = new MapLocation(x / bot.numInitialArchons, y / bot.numInitialArchons);
        for (final MapLocation loc : bot.myInitialArchonLocs) {
            x += loc.x;
            y += loc.y;
        }
        final int numArchons = 2 * bot.numInitialArchons;
        this.mapCentroid = new MapLocation(x / numArchons, y / numArchons);
        // our home base is the archon that is the furthest from enemy centroid
        int furthestIndex = 0;
        float furthestDistance = bot.myInitialArchonLocs[0].distanceTo(enemyInitialCentroid);
        for (int i = 1; i < bot.numInitialArchons; i++) {
            float distance = bot.myInitialArchonLocs[1].distanceTo(enemyInitialCentroid);
            if (distance > furthestDistance) {
                furthestDistance = distance;
                furthestIndex = i;
            }
        }
        final MapLocation furthestArchon = bot.myInitialArchonLocs[furthestIndex];
        this.baseDir = furthestArchon.directionTo(getArcCenter());
        this.myInitialHomeBase = furthestArchon.add(baseDir,
                RobotType.ARCHON.bodyRadius + GameConstants.BULLET_TREE_RADIUS * 2 + 0.01f);
        this.separation = myInitialHomeBase.distanceTo(getArcCenter());
    }

    public final MapLocation getArcCenter() {
        return enemyInitialCentroid;
        // return mapCentroid;
    }

    public final MapLocation getArcLoc(final Direction dir) {
        return getArcCenter().add(dir, separation);
    }

    public final Direction getArcDir(final MapLocation myLoc) {
        return getArcCenter().directionTo(myLoc);
    }

    public final float getRadianStep(final MapLocation myLoc, final float stepDistance) {
        // positive if going counter-clockwise, negative otherwise
        final float radianStep = stepDistance / separation;
        // Set initial direction
        if (myInitialHomeBase.directionTo(myLoc).radiansBetween(baseDir) > 0) {
            return -radianStep;
        } else {
            return radianStep;
        }
    }
}
