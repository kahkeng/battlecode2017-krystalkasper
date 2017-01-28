package x_Streets;

import battlecode.common.BulletInfo;
import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import x_Base.BotBase;
import x_Base.Combat;
import x_Base.Messaging;
import x_Base.StrategyFeature;

public strictfp class BotSoldier extends BotBase {

    public static final int ENEMY_GARDENER_EXPIRY = 300;
    public static MapLocation enemyGardenerLoc = null;
    public static int enemyGardenerRound = 0;

    public BotSoldier(final RobotController rc) {
        super(rc);
        StrategyFeature.initialize(rc);
        DEBUG = true;
    }

    public void run() throws GameActionException {
        while (true) {
            try {
                startLoop();
                if (StrategyFeature.IMPROVED_COMBAT1.enabled()) {
                    if (!Combat.seekAndAttackAndSurroundEnemy5(this)) {
                        macroCombatStrategy();
                    }
                } else {
                    final BulletInfo[] bullets = rc.senseNearbyBullets();
                    final Direction dodgeDir = getDodgeDirection(bullets);
                    if (dodgeDir != null) {
                        tryMove(myLoc.add(dodgeDir));
                    }
                    if (!Combat.seekAndAttackAndSurroundEnemy3(this)) {
                        macroCombatStrategy();
                    }
                }

                Clock.yield();
            } catch (Exception e) {
                System.out.println("Soldier Exception");
                e.printStackTrace();
            }
        }
    }

    public final void macroCombatStrategy() throws GameActionException {
        if (StrategyFeature.COMBAT_SNIPE_BASES.enabled()) {
            final MapLocation loc;
            if (enemyGardenerLoc != null && enemyGardenerRound >= rc.getRoundNum() - ENEMY_GARDENER_EXPIRY) {
                loc = enemyGardenerLoc;
            } else {
                final int numEnemyGardeners = Messaging.getEnemyGardeners(broadcastedEnemyGardeners, this);
                if (numEnemyGardeners > 0) {
                    // Pick nearest one and commit to it
                    enemyGardenerLoc = null;
                    float nearestDist = 0;
                    for (int i = 0; i < numEnemyGardeners; i++) {
                        final MapLocation gardenerLoc = broadcastedEnemyGardeners[i];
                        final float dist = myLoc.distanceTo(gardenerLoc);
                        if (enemyGardenerLoc == null || dist < nearestDist) {
                            enemyGardenerLoc = gardenerLoc;
                            nearestDist = dist;
                        }
                    }
                }
                moveTowardsTreeBorder();
            }
        } else {
            moveTowardsTreeBorder();
        }
    }
}
