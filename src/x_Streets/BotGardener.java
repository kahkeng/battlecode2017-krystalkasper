package x_Streets;

import java.util.HashMap;

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
import x_Base.StrategyFeature;
import x_Base.Util;

public strictfp class BotGardener extends BotBase {

    public static final float TRIANGLE_DY = 4.2f;
    public static final float TRIANGLE_DX = (float) (TRIANGLE_DY / Math.sqrt(3) * 2);
    public static final float WATER_THRESHOLD = GameConstants.BULLET_TREE_MAX_HEALTH - 10.0f;

    public static final float WAR_THRESHOLD_DISTANCE = 20.0f;
    public static final int FLEE_EXPIRY_ROUNDS = 50;

    public static final int MAX_BUILD_PENALTY = 5;
    public static final float BUILD_PENALTY = 1.0f;
    public static final float TREE_SPAWN_LUMBERJACK_RADIUS = -1;
    public static final float PLANT_DIST = RobotType.GARDENER.bodyRadius + GameConstants.BULLET_TREE_RADIUS + 0.01f;

    public static int buildCount = 0; // used to ensure other gardeners have their chance at building
    public static int lastFleeRound = -FLEE_EXPIRY_ROUNDS; // last time we fleed
    public static TreeInfo rememberedTree = null;
    public static MapLocation rememberedPlantLoc = null;
    public static final HashMap<Integer, Integer> seenTreeIDs = new HashMap<Integer, Integer>();
    public static boolean isFarmer = true;

    public BotGardener(final RobotController rc) {
        super(rc);
        StrategyFeature.initialize(rc);
        DEBUG = true;
    }

    public void run() throws GameActionException {
        try {
            earlyGameSeeding();
        } catch (Exception e) {
            System.out.println("Gardener Exception");
            e.printStackTrace();
        }
        while (true) {
            if (isFarmer) {
                farmingLoop();
            } else {
                harassmentLoop(/* withScout= */false);
            }
        }
    }

    public final void farmingLoop() {
        while (true) {
            try {
                startLoop();
                findHomeArchon();
                waterTrees();
                final RobotInfo[] enemies = rc.senseNearbyRobots(-1, enemyTeam);
                final RobotInfo worstEnemy = enemies.length == 0 ? null : Combat.prioritizedEnemy(this, enemies);
                final MapLocation enemyLoc;
                boolean shouldFlee = false;
                if (worstEnemy != null) {
                    switch (worstEnemy.type) {
                    case SOLDIER:
                    case TANK:
                    case LUMBERJACK:
                    case SCOUT:
                        Messaging.broadcastPriorityEnemyRobot(this, worstEnemy);
                        shouldFlee = true;
                        break;
                    default:
                        Messaging.broadcastEnemyRobot(this, worstEnemy);
                        break;
                    }
                    enemyLoc = worstEnemy.location;
                } else {
                    final int numEnemies = Messaging.getEnemyRobots(broadcastedEnemies, this);
                    if (numEnemies > 0) {
                        enemyLoc = broadcastedEnemies[0];
                        shouldFlee = true; // can't tell if dangerous but just in case
                    } else {
                        enemyLoc = null;
                    }
                }
                final int clock = rc.getRoundNum();
                if (enemyLoc != null) {
                    if (shouldFlee && enemyLoc.distanceTo(myLoc) < FLEE_DISTANCE * 2) {
                        Debug.debug_line(this, myLoc, enemyLoc, 255, 0, 0);
                        fleeFromEnemy(enemyLoc);
                        lastFleeRound = clock;
                    }
                }
                if (enemyLoc != null || lastFleeRound >= clock - FLEE_EXPIRY_ROUNDS) {
                    buildRobotsInWar(worstEnemy, enemyLoc);
                } else {
                    buildRobotsInPeace();
                }
                if (lastFleeRound < clock) { // if didn't just flee
                    final TreeInfo[] nearbyTrees = rc.senseNearbyTrees(-1, myTeam);
                    broadcastMyTrees(nearbyTrees);
                    final TreeInfo treeToWater = findTreeToWater(nearbyTrees, true);
                    if (treeToWater != null) {
                        // Debug.debug_print(this, "found tree to water1");
                        rememberedTree = treeToWater;
                        if (myLoc.distanceTo(treeToWater.location) > myType.bodyRadius
                                + GameConstants.INTERACTION_DIST_FROM_EDGE) {
                            nav.setDestination(treeToWater.location);
                            if (!tryMove(nav.getNextLocation())) {
                                randomlyJitter();
                            }
                        }
                    } else {
                        final MapLocation plantLoc;
                        if (StrategyFeature.GARDENER_FARM_TRIANGLE.enabled()) {
                            plantLoc = findIdealPlantLocation2();
                        } else {
                            plantLoc = findIdealPlantLocation0();
                        }
                        if (plantLoc != null) {
                            // Debug.debug_print(this, "found place to plant");
                            rememberedPlantLoc = plantLoc;
                            plantTreeAtLocation(plantLoc);
                        } else {
                            final TreeInfo treeToWater2 = findTreeToWater(nearbyTrees, false);
                            if (treeToWater2 != null) {
                                // Debug.debug_print(this, "found tree to water2");
                                rememberedTree = treeToWater2;
                                if (myLoc.distanceTo(treeToWater2.location) > myType.bodyRadius
                                        + GameConstants.INTERACTION_DIST_FROM_EDGE) {
                                    nav.setDestination(treeToWater2.location);
                                    if (!tryMove(nav.getNextLocation())) {
                                        randomlyJitter();
                                    }
                                }
                            } else {
                                // Debug.debug_print(this, "random jitter");
                                if (rc.getTreeCount() < 3) {
                                    // become harasser
                                    isFarmer = false;
                                    return;
                                } else {
                                    randomlyJitter();
                                }
                            }
                        }
                    }
                }

                Clock.yield();
            } catch (Exception e) {
                System.out.println("Gardener Exception");
                e.printStackTrace();
            }
        }
    }

    public final void buildRobotsInWar(final RobotInfo worstEnemy, final MapLocation enemyLoc)
            throws GameActionException {
        if (worstEnemy != null) {
            buildSoldiers(myLoc.directionTo(enemyLoc));
        } else if (myLoc.distanceTo(enemyLoc) <= WAR_THRESHOLD_DISTANCE) {
            if (rc.getRobotCount() * 1.5 < rc.getTreeCount()) {
                buildTanks(formation.baseDir);
            }
            if (rc.getRobotCount() < rc.getTreeCount()) {
                buildSoldiers(formation.baseDir);
            }
        }
    }

    public final void buildRobotsInPeace() throws GameActionException {
        if (Messaging.getNumScouts(this) < 1 && lastFleeRound < rc.getRoundNum() - FLEE_EXPIRY_ROUNDS) {
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
        if (myLoc.distanceTo(plantLoc) > PLANT_DIST + myType.strideRadius) {
            // Debug.debug_print(this, "navigating towards plant loc");
            nav.setDestination(plantLoc);
            if (!tryMove(nav.getNextLocation())) {
                randomlyJitter();
            }
        } else {
            // Debug.debug_print(this, "getting into plant position");
            final Direction plantDir = myLoc.directionTo(plantLoc);
            final MapLocation fromLoc = plantLoc.add(plantDir.opposite(), PLANT_DIST);
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

    public final MapLocation findIdealPlantLocation0() {
        MapLocation bestLoc = null;
        float bestDist = 0;
        if (rememberedPlantLoc != null) {
            final float dist = myLoc.distanceTo(rememberedPlantLoc);
            if (dist <= myType.sensorRadius - GameConstants.BULLET_TREE_RADIUS || dist >= myType.sensorRadius * 1.5f) {
                rememberedPlantLoc = null;
            } else {
                bestLoc = rememberedPlantLoc;
                bestDist = distanceHeuristicPlantLocation(bestLoc);
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
                final TreeInfo[] nearbyTrees = rc.senseNearbyTrees(loc,
                        GameConstants.BULLET_TREE_RADIUS + myType.bodyRadius * 2 + 0.01f, null);
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

    public final MapLocation findIdealPlantLocation1() {
        MapLocation bestLoc = null;
        float bestDist = 0;
        if (rememberedPlantLoc != null) {
            final float dist = myLoc.distanceTo(rememberedPlantLoc);
            if (dist <= myType.sensorRadius - GameConstants.BULLET_TREE_RADIUS || dist >= myType.sensorRadius * 1.5f) {
                rememberedPlantLoc = null;
            } else {
                bestLoc = rememberedPlantLoc;
                bestDist = distanceHeuristicPlantLocation(bestLoc);
            }
        }

        final int xi = (int) (2 * myLoc.x / TRIANGLE_DX);
        final int yi = (int) (2 * myLoc.y / TRIANGLE_DY);
        for (int i = -3; i <= 3; i++) {
            for (int j = -3; j <= 3; j++) {
                final int x = xi + i;
                final int y = yi + j;

                if (DEBUG) {
                    final float xf = x * TRIANGLE_DX / 2 + (y & 3) * TRIANGLE_DX / 4;
                    final float yf = y *
                            TRIANGLE_DY / 2;
                    final MapLocation loc = new MapLocation(xf, yf);
                    Debug.debug_dot(this, loc, 128,
                            128, 128);
                }

                boolean ok = false;
                if ((x & 1) == 0 && (y & 1) == 0) {
                    ok = true;
                } else if ((y & 3) == 1 && (x % 3 < 2)) {
                    ok = true;
                } else if ((y & 3) == 0 && (x % 6 == 1)) {
                    ok = true;
                } else if ((y & 3) == 2 && (x % 6 == 3)) {
                    ok = true;
                }
                if (!ok) {
                    continue;
                }
                final float xf = x * TRIANGLE_DX / 2 + (y & 3) * TRIANGLE_DX / 4;
                final float yf = y * TRIANGLE_DY / 2;
                final MapLocation loc = new MapLocation(xf, yf);
                if (feasiblePlantLocation(loc)) {
                    final float dist = distanceHeuristicPlantLocation(loc);

                    Debug.debug_dot(this, loc, 255, 255, 255);
                    if (bestLoc == null || dist < bestDist) {
                        bestLoc = loc;
                        bestDist = dist;
                    }
                }
            }
        }
        return bestLoc;
    }

    public final MapLocation findIdealPlantLocation2() {
        MapLocation bestLoc = null;
        float bestDist = 0;
        if (rememberedPlantLoc != null) {
            final float dist = myLoc.distanceTo(rememberedPlantLoc);
            if (dist <= myType.sensorRadius - GameConstants.BULLET_TREE_RADIUS || dist >= myType.sensorRadius * 1.5f) {
                rememberedPlantLoc = null;
            } else {
                bestLoc = rememberedPlantLoc;
                bestDist = distanceHeuristicPlantLocation(bestLoc);
            }
        }

        final int xi = (int) (2 * myLoc.x / TRIANGLE_DX);
        final int yi = (int) (2 * myLoc.y / TRIANGLE_DY);
        final int[][] combinations = Util.TRIANGLE_PATTERN[xi % 6][yi % 4];
        for (int i = 0; i < combinations.length; i++) {
            final int[] combination = combinations[i];
            final int x = xi + combination[0];
            final int y = yi + combination[1];

            if (DEBUG) {
                final float xf = x * TRIANGLE_DX / 2 + (y & 3) * TRIANGLE_DX / 4;
                final float yf = y *
                        TRIANGLE_DY / 2;
                final MapLocation loc = new MapLocation(xf, yf);
                Debug.debug_dot(this, loc, 128,
                        128, 128);
            }

            final float xf = x * TRIANGLE_DX / 2 + (y & 3) * TRIANGLE_DX / 4;
            final float yf = y * TRIANGLE_DY / 2;
            final MapLocation loc = new MapLocation(xf, yf);
            if (feasiblePlantLocation(loc)) {
                final float dist = distanceHeuristicPlantLocation(loc);

                Debug.debug_dot(this, loc, 255, 255, 255);
                if (bestLoc == null || dist < bestDist) {
                    bestLoc = loc;
                    bestDist = dist;
                }
            }
        }
        if (bestLoc != null)
            Debug.debug_dot(this, bestLoc, 0, 128, 0);
        return bestLoc;
    }

    public final float distanceHeuristicPlantLocation(final MapLocation loc) {
        if (StrategyFeature.GARDENER_PLANT_NEAR_ARCHON.enabled() && homeArchon != null) {
            final float archonDist = (int) (loc.distanceTo(homeArchon) / 4) * 10; // smooth ties
            final float dist = loc.distanceTo(myLoc);
            if (dist <= PLANT_DIST + myType.strideRadius) {
                final Direction plantDir = myLoc.directionTo(loc);
                final MapLocation fromLoc = loc.add(plantDir.opposite(), PLANT_DIST);
                return archonDist + fromLoc.distanceTo(myLoc);
            } else {
                return archonDist + dist;
            }
        } else {
            final float dist = loc.distanceTo(myLoc);
            if (dist <= PLANT_DIST + myType.strideRadius) {
                final Direction plantDir = myLoc.directionTo(loc);
                final MapLocation fromLoc = loc.add(plantDir.opposite(), PLANT_DIST);
                return fromLoc.distanceTo(myLoc);
            } else {
                return dist;
            }
        }

    }

    public final boolean feasiblePlantLocation(final MapLocation loc) {
        final float dist = myLoc.distanceTo(loc);
        if (dist > myType.sensorRadius - GameConstants.BULLET_TREE_RADIUS) {
            return false;
        }
        final float bufferDist = GameConstants.BULLET_TREE_RADIUS + myType.bodyRadius * 2 + 0.01f;
        if (mapEdges.distanceFromEdge(loc) < bufferDist) {
            return false;
        }
        final TreeInfo[] nearbyMyTrees = rc.senseNearbyTrees(loc,
                GameConstants.BULLET_TREE_RADIUS + 0.01f, myTeam);
        if (nearbyMyTrees.length > 0) {
            return false;
        }
        final TreeInfo[] nearbyTrees = rc.senseNearbyTrees(loc,
                bufferDist, Team.NEUTRAL);
        if (nearbyTrees.length > 0) {
            return false;
        }
        final RobotInfo[] nearbyRobots = rc.senseNearbyRobots(loc, GameConstants.BULLET_TREE_RADIUS + 0.01f,
                null);
        if (nearbyRobots.length > 0) {
            return false;
        }
        return true;
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
                final MapLocation treeLoc = broadcastedMyTrees[i];
                if (myLoc.distanceTo(treeLoc) >= myType.sensorRadius * 1.5f) {
                    continue;
                }
                final float score = broadcastedMyTreesHealth[i];
                if (bestTree == null || score < bestScore) {
                    bestTree = new TreeInfo(0, myTeam, treeLoc, GameConstants.BULLET_TREE_RADIUS, score,
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
            if (meta.getTerrainDensity(myLoc) >= 0.5) {
                while (!tryBuildRobot(RobotType.LUMBERJACK, formation.baseDir)) {
                    startLoop();
                    earlyFleeFromEnemy();
                    Clock.yield();
                }
            } else {
                while (!tryBuildRobot(RobotType.SCOUT, formation.baseDir)) {
                    startLoop();
                    Messaging.broadcastPotentialScout(this);
                    earlyFleeFromEnemy();
                    Clock.yield();
                }
            }
            while (!tryBuildRobot(RobotType.SOLDIER, formation.baseDir)) {
                startLoop();
                earlyFleeFromEnemy();
                Clock.yield();
            }
        } else {
            if (meta.getTerrainDensity(myLoc) >= 0.5) {
                while (!tryBuildRobot(RobotType.LUMBERJACK, formation.baseDir)) {
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

    public final boolean tryPlantTreesWithSpace() throws GameActionException {
        Direction dir = formation.baseDir;
        Direction plantDir = null;
        int canBuild = 0;
        for (int i = 0; i < 6; i++) {
            final MapLocation buildLoc = myLoc.add(dir, myType.bodyRadius * 2 + 0.01f);
            if (rc.senseNearbyRobots(buildLoc, myType.bodyRadius, null).length == 0
                    && rc.senseNearbyTrees(buildLoc, myType.bodyRadius, null).length == 0) {
                canBuild += 1;
                if (canBuild == 2) {
                    plantDir = dir;
                    break;
                }
            }
            dir = dir.rotateRightDegrees(60.0f);
        }
        if (plantDir != null && rc.canPlantTree(plantDir)) {
            rc.plantTree(plantDir);
            return true;
        }
        return false;
    }

    public final void harassmentLoop(final boolean withScout) {
        int buildIndex = 0;
        while (true) {
            try {
                if (rc.getTreeCount() >= 10) {
                    isFarmer = true;
                    return;
                }
                startLoop();
                waterTrees();
                final RobotInfo[] enemies = rc.senseNearbyRobots(-1, enemyTeam);
                final RobotInfo worstEnemy = enemies.length == 0 ? null : Combat.prioritizedEnemy(this, enemies);
                if (worstEnemy != null) {
                    switch (worstEnemy.type) {
                    case SOLDIER:
                    case TANK:
                    case LUMBERJACK:
                    case SCOUT:
                        Messaging.broadcastPriorityEnemyRobot(this, worstEnemy);
                        break;
                    default:
                        Messaging.broadcastEnemyRobot(this, worstEnemy);
                        break;
                    }
                } else {
                    tryPlantTreesWithSpace();
                }
                final RobotType buildType;
                if (withScout) {
                    switch (buildIndex) {
                    default:
                    case 0:
                        buildType = RobotType.SCOUT;
                        break;
                    case 1:
                        buildType = RobotType.SOLDIER;
                        break;
                    case 2: // TODO: based on tree density
                        buildType = RobotType.LUMBERJACK;
                        break;
                    }
                } else {
                    switch (buildIndex) {
                    default:
                    case 0:
                        buildType = RobotType.SOLDIER;
                        break;
                    case 1: // TODO: based on tree density
                        buildType = RobotType.LUMBERJACK;
                        break;
                    }
                }
                if (rc.getTeamBullets() >= buildType.bulletCost + buildCount * BUILD_PENALTY) {
                    if (tryBuildRobot(buildType, formation.baseDir)) {
                        if (withScout) {
                            buildIndex = (buildIndex + 1) % 3;
                        } else {
                            buildIndex = (buildIndex + 1) % 2;
                        }
                        buildCount = (buildCount + 1) % MAX_BUILD_PENALTY;
                    }
                }
                Clock.yield();
            } catch (Exception e) {
                System.out.println("Gardener Exception");
                e.printStackTrace();
            }
        }
    }
}
