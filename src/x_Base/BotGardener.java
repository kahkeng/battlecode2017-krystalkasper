package x_Base;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.GameConstants;
import battlecode.common.RobotController;
import battlecode.common.RobotType;
import battlecode.common.TreeInfo;

public strictfp class BotGardener extends BotBase {

    public static final RobotType[] BUILD_ORDER = { RobotType.LUMBERJACK, RobotType.SCOUT, /* RobotType.SOLDIER */ };
    public static final float BUILD_BULLET_BUFFER = 1.0f;
    int buildIndex = 0;

    public BotGardener(final RobotController rc) {
        super(rc);
    }

    public void run() throws GameActionException {
        while (true) {
            try {
                startLoop();
                Messaging.broadcastGardener(this);
                // final MapLocation[] myArchons = Messaging.readArchonLocation(this);

                waterTrees();
                plantTrees();
                buildUnits();

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

    public final boolean plantTrees() throws GameActionException {
        Direction dir = Direction.getNorth();
        int i = 0;
        while (i < 5 && !rc.canPlantTree(dir)) {
            dir = dir.rotateRightDegrees(60.0f);
            i += 1;
        }
        if (i < 5) {
            rc.plantTree(dir);
            return true;
        }
        return false;
    }

    public final void buildUnits() throws GameActionException {
        final RobotType buildType = BUILD_ORDER[buildIndex % BUILD_ORDER.length];
        if (rc.getTeamBullets() < buildType.bulletCost + buildIndex * BUILD_BULLET_BUFFER) {
            return;
        }
        Direction dir = Direction.getNorth();
        int i = 0;
        while (i < 6 && !rc.canBuildRobot(buildType, dir)) {
            dir = dir.rotateRightDegrees(60.0f);
            i += 1;
        }
        if (i < 6) {
            rc.buildRobot(buildType, dir);
            buildIndex = (buildIndex + 1) % (2 * BUILD_ORDER.length);
        }
    }

}
