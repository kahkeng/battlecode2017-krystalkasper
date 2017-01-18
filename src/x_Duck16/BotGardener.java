package x_Duck16;

import battlecode.common.Clock;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotType;

public strictfp class BotGardener extends x_Arc.BotGardener {

    public BotGardener(final RobotController rc) {
        super(rc);
    }

    public void run() throws GameActionException {
        try {
            earlyGame();
        } catch (Exception e) {
            System.out.println("Gardener Exception");
            e.printStackTrace();
        }
        while (true) {
            try {
                startLoop();
                // Messaging.broadcastGardener(this);
                // final MapLocation[] myArchons = Messaging.readArchonLocation(this);

                waterTrees();
                final MapLocation enemyLoc = buildCombatUnitsIfNeeded();
                if (enemyLoc != null) {
                    fleeFromEnemyAlongArc(enemyLoc);
                } else {
                    plantTreesInArc(0);
                    buildLumberjacksIfNeeded();
                }

                Clock.yield();

            } catch (Exception e) {
                System.out.println("Gardener Exception");
                e.printStackTrace();
            }
        }
    }

    public final void earlyGame() throws GameActionException {
        if (rc.getRoundNum() > 10) {
            return;
        }
        while (!tryBuildRobot(RobotType.SCOUT, formation.baseDir)) {
            startLoop();
            Clock.yield();
        }
        while (!tryBuildRobot(RobotType.SCOUT, formation.baseDir)) {
            startLoop();
            Clock.yield();
        }
        while (!tryBuildRobot(RobotType.SOLDIER, formation.baseDir)) {
            startLoop();
            Clock.yield();
        }
        while (!tryPlantTrees()) {
            startLoop();
            Clock.yield();
        }
    }

}
