package x_LandRush;

import battlecode.common.Clock;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import x_Base.Combat;

public strictfp class BotArchon extends x_Duck16.BotArchon {

    public BotArchon(final RobotController rc) {
        super(rc);
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
                // moveCloserToArc();
                nav.setDestination(enemyInitialArchonLocs[0]);
                final MapLocation nextLoc = nav.getNextLocation();
                if (!tryMove(nextLoc)) {
                    System.out.println("can't move to nextLoc " + nextLoc);
                }

                Clock.yield();

            } catch (Exception e) {
                System.out.println("Archon Exception");
                e.printStackTrace();
            }
        }
    }

}
