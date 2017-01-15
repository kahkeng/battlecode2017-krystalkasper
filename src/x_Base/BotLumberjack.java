package x_Base;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.Team;
import battlecode.common.TreeInfo;

public strictfp class BotLumberjack extends BotBase {

    public static final float CLEAR_RADIUS = 4.0f;
    public final Formations formation;
    public static Direction arcDirection;
    public static float radianStep; // positive is rotating right relative to enemy base

    public BotLumberjack(final RobotController rc) {
        super(rc);
        formation = new Formations(this);
        myLoc = rc.getLocation();
        arcDirection = formation.getArcDir(myLoc);
        radianStep = formation.getRadianStep(myLoc, CLEAR_RADIUS);
    }

    public void run() throws GameActionException {
        while (true) {
            try {
                startLoop();
                findAndChopNeutralTrees();

                Clock.yield();
            } catch (Exception e) {
                System.out.println("Lumberjack Exception");
                e.printStackTrace();
            }
        }
    }

    public final void advanceArcDirection() {
        arcDirection = arcDirection.rotateRightRads(radianStep * 2);
    }

    public final void findAndChopNeutralTrees() throws GameActionException {
        final MapLocation arcLoc = formation.getArcLoc(arcDirection);
        rc.setIndicatorLine(formation.myInitialHomeBase, formation.enemyInitialCentroid, 255, 0, 0);
        rc.setIndicatorDot(arcLoc, 255, 0, 0);

        // First priority, ensure no neutral trees near any gardeners

        // Goal is to make sure radius of X around the home base arc is free of neutral trees
        if (!rc.canSenseAllOfCircle(arcLoc, CLEAR_RADIUS)) {
            // move towards verifyLoc if possible
            if (!tryMove(arcLoc)) {
                // do something?
            }
            chopNearbyNeutralTrees();
            return;
        }
        // Check for neutral trees within X radius of arcLoc
        final TreeInfo[] trees = rc.senseNearbyTrees(arcLoc, CLEAR_RADIUS, Team.NEUTRAL);
        if (trees.length == 0) {
            advanceArcDirection();
            findAndChopNeutralTrees();
            return;
        }
        // Check if can chop
        final TreeInfo nearestTree = trees[0];
        if (rc.canChop(nearestTree.location)) {
            rc.chop(nearestTree.location);
            // move closer
            final Direction touchDir = nearestTree.location.directionTo(myLoc);
            final MapLocation touchLoc = nearestTree.location.add(touchDir,
                    nearestTree.radius + myType.bodyRadius + 0.01f);
            tryMove(touchLoc);
        } else {
            // Get closer but without entering the tree location
            final Direction adjDir = nearestTree.location.directionTo(myLoc);
            final MapLocation adjLoc = nearestTree.location.add(adjDir,
                    nearestTree.radius + myType.bodyRadius + myType.strideRadius - 0.01f);
            if (!tryMove(adjLoc)) {
                // do something?
            }
            chopNearbyNeutralTrees();
        }
    }

    public final void chopNearbyNeutralTrees() throws GameActionException {
        final TreeInfo[] trees = rc.senseNearbyTrees(myLoc, myType.bodyRadius + myType.strideRadius, Team.NEUTRAL);
        for (final TreeInfo tree : trees) {
            if (rc.canChop(tree.ID)) {
                rc.chop(tree.ID);
                return;
            }
        }
    }

}
