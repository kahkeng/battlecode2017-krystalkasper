package x_ArcNoScouts;

import battlecode.common.GameActionException;
import battlecode.common.RobotController;

public strictfp class RobotPlayer {
    static RobotController rc;

    /**
     * run() is the method that is called when a robot is instantiated in the Battlecode world. If this method returns,
     * the robot dies!
     **/
    public static void run(RobotController rc) throws GameActionException {
        switch (rc.getType()) {
        case ARCHON:
            new x_Arc.BotArchon(rc).run();
            break;
        case GARDENER:
            new BotGardener(rc).run();
            break;
        case SCOUT:
            new x_Arc.BotScout(rc).run();
            break;
        case TANK:
            new x_Base.BotTank(rc).run();
            break;
        case SOLDIER:
            new x_Base.BotSoldier(rc).run();
            break;
        case LUMBERJACK:
            new x_Base.BotLumberjack(rc).run();
            break;
        }
    }

}
