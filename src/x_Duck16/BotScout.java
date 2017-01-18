package x_Duck16;

import battlecode.common.Clock;
import battlecode.common.GameActionException;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.TreeInfo;
import x_Base.Combat;
import x_Base.Debug;

public strictfp class BotScout extends x_Arc.BotArcBase {

    public BotScout(final RobotController rc) {
        super(rc);
        DEBUG = true;
    }

    public void run() throws GameActionException {
        int archonID = (rc.getRoundNum() / 50) % numInitialArchons;
        while (true) {
            try {
                startLoop();

                if (!Combat.harrassEnemy(this)) {
                    tryMove(enemyInitialArchonLocs[archonID]);
                }

                Clock.yield();

            } catch (Exception e) {
                System.out.println("Scout Exception");
                e.printStackTrace();
            }
        }
    }

    public final boolean hideInBulletTree() throws GameActionException {
        // First look for our trees
        final TreeInfo[] myTrees = rc.senseNearbyTrees(-1, myTeam);
        if (myTrees.length > 0) {
            for (final TreeInfo tree : myTrees) {
                // does not return self, so it works to pick the closest tree
                final RobotInfo[] robots = rc.senseNearbyRobots(tree.location, tree.radius, myTeam);
                if (robots.length == 0) {
                    tryMove(tree.location);
                    Debug.debug_print(this, "hiding in tree " + tree.location);
                    return true;
                }
            }
        }
        // Next look for enemy trees
        final TreeInfo[] enemyTrees = rc.senseNearbyTrees(-1, enemyTeam);
        if (enemyTrees.length > 0) {
            for (final TreeInfo tree : enemyTrees) {
                final RobotInfo[] robots = rc.senseNearbyRobots(tree.location, tree.radius, myTeam);
                if (robots.length == 0) {
                    tryMove(tree.location);
                    return true;
                }
            }
        }
        return false;
    }

}
