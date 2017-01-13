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

    static MapLocation homeArchon = null;
    static MapLocation myLoc = null;
    static final MapLocation[] broadcastedEnemies = new MapLocation[BotBase.MAX_ENEMY_ROBOTS + 1];

    public BotScout(final RobotController rc) {
        super(rc);
    }

    public void run() throws GameActionException {
        while (true) {
            try {
                myLoc = rc.getLocation();

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

    public final void findHomeArchon() throws GameActionException {
        final MapLocation[] myArchons = Messaging.readArchonLocation(this);
        homeArchon = null;
        float minDistance = 0;
        for (final MapLocation archon : myArchons) {
            if (archon == null) {
                continue;
            }
            final float distance = archon.distanceTo(myLoc);
            if (homeArchon == null || distance < minDistance) {
                homeArchon = archon;
                minDistance = distance;
            }
        }
    }

    public final boolean seekAndAttackEnemy() throws GameActionException {
        // See if enemy within sensor range
        final RobotInfo[] enemies = rc.senseNearbyRobots(-1, enemyTeam);
        RobotInfo nearestEnemy = null;
        float minDistance = 0;
        for (final RobotInfo enemy : enemies) {
            Messaging.broadcastEnemyRobot(this, enemy);
            final float distance = enemy.location.distanceTo(myLoc) - enemy.getRadius() - myType.bodyRadius;
            if (nearestEnemy == null || distance < minDistance) {
                nearestEnemy = enemy;
                minDistance = distance;
            }
        }
        if (nearestEnemy != null) {
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
