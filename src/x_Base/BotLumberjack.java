package x_Base;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.RobotType;
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
                if (!clearObstructedGardeners()) {
                    clearNeutralTreesAlongArc();
                }

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

    public final boolean clearObstructedGardeners() throws GameActionException {
        // First priority, ensure no neutral trees near any gardeners
        final RobotInfo[] robots = rc.senseNearbyRobots(-1, myTeam);
        TreeInfo nearestTree = null;
        float minDistance = 0;
        for (final RobotInfo robot : robots) {
            if (robot.type != RobotType.GARDENER) {
                continue;
            }
            final TreeInfo[] trees = rc.senseNearbyTrees(robot.location, CLEAR_RADIUS, Team.NEUTRAL);
            // only check the closest neutral tree per gardener?
            if (trees.length == 0) {
                continue;
            }
            final TreeInfo tree = trees[0];
            final float distance = myLoc.distanceTo(tree.location);
            if (nearestTree == null || distance < minDistance) {
                nearestTree = tree;
                minDistance = distance;
            }
            /*
             * for (final TreeInfo tree : trees) { final float distance = myLoc.distanceTo(tree.location); if
             * (nearestTree == null || distance < minDistance) { nearestTree = tree; minDistance = distance; } }
             */
        }
        if (nearestTree != null) {
            clearSpecificNeutralTree(nearestTree);
            return true;
        }
        return false;
    }

    public final void clearSpecificNeutralTree(final TreeInfo tree) throws GameActionException {
        if (rc.canChop(tree.location)) {
            rc.chop(tree.location);
            // move closer
            final Direction touchDir = tree.location.directionTo(myLoc);
            final MapLocation touchLoc = tree.location.add(touchDir,
                    tree.radius + myType.bodyRadius + 0.01f);
            tryMove(touchLoc);
        } else {
            // Get closer but without entering the tree location
            final Direction adjDir = tree.location.directionTo(myLoc);
            final MapLocation adjLoc = tree.location.add(adjDir,
                    tree.radius + myType.bodyRadius + myType.strideRadius - 0.01f);
            if (!tryMove(adjLoc)) {
                // do something?
            }
            // Make sure we clear our way there if we are blocked
            chopAnyNearbyNeutralTrees();
        }
    }

    public final void clearNeutralTreesAlongArc() throws GameActionException {
        final MapLocation arcLoc = formation.getArcLoc(arcDirection);
        rc.setIndicatorLine(formation.myInitialHomeBase, formation.enemyInitialCentroid, 255, 0, 0);
        rc.setIndicatorDot(arcLoc, 255, 0, 0);

        // Goal is to make sure radius of X around the home base arc is free of neutral trees
        if (!rc.canSenseAllOfCircle(arcLoc, CLEAR_RADIUS)) {
            // move towards verifyLoc if possible
            if (!tryMove(arcLoc)) {
                // do something?
            }
            chopAnyNearbyNeutralTrees();
            return;
        }
        // Check for neutral trees within X radius of arcLoc
        final TreeInfo[] trees = rc.senseNearbyTrees(arcLoc, CLEAR_RADIUS, Team.NEUTRAL);
        if (trees.length == 0) {
            advanceArcDirection();
            clearNeutralTreesAlongArc();
            return;
        }
        // Check if can chop
        clearSpecificNeutralTree(trees[0]);
    }

    public final void chopAnyNearbyNeutralTrees() throws GameActionException {
        final TreeInfo[] trees = rc.senseNearbyTrees(myLoc, myType.bodyRadius + myType.strideRadius, Team.NEUTRAL);
        for (final TreeInfo tree : trees) {
            if (rc.canChop(tree.ID)) {
                rc.chop(tree.ID);
                return;
            }
        }
    }

}
