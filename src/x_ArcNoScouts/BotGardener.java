package x_ArcNoScouts;

import battlecode.common.Clock;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;

public strictfp class BotGardener extends x_Arc.BotGardener {

    public BotGardener(final RobotController rc) {
        super(rc);
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
                }

                Clock.yield();

            } catch (Exception e) {
                System.out.println("Gardener Exception");
                e.printStackTrace();
            }
        }
    }

}
