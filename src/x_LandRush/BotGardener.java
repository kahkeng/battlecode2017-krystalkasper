package x_LandRush;

import battlecode.common.Clock;
import battlecode.common.GameActionException;
import battlecode.common.RobotController;
import battlecode.common.RobotType;

public strictfp class BotGardener extends x_Duck16.BotGardener {

    public BotGardener(final RobotController rc) {
        super(rc);
    }

    public void run() throws GameActionException {
        while (true) {
            try {
                while (!tryBuildRobot(RobotType.SOLDIER, formation.baseDir)) {
                    startLoop();
                    Clock.yield();
                }
                while (true) {
                    Clock.yield();
                }
            } catch (Exception e) {
                System.out.println("Gardener Exception");
                e.printStackTrace();
            }
        }

    }

}
