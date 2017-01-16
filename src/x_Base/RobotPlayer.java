package x_Base;

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
            new BotArchon(rc).run();
            break;
        case GARDENER:
            new BotGardener(rc).run();
            break;
        case SCOUT:
            new BotScout(rc).run();
            break;
        case TANK:
            break;
        case SOLDIER:
            new BotSoldier(rc).run();
            break;
        case LUMBERJACK:
            new BotLumberjack(rc).run();
            break;
        }
    }

}