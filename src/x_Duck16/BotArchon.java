package x_Duck16;

import battlecode.common.Clock;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import x_Base.Combat;

public strictfp class BotArchon extends x_Arc.BotArchon {

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

}
