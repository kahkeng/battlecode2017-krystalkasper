package x_Streets;

import java.util.HashMap;
import java.util.HashSet;

import battlecode.common.Clock;
import battlecode.common.GameActionException;
import battlecode.common.GameConstants;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.RobotType;
import battlecode.common.TreeInfo;
import x_Base.Combat;
import x_Base.Debug;
import x_Base.Messaging;
import x_Base.StrategyFeature;

public strictfp class BotArchon extends x_Base.BotArchon {

    /** Hire a gardener if none in this radius. */
    public static final float GARDENER_RADIUS = 10.0f;
    public static final int MAX_GARDENERS_AROUND = 5;
    public static final float ARCHON_WATER_THRESHOLD = GameConstants.BULLET_TREE_MAX_HEALTH / 3;
    public static final HashMap<Integer, Integer> seenTreeIDs = new HashMap<Integer, Integer>();
    public static final int[] broadcastedHarasserIDs = new int[BotGardener.MAX_HARASSERS + 1];

    public BotArchon(final RobotController rc) {
        super(rc);
        StrategyFeature.initialize(rc);
        DEBUG = true;
    }

    public void run() throws GameActionException {
        boolean shouldSpawnGardeners = false;
        final boolean isShortGame = meta.isShortGame();
        if (isShortGame && rc.getLocation().equals(formation.nearestArchon)
                || !isShortGame && rc.getLocation().equals(formation.furthestArchon)) {
            shouldSpawnGardeners = true;
        }
        while (true) {
            try {
                startLoop();
                Messaging.broadcastArchonLocation(this);

                // TODO: prioritized enemies
                final RobotInfo[] enemies = rc.senseNearbyRobots(-1, enemyTeam);
                final RobotInfo worstEnemy = enemies.length == 0 ? null : Combat.prioritizedEnemy(this, enemies);
                boolean fleeing = false;
                if (worstEnemy != null) {
                    switch (worstEnemy.type) {
                    case SOLDIER:
                    case TANK:
                    case LUMBERJACK:
                    case SCOUT:
                        Messaging.broadcastPriorityEnemyRobot(this, worstEnemy);
                        fleeFromEnemy(worstEnemy.location);
                        fleeing = true;
                        break;
                    default:
                        Messaging.broadcastEnemyRobot(this, worstEnemy);
                        break;
                    }
                }
                if (!shouldSpawnGardeners && (rc.getRoundNum() > 55
                        || rc.getRoundNum() >= 2 && rc.getRoundNum() <= 5 && rc.getRobotCount() == numInitialArchons)) {
                    shouldSpawnGardeners = true;
                }
                if (shouldSpawnGardeners) {
                    final int numGardeners = numGardenersAround();
                    if (numGardeners == 0) {
                        tryHireGardenerWithSpace(formation.baseDir);
                    } else if (numGardeners < MAX_GARDENERS_AROUND && worstEnemy == null) {
                        hireGardenersIfNeedWatering();
                    }
                }

                if (!fleeing) {
                    final TreeInfo tree = archonFindTreesToShake();
                    if (tree != null) {
                        if (!tryMove(tree.location)) {
                            randomlyJitter();
                        }
                    } else {
                        randomlyJitter();
                    }
                }

                Clock.yield();

            } catch (Exception e) {
                System.out.println("Archon Exception");
                e.printStackTrace();
            }
        }

    }

    public final void hireGardenersIfNeedWatering() throws GameActionException {
        // Check if we have a tree to water
        TreeInfo bestTree = null;
        float bestScore = 0f;
        final TreeInfo[] trees = rc.senseNearbyTrees(-1, myTeam);
        broadcastMyTrees(trees);
        for (final TreeInfo tree : trees) {
            // Check if this is a new tree
            Integer round = seenTreeIDs.get(tree.ID);
            if (round == null) {
                seenTreeIDs.put(tree.ID, rc.getRoundNum());
                continue;
            } else if (rc.getRoundNum() - round < 10) {
                continue;
            }
            final float score = tree.health /*
                                             * - GameConstants.BULLET_TREE_DECAY_RATE * myLoc.distanceTo(tree.location)
                                             * / RobotType.GARDENER.strideRadius / 1.2f
                                             */;
            if (score < ARCHON_WATER_THRESHOLD) {
                Debug.debug_line(this, myLoc, tree.location, 255, 0, 0);
                if (bestTree == null || score < bestScore) {
                    bestTree = tree;
                    bestScore = score;
                }
            }
        }
        if (bestTree != null) {
            tryHireGardenerWithSpace(myLoc.directionTo(bestTree.location));
        }
    }

    public final int numGardenersAround() throws GameActionException {
        final RobotInfo[] robots = rc.senseNearbyRobots(GARDENER_RADIUS, myTeam);
        final int numHarassers = Messaging.getHarasserIDs(broadcastedHarasserIDs, this);
        final HashSet<Integer> harasserIDs = new HashSet<Integer>();
        for (int i = 0; i < numHarassers; i++) {
            harasserIDs.add(broadcastedHarasserIDs[i]);
        }
        int count = 0;
        final int clock = rc.getRoundNum();
        for (final RobotInfo robot : robots) {
            if (robot.type != RobotType.GARDENER) {
                continue;
            }
            // skip harassers only after at least 150 rounds, and if they have at least 2 trees next to them
            if (clock >= 150 && harasserIDs.contains(robot.ID)) {
                final TreeInfo[] trees = rc.senseNearbyTrees(robot.location, robot.type.bodyRadius +
                        GameConstants.BULLET_TREE_RADIUS + 0.5f, myTeam);
                if (trees.length >= 2) {
                    continue;
                }
            }
            count++;
        }
        return count;
    }
}
