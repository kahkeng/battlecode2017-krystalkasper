package x_Seeding;

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
import x_Base.Meta.TerrainType;

public strictfp class BotGardener extends BotBase {

    public static final float TRIANGLE_DX = 4.1f;
    public static final float TRIANGLE_DY = (float) (TRIANGLE_DX / 2 * Math.sqrt(3));
    public static final float WATER_THRESHOLD = GameConstants.BULLET_TREE_MAX_HEALTH - 10.0f;
    public static final int MAX_BUILD_PENALTY = 5;
    public static final float BUILD_PENALTY = 1.0f;
    public static final float TREE_SPAWN_LUMBERJACK_RADIUS = -1;

    public static int buildCount = 0; // used to ensure other gardeners have their chance at building
    public static TreeInfo rememberedTree = null;
    public static MapLocation rememberedPlantLoc = null;

    public BotGardener(final RobotController rc) {
        super(rc);
    }

    public void run() throws GameActionException {
        try {
            earlyGameSeeding();
        } catch (Exception e) {
            System.out.println("Gardener Exception");
            e.printStackTrace();
        }
        farmingLoop();
    }

    public final void farmingLoop() {
        while (true) {
            try {
                startLoop();
                waterTrees();
                final RobotInfo[] enemies = rc.senseNearbyRobots(-1, enemyTeam);
                final RobotInfo worstEnemy = enemies.length == 0 ? null : Combat.prioritizedEnemy(this, enemies);
                final MapLocation enemyLoc;
                if (worstEnemy != null) {
                    Messaging.broadcastEnemyRobot(this, worstEnemy);
                    enemyLoc = worstEnemy.location;
                } else {
                    final int numEnemies = Messaging.getEnemyRobots(broadcastedEnemies, this);
                    if (numEnemies > 0) {
                        enemyLoc = broadcastedEnemies[0];
                    } else {
                        enemyLoc = null;
                    }
                }
                boolean fleeing = false;
                if (enemyLoc != null) {
                    if (enemyLoc.distanceTo(myLoc) < FLEE_DISTANCE * 2) {
                        Debug.debug_line(this, myLoc, enemyLoc, 255, 0, 0);
                        fleeFromEnemy(enemyLoc);
                        fleeing = true;
                    }
                    if (worstEnemy != null || rc.getRobotCount() < rc.getTreeCount()) {
                        buildSoldiers(myLoc.directionTo(enemyLoc));
                    }
                }
                if (!fleeing) {
                    final TreeInfo[] nearbyTrees = rc.senseNearbyTrees(-1, myTeam);
                    broadcastMyTrees(nearbyTrees);
                    final TreeInfo treeToWater = findTreeToWater(nearbyTrees, true);
                    if (treeToWater != null) {
                        rememberedTree = treeToWater;
                        if (myLoc.distanceTo(treeToWater.location) > myType.bodyRadius
                                + GameConstants.INTERACTION_DIST_FROM_EDGE) {
                            nav.setDestination(treeToWater.location);
                            if (!tryMove(nav.getNextLocation())) {
                                randomlyJitter();
                            }
                        }
                    } else {
                        final MapLocation plantLoc = findIdealPlantLocation();
                        if (plantLoc != null) {
                            rememberedPlantLoc = plantLoc;
                            plantTreeAtLocation(plantLoc);
                        } else {
                            final TreeInfo treeToWater2 = findTreeToWater(nearbyTrees, false);
                            if (treeToWater2 != null) {
                                rememberedTree = treeToWater2;
                                if (myLoc.distanceTo(treeToWater2.location) > myType.bodyRadius
                                        + GameConstants.INTERACTION_DIST_FROM_EDGE) {
                                    nav.setDestination(treeToWater2.location);
                                    if (!tryMove(nav.getNextLocation())) {
                                        randomlyJitter();
                                    }
                                }
                            } else {
                                randomlyJitter();
                            }
                        }
                    }
                }
                if (Messaging.getNumScouts(this) < 1) {
                    buildScouts(formation.baseDir);
                } else if (meta.getTerrainType(myLoc) == TerrainType.DENSE) {
                    buildLumberjacksForFarming();
                    if (rc.getRobotCount() < rc.getTreeCount()) {
                        buildSoldiers(formation.baseDir);
                    }
                } else {
                    if (rc.getRobotCount() * 1.5 < rc.getTreeCount()) {
                        buildTanks(formation.baseDir);
                    }
                }

                Clock.yield();
            } catch (Exception e) {
                System.out.println("Gardener Exception");
                e.printStackTrace();
            }
        }
    }

    public final void waterTrees() throws GameActionException {
        // water lowest health tree
        final TreeInfo[] trees = rc.senseNearbyTrees(
                myType.bodyRadius + GameConstants.INTERACTION_DIST_FROM_EDGE + 0.01f,
                myTeam);
        TreeInfo lowestTree = null;
        float lowestHealth = 0;
        for (final TreeInfo tree : trees) {
            if (rc.canWater(tree.ID) && tree.health < tree.maxHealth) {
                if (lowestTree == null || tree.health < lowestHealth) {
                    lowestTree = tree;
                    lowestHealth = tree.health;
                }
            }
        }
        if (lowestTree != null) {
            rc.water(lowestTree.ID);
        }
    }

    public final void plantTreeAtLocation(final MapLocation plantLoc) throws GameActionException {
        final float plantDist = myType.bodyRadius + GameConstants.BULLET_TREE_RADIUS + 0.01f;
        if (myLoc.distanceTo(plantLoc) > plantDist + myType.strideRadius) {
            nav.setDestination(plantLoc);
            if (!tryMove(nav.getNextLocation())) {
                randomlyJitter();
            }
        } else {
            final Direction plantDir = myLoc.directionTo(plantLoc);
            final MapLocation fromLoc = plantLoc.add(plantDir.opposite(), plantDist);
            if (myLoc.distanceTo(fromLoc) > 0.01f) {
                if (!tryMove(fromLoc)) {
                    randomlyJitter();
                }
            } else {
                if (rc.canPlantTree(plantDir)) {
                    rc.plantTree(plantDir);
                }
            }
        }
    }

    public final MapLocation findIdealPlantLocation() {
        MapLocation bestLoc = null;
        float bestDist = 0;
        if (rememberedPlantLoc != null) {
            final float dist = myLoc.distanceTo(rememberedPlantLoc);
            if (dist <= myType.sensorRadius - GameConstants.BULLET_TREE_RADIUS || dist >= myType.sensorRadius * 1.5f) {
                rememberedPlantLoc = null;
            } else {
                bestLoc = rememberedPlantLoc;
                bestDist = dist;
            }
        }

        final int xi = (int) (myLoc.x / TRIANGLE_DX);
        final int yi = (int) (myLoc.y / TRIANGLE_DY);
        for (int i = -2; i <= 2; i++) {
            for (int j = -2; j <= 2; j++) {
                final int x = xi + i;
                final int y = yi + j;
                final float xf = x * TRIANGLE_DX + (y & 1) * TRIANGLE_DX / 2;
                final float yf = y * TRIANGLE_DY;
                final MapLocation loc = new MapLocation(xf, yf);
                final float dist = myLoc.distanceTo(loc);
                if (dist > myType.sensorRadius - GameConstants.BULLET_TREE_RADIUS) {
                    continue;
                }
                if (mapEdges.isOffMap(loc)) {
                    continue;
                }
                final TreeInfo[] nearbyTrees = rc.senseNearbyTrees(loc, GameConstants.BULLET_TREE_RADIUS + 0.01f, null);
                if (nearbyTrees.length > 0) {
                    continue;
                }
                final RobotInfo[] nearbyRobots = rc.senseNearbyRobots(loc, GameConstants.BULLET_TREE_RADIUS + 0.01f,
                        null);
                if (nearbyRobots.length > 0) {
                    continue;
                }
                Debug.debug_dot(this, loc, 255, 255, 255);
                if (bestLoc == null || dist < bestDist) {
                    bestLoc = loc;
                    bestDist = dist;
                }
            }
        }
        return bestLoc;
    }

    public final TreeInfo findTreeToWater(final TreeInfo[] trees, boolean onlyHighPriority) throws GameActionException {
        // Prioritized by health/distance traveled
        TreeInfo bestTree = null;
        float bestScore = 0;
        if (rememberedTree != null) {
            if (rc.canSensePartOfCircle(rememberedTree.location, GameConstants.BULLET_TREE_RADIUS) ||
                    myLoc.distanceTo(rememberedTree.location) >= myType.sensorRadius * 1.5f) {
                rememberedTree = null;
            } else {
                if (onlyHighPriority && rememberedTree.health >= WATER_THRESHOLD) {
                } else {
                    bestTree = rememberedTree;
                    bestScore = rememberedTree.health;
                }
            }
        }
        for (final TreeInfo tree : trees) {
            final float score = tree.health/*
                                            * - GameConstants.BULLET_TREE_DECAY_RATE * myLoc.distanceTo(tree.location) /
                                            * myType.strideRadius / 1.2f
                                            */;
            if (onlyHighPriority && score >= WATER_THRESHOLD) {
                continue;
            }
            if (bestTree == null || score < bestScore) {
                bestTree = tree;
                bestScore = score;
            }
        }
        if (bestTree == null && onlyHighPriority) {
            final int numTrees = Messaging.getMyTrees(broadcastedMyTrees, broadcastedMyTreesHealth, this);
            for (int i = 0; i < numTrees; i++) {
                final float score = broadcastedMyTreesHealth[i];
                if (bestTree == null || score < bestScore) {
                    bestTree = new TreeInfo(0, myTeam, broadcastedMyTrees[i], GameConstants.BULLET_TREE_RADIUS, score,
                            0, null);
                    bestScore = score;
                }
            }
        }
        return bestTree;
    }

    public final void buildScouts(final Direction buildDir) throws GameActionException {
        if (!rc.isBuildReady()) {
            return;
        }
        final RobotType buildType = RobotType.SCOUT;
        if (rc.getTeamBullets() < buildType.bulletCost + buildCount * BUILD_PENALTY) {
            return;
        }

        if (tryBuildRobot(buildType, buildDir)) {
            Messaging.broadcastPotentialScout(this);
            buildCount = (buildCount + 1) % MAX_BUILD_PENALTY;
        }
    }

    public final void buildSoldiers(final Direction buildDir) throws GameActionException {
        if (!rc.isBuildReady()) {
            return;
        }
        final RobotType buildType = RobotType.SOLDIER;
        if (rc.getTeamBullets() < buildType.bulletCost + buildCount * BUILD_PENALTY) {
            return;
        }

        if (tryBuildRobot(buildType, buildDir)) {
            buildCount = (buildCount + 1) % MAX_BUILD_PENALTY;
        }
    }

    public final void buildTanks(final Direction buildDir) throws GameActionException {
        if (!rc.isBuildReady()) {
            return;
        }
        final RobotType buildType = RobotType.TANK;
        if (rc.getTeamBullets() < buildType.bulletCost + buildCount * BUILD_PENALTY) {
            return;
        }

        if (tryBuildRobot(buildType, buildDir)) {
            buildCount = (buildCount + 1) % MAX_BUILD_PENALTY;
        }
    }

    public final void buildLumberjacksForFarming() throws GameActionException {
        if (!rc.isBuildReady()) {
            return;
        }
        final RobotType buildType = RobotType.LUMBERJACK;
        if (rc.getTeamBullets() < buildType.bulletCost + buildCount * BUILD_PENALTY) {
            return;
        }

        // Build this if we have neutral trees within some radius, or were blocked recently
        final TreeInfo[] trees = rc.senseNearbyTrees(TREE_SPAWN_LUMBERJACK_RADIUS, Team.NEUTRAL);
        if (trees.length == 0) {
            return;
        } else {
            // Broadcast one of the trees
            Messaging.broadcastNeutralTree(this, trees[0]);
        }

        // But only if we don't have enough lumberjacks
        final RobotInfo[] robots = rc.senseNearbyRobots(-1, myTeam);
        int numLumberjacks = 0;
        for (final RobotInfo robot : robots) {
            if (robot.type == RobotType.LUMBERJACK) {
                numLumberjacks++;
            }
        }
        if (trees.length > numLumberjacks && numLumberjacks < 2) {
            if (tryBuildRobot(buildType, formation.baseDir)) {
                buildCount = (buildCount + 1) % MAX_BUILD_PENALTY;
            }
        }
    }

    public final void earlyFleeFromEnemy() throws GameActionException {
        final RobotInfo[] enemies = rc.senseNearbyRobots(-1, enemyTeam);
        final RobotInfo worstEnemy = enemies.length == 0 ? null : Combat.prioritizedEnemy(this, enemies);
        final MapLocation enemyLoc;
        if (worstEnemy != null) {
            Messaging.broadcastEnemyRobot(this, worstEnemy);
            enemyLoc = worstEnemy.location;
        } else {
            final int numEnemies = Messaging.getEnemyRobots(broadcastedEnemies, this);
            if (numEnemies > 0) {
                enemyLoc = broadcastedEnemies[0];
            } else {
                enemyLoc = null;
            }
        }
        if (enemyLoc != null) {
            fleeFromEnemy(enemyLoc);
        }
    }

    public final void earlyGameSeeding() throws GameActionException {
        if (rc.getRoundNum() > 10) {
            return;
        }
        if (meta.isLongGame()) {
            while (!tryBuildRobot(RobotType.SCOUT, formation.baseDir)) {
                startLoop();
                Messaging.broadcastPotentialScout(this);
                earlyFleeFromEnemy();
                Clock.yield();
            }
            while (!tryBuildRobot(RobotType.SCOUT, formation.baseDir)) {
                startLoop();
                Messaging.broadcastPotentialScout(this);
                earlyFleeFromEnemy();
                Clock.yield();
            }
            while (!tryBuildRobot(RobotType.SOLDIER, formation.baseDir)) {
                startLoop();
                earlyFleeFromEnemy();
                Clock.yield();
            }
        } else {
            while (!tryBuildRobot(RobotType.SOLDIER, formation.baseDir)) {
                startLoop();
                earlyFleeFromEnemy();
                Clock.yield();
            }
            while (!tryBuildRobot(RobotType.SOLDIER, formation.baseDir)) {
                startLoop();
                earlyFleeFromEnemy();
                Clock.yield();
            }
            while (!tryBuildRobot(RobotType.SCOUT, formation.baseDir)) {
                startLoop();
                earlyFleeFromEnemy();
                Clock.yield();
            }
        }
        // Greedily find better spawning position
        float lowestDensity = meta.getTerrainDensity(myLoc);
        MapLocation lowestLoc = myLoc;
        Direction dir = Direction.NORTH;
        for (int i = 0; i < 12; i++) {
            final MapLocation loc = myLoc.add(dir, myType.bodyRadius * 2);
            if (!mapEdges.isOffMap(loc)) {
                final float density = meta.getTerrainDensity(loc);
                if (density < lowestDensity) {
                    lowestDensity = density;
                    lowestLoc = loc;
                }
            }
            dir = dir.rotateLeftDegrees(30.0f);
        }
        while (myLoc.distanceTo(lowestLoc) > 0.01f) {
            startLoop();
            if (!tryMove(lowestLoc)) {
                break;
            }
            Clock.yield();
        }
    }

}
