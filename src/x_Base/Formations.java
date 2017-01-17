package x_Base;

import battlecode.common.Direction;
import battlecode.common.GameConstants;
import battlecode.common.MapLocation;
import battlecode.common.RobotType;

public strictfp class Formations {

    public static final float EDGE_BUFFER = 6.0f;

    public final BotBase bot;
    public final MapLocation enemyInitialCentroid;
    public final MapLocation myInitialHomeBase;
    public final MapLocation mapCentroid; // not necessarily map center if reflected map
    public final Direction baseDir; // direction from base to center
    public final float separation;

    public Formations(final BotBase bot) {
        this.bot = bot;
        this.enemyInitialCentroid = bot.mapEdges.enemyInitialCentroid;
        this.mapCentroid = bot.mapEdges.mapCentroid;

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
                RobotType.ARCHON.bodyRadius + GameConstants.BULLET_TREE_RADIUS * 4 + 0.01f);
        this.separation = myInitialHomeBase.distanceTo(getArcCenter());
    }

    public final MapLocation getArcCenter() {
        // return enemyInitialCentroid;
        return mapCentroid;
    }

    public final MapLocation getArcLoc(final Direction dir) {
        final float deltaX = (bot.mapEdges.maxX - bot.mapEdges.minX) * 0.5f - EDGE_BUFFER;
        final float deltaY = (bot.mapEdges.maxY - bot.mapEdges.minY) * 0.5f - EDGE_BUFFER;
        if (deltaX < separation || deltaY < separation) {
            final float a = Math.min(deltaX, separation);
            final float b = Math.min(deltaY, separation);
            final double b1 = b * Math.cos(dir.radians);
            final double a1 = a * Math.sin(dir.radians);
            final double R = a * b / Math.sqrt(b1 * b1 + a1 * a1);
            final float dx = (float) (R * Math.cos(dir.radians));
            final float dy = (float) (R * Math.sin(dir.radians));
            return getArcCenter().translate(dx, dy);
        } else {
            return getArcCenter().add(dir, separation);
        }
    }

    public final Direction getArcDir(final MapLocation myLoc) {
        // TODO: snap to grid?
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
