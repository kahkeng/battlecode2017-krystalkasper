package x_Duck16;

import battlecode.common.Clock;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.RobotType;
import x_Base.Combat;

public strictfp class BotArchon extends x_Arc.BotArcBase {

    /** Hire a gardener if none in this radius. */
    public static final float GARDENER_RADIUS = 10.0f;

    public BotArchon(final RobotController rc) {
        super(rc);
        radianStep = formation.getRadianStep(myLoc, FLEE_RADIUS);
    }

    public void run() throws GameActionException {
        boolean shouldSpawnGardeners = false;
        if (rc.getLocation().equals(formation.furthestArchon)) {
            shouldSpawnGardeners = true;
        }
        while (true) {
            try {
                startLoop();
                // Messaging.broadcastArchonLocation(this);

                final MapLocation enemyLoc = Combat.senseNearbyEnemies(this);
                if (enemyLoc != null) {
                    fleeFromEnemyAlongArc(enemyLoc);
                }
                if (!shouldSpawnGardeners && (rc.getRoundNum() > 55)) {
                    shouldSpawnGardeners = true;
                }
                if (shouldSpawnGardeners) {
                    hireGardenersIfNoneAround();
                }
                moveCloserToArc();

                Clock.yield();

            } catch (Exception e) {
                System.out.println("Archon Exception");
                e.printStackTrace();
            }
        }
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
