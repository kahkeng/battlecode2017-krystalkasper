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
import x_Base.BotLumberjack;
import x_Base.Debug;
import x_Base.Formations;
import x_Base.Messaging;
import x_Base.Util;

public strictfp class BotGardener extends x_Base.BotGardener {

    /** Plant tree within this radius of arc loc. */
    public static final float PLANT_RADIUS = GameConstants.BULLET_TREE_RADIUS + 0.01f;
    /** If neutral tree within this range, build lumberjack. */
    public static final float LUMBERJACK_RADIUS = 4.0f;
    public final Formations formation;
    public static Direction arcDirection;
    public static float radianStep; // positive is rotating right relative to enemy base

    public BotGardener(final RobotController rc) {
        super(rc);
        DEBUG = true;
        formation = new Formations(this);
        myLoc = rc.getLocation();
        arcDirection = formation.getArcDir(myLoc);
        radianStep = formation.getRadianStep(myLoc, PLANT_RADIUS);
    }

    public void run() throws GameActionException {
        while (true) {
            try {
                startLoop();
                Messaging.broadcastGardener(this);
                // final MapLocation[] myArchons = Messaging.readArchonLocation(this);

                waterTrees();
                plantTreesInArc(0);
                buildLumberjacksIfNeeded();

                Clock.yield();

            } catch (Exception e) {
                System.out.println("Gardener Exception");
                e.printStackTrace();
            }
        }
    }

    public final void reverseArcDirection() {
        radianStep = -radianStep;
        advanceArcDirection();
    }

    public final void advanceArcDirection() {
        arcDirection = arcDirection.rotateRightRads(radianStep * 2);
    }

    public final void randomlyJitter() throws GameActionException {
        float radius = myType.strideRadius;
        while (!tryMove(Util.randomDirection(), radius, 20, 18)) {
            radius /= 2;
        }
    }

    public final void plantTreesInArc(int attempt) throws GameActionException {
        if (attempt > 1) {
            randomlyJitter();
            Debug.debug_print(this, "jitter0");
            return;
        }
        final MapLocation arcLoc = formation.getArcLoc(arcDirection);
        rc.setIndicatorLine(formation.myInitialHomeBase, formation.enemyInitialCentroid, 0, 255, 0);
        rc.setIndicatorLine(arcLoc, formation.enemyInitialCentroid, 0, 0, 255);
        rc.setIndicatorDot(arcLoc, 0, 255, 0);
        // Goal is to make sure radius of X around the home base arc is filled with our trees

        // To sense up to X around the arc, you need to be something like at most sense_radius - X away
        final Direction plantDir = myLoc.directionTo(arcLoc);
        if (!rc.canSenseAllOfCircle(arcLoc, PLANT_RADIUS)) {
            // move towards arcLoc if possible
            if (!tryMove(arcLoc)) {
                // Move away to give space to others who might try to clear out
                randomlyJitter();
                Debug.debug_print(this, "jitter1");
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
        final TreeInfo[] neutralTrees = rc.senseNearbyTrees(arcLoc, BotLumberjack.CLEAR_RADIUS, Team.NEUTRAL);
        if (neutralTrees.length > 0) {
            Debug.debug_print(this, "neutral tree too close by " + neutralTrees[0] + " " + arcLoc);
            // reverse in the meantime
            reverseArcDirection();
            return;
        }
        // Move closer to planting position
        final MapLocation plantLoc = arcLoc.add(arcLoc.directionTo(myLoc),
                myType.bodyRadius + GameConstants.BULLET_TREE_RADIUS + 0.01f);
        final float dist = myLoc.distanceTo(plantLoc);
        if (dist >= 0.01f) {
            // move towards arcLoc if possible
            if (!tryMove(plantLoc)) {
                // Move away to give space to others who might try to clear out
                randomlyJitter();
                Debug.debug_print(this, "jitter2");
            }
            return;
        }
        // Check if can plant
        if (rc.canPlantTree(plantDir)) {
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

    public final void buildLumberjacksIfNeeded() throws GameActionException {
        final RobotType buildType = RobotType.LUMBERJACK;
        if (rc.getTeamBullets() < buildType.bulletCost) {
            return;
        }
        // Build this if we have neutral trees within some radius
        final TreeInfo[] trees = rc.senseNearbyTrees(LUMBERJACK_RADIUS, Team.NEUTRAL);
        if (trees.length == 0) {
            return;
        }
        final MapLocation centerLoc = formation.getArcCenter();
        Direction dir = centerLoc.directionTo(myLoc);
        final int numTries = 12;
        final float degreeDelta = 360.0f / numTries;
        int i = 0;
        while (i < numTries && !rc.canBuildRobot(buildType, dir)) {
            i += 1;
            final int sign = (i % 2) * 2 - 1;
            dir = dir.rotateRightDegrees(degreeDelta * i * sign);
        }
        if (i < numTries) {
            rc.buildRobot(buildType, dir);
        }
    }
}
