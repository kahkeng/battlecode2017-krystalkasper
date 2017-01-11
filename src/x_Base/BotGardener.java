package x_Base;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.GameConstants;
import battlecode.common.RobotController;
import battlecode.common.RobotType;
import battlecode.common.TreeInfo;

public strictfp class BotGardener extends BotBase {

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

    public final void waterTrees() throws GameActionException {
        final TreeInfo[] trees = rc.senseNearbyTrees(myType.bodyRadius + myType.strideRadius, myTeam);
        for (final TreeInfo tree : trees) {
            if (rc.canWater(tree.ID) && tree.getHealth() < tree.maxHealth - GameConstants.WATER_HEALTH_REGEN_RATE) {
                rc.water(tree.ID);
                return;
            }
        }
    }

    public final void plantTrees() throws GameActionException {
        Direction dir = Direction.getNorth();
        int i = 0;
        while (i < 5 && !rc.canPlantTree(dir)) {
            dir = dir.rotateRightDegrees(60.0f);
            i += 1;
        }
        if (i < 5) {
            rc.plantTree(dir);
        }
    }

    public final void buildTank() throws GameActionException {
        Direction dir = Direction.getNorth();
        int i = 0;
        while (i < 6 && !rc.canBuildRobot(RobotType.SCOUT, dir)) {
            dir = dir.rotateRightDegrees(60.0f);
            i += 1;
        }
        if (i < 6) {
            rc.buildRobot(RobotType.SCOUT, dir);
        }
    }

}
