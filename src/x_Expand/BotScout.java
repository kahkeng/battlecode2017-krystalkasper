package x_Expand;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;

public strictfp class BotScout extends x_Base.BotScout {

    static MapLocation homeArchon = null;
    static MapLocation myLoc = null;

    public BotScout(final RobotController rc) {
        super(rc);
    }

    public void run() throws GameActionException {
        while (true) {
            try {
                startLoop();
                if (!seekAndAttackEnemy()) {
                    findHomeArchon();
                    if (homeArchon != null) {
                        final Direction d = homeArchon.directionTo(myLoc);
                        tryMove(d);
                    }
                }

                Clock.yield();

            } catch (Exception e) {
                System.out.println("Scout Exception");
                e.printStackTrace();
            }
        }
    }

}
