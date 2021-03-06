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

    public static final float TREE_TO_ROBOT_RATIO = 1.2f;
    public static final float WAR_THRESHOLD_DISTANCE = 20.0f;
    public static final int FLEE_EXPIRY_ROUNDS = 25;
    public static final int COMBAT_BUILD_EXPIRY_ROUNDS = 100;

    public static final int MAX_BUILD_PENALTY = 5;
    public static final float BUILD_PENALTY = 1.0f;
    public static final float TREE_SPAWN_LUMBERJACK_RADIUS = -1;
    public static final float PLANT_DIST = RobotType.GARDENER.bodyRadius + GameConstants.BULLET_TREE_RADIUS + 0.01f;
    public static final int MAX_HARASSERS = 10;

    public static int buildCount = 0; // used to ensure other gardeners have their chance at building
    public static int lastFleeRound = -FLEE_EXPIRY_ROUNDS; // last time we fleed
    public static int lastCombatBuildRound = -1; // last time we built a combat unit (soldier/tank)
    public static TreeInfo rememberedTree = null;
    public static MapLocation rememberedPlantLoc = null;
    public static final HashMap<Integer, Integer> seenTreeIDs = new HashMap<Integer, Integer>();

    public enum GardenerState {
        FARMER, ROAMER, HARASSER;
    }

    public static GardenerState state = GardenerState.FARMER;

    public BotGardener(final RobotController rc) {
        super(rc);
        StrategyFeature.initialize(rc);
        DEBUG = true;
        lastCombatBuildRound = rc.getRoundNum();
    }

    public void run() throws GameActionException {
        try {
            earlyGameSeeding();
        } catch (Exception e) {
            System.out.println("Gardener Exception");
            e.printStackTrace();
        }
        while (true) {
            switch (state) {
            case FARMER:
                farmingLoop();
                break;
            case ROAMER:
                roamingLoop();
                break;
            case HARASSER:
                harassmentLoop(/* withScout= */false);
                break;
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
                MapLocation enemyLoc = null;
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
                    MapLocation nearestLoc = null;
                    float nearestDist = 0;
                    final int numPriorityEnemies = Messaging.getPriorityEnemyRobots(broadcastedPriorityEnemies, this);
                    for (int i = 0; i < numPriorityEnemies; i++) {
                        final MapLocation enemy = broadcastedPriorityEnemies[i];
                        final float dist = myLoc.distanceTo(enemy);
                        if (nearestLoc == null || dist < nearestDist) {
                            nearestLoc = enemy;
                            nearestDist = dist;
                        }
                    }
                    final int numEnemies = Messaging.getEnemyRobots(broadcastedEnemies, this);
                    for (int i = 0; i < numEnemies; i++) {
                        final RobotInfo enemy = broadcastedEnemies[i];
                        final float dist = myLoc.distanceTo(enemy.location);
                        if (nearestLoc == null || dist < nearestDist) {
                            nearestLoc = enemy.location; // can't tell if dangerous enemy but just in case
                            nearestDist = dist;
                        }
                    }
                    if (nearestLoc != null) {
                        enemyLoc = nearestLoc;
                        if (myLoc.distanceTo(enemyLoc) < FLEE_DISTANCE * 2) {
                            shouldFlee = true;
                        }
                    }
                }
                final int clock = rc.getRoundNum();
                // enemyLoc is non-null if there is a worstEnemy or the nearest broadcasted one
                // if shouldFlee is true, the enemyLoc should already be within flee distance
                if (enemyLoc != null && shouldFlee) {
                    Debug.debug_line(this, myLoc, enemyLoc, 255, 0, 0);
                    fleeFromEnemy(enemyLoc);
                    lastFleeRound = clock;
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
                        // Only plant trees if we have enough defensive units
                        final MapLocation plantLoc;
                        if (rc.getRobotCount() >= rc.getTreeCount() * TREE_TO_ROBOT_RATIO
                                || numNearbyCombatUnits() > 0) {
                            if (StrategyFeature.GARDENER_FARM_TRIANGLE.enabled()) {
                                plantLoc = findIdealPlantLocation2();
                            } else {
                                plantLoc = findIdealPlantLocation0();
                            }
                        } else {
                            plantLoc = null;
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
                                if (rc.getTreeCount() < 3 && Messaging.getNumHarassers(this) < MAX_HARASSERS) {
                                    // become roamer then harasser
                                    state = GardenerState.ROAMER;
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
            if (numNearbyCombatUnits() < 3) {
                buildSoldiers(myLoc.directionTo(enemyLoc));
            }
        } else if (enemyLoc == null || myLoc.distanceTo(enemyLoc) <= WAR_THRESHOLD_DISTANCE) {
            // Enemy loc is null if we fleed recently. Otherwise, it is just the nearest broadcasted enemy
            // Prefer tanks if we can build them
            buildTanks(formation.baseDir);
            if (rc.getRobotCount() < rc.getTreeCount() * TREE_TO_ROBOT_RATIO) {
                buildSoldiers(formation.baseDir);
            }
        }
    }

    public final void buildRobotsInPeace() throws GameActionException {
        final int clock = rc.getRoundNum();
        if (StrategyFeature.GARDENER_MORE_SOLDIERS.enabled()
                && lastCombatBuildRound < clock - COMBAT_BUILD_EXPIRY_ROUNDS && numNearbySoldiersAndTanks() < 3) {
            buildSoldiers(formation.baseDir);
        } else if (Messaging.getNumScouts(this) < 1 && lastFleeRound < clock - FLEE_EXPIRY_ROUNDS
                && numNearbyCombatUnits() > 0 && rc.getRobotCount() >= rc.getTreeCount() * TREE_TO_ROBOT_RATIO) {
            buildScouts(formation.baseDir);
        } else if (meta.getTerrainType(myLoc, /* includeRobots= */false) == TerrainType.DENSE) {
            buildLumberjacksForFarming();
            if (rc.getRobotCount() < rc.getTreeCount() * TREE_TO_ROBOT_RATIO) {
                buildSoldiers(formation.baseDir);
            }
        } else {
            if (rc.getRobotCount() < rc.getTreeCount() * TREE_TO_ROBOT_RATIO) {
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
            lastCombatBuildRound = rc.getRoundNum();
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
            lastCombatBuildRound = rc.getRoundNum();
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

        // Build this if we have neutral trees within some radius, or there is a neutral robot tree
        final TreeInfo[] allNeutralTrees = rc.senseNearbyTrees(-1, Team.NEUTRAL);
        TreeInfo hasRobotTree = null;
        outer: for (final TreeInfo tree : allNeutralTrees) {
            if (tree.containedRobot == null) {
                continue;
            }
            switch (tree.containedRobot) {
            case SOLDIER:
            case TANK:
            case LUMBERJACK:
            case ARCHON:
                hasRobotTree = tree;
                break outer;
            default:
                continue outer;
            }
        }
        if (hasRobotTree != null) {
            Messaging.broadcastNeutralTree(this, hasRobotTree);

            // But only if we don't have enough lumberjacks
            final int numLumberjacks = numNearbyLumberjacks();
            if (numLumberjacks < 1) {
                if (tryBuildRobot(buildType, formation.baseDir)) {
                    buildCount = (buildCount + 1) % MAX_BUILD_PENALTY;
                }
                return;
            }
        }

        final TreeInfo[] nearbyNeutralTrees = rc.senseNearbyTrees(TREE_SPAWN_LUMBERJACK_RADIUS, Team.NEUTRAL);
        if (nearbyNeutralTrees.length == 0) {
            return;
        } else {
            // Broadcast one of the trees
            Messaging.broadcastNeutralTree(this, nearbyNeutralTrees[0]);
        }

        // But only if we don't have enough lumberjacks
        final int numLumberjacks = numNearbyLumberjacks();
        if (nearbyNeutralTrees.length > numLumberjacks && (numLumberjacks < 3 || rc.getTeamBullets() > 500)) {
            if (tryBuildRobot(buildType, formation.baseDir)) {
                buildCount = (buildCount + 1) % MAX_BUILD_PENALTY;
            }
        }
    }

    public final int numNearbyLumberjacks() throws GameActionException {
        final RobotInfo[] robots = rc.senseNearbyRobots(-1, myTeam);
        int numLumberjacks = 0;
        for (final RobotInfo robot : robots) {
            if (robot.type == RobotType.LUMBERJACK) {
                numLumberjacks++;
            }
        }
        return numLumberjacks;
    }

    public final int numNearbySoldiers() throws GameActionException {
        final RobotInfo[] robots = rc.senseNearbyRobots(-1, myTeam);
        int numSoldiers = 0;
        for (final RobotInfo robot : robots) {
            if (robot.type == RobotType.SOLDIER) {
                numSoldiers++;
            }
        }
        return numSoldiers;
    }

    public final int numNearbySoldiersAndTanks() throws GameActionException {
        final RobotInfo[] robots = rc.senseNearbyRobots(-1, myTeam);
        int num = 0;
        for (final RobotInfo robot : robots) {
            if (robot.type == RobotType.SOLDIER || robot.type == RobotType.TANK) {
                num++;
            }
        }
        return num;
    }

    public final int numNearbyCombatUnits() throws GameActionException {
        final RobotInfo[] friendlies = rc.senseNearbyRobots(-1, myTeam);
        int numFriendlies = 0;
        for (final RobotInfo friendly : friendlies) {
            switch (friendly.type) {
            case SOLDIER:
            case TANK:
            case LUMBERJACK:
                numFriendlies++;
                break;
            default:
                break;
            }
        }
        return numFriendlies;
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
                enemyLoc = broadcastedEnemies[0].location;
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
        final float terrainDensity = meta.getTerrainDensity(myLoc, /* includeRobots= */true);
        if (meta.isLongGame()) {
            while (!tryBuildRobot(RobotType.SCOUT, formation.baseDir)) {
                startLoop();
                Messaging.broadcastPotentialScout(this);
                earlyFleeFromEnemy();
                Clock.yield();
            }
            if (terrainDensity >= 0.5) {
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
            if (terrainDensity >= 0.5) {
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
            if (terrainDensity >= 0.3 && terrainDensity < 0.5) {
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
            while (!tryBuildRobot(RobotType.SCOUT, formation.baseDir)) {
                startLoop();
                earlyFleeFromEnemy();
                Clock.yield();
            }
        }
        // Greedily find better spawning position
        findBetterSpawningPosition();

        // Start out as a harasser
        state = GardenerState.HARASSER;
    }

    public final void findBetterSpawningPosition() throws GameActionException {
        float lowestDensity = meta.getTerrainDensity(myLoc, /* includeRobots= */true);
        MapLocation lowestLoc = myLoc;
        Direction dir = Direction.NORTH;
        for (int i = 0; i < 12; i++) {
            final MapLocation loc = myLoc.add(dir, myType.bodyRadius * 2);
            if (!mapEdges.isOffMap(loc)) {
                final float density = meta.getTerrainDensity(loc, /* includeRobots= */true);
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
                    && rc.senseNearbyTrees(buildLoc, myType.bodyRadius, null).length == 0
                    && mapEdges.distanceFromEdge(buildLoc) >= myType.bodyRadius * 2 + 0.01f) {
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

    public final void roamingLoop() {
        final MapLocation startLoc = rc.getLocation();
        MapLocation baseLoc = startLoc;
        MapLocation targetLoc = null;
        int targetRound = 0;
        while (true) {
            try {
                startLoop();
                Messaging.broadcastHarasser(this);

                waterTrees();

                final int clock = rc.getRoundNum();
                if (targetLoc == null) {
                    targetLoc = baseLoc.add(Util.randomDirection(), 10.0f);
                    targetRound = clock;
                }

                if (myLoc.distanceTo(targetLoc) <= 2.0f || targetRound < clock - 20) {
                    findBetterSpawningPosition();
                    state = GardenerState.HARASSER;
                    return;
                } else {
                    nav.setDestination(targetLoc);
                    if (!tryMove(targetLoc)) {
                        randomlyJitter();
                    }
                }

                Clock.yield();
            } catch (Exception e) {
                System.out.println("Gardener Exception");
                e.printStackTrace();
            }
        }
    }

    public final void harassmentLoop(final boolean withScout) {
        int buildIndex = 0;
        while (true) {
            try {
                if (rc.getTreeCount() >= 10) {
                    state = GardenerState.FARMER;
                    return;
                }
                startLoop();
                Messaging.broadcastHarasser(this);

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
                // count friendly units before building new ones
                if (numNearbyCombatUnits() < 6) {
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
                        if (numNearbySoldiers() < 2) {
                            buildType = RobotType.SOLDIER;
                        } else if (rc.senseNearbyTrees(TREE_SPAWN_LUMBERJACK_RADIUS, Team.NEUTRAL).length > 5) {
                            buildType = RobotType.LUMBERJACK;
                        } else {
                            switch (buildIndex) {
                            default:
                            case 0:
                                buildType = RobotType.LUMBERJACK;
                                break;
                            case 1: // TODO: based on tree density
                                buildType = RobotType.SOLDIER;
                                break;
                            }
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
                }
                Clock.yield();
            } catch (Exception e) {
                System.out.println("Gardener Exception");
                e.printStackTrace();
            }
        }
    }
}
