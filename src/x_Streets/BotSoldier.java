package x_Streets;

import battlecode.common.BulletInfo;
import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import x_Base.BotBase;
import x_Base.Combat;
import x_Base.Debug;
import x_Base.Messaging;
import x_Base.SprayCombat;
import x_Base.StrategyFeature;

public strictfp class BotSoldier extends BotBase {

    public static final int ENEMY_GARDENER_EXPIRY = 100;
    public static final float SNIPE_DISTANCE = 15.0f;
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
                if (StrategyFeature.COMBAT_SPRAY1.enabled()) {
                    if (!SprayCombat.sprayEnemy1(this)) {
                        if (!Combat.attackPriorityEnemies(this)) {
                            if (!StrategyFeature.COMBAT_SNIPE_BASES.enabled()) {
                                // Else head towards closest known broadcasted enemies
                                if (!Combat.headTowardsBroadcastedEnemy(this, 100.0f)) {
                                    macroCombatStrategy();
                                }
                            } else {
                                macroCombatStrategy();
                            }
                        }
                    }
                } else if (StrategyFeature.IMPROVED_COMBAT1.enabled()) {
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
            // Check if previously committed location is still reported, if so, refresh timer
            final int clock = rc.getRoundNum();
            final int numEnemyGardeners = Messaging.getEnemyGardeners(broadcastedEnemyGardeners, this);
            MapLocation nearestLoc = null;
            float nearestDist = 0;
            if (numEnemyGardeners > 0) {
                // Otherwise, pick nearest one and commit to it
                for (int i = 0; i < numEnemyGardeners; i++) {
                    final MapLocation gardenerLoc = broadcastedEnemyGardeners[i];
                    if (enemyGardenerLoc != null && gardenerLoc.equals(enemyGardenerLoc)) {
                        enemyGardenerRound = clock;
                    }
                    final float dist = myLoc.distanceTo(gardenerLoc);
                    if (nearestLoc == null || dist < nearestDist) {
                        nearestLoc = gardenerLoc;
                        nearestDist = dist;
                    }
                }
            }
            if (enemyGardenerLoc == null || enemyGardenerRound < clock - ENEMY_GARDENER_EXPIRY) {
                enemyGardenerLoc = nearestLoc;
                enemyGardenerRound = clock;
            }
            if (enemyGardenerLoc != null) {
                Debug.debug_dot(this, enemyGardenerLoc, 0, 255, 255);
                // move towards gardener loc, but set up sniping position once close enough
                final float enemyGardenerDist = myLoc.distanceTo(enemyGardenerLoc);
                if (enemyGardenerDist <= myType.sensorRadius) {
                    enemyGardenerLoc = null; // don't need to snipe any more
                } else if (enemyGardenerDist <= SNIPE_DISTANCE) {
                    if (rc.canFireSingleShot()) {
                        final Direction enemyDir = myLoc.directionTo(enemyGardenerLoc);
                        rc.fireSingleShot(enemyDir);
                    }
                } else {
                    nav.setDestination(enemyGardenerLoc);
                    if (!tryMove(nav.getNextLocation())) {
                        randomlyJitter();
                    }
                }
            } else {
                moveTowardsTreeBorder2();
            }
        } else {
            moveTowardsTreeBorder2();
        }
    }
}
