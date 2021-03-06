package x_Streets;

import battlecode.common.Clock;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.RobotType;
import battlecode.common.TreeInfo;
import x_Base.Combat;
import x_Base.Messaging;
import x_Base.StrategyFeature;

public strictfp class BotScout extends x_Base.BotBase {

    public static int archonID = 0;
    public static MapLocation randomTarget = null;
    public static int lastSawEnemy = 0;

    public BotScout(final RobotController rc) {
        super(rc);
        StrategyFeature.initialize(rc);
        DEBUG = true;
    }

    public void run() throws GameActionException {
        archonID = (rc.getRoundNum() / 50) % numInitialArchons;
        while (true) {
            try {
                startLoop();
                Messaging.broadcastScout(this);

                if (StrategyFeature.COMBAT_SNIPE_BASES.enabled()) {
                    // Broadcast any sighted enemy base
                    final RobotInfo[] enemies = rc.senseNearbyRobots(-1, enemyTeam);
                    for (final RobotInfo enemy : enemies) {
                        if (enemy.type != RobotType.GARDENER) {
                            continue;
                        }
                        if (enemy.getMoveCount() != 0) {
                            continue;
                        }
                        Messaging.broadcastEnemyGardener(this, enemy);
                    }
                }

                if (!Combat.attackPriorityEnemies(this)) {
                    if (Combat.avoidEnemy(this)) {
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
                }

                Clock.yield();

            } catch (Exception e) {
                System.out.println("Scout Exception");
                e.printStackTrace();
            }
        }
    }

    public final void patrolEnemyArchonLocs(final int attempt) throws GameActionException {
        if (attempt > 4) {
            // go to the furthest broadcasted location from my archon
            /*
             * final MapLocation[] locs = rc.senseBroadcastingRobotLocations();
             * 
             * if (locs.length == 0) { randomTarget = formation.mapCentroid; } else { randomTarget = locs[0]; }
             */
            randomTarget = formation.mapCentroid;
            return;
        }
        final MapLocation archonLoc;
        if (archonID < enemyInitialArchonLocs.length) {
            archonLoc = enemyInitialArchonLocs[archonID];
        } else {
            archonLoc = Messaging.getLastEnemyLocation(this);
        }
        if (archonLoc == null || myLoc.distanceTo(archonLoc) <= 6.0f) {
            archonID = (archonID + 1) % (numInitialArchons + 1);
            patrolEnemyArchonLocs(attempt + 1);
        } else {
            tryMove(archonLoc);
        }
    }

}
