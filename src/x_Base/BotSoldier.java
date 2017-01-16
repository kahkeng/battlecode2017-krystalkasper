package x_Base;

import battlecode.common.Clock;
import battlecode.common.GameActionException;
import battlecode.common.RobotController;
import x_Arc.BotArcBase;

public strictfp class BotSoldier extends BotArcBase {

    public BotSoldier(final RobotController rc) {
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
                System.out.println("Soldier Exception");
                e.printStackTrace();
            }
        }
    }

}
