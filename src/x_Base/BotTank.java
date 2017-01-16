package x_Base;

import battlecode.common.Clock;
import battlecode.common.GameActionException;
import battlecode.common.RobotController;
import x_Arc.BotArcBase;

public strictfp class BotTank extends BotArcBase {

    public BotTank(final RobotController rc) {
        super(rc);
        DEBUG = true;
    }

    public void run() throws GameActionException {
        while (true) {
            try {
                startLoop();

                if (!Combat.seekAndAttackAndSurroundEnemy(this)) {
                    patrolAlongArc();
                }

                Clock.yield();
            } catch (Exception e) {
                System.out.println("Tank Exception");
                e.printStackTrace();
            }
        }
    }

}
