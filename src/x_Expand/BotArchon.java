package x_Expand;

import battlecode.common.Clock;
import battlecode.common.GameActionException;
import battlecode.common.RobotController;
import x_Base.Messaging;
import x_Base.Util;

public strictfp class BotArchon extends x_Base.BotArchon {

    public BotArchon(final RobotController rc) {
        super(rc);
    }

    public void run() throws GameActionException {
        while (true) {
            try {
                startLoop();
                Messaging.broadcastArchonLocation(this);
                hireGardeners();

                // Move randomly
                tryMove(Util.randomDirection());

                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                System.out.println("Archon Exception");
                e.printStackTrace();
            }
        }
    }

}
