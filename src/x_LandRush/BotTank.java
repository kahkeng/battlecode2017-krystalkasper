package x_LandRush;

import battlecode.common.Clock;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import x_Base.Combat;

public strictfp class BotTank extends x_Base.BotTank {

    public BotTank(final RobotController rc) {
        super(rc);
        DEBUG = true;
    }

    public void run() throws GameActionException {
        while (true) {
            try {
                startLoop();

                if (!Combat.seekAndAttackAndSurroundEnemy(this)) {
                    // patrolAlongArc();
                    nav.setDestination(enemyInitialArchonLocs[0]);
                    final MapLocation nextLoc = nav.getNextLocation();
                    if (!tryMove(nextLoc)) {
                        break;
                    }
                }

                Clock.yield();
            } catch (Exception e) {
                System.out.println("Tank Exception");
                e.printStackTrace();
            }
        }
    }

}
