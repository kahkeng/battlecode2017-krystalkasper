package x_Streets;

import battlecode.common.BulletInfo;
import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.GameConstants;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.RobotType;
import battlecode.common.Team;
import battlecode.common.TreeInfo;
import x_Base.BotBase;
import x_Base.Combat;
import x_Base.Debug;
import x_Base.Messaging;
import x_Base.StrategyFeature;

public strictfp class BotLumberjack extends BotBase {

    public static final float CLEAR_RADIUS = 4.0f;
    public static final float ENEMY_REACTION_RANGE = 10.0f;
    public static TreeInfo chosenTree = null;

    public BotLumberjack(final RobotController rc) {
        super(rc);
        StrategyFeature.initialize(rc);
        DEBUG = true;
    }

    public void run() throws GameActionException {
        while (true) {
            try {
                startLoop();
                final BulletInfo[] bullets = rc.senseNearbyBullets();
                final Direction dodgeDir = getDodgeDirection(bullets);
                if (dodgeDir != null) {
                    tryMove(myLoc.add(dodgeDir));
                }

                if (Combat.strikeEnemiesFromBehind2(this)) {
                    // Make space for movement
                    chopAnyNearbyUnownedTrees();
                } else {
                    if (!clearChosenTree()) {
                        if (!clearObstructedArchonsAndGardeners()) {
                            if (!clearEnemyTrees()) {
                                clearNeutralTreesNearBase();
                            }
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
        final boolean moved = tryMove(touchLoc);
        if (rc.canChop(nearestTree.ID)) {
            rc.chop(nearestTree.ID);
        } else {
            chopAnyNearbyUnownedTrees();
        }
        if (!moved && !rc.hasAttacked()) {
            randomlyJitter();
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
            if (trees.length == 0) {
                continue;
            }
            for (final TreeInfo tree : trees) {
                final float distance = myLoc.distanceTo(tree.location);
                if (nearestTree == null || distance < minDistance) {
                    nearestTree = tree;
                    minDistance = distance;
                }
            }
        }
        if (nearestTree != null) {
            clearSpecificNeutralTree(nearestTree);
            return true;
        }
        return false;
    }

    public final void clearNeutralTreesNearBase() throws GameActionException {
        // If I don't see any of my own bullet trees, head towards nearest archon
        final TreeInfo[] myTrees = rc.senseNearbyTrees(-1, myTeam);
        if (myTrees.length == 0) {
            final MapLocation nearestArchon = getNearestArchon();
            nav.setDestination(nearestArchon);
            final boolean moved = tryMove(nav.getNextLocation());
            chopAnyNearbyUnownedTrees();
            if (!moved && !rc.hasAttacked()) {
                randomlyJitter();
            }
            return;
        }

        // Get centroid of our trees
        float x = 0, y = 0;
        for (final TreeInfo tree : myTrees) {
            x += tree.location.x;
            y += tree.location.y;
        }
        final MapLocation centroid = new MapLocation(x / myTrees.length, y / myTrees.length);
        // Navigate towards nearest neutral tree to base tree
        final TreeInfo[] neutralTrees = rc.senseNearbyTrees(-1, Team.NEUTRAL);
        TreeInfo nearestTree = null;
        float nearestDist = 0;
        for (final TreeInfo tree : neutralTrees) {
            final float dist = tree.location.distanceTo(centroid);
            if (nearestTree == null || dist < nearestDist) {
                nearestTree = tree;
                nearestDist = dist;
            }
        }
        if (nearestTree != null) {
            Messaging.broadcastNeutralTree(this, nearestTree);
            clearSpecificNeutralTree(nearestTree);
            return;
        }

        // Check for broadcasted trees
        if (headTowardsBroadcastedTree()) {
            return;
        }

        // Otherwise, just hang around at some distance from centroid
        final MapLocation moveLoc = centroid.add(centroid.directionTo(myLoc), myType.sensorRadius - 1.0f);
        final boolean moved = tryMove(moveLoc);

        // Check if can chop
        chopAnyNearbyUnownedTrees();

        if (!moved && !rc.hasAttacked()) {
            randomlyJitter();
        }
    }

    public final boolean headTowardsBroadcastedTree() throws GameActionException {
        // Head towards closest known broadcasted trees
        final int numTrees = Messaging.getNeutralTrees(broadcastedNeutralTrees, this);
        MapLocation nearestLoc = null;
        float minDistance = 0;
        for (int i = 0; i < numTrees; i++) {
            final MapLocation treeLoc = broadcastedNeutralTrees[i];
            final float distance = treeLoc.distanceTo(myLoc);
            if (nearestLoc == null || distance < minDistance) {
                nearestLoc = treeLoc;
                minDistance = distance;
            }
        }
        if (nearestLoc != null) {
            nav.setDestination(nearestLoc);
            final boolean moved = tryMove(nav.getNextLocation());
            chopAnyNearbyUnownedTrees();
            if (!moved && !rc.hasAttacked()) {
                randomlyJitter();
            }
            return true;
        }
        return false;
    }

    public final void clearSpecificNeutralTree(final TreeInfo tree) throws GameActionException {
        if (rc.canChop(tree.location)) {
            chosenTree = tree;
            rc.chop(tree.location);
            // move closer
            final Direction touchDir = tree.location.directionTo(myLoc);
            final MapLocation touchLoc = tree.location.add(touchDir,
                    tree.radius + myType.bodyRadius + 0.01f);
            tryMove(touchLoc);
        } else {
            nav.setDestination(tree.location);
            final boolean moved = tryMove(nav.getNextLocation());
            chopAnyNearbyUnownedTrees();
            if (!moved && !rc.hasAttacked()) {
                nav.reset();
                randomlyJitter();
            }
        }
    }

    public final boolean clearChosenTree() throws GameActionException {
        if (!StrategyFeature.LUMBERJACK_FOCUS.enabled()) {
            return false;
        }
        if (chosenTree == null) {
            return false;
        }
        Debug.debug_dot(this, chosenTree.location, 0, 255, 0);
        if (!rc.hasAttacked() && !rc.canChop(chosenTree.location)) {
            chosenTree = null;
            return false;
        }
        if (rc.canChop(chosenTree.location)) {
            rc.chop(chosenTree.location);
        }
        return true;
    }

    public final void chopAnyNearbyUnownedTrees() throws GameActionException {
        if (rc.hasAttacked()) {
            return;
        }
        // Prioritize enemy trees first, choosing lowest health
        final TreeInfo[] enemyTrees = rc.senseNearbyTrees(myLoc,
                myType.bodyRadius + GameConstants.INTERACTION_DIST_FROM_EDGE + 0.01f, enemyTeam);
        TreeInfo bestTree = null;
        float bestScore = 0;
        for (final TreeInfo tree : enemyTrees) {
            if (rc.canChop(tree.ID)) {
                if (bestTree == null || tree.health < bestScore) {
                    bestTree = tree;
                    bestScore = tree.health;
                }
            }
        }
        if (bestTree != null) {
            rc.chop(bestTree.ID);
            return;
        }
        final TreeInfo[] neutralTrees = rc.senseNearbyTrees(myLoc,
                myType.bodyRadius + GameConstants.INTERACTION_DIST_FROM_EDGE + 0.01f,
                Team.NEUTRAL);
        // Prioritize trees with robots, lowest health first
        for (final TreeInfo tree : neutralTrees) {
            if (tree.containedRobot == null) {
                continue;
            }
            if (rc.canChop(tree.ID)) {
                if (bestTree == null || tree.health < bestScore) {
                    bestTree = tree;
                    bestScore = tree.health;
                }
            }
        }
        if (bestTree != null) {
            rc.chop(bestTree.ID);
            return;
        }
        for (final TreeInfo tree : neutralTrees) {
            if (tree.containedRobot != null) {
                continue;
            }
            if (rc.canChop(tree.ID)) {
                if (bestTree == null || tree.health < bestScore) {
                    bestTree = tree;
                    bestScore = tree.health;
                }
            }
        }
        if (bestTree != null) {
            rc.chop(bestTree.ID);
            return;
        }
    }

}
