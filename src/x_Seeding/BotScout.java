package x_Seeding;

import battlecode.common.Clock;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.TreeInfo;
import x_Base.Combat;

public strictfp class BotScout extends x_Base.BotBase {

    public static int archonID = 0;
    public static MapLocation randomTarget = null;
    public static int lastSawEnemy = 0;

    public BotScout(final RobotController rc) {
        super(rc);
        DEBUG = true;
    }

    public void run() throws GameActionException {
        archonID = (rc.getRoundNum() / 50) % numInitialArchons;
        while (true) {
            try {
                startLoop();

                if (Combat.harrassEnemy(this)) {
                    lastSawEnemy = rc.getRoundNum();
                } else {
                    final TreeInfo tree = scoutFindTreesToShake();
                    if (tree != null) {
                        if (!tryMove(tree.location)) {
                            randomlyJitter();
                        }
                    } else if (randomTarget != null) {
                        if (myLoc.distanceTo(randomTarget) <= 6.0f) {
                            randomTarget = null;
                        } else {
                            tryMove(randomTarget);
                        }
                    } else {
                        patrolEnemyArchonLocs(0);
                    }
                }

                Clock.yield();

            } catch (Exception e) {
                System.out.println("Scout Exception");
                e.printStackTrace();
            }
        }
    }

    public final void patrolEnemyArchonLocs(final int attempt) throws GameActionException {
        if (attempt > 3 || rc.getRoundNum() - lastSawEnemy > 100) {
            // go to a random broadcasted location
            final MapLocation[] locs = rc.senseBroadcastingRobotLocations();
            if (locs.length == 0) {
                randomTarget = formation.mapCentroid;
            } else {
                randomTarget = locs[0];
            }
            return;
        }
        final MapLocation archonLoc = enemyInitialArchonLocs[archonID];
        if (myLoc.distanceTo(archonLoc) <= 6.0f) {
            archonID = (archonID + 1) % numInitialArchons;
            patrolEnemyArchonLocs(attempt + 1);
        } else {
            tryMove(archonLoc);
        }
    }

}
