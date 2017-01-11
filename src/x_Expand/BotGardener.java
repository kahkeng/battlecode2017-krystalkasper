package x_Expand;

import battlecode.common.Clock;
import battlecode.common.GameActionException;
import battlecode.common.RobotController;
import x_Base.Messaging;

public strictfp class BotGardener extends x_Base.BotGardener {

    public BotGardener(final RobotController rc) {
        super(rc);
    }

    public void run() throws GameActionException {
        while (true) {
            try {
                Messaging.broadcastGardener(this);
                // final MapLocation[] myArchons = Messaging.readArchonLocation(this);

                waterTrees();
                plantTrees();
                buildTank();

                Clock.yield();

            } catch (Exception e) {
                System.out.println("Gardener Exception");
                e.printStackTrace();
            }
        }
    }

}
