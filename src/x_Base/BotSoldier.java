package x_Base;

import battlecode.common.Clock;
import battlecode.common.GameActionException;
import battlecode.common.RobotController;

public strictfp class BotSoldier extends BotBase {

    public BotSoldier(final RobotController rc) {
        super(rc);
    }

    public void run() throws GameActionException {
        while (true) {
            try {
                startLoop();

                Clock.yield();
            } catch (Exception e) {
                System.out.println("Soldier Exception");
                e.printStackTrace();
            }
        }
    }

}
