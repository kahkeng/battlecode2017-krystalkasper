package x_Arc;

import battlecode.common.Clock;
import battlecode.common.GameActionException;
import battlecode.common.GameConstants;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.RobotType;
import x_Base.Combat;

public strictfp class BotArchon extends BotArcBase {

    /** Hire a gardener if none in this radius. */
    public static final float GARDENER_RADIUS = 10.0f;

    public BotArchon(final RobotController rc) {
        super(rc);
        DEBUG = true;
        radianStep = formation.getRadianStep(myLoc, FLEE_RADIUS);
    }

    public void run() throws GameActionException {
        while (true) {
            try {
                startLoop();
                // Messaging.broadcastArchonLocation(this);

                final MapLocation enemyLoc = Combat.senseNearbyEnemies(this);
                if (enemyLoc != null) {
                    fleeFromEnemyAlongArc(enemyLoc);
                }
                hireGardenersIfNoneAround();
                moveCloserToArc();

                Clock.yield();

            } catch (Exception e) {
                System.out.println("Archon Exception");
                e.printStackTrace();
            }
        }
    }

    public final void hireGardenersIfNoneAround() throws GameActionException {
        final RobotInfo[] robots = rc.senseNearbyRobots(GARDENER_RADIUS, myTeam);
        boolean found = false;
        for (final RobotInfo robot : robots) {
            if (robot.type != RobotType.GARDENER) {
                continue;
            }
            // those surrounded by 4 trees don't count
            final TreeInfo[] trees = rc.senseNearbyTrees(robot.location, robot.type.bodyRadius +
                    GameConstants.BULLET_TREE_RADIUS + 0.5f, myTeam);
            if (trees.length >= 4) {
                continue;
            }
            found = true;
            break;
        }
        // Debug.debug_print(this, "hire gardener " + found);
        if (!found) {
            tryHireGardener(formation.getArcDir(myLoc).opposite());
        }
    }

}
