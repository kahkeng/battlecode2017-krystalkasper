package x_Base;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;

public strictfp class BotArchon extends BotBase {
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

    public final void hireGardeners() throws GameActionException {
        final int num = Messaging.numGardener(this);
        if (num < 2) {
            // TODO: make this away from enemy
            Direction dir = Util.randomDirection();
            int i = 0;
            while (i < 6 && !rc.canHireGardener(dir)) {
                dir = dir.rotateRightDegrees(60.0f);
                i += 1;
            }
            if (i < 6) {
                rc.hireGardener(dir);
                Messaging.broadcastPotentialGardener(this);
            }
        }
    }

}
