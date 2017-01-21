package x_LandRush;

import battlecode.common.Clock;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import x_Base.Combat;

public strictfp class BotSoldier extends x_Base.BotSoldier {

    public BotSoldier(final RobotController rc) {
        super(rc);
        DEBUG = true;
    }

    public void run() throws GameActionException {
        while (true) {
            try {
                startLoop();

                // Rush to enemy archon location
                if (!Combat.seekAndAttackAndSurroundEnemy(this)) {
                    // patrolAlongArc();
                    nav.setDestination(enemyInitialArchonLocs[0]);
                    final MapLocation nextLoc = nav.getNextLocation();
                    if (!tryMove(nextLoc)) {
                        break;
                    }
                }

                // Make them explore the map

                Clock.yield();
            } catch (Exception e) {
                System.out.println("Soldier Exception");
                e.printStackTrace();
            }
        }
    }

}
