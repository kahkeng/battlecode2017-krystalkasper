package x_Base;

import battlecode.common.Clock;
import battlecode.common.GameActionException;
import battlecode.common.RobotController;

public strictfp class BotTank extends BotBase {

    public BotTank(final RobotController rc) {
        super(rc);
    }

    public void run() throws GameActionException {
        while (true) {
            try {
                startLoop();

                Clock.yield();
            } catch (Exception e) {
                System.out.println("Tank Exception");
                e.printStackTrace();
            }
        }
    }

}
