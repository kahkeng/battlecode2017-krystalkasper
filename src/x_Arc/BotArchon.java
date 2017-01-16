package x_Arc;

import battlecode.common.Clock;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.RobotType;
import x_Base.Messaging;

public strictfp class BotArchon extends BotArcBase {

    /** Hire a gardener if none in this radius. */
    public static final float GARDENER_RADIUS = 7.0f;

    public BotArchon(final RobotController rc) {
        super(rc);
        radianStep = formation.getRadianStep(myLoc, FLEE_RADIUS);
    }

    public void run() throws GameActionException {
        while (true) {
            try {
                startLoop();
                // Messaging.broadcastArchonLocation(this);

                final MapLocation enemyLoc = senseNearbyEnemies();
                if (enemyLoc != null) {
                    fleeFromEnemyAlongArc(enemyLoc);
                }
                hireGardenersIfNoneAround();

                Clock.yield();

            } catch (Exception e) {
                System.out.println("Archon Exception");
                e.printStackTrace();
            }
        }
    }

    public final MapLocation senseNearbyEnemies() throws GameActionException {
        final RobotInfo[] enemyRobots = rc.senseNearbyRobots(-1, enemyTeam);
        if (enemyRobots.length > 0) {
            for (final RobotInfo enemy : enemyRobots) {
                Messaging.broadcastEnemyRobot(this, enemy);
            }
            return enemyRobots[0].location;
        }
        final int numEnemies = Messaging.getEnemyRobots(broadcastedEnemies, this);
        MapLocation nearestLoc = null;
        float minDistance = 0;
        for (int i = 0; i < numEnemies; i++) {
            final MapLocation enemyLoc = broadcastedEnemies[i];
            final float distance = enemyLoc.distanceTo(myLoc);
            if (nearestLoc == null || distance < minDistance) {
                nearestLoc = enemyLoc;
                minDistance = distance;
            }
        }
        return nearestLoc;
    }

    public final void hireGardenersIfNoneAround() throws GameActionException {
        final RobotInfo[] robots = rc.senseNearbyRobots(GARDENER_RADIUS, myTeam);
        boolean found = false;
        for (final RobotInfo robot : robots) {
            if (robot.type != RobotType.GARDENER) {
                continue;
            }
            found = true;
            break;
        }
        if (!found) {
            tryHireGardener(formation.getArcDir(myLoc).opposite());
        }
    }

}
