package x_Expand;

import battlecode.common.Clock;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import x_Base.Messaging;
import x_Base.Util;

public strictfp class BotArchon extends x_Base.BotArchon {
    public int myArchonID = -1;

    public BotArchon(final RobotController rc) {
        super(rc);
        final MapLocation myLoc = rc.getLocation();
        for (int i = 0; i < numInitialArchons; i++) {
            if (myInitialArchonLocs[i].equals(myLoc)) {
                myArchonID = i;
                break;
            }
        }
    }

    public void run() throws GameActionException {
        while (true) {
            try {
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
