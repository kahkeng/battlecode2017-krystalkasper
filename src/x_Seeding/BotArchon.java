package x_Seeding;

import battlecode.common.Clock;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.RobotType;
import battlecode.common.TreeInfo;
import x_Base.Combat;

public strictfp class BotArchon extends x_Base.BotArchon {

    /** Hire a gardener if none in this radius. */
    public static final float GARDENER_RADIUS = 10.0f;

    public BotArchon(final RobotController rc) {
        super(rc);
    }

    public void run() throws GameActionException {
        boolean shouldSpawnGardeners = false;
        final boolean isShortGame = meta.isShortGame();
        if (isShortGame && rc.getLocation().equals(formation.nearestArchon)
                || !isShortGame && rc.getLocation().equals(formation.furthestArchon)) {
            shouldSpawnGardeners = true;
        }
        while (true) {
            try {
                startLoop();
                // Messaging.broadcastArchonLocation(this);

                // TODO: prioritized enemies
                final MapLocation enemyLoc = Combat.senseNearbyEnemies(this);
                if (enemyLoc != null) {
                    // fleeFromEnemyAlongArc(enemyLoc);
                }
                if (!shouldSpawnGardeners && (rc.getRoundNum() > 55
                        || rc.getRoundNum() <= 5 && rc.getRobotCount() == numInitialArchons)) {
                    shouldSpawnGardeners = true;
                }
                if (shouldSpawnGardeners) {
                    hireGardenersIfNoneAroundWithSpace();
                }

                final TreeInfo tree = archonFindTreesToShake();
                if (tree != null) {
                    tryMove(tree.location);
                }
                // moveCloserToArc();

                Clock.yield();

            } catch (Exception e) {
                System.out.println("Archon Exception");
                e.printStackTrace();
            }
        }

    }

    public final void hireGardenersIfNoneAroundWithSpace() throws GameActionException {
        // Only hire if gardener has space to spawn
        final RobotInfo[] robots = rc.senseNearbyRobots(GARDENER_RADIUS, myTeam);
        boolean found = false;
        for (final RobotInfo robot : robots) {
            if (robot.type != RobotType.GARDENER) {
                continue;
            }
            // those surrounded by 4 trees don't count
            /*
             * final TreeInfo[] trees = rc.senseNearbyTrees(robot.location, robot.type.bodyRadius +
             * GameConstants.BULLET_TREE_RADIUS + 0.5f, myTeam); if (trees.length >= 4) { continue; }
             */
            found = true;
            break;
        }
        // Debug.debug_print(this, "hire gardener " + found);
        if (!found) {
            tryHireGardenerWithSpace(formation.getArcDir(myLoc).opposite());
        }
    }
}
