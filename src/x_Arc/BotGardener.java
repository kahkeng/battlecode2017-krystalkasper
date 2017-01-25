package x_Arc;

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
import x_Base.Debug;
import x_Base.Messaging;

public strictfp class BotGardener extends BotArcBase {

    /** Plant tree within this radius of arc loc. */
    public static final float PLANT_RADIUS = GameConstants.BULLET_TREE_RADIUS + 0.01f;
    /** Min amount of bullets to plant trees. */
    public static final float PLANT_TREE_MIN_BULLETS = 101.0f;
    /** If neutral tree within this range, build lumberjack. */
    public static final float TREE_SPAWN_LUMBERJACK_RADIUS = 4.0f;
    public static final float TREE_SPAWN_SCOUT_RADIUS = 4.0f;
    public static final int MAX_BUILD_PENALTY = 5;
    public static final float BUILD_PENALTY = 1.0f;

    public static int buildCount = 0; // used to ensure other gardeners have their chance at building
    public static int roundBlockedByNeutralTree = 0; // used to determine if we should spawn lumberjack

    public BotGardener(final RobotController rc) {
        super(rc);
        DEBUG = true;
        radianStep = formation.getRadianStep(myLoc, PLANT_RADIUS);
    }

    public void run() throws GameActionException {
        while (true) {
            try {
                startLoop();
                // Messaging.broadcastGardener(this);
                // final MapLocation[] myArchons = Messaging.readArchonLocation(this);

                waterTrees();
                final MapLocation enemyLoc = buildCombatUnitsIfNeeded();
                if (enemyLoc != null) {
                    fleeFromEnemyAlongArc(enemyLoc);
                } else {
                    plantTreesInArc(0);
                    buildLumberjacksIfNeeded();
                    buildScoutsIfNeeded();
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

    public final boolean tryPlantTrees() throws GameActionException {
        Direction dir = Direction.getNorth();
        int i = 0;
        while (i < 12 && !rc.canPlantTree(dir)) {
            dir = dir.rotateRightDegrees(30.0f);
            i += 1;
        }
        if (rc.canPlantTree(dir)) {
            rc.plantTree(dir);
            return true;
        }
        return false;
    }

    public final void plantTreesInArc(int attempt) throws GameActionException {
        if (attempt > 1) {
            randomlyJitter();
            Debug.debug_print(this, "jitter0");
            return;
        }
        final MapLocation arcLoc = formation.getArcLoc(arcDirection);
        // Debug.debug_line(this, formation.myInitialHomeBase, formation.enemyInitialCentroid, 0, 255, 0);
        // Debug.debug_line(this, arcLoc, formation.enemyInitialCentroid, 0, 0, 255);
        Debug.debug_dot(this, arcLoc, 0, 255, 0);
        // Goal is to make sure radius of X around the home base arc is filled with our trees

        // To sense up to X around the arc, you need to be something like at most sense_radius - X away
        final Direction plantDir = myLoc.directionTo(arcLoc);
        if (!rc.canSenseAllOfCircle(arcLoc, PLANT_RADIUS)) {
            Debug.debug_print(this, "can't sense near arcLoc");
            // move towards arcLoc if possible
            if (!tryMove(arcLoc)) {
                Debug.debug_print(this, "can't get close enough to sense arcLoc");
                reverseArcDirection();
                // Move away to give space to others who might try to clear out
                // randomlyJitter();
                // Debug.debug_print(this, "jitter1");
            }
            return;
        }
        // Check for our trees within X radius of arcLoc
        final TreeInfo[] bulletTrees = rc.senseNearbyTrees(arcLoc, PLANT_RADIUS, myTeam);
        if (bulletTrees.length > 0) {
            Debug.debug_print(this, "already has tree " + attempt);
            advanceArcDirection();
            plantTreesInArc(attempt + 1);
            return;
        }
        // Check if another gardener is nearby, if so, we can flip direction of search
        // TODO: make sure we can sense all of circle again
        final RobotInfo[] robots = rc.senseNearbyRobots(arcLoc, PLANT_RADIUS * 2, myTeam);
        final float arcLocDist = myLoc.distanceTo(arcLoc);
        boolean hasGardener = false;
        for (final RobotInfo robot : robots) {
            if (robot.type == RobotType.GARDENER && robot.location.distanceTo(arcLoc) < arcLocDist) {
                hasGardener = true;
                break;
            }
        }
        if (hasGardener) {
            Debug.debug_print(this, "already has gardener " + attempt);
            reverseArcDirection();
            plantTreesInArc(attempt + 1);
            return;
        }
        // Check if there is a neutral tree too close by. If so, we should wait till they are cleared
        // We don't use lumberjack's CLEAR_RADIUS because that would be too sensitive. Plus we would
        // automatically build more lumberjacks if there are trees within TREE_SPAWN_LUMBERJACK_RADIUS.
        final TreeInfo[] neutralTrees = rc.senseNearbyTrees(arcLoc, PLANT_RADIUS * 2, Team.NEUTRAL);
        if (neutralTrees.length > 0) {
            Debug.debug_print(this, "neutral tree too close by " + neutralTrees[0] + " " + arcLoc);
            // reverse in the meantime
            roundBlockedByNeutralTree = rc.getRoundNum();
            reverseArcDirection();
            plantTreesInArc(attempt + 1);
            return;
        }
        // Move closer to planting position
        final MapLocation plantLoc = arcLoc.add(arcLoc.directionTo(myLoc),
                myType.bodyRadius + GameConstants.BULLET_TREE_RADIUS + 0.01f);
        final float dist = myLoc.distanceTo(plantLoc);
        if (dist >= 0.01f) {
            Debug.debug_print(this, "not close enough to desired plant location");
            // move towards arcLoc if possible
            if (!tryMove(plantLoc)) {
                Debug.debug_print(this, "can't get closer to plant");
                reverseArcDirection();
                // Move away to give space to others who might try to clear out
                // randomlyJitter();
                // Debug.debug_print(this, "jitter2");
            }
            // If we aren't successful in getting closer, return and try again
            if (myLoc.distanceTo(plantLoc) >= 0.01f) {
                return;
            }
        }
        // Check if can plant
        if (rc.getTeamBullets() >= PLANT_TREE_MIN_BULLETS && rc.canPlantTree(plantDir)) {
            Debug.debug_print(this, "planting");
            rc.plantTree(plantDir);
        } else {
            // TODO: try small adjustments to direction?
            // Move away to give space to others who might try to clear out
            randomlyJitter();
            Debug.debug_print(this, "jitter3");
            return;
        }
    }

    public final MapLocation buildCombatUnitsIfNeeded() throws GameActionException {
        // Build this if we have enemies within sight
        final RobotInfo[] enemyRobots = rc.senseNearbyRobots(-1, enemyTeam);
        if (enemyRobots.length == 0) {
            return null;
        }
        for (final RobotInfo enemy : enemyRobots) {
            Messaging.broadcastEnemyRobot(this, enemy);
        }
        final MapLocation enemyLoc = enemyRobots[0].location;
        final RobotType buildType = RobotType.SOLDIER;
        if (!rc.isBuildReady() || rc.getTeamBullets() < buildType.bulletCost + buildCount * BUILD_PENALTY) {
            return enemyLoc;
        }
        final Direction spawnDir = myLoc.directionTo(enemyLoc);
        if (tryBuildRobot(buildType, spawnDir)) {
            buildCount = (buildCount + 1) % MAX_BUILD_PENALTY;
        }
        return enemyLoc;
    }

    public final void buildLumberjacksIfNeeded() throws GameActionException {
        if (!rc.isBuildReady()) {
            return;
        }
        final RobotType buildType = RobotType.LUMBERJACK;
        if (rc.getTeamBullets() < buildType.bulletCost + buildCount * BUILD_PENALTY) {
            return;
        }

        // Build this if we have neutral trees within some radius, or were blocked recently
        final TreeInfo[] trees = rc.senseNearbyTrees(TREE_SPAWN_LUMBERJACK_RADIUS, Team.NEUTRAL);
        if (roundBlockedByNeutralTree < rc.getRoundNum() - 15 && trees.length == 0) {
            return;
        }

        // But only if we don't have enough lumberjacks
        final RobotInfo[] robots = rc.senseNearbyRobots(TREE_SPAWN_LUMBERJACK_RADIUS, myTeam);
        int numLumberjacks = 0;
        for (final RobotInfo robot : robots) {
            if (robot.type == RobotType.LUMBERJACK) {
                numLumberjacks++;
            }
        }
        if (trees.length > numLumberjacks && numLumberjacks < 2) {
            // final MapLocation centerLoc = formation.getArcCenter();
            // final Direction spawnDir = centerLoc.directionTo(myLoc);
            final Direction spawnDir = trees.length > 0 ? myLoc.directionTo(trees[0].location) : arcDirection;
            if (tryBuildRobot(buildType, spawnDir)) {
                buildCount = (buildCount + 1) % MAX_BUILD_PENALTY;
            }
        }
    }

    public final void buildScoutsIfNeeded() throws GameActionException {
        if (!rc.isBuildReady()) {
            return;
        }
        final RobotType buildType = RobotType.SCOUT;
        if (rc.getTeamBullets() < buildType.bulletCost + buildCount * BUILD_PENALTY) {
            return;
        }
        // Build this if we have more bullet trees than scouts
        final TreeInfo[] trees = rc.senseNearbyTrees(TREE_SPAWN_SCOUT_RADIUS, myTeam);
        if (trees.length == 0) {
            return;
        }

        // But only if we don't have enough lumberjacks
        final RobotInfo[] robots = rc.senseNearbyRobots(TREE_SPAWN_SCOUT_RADIUS, myTeam);
        int numScouts = 0;
        for (final RobotInfo robot : robots) {
            if (robot.type == RobotType.SCOUT) {
                numScouts++;
            }
        }
        if (trees.length > numScouts && trees.length >= 5) {
            // final MapLocation centerLoc = formation.getArcCenter();
            // final Direction spawnDir = centerLoc.directionTo(myLoc);
            final Direction spawnDir = myLoc.directionTo(trees[0].location);
            if (tryBuildRobot(buildType, spawnDir)) {
                buildCount = (buildCount + 1) % MAX_BUILD_PENALTY;
            }
        }
    }
}
