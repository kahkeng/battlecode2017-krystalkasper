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
import x_Arc.BotArcBase;

public strictfp class BotLumberjack extends BotArcBase {

    public static final float CLEAR_RADIUS = 4.0f;
    public static final float ENEMY_REACTION_RANGE = 10.0f;

    public BotLumberjack(final RobotController rc) {
        super(rc);
        DEBUG = true;
        radianStep = formation.getRadianStep(myLoc, CLEAR_RADIUS);
    }

    public void run() throws GameActionException {
        while (true) {
            try {
                startLoop();

                if (Combat.strikeEnemiesFromBehind(this)) {
                    // Make space for movement
                    chopAnyNearbyUnownedTrees();
                } else {
                    if (!clearObstructedArchonsAndGardeners()) {
                        if (!clearEnemyTrees()) {
                            clearNeutralTreesAlongArc();
                        }
                    }
                }

                Clock.yield();
            } catch (Exception e) {
                System.out.println("Lumberjack Exception");
                e.printStackTrace();
            }
        }
    }

    public final boolean clearEnemyTrees() throws GameActionException {
        // Prioritize enemy trees first
        final TreeInfo[] enemyTrees = rc.senseNearbyTrees(-1, enemyTeam);
        if (enemyTrees.length == 0) {
            return false;
        }
        final TreeInfo nearestTree = enemyTrees[0];
        if (rc.canChop(nearestTree.ID)) {
            rc.chop(nearestTree.ID);
            return true;
        }
        // Get closer to nearest tree
        final Direction touchDir = nearestTree.location.directionTo(myLoc);
        final MapLocation touchLoc = nearestTree.location.add(touchDir,
                nearestTree.radius + myType.bodyRadius + myType.strideRadius - 0.01f);
        if (tryMove(touchLoc)) {
            if (rc.canChop(nearestTree.ID)) {
                rc.chop(nearestTree.ID);
            }
        } else {
            chopAnyNearbyUnownedTrees();
        }
        return true;
    }

    public final boolean clearObstructedArchonsAndGardeners() throws GameActionException {
        // First priority, ensure no neutral trees near any gardeners/archons
        final RobotInfo[] robots = rc.senseNearbyRobots(-1, myTeam);
        TreeInfo nearestTree = null;
        float minDistance = 0;
        for (final RobotInfo robot : robots) {
            if (robot.type != RobotType.GARDENER && robot.type != RobotType.ARCHON) {
                continue;
            }
            final TreeInfo[] trees = rc.senseNearbyTrees(robot.location, CLEAR_RADIUS, Team.NEUTRAL);
            // only check the closest neutral tree per gardener/archon?
            if (trees.length == 0) {
                continue;
            }
            final TreeInfo tree = trees[0];
            final float distance = myLoc.distanceTo(tree.location);
            if (nearestTree == null || distance < minDistance) {
                nearestTree = tree;
                minDistance = distance;
            }
        }
        if (nearestTree != null) {
            clearSpecificNeutralTree(nearestTree);
            return true;
        }
        return false;
    }

    public final void clearNeutralTreesAlongArc() throws GameActionException {
        final MapLocation arcLoc = getArcLoc();
        // rc.setIndicatorLine(formation.myInitialHomeBase, formation.enemyInitialCentroid, 255, 0, 0);
        // rc.setIndicatorDot(arcLoc, 255, 0, 0);

        // If there are 2 lumberjacks closer than us to arcLoc, move on
        final float arcLocDist = myLoc.distanceTo(arcLoc);
        final RobotInfo[] robots = rc.senseNearbyRobots(arcLoc, CLEAR_RADIUS, myTeam);
        int numLumberjacks = 0;
        for (final RobotInfo robot : robots) {
            if (robot.type != RobotType.LUMBERJACK) {
                continue;
            }
            if (robot.location.distanceTo(arcLoc) < arcLocDist) {
                numLumberjacks++;
            }
        }
        if (numLumberjacks >= 2) {
            advanceArcDirection();
            clearNeutralTreesAlongArc();
            return;
        }

        // Goal is to make sure radius of X around the home base arc is free of neutral trees
        if (!rc.canSenseAllOfCircle(arcLoc, CLEAR_RADIUS)) {
            // move towards verifyLoc if possible
            if (!tryMove(arcLoc)) {
                // do something?
            }
            chopAnyNearbyUnownedTrees();
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

    public final void clearSpecificNeutralTree(final TreeInfo tree) throws GameActionException {
        if (rc.canChop(tree.location)) {
            rc.chop(tree.location);
            // move closer
            final Direction touchDir = tree.location.directionTo(myLoc);
            final MapLocation touchLoc = tree.location.add(touchDir,
                    tree.radius + myType.bodyRadius + 0.01f);
            tryMove(touchLoc);
        } else if (!rc.hasMoved()) {
            // Get closer but without entering the tree location
            final Direction adjDir = tree.location.directionTo(myLoc);
            final MapLocation adjLoc = tree.location.add(adjDir,
                    tree.radius + myType.bodyRadius + myType.strideRadius - 0.01f);
            if (tryMove(adjLoc)) {
                // Try to chop again
                clearSpecificNeutralTree(tree);
            } else {
                // TODO: do something if blocked by other robots
                // Make sure we clear our way there if we are blocked
                chopAnyNearbyUnownedTrees();
            }
        } else {
            // Make sure we clear our way there if we are blocked
            chopAnyNearbyUnownedTrees();
        }
    }

    public final void chopAnyNearbyUnownedTrees() throws GameActionException {
        if (rc.hasAttacked()) {
            return;
        }
        // Prioritize enemy trees first
        final TreeInfo[] enemyTrees = rc.senseNearbyTrees(myLoc, myType.bodyRadius + myType.strideRadius, enemyTeam);
        for (final TreeInfo tree : enemyTrees) {
            if (rc.canChop(tree.ID)) {
                rc.chop(tree.ID);
                return;
            }
        }
        final TreeInfo[] neutralTrees = rc.senseNearbyTrees(myLoc, myType.bodyRadius + myType.strideRadius,
                Team.NEUTRAL);
        // Prioritize trees with robots
        for (final TreeInfo tree : neutralTrees) {
            if (tree.containedRobot == null) {
                continue;
            }
            if (rc.canChop(tree.ID)) {
                rc.chop(tree.ID);
                return;
            }
        }
        for (final TreeInfo tree : neutralTrees) {
            if (tree.containedRobot != null) {
                continue;
            }
            if (rc.canChop(tree.ID)) {
                rc.chop(tree.ID);
                return;
            }
        }
    }

}
