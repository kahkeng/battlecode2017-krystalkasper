package x_Base;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;

public strictfp class BotScout extends BotBase {

    public static final float SCOUT_DISTANCE_ATTACK_RANGE = 10.0f;
    public static final float SCOUT_DISTANCE_EXPLORE_RANGE = 20.0f;

    public BotScout(final RobotController rc) {
        super(rc);
        StrategyFeature.initialize(rc);
    }

    public void run() throws GameActionException {
        while (true) {
            try {
                startLoop();

                if (!seekAndAttackEnemy()) {
                    findHomeArchon();
                    if (homeArchon != null) {
                        final Direction d = homeArchon.directionTo(myLoc);
                        if (homeArchon.distanceTo(myLoc) <= SCOUT_DISTANCE_EXPLORE_RANGE) {
                            tryMove(d.rotateLeftDegrees(30));
                        } else {
                            tryMove(d.opposite().rotateRightDegrees(30));
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

    public final boolean seekAndAttackEnemy() throws GameActionException {
        // See if enemy within sensor range
        final RobotInfo[] enemies = rc.senseNearbyRobots(-1, enemyTeam);
        for (final RobotInfo enemy : enemies) {
            Messaging.broadcastEnemyRobot(this, enemy);
        }
        RobotInfo nearestEnemy = enemies.length == 0 ? null : enemies[0];
        if (nearestEnemy != null) {
            final float minDistance = nearestEnemy.location.distanceTo(myLoc) - nearestEnemy.getRadius()
                    - myType.bodyRadius;
            final Direction enemyDir = myLoc.directionTo(nearestEnemy.location);
            float moveDist = Math.min(minDistance - 0.01f, myType.strideRadius);
            boolean distanceAttack = false;
            if (minDistance < SCOUT_DISTANCE_ATTACK_RANGE && StrategyFeature.SCOUT_DISTANCE_ATTACK.enabled()) {
                // Check if any friendly robots lie in path
                final RobotInfo[] friendlies = rc.senseNearbyRobots(SCOUT_DISTANCE_ATTACK_RANGE, myTeam);
                boolean willCollide = false;
                for (final RobotInfo friendly : friendlies) {
                    final float friendlyDist = myLoc.distanceTo(friendly.location);
                    final float diffRad = enemyDir.radiansBetween(myLoc.directionTo(friendly.location));
                    if (Math.abs(diffRad) <= Math.PI / 4) {
                        final double perpDist = Math.abs(Math.sin(diffRad) * friendlyDist);
                        // TODO: make this have some buffer or account for trajectory
                        if (perpDist <= friendly.getRadius() + 0.1f) { // will collide
                            willCollide = true;
                            break;
                        }
                    }
                }
                if (!willCollide) {
                    moveDist = Math.min(moveDist, myType.bulletSpeed - 0.01f);
                    distanceAttack = true;
                }
            }
            if (moveDist > 0) {
                tryMove(enemyDir, moveDist, 20, 3);
            }
            if (rc.canFireSingleShot() && (distanceAttack || minDistance <= 1.05 * myType.bodyRadius)) {
                rc.fireSingleShot(enemyDir);
            }
            return true;
        }
        // Else head towards closest known broadcasted enemies
        final int numEnemies = Messaging.getEnemyRobots(broadcastedEnemies, this);
        MapLocation nearestLoc = null;
        float minDistance = 0;
        for (int i = 0; i < numEnemies; i++) {
            final MapLocation enemyLoc = broadcastedEnemies[i];
            final float distance = enemyLoc.distanceTo(myLoc);
            if (nearestLoc == null || distance < minDistance) {
                nearestLoc = enemyLoc;
                minDistance = distance;
            }
        }
        if (nearestLoc != null) {
            final Direction enemyDir = myLoc.directionTo(nearestLoc);
            tryMove(enemyDir);
            return true;
        }
        return false;
    }

}
