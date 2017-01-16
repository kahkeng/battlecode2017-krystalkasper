package x_Arc;

import battlecode.common.Clock;
import battlecode.common.GameActionException;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.TreeInfo;
import x_Base.Combat;
import x_Base.Debug;

public strictfp class BotScout extends BotArcBase {

    public static final float SCOUT_DISTANCE_ATTACK_RANGE = 10.0f;
    public static final float SCOUT_DISTANCE_EXPLORE_RANGE = 20.0f;

    public BotScout(final RobotController rc) {
        super(rc);
        DEBUG = true;
    }

    public void run() throws GameActionException {
        while (true) {
            try {
                startLoop();

                if (hideInBulletTree()) {
                    Debug.debug_print(this, "stationary attack");
                    Combat.stationaryAttackEnemy(this);
                } else {
                    Debug.debug_print(this, "no tree to hide in");
                    // TODO: do something. find a gardener/archon
                    if (!Combat.seekAndAttackAndSurroundEnemy(this)) {
                        if (!tryMove(formation.getArcCenter())) { // offer as sacrifice, also a lure
                            patrolAlongArc();
                        }
                    }
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
