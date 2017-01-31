package x_Base;

import battlecode.common.BulletInfo;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.GameConstants;
import battlecode.common.MapLocation;
import battlecode.common.RobotInfo;
import battlecode.common.RobotType;
import battlecode.common.Team;
import battlecode.common.TreeInfo;

public strictfp class Combat {

    public static final float DISTANCE_ATTACK_RANGE = 5.0f;
    public static final float SURROUND_RANGE = 1.0f;
    public static final float ENEMY_REACTION_RANGE = 30.0f;
    public static final float HARRASS_RANGE = 3.0f; // range for harassing
    public static final float AVOID_RANGE = 6.0f; // range for avoiding
    public static final float EPS = 0.0001f;
    public static final float MAX_ROBOT_RADIUS = 2.0f;
    public static final float TRIAD_RADIANS = (float) Math.toRadians(20.0f);
    public static final float PENTAD_RADIANS = (float) Math.toRadians(30.0f);
    public static final float COUNTER_BUFFER_DIST = RobotType.SOLDIER.bodyRadius - RobotType.SOLDIER.strideRadius - EPS;
    public static final int PROCESS_OBJECT_LIMIT = 15;
    public static Direction lastFiredDir = null;

    public static final MapLocation senseNearbyEnemies(final BotBase bot) throws GameActionException {
        final RobotInfo[] enemyRobots = bot.rc.senseNearbyRobots(-1, bot.enemyTeam);
        if (enemyRobots.length > 0) {
            for (final RobotInfo enemy : enemyRobots) {
                Messaging.broadcastEnemyRobot(bot, enemy);
            }
            return enemyRobots[0].location;
        }
        MapLocation nearestLoc = null;
        float minDistance = 0;
        final int numPriorityEnemies = Messaging.getPriorityEnemyRobots(bot.broadcastedPriorityEnemies, bot);
        for (int i = 0; i < numPriorityEnemies; i++) {
            final MapLocation enemyLoc = bot.broadcastedPriorityEnemies[i];
            final float distance = enemyLoc.distanceTo(bot.myLoc);
            if (nearestLoc == null || distance < minDistance) {
                nearestLoc = enemyLoc;
                minDistance = distance;
            }
        }
        final int numEnemies = Messaging.getEnemyRobots(bot.broadcastedEnemies, bot);
        for (int i = 0; i < numEnemies; i++) {
            final MapLocation enemyLoc = bot.broadcastedEnemies[i].location;
            final float distance = enemyLoc.distanceTo(bot.myLoc);
            if (nearestLoc == null || distance < minDistance) {
                nearestLoc = enemyLoc;
                minDistance = distance;
            }
        }
        return nearestLoc;
    }

    public static final boolean seekAndAttackEnemy(final BotBase bot) throws GameActionException {
        // See if enemy within sensor range
        final RobotInfo[] enemies = bot.rc.senseNearbyRobots(-1, bot.enemyTeam);
        for (final RobotInfo enemy : enemies) {
            Messaging.broadcastEnemyRobot(bot, enemy);
        }
        final RobotInfo nearestEnemy = enemies.length == 0 ? null : enemies[0];
        if (nearestEnemy != null) {
            final float minDistance = nearestEnemy.location.distanceTo(bot.myLoc) - nearestEnemy.getRadius()
                    - bot.myType.bodyRadius;
            final Direction enemyDir = bot.myLoc.directionTo(nearestEnemy.location);
            float moveDist = Math.min(minDistance - EPS, bot.myType.strideRadius);
            boolean distanceAttack = false;
            if (minDistance <= DISTANCE_ATTACK_RANGE) {
                // Check if any friendly robots lie in path
                final RobotInfo[] friendlies = bot.rc.senseNearbyRobots(DISTANCE_ATTACK_RANGE, bot.myTeam);
                boolean willCollide = false;
                for (final RobotInfo friendly : friendlies) {
                    final float friendlyDist = bot.myLoc.distanceTo(friendly.location);
                    final float diffRad = enemyDir.radiansBetween(bot.myLoc.directionTo(friendly.location));
                    if (Math.abs(diffRad) <= Math.PI / 3) {
                        final double perpDist = Math.abs(Math.sin(diffRad) * friendlyDist);
                        // TODO: make this have some buffer or account for trajectory
                        if (perpDist <= friendly.getRadius() + EPS) { // will collide
                            willCollide = true;
                            break;
                        }
                    }
                }
                if (!willCollide) {
                    // Don't move faster than your own bullet or it will hit you
                    moveDist = Math.min(moveDist, bot.myType.bulletSpeed - EPS);
                    distanceAttack = true;
                }
            }
            final Direction enemyDir2;
            if (moveDist > 0) {
                if (bot.tryMove(enemyDir, moveDist, 20, 3)) {
                    enemyDir2 = bot.myLoc.directionTo(nearestEnemy.location);
                } else {
                    enemyDir2 = enemyDir;
                }
            } else {
                enemyDir2 = enemyDir;
            }
            if (bot.rc.canFireSingleShot() && (distanceAttack || minDistance <= bot.myType.bodyRadius)) {
                bot.rc.fireSingleShot(enemyDir2);
            }
            return true;
        }
        // Else head towards closest known broadcasted enemies
        return headTowardsBroadcastedEnemy(bot, ENEMY_REACTION_RANGE);
    }

    public static final boolean seekAndAttackAndSurroundEnemy(final x_Arc.BotArcBase bot) throws GameActionException {
        final RobotInfo[] enemies = bot.rc.senseNearbyRobots(-1, bot.enemyTeam);
        final RobotInfo worstEnemy = enemies.length == 0 ? null : prioritizedEnemy(bot, enemies);
        if (worstEnemy != null) {
            Messaging.broadcastEnemyRobot(bot, worstEnemy);
            final float enemyRadius = worstEnemy.getRadius();
            final Direction enemyDir = bot.myLoc.directionTo(worstEnemy.location);

            final Direction backDir = bot.getArcLoc().directionTo(bot.getNextArcLoc());
            final Direction sideDir; // side dir depends on which side of enemy we are on
            if (backDir.radiansBetween(enemyDir) > 0) {
                sideDir = bot.arcDirection.opposite();
            } else {
                sideDir = bot.arcDirection;
            }
            final MapLocation moveLoc = worstEnemy.location.add(sideDir,
                    enemyRadius + SURROUND_RANGE + bot.myType.bodyRadius - EPS);

            // move first before attacking
            bot.tryMove(moveLoc);
            final Direction latestEnemyDir = bot.myLoc.directionTo(worstEnemy.location);

            final float minDistance = worstEnemy.location.distanceTo(bot.myLoc) - enemyRadius - bot.myType.bodyRadius;
            boolean distanceAttack = false;
            if (minDistance <= DISTANCE_ATTACK_RANGE) {
                final float latestEnemyDistance = worstEnemy.location.distanceTo(bot.myLoc);
                if (!willBulletCollideWithFriendlies(bot, latestEnemyDir, latestEnemyDistance, enemyRadius)
                        && !willBulletCollideWithTrees(bot, latestEnemyDir, latestEnemyDistance, enemyRadius)) {
                    distanceAttack = true;
                }
            }
            if (bot.rc.canFireSingleShot() && (distanceAttack || minDistance < bot.myType.bodyRadius)) {
                bot.rc.fireSingleShot(latestEnemyDir);
            }
            return true;
        }
        // Else head towards closest known broadcasted enemies
        return headTowardsBroadcastedEnemy(bot, ENEMY_REACTION_RANGE);
    }

    public static final boolean seekAndAttackAndSurroundEnemy2(final BotBase bot) throws GameActionException {
        final RobotInfo[] enemies = bot.rc.senseNearbyRobots(-1, bot.enemyTeam);
        final RobotInfo worstEnemy = enemies.length == 0 ? null : prioritizedEnemy(bot, enemies);
        if (worstEnemy != null) {
            Messaging.broadcastEnemyRobot(bot, worstEnemy);
            final float enemyRadius = worstEnemy.getRadius();
            final Direction enemyDir = bot.myLoc.directionTo(worstEnemy.location);

            // TODO: use soldier potential to surround

            final MapLocation moveLoc;
            if (worstEnemy.type == RobotType.GARDENER || worstEnemy.type == RobotType.ARCHON) {
                moveLoc = worstEnemy.location;
            } else {
                final Direction sideDir; // side dir depends on which side of enemy we are on
                if (bot.nav.preferRight) {
                    sideDir = enemyDir.rotateLeftDegrees(90.0f);
                } else {
                    sideDir = enemyDir.rotateRightDegrees(90.0f);
                }
                moveLoc = worstEnemy.location.add(sideDir,
                        enemyRadius + SURROUND_RANGE + bot.myType.bodyRadius - EPS);
            }

            // move first before attacking
            bot.nav.setDestination(moveLoc);
            bot.tryMove(bot.nav.getNextLocation());
            final Direction latestEnemyDir = bot.myLoc.directionTo(worstEnemy.location);

            final float latestEnemyDistance = worstEnemy.location.distanceTo(bot.myLoc);
            final float minDistance = latestEnemyDistance - enemyRadius - bot.myType.bodyRadius;
            boolean distanceAttack = false;
            final float distanceAttackRange;
            if (worstEnemy.type == RobotType.SCOUT) {
                distanceAttackRange = 3.0f;
            } else {
                distanceAttackRange = DISTANCE_ATTACK_RANGE;
            }
            if (minDistance <= distanceAttackRange) {
                if (!willBulletCollideWithFriendlies(bot, latestEnemyDir, latestEnemyDistance, enemyRadius)
                        && !willBulletCollideWithTrees(bot, latestEnemyDir, latestEnemyDistance, enemyRadius)) {
                    distanceAttack = true;
                }
            }
            // Check if should do triad/pentad shots
            if (distanceAttack || minDistance < GameConstants.NEUTRAL_TREE_MIN_RADIUS) {
                attackSpecificEnemy(bot, worstEnemy);
            }
            return true;
        }
        // Else head towards closest known broadcasted enemies
        return headTowardsBroadcastedEnemy(bot, 50.0f);
    }

    public static final boolean seekAndAttackAndSurroundEnemy3(final BotBase bot) throws GameActionException {
        // This might have a bug with the 30 degree angle
        final RobotInfo[] enemies = bot.rc.senseNearbyRobots(-1, bot.enemyTeam);
        final RobotInfo worstEnemy = enemies.length == 0 ? null : prioritizedEnemy(bot, enemies);
        if (worstEnemy != null) {
            Messaging.broadcastEnemyRobot(bot, worstEnemy);
            final float enemyRadius = worstEnemy.getRadius();
            final Direction enemyDir = bot.myLoc.directionTo(worstEnemy.location);

            // move first before attacking
            if (!bot.rc.hasMoved()) {
                final MapLocation moveLoc;
                final Direction sideDir; // side dir depends on which side of enemy we are on
                if (bot.nav.preferRight) {
                    sideDir = enemyDir.rotateLeftDegrees(30.0f);
                } else {
                    sideDir = enemyDir.rotateRightDegrees(30.0f);
                }
                if (worstEnemy.type == RobotType.GARDENER || worstEnemy.type == RobotType.ARCHON) {
                    moveLoc = worstEnemy.location.add(sideDir,
                            enemyRadius + bot.myType.bodyRadius + EPS);
                } else {
                    moveLoc = worstEnemy.location.add(sideDir,
                            enemyRadius + SURROUND_RANGE + bot.myType.bodyRadius - EPS);
                }
                bot.nav.setDestination(moveLoc);
                bot.tryMove(bot.nav.getNextLocation());
            }
            final Direction latestEnemyDir = bot.myLoc.directionTo(worstEnemy.location);

            final float latestEnemyDistance = worstEnemy.location.distanceTo(bot.myLoc);
            final float minDistance = latestEnemyDistance - enemyRadius - bot.myType.bodyRadius;
            boolean distanceAttack = false;
            final float distanceAttackRange;
            if (worstEnemy.type == RobotType.SCOUT) {
                distanceAttackRange = 3.0f;
            } else {
                distanceAttackRange = DISTANCE_ATTACK_RANGE;
            }
            if (minDistance <= distanceAttackRange) {
                if (!willBulletCollideWithFriendlies(bot, latestEnemyDir, latestEnemyDistance, enemyRadius)
                        && !willBulletCollideWithTrees(bot, latestEnemyDir, latestEnemyDistance, enemyRadius)) {
                    distanceAttack = true;
                }
            }
            // Check if should do triad/pentad shots
            if (distanceAttack || minDistance < GameConstants.NEUTRAL_TREE_MIN_RADIUS) {
                attackSpecificEnemy(bot, worstEnemy);
            }
            return true;
        }
        // Else head towards closest known broadcasted enemies
        return headTowardsBroadcastedEnemy(bot, 100.0f);
    }

    public static final boolean seekAndAttackAndSurroundEnemy4(final BotBase bot) throws GameActionException {
        final RobotInfo[] enemies = bot.rc.senseNearbyRobots(-1, bot.enemyTeam);
        final RobotInfo worstEnemy = enemies.length == 0 ? null : prioritizedEnemy(bot, enemies);
        if (worstEnemy != null) {
            Messaging.broadcastEnemyRobot(bot, worstEnemy);
            final float enemyRadius = worstEnemy.getRadius();
            final Direction enemyDir = bot.myLoc.directionTo(worstEnemy.location);

            // move first before attacking
            if (!bot.rc.hasMoved()) {
                final MapLocation moveLoc;
                final Direction sideDir; // side dir depends on which side of enemy we are on
                if (bot.nav.preferRight) {
                    sideDir = enemyDir.rotateLeftDegrees(30.0f);
                } else {
                    sideDir = enemyDir.rotateRightDegrees(30.0f);
                }
                moveLoc = worstEnemy.location.add(sideDir,
                        enemyRadius + bot.myType.bodyRadius + EPS);
                bot.nav.setDestination(moveLoc);
                bot.tryMove(bot.nav.getNextLocation());
            }
            final Direction latestEnemyDir = bot.myLoc.directionTo(worstEnemy.location);

            final float latestEnemyDistance = worstEnemy.location.distanceTo(bot.myLoc);
            final float minDistance = latestEnemyDistance - enemyRadius - bot.myType.bodyRadius;
            boolean distanceAttack = false;
            final float distanceAttackRange;
            if (worstEnemy.type == RobotType.SCOUT) {
                distanceAttackRange = 3.0f;
            } else {
                distanceAttackRange = DISTANCE_ATTACK_RANGE;
            }
            if (minDistance <= distanceAttackRange) {
                if (!willBulletCollideWithFriendlies(bot, latestEnemyDir, latestEnemyDistance, enemyRadius)
                        && !willBulletCollideWithTrees(bot, latestEnemyDir, latestEnemyDistance, enemyRadius)) {
                    distanceAttack = true;
                }
            }
            // Check if should do triad/pentad shots
            if (distanceAttack || minDistance < GameConstants.NEUTRAL_TREE_MIN_RADIUS) {
                attackSpecificEnemy(bot, worstEnemy);
            }
            return true;
        }
        // Else head towards closest known broadcasted enemies
        return headTowardsBroadcastedEnemy(bot, 100.0f);
    }

    public static final boolean seekAndAttackAndSurroundEnemy5(final BotBase bot) throws GameActionException {
        final RobotInfo[] enemies = bot.rc.senseNearbyRobots(-1, bot.enemyTeam);
        final RobotInfo worstEnemy = enemies.length == 0 ? null : prioritizedEnemy(bot, enemies);
        if (worstEnemy != null) {
            Messaging.broadcastEnemyRobot(bot, worstEnemy);
            final float enemyRadius = worstEnemy.getRadius();
            final Direction enemyDir = bot.myLoc.directionTo(worstEnemy.location);

            // move first before attacking
            if (!bot.rc.hasMoved()) {
                final MapLocation moveLoc;
                final Direction sideDir; // side dir depends on which side of enemy we are on
                if (bot.nav.preferRight) {
                    sideDir = enemyDir.rotateLeftDegrees(150.0f);
                } else {
                    sideDir = enemyDir.rotateRightDegrees(150.0f);
                }
                if (worstEnemy.type == RobotType.GARDENER || worstEnemy.type == RobotType.ARCHON) {
                    moveLoc = worstEnemy.location.subtract(enemyDir,
                            enemyRadius + bot.myType.bodyRadius + EPS);
                    Debug.debug_dot(bot, moveLoc, 0, 128, 0);
                } else if (bot.rc.getHealth() > bot.myType.maxHealth / 2 && worstEnemy.type != RobotType.LUMBERJACK) {
                    moveLoc = worstEnemy.location.add(sideDir,
                            enemyRadius + bot.myType.bodyRadius + EPS);
                    Debug.debug_dot(bot, moveLoc, 0, 128, 0);
                } else {
                    moveLoc = worstEnemy.location.add(sideDir,
                            enemyRadius + SURROUND_RANGE + bot.myType.bodyRadius - EPS);
                    Debug.debug_dot(bot, moveLoc, 128, 0, 0);
                }
                bot.nav.setDestination(moveLoc);
                bot.tryMove(bot.nav.getNextLocation());
            }
            final Direction latestEnemyDir = bot.myLoc.directionTo(worstEnemy.location);

            final float latestEnemyDistance = worstEnemy.location.distanceTo(bot.myLoc);
            final float minDistance = latestEnemyDistance - enemyRadius - bot.myType.bodyRadius;
            boolean distanceAttack = false;
            final float distanceAttackRange;
            if (worstEnemy.type == RobotType.SCOUT) {
                distanceAttackRange = 3.0f;
            } else {
                distanceAttackRange = DISTANCE_ATTACK_RANGE;
            }
            if (minDistance <= distanceAttackRange) {
                if (!willBulletCollideWithFriendlies(bot, latestEnemyDir, latestEnemyDistance, enemyRadius)
                        && !willBulletCollideWithTrees(bot, latestEnemyDir, latestEnemyDistance, enemyRadius)) {
                    distanceAttack = true;
                }
            }
            // Check if should do triad/pentad shots
            if (distanceAttack || minDistance < GameConstants.NEUTRAL_TREE_MIN_RADIUS) {
                if (!attackSpecificEnemy(bot, worstEnemy)) {
                    // didn't manage to attack worst enemy. pick any other enemy to attack
                    for (int i = 0; i < enemies.length; i++) {
                        final RobotInfo enemy1 = enemies[i];
                        if (enemy1.ID == worstEnemy.ID) {
                            continue;
                        }
                        final float enemyRadius1 = enemy1.getRadius();
                        final Direction enemyDir1 = bot.myLoc.directionTo(enemy1.location);

                        final float enemyDistance1 = enemy1.location.distanceTo(bot.myLoc);
                        final float minDistance1 = enemyDistance1 - enemyRadius1 - bot.myType.bodyRadius;
                        boolean distanceAttack1 = false;
                        final float distanceAttackRange1;
                        if (enemy1.type == RobotType.SCOUT) {
                            distanceAttackRange1 = 3.0f;
                        } else {
                            distanceAttackRange1 = DISTANCE_ATTACK_RANGE;
                        }
                        if (minDistance1 <= distanceAttackRange1) {
                            if (!willBulletCollideWithFriendlies(bot, enemyDir1, enemyDistance1, enemyRadius1)
                                    && !willBulletCollideWithTrees(bot, enemyDir1, enemyDistance1, enemyRadius1)) {
                                distanceAttack1 = true;
                            }
                        }
                        if (distanceAttack1 || minDistance1 < GameConstants.NEUTRAL_TREE_MIN_RADIUS) {
                            if (attackSpecificEnemy(bot, enemy1)) {
                                break;
                            }
                        }
                    }
                }
            }
            return true;
        }
        if (!StrategyFeature.COMBAT_SNIPE_BASES.enabled()) {
            // Else head towards closest known broadcasted enemies
            return headTowardsBroadcastedEnemy(bot, 100.0f);
        } else {
            return false;
        }
    }

    public static final boolean attackPriorityEnemies(final BotBase bot) throws GameActionException {
        final MapLocation nearestEnemy = getNearestPriorityEnemy(bot);
        if (nearestEnemy != null) {
            if (bot.rc.canSenseLocation(nearestEnemy)) {
                final RobotInfo enemy = bot.rc.senseRobotAtLocation(nearestEnemy);
                if (enemy != null) {
                    if (bot.myType == RobotType.SOLDIER || bot.myType == RobotType.TANK) {
                        SprayCombat.spraySpecificEnemy(bot, enemy, new RobotInfo[0]);
                        return true;
                    }
                    // Move closer first before attacking
                    if (bot.myType == RobotType.SCOUT && bot.myLoc.distanceTo(enemy.location) <= 5.0f) {
                        avoidSpecificEnemy(bot, enemy);
                    } else {
                        if (!bot.rc.hasMoved()) {
                            bot.tryMove(enemy.location);
                        }
                    }
                    final float enemyRadius = enemy.getRadius();
                    final float enemyDistance = bot.myLoc.distanceTo(enemy.location);
                    final float minDistance = enemyDistance - enemyRadius - bot.myType.bodyRadius;
                    final Direction enemyDir = bot.myLoc.directionTo(enemy.location);
                    boolean canAttack = false;
                    if (minDistance <= DISTANCE_ATTACK_RANGE &&
                            !willBulletCollideWithFriendlies(bot, enemyDir, enemyDistance, enemyRadius)
                            && !willBulletCollideWithTrees(bot, enemyDir, enemyDistance, enemyRadius)) {
                        canAttack = true;
                    }
                    if (canAttack) {
                        attackSpecificEnemy(bot, enemy);
                    }
                }
            }
            // head towards nearest enemy
            bot.nav.setDestination(nearestEnemy);
            if (!bot.tryMove(bot.nav.getNextLocation())) {
                bot.randomlyJitter();
            }
            return true;
        }
        return false;
    }

    public static final boolean attackSpecificEnemy(final BotBase bot, final RobotInfo enemy)
            throws GameActionException {
        final float enemyRadius = enemy.getRadius();
        final float enemyDistance = bot.myLoc.distanceTo(enemy.location);
        final Direction enemyDir = bot.myLoc.directionTo(enemy.location);
        final float minDistance = enemyDistance - enemyRadius - bot.myType.bodyRadius;
        final float theta = (float) Math.asin(enemyRadius / enemyDistance);
        final float minPentadDist, minTriadDist;
        if (StrategyFeature.IMPROVED_COMBAT1.enabled()) {
            minPentadDist = 4.0f;
            minTriadDist = 6.0f;
        } else {
            minPentadDist = 3.0f;
            minTriadDist = 5.0f;
        }
        if (bot.rc.canFirePentadShot()
                && (theta * 2 >= PENTAD_RADIANS || enemy.type == RobotType.SCOUT
                        || minDistance <= minPentadDist)) {
            final Direction dirL2 = enemyDir.rotateLeftRads(PENTAD_RADIANS);
            final Direction dirL1 = enemyDir.rotateLeftRads(PENTAD_RADIANS / 2);
            final Direction dirR2 = enemyDir.rotateRightRads(PENTAD_RADIANS);
            final Direction dirR1 = enemyDir.rotateRightRads(PENTAD_RADIANS / 2);
            boolean ok = true;
            if (willBulletCollideWithFriendlies(bot, dirL2, enemyDistance, 0) ||
                    willBulletCollideWithFriendlies(bot, dirL1, enemyDistance, 0) ||
                    willBulletCollideWithFriendlies(bot, dirR2, enemyDistance, 0) ||
                    willBulletCollideWithFriendlies(bot, dirR1, enemyDistance, 0)) {
                ok = false;
            } else {
                int count = 0;
                if (!willBulletCollideWithTrees(bot, dirL2, enemyDistance, 0))
                    count++;
                if (!willBulletCollideWithTrees(bot, dirL1, enemyDistance, 0))
                    count++;
                if (!willBulletCollideWithTrees(bot, dirR2, enemyDistance, 0))
                    count++;
                if (!willBulletCollideWithTrees(bot, dirR1, enemyDistance, 0))
                    count++;
                if (count < 2)
                    ok = false;
            }
            if (ok) {
                SprayCombat.debugPentad(bot, enemyDir);
                final Direction enemyDir2;
                if (StrategyFeature.COMBAT_COUNTER_DODGE.enabled() && lastFiredDir != null) {
                    final float radBetween = lastFiredDir.radiansBetween(enemyDir);
                    final float radAdjust = COUNTER_BUFFER_DIST / enemyDistance;
                    if (radBetween > 0) {
                        enemyDir2 = enemyDir.rotateLeftRads(radAdjust);
                    } else {
                        enemyDir2 = enemyDir.rotateRightRads(radAdjust);
                    }
                } else {
                    enemyDir2 = enemyDir;
                }
                bot.rc.firePentadShot(enemyDir2);
                lastFiredDir = enemyDir;
                return true;
            }
        }
        if (bot.rc.canFireTriadShot()
                && (theta * 2 >= TRIAD_RADIANS || enemy.type == RobotType.SCOUT || minDistance <= minTriadDist)) {
            final Direction dirL1 = enemyDir.rotateLeftRads(TRIAD_RADIANS);
            final Direction dirR1 = enemyDir.rotateRightRads(TRIAD_RADIANS);
            boolean ok = true;
            if (willBulletCollideWithFriendlies(bot, dirL1, enemyDistance, 0) ||
                    willBulletCollideWithFriendlies(bot, dirR1, enemyDistance, 0)) {
                ok = false;
            } else {
                int count = 0;
                if (!willBulletCollideWithTrees(bot, dirL1, enemyDistance, 0))
                    count++;
                if (!willBulletCollideWithTrees(bot, dirR1, enemyDistance, 0))
                    count++;
                if (count < 1)
                    ok = false;
            }
            if (ok) {
                SprayCombat.debugTriad(bot, enemyDir);
                final Direction enemyDir2;
                if (StrategyFeature.COMBAT_COUNTER_DODGE.enabled() && lastFiredDir != null) {
                    final float radBetween = lastFiredDir.radiansBetween(enemyDir);
                    final float radAdjust = COUNTER_BUFFER_DIST / enemyDistance;
                    if (radBetween > 0) {
                        enemyDir2 = enemyDir.rotateLeftRads(radAdjust);
                    } else {
                        enemyDir2 = enemyDir.rotateRightRads(radAdjust);
                    }
                } else {
                    enemyDir2 = enemyDir;
                }
                bot.rc.fireTriadShot(enemyDir2);
                lastFiredDir = enemyDir;
                return true;
            }
        }
        if (bot.rc.canFireSingleShot()) {
            SprayCombat.debugSingle(bot, enemyDir);
            final Direction enemyDir2;
            if (StrategyFeature.COMBAT_COUNTER_DODGE.enabled() && lastFiredDir != null) {
                final float radBetween = lastFiredDir.radiansBetween(enemyDir);
                final float radAdjust = COUNTER_BUFFER_DIST / enemyDistance;
                if (radBetween > 0) {
                    enemyDir2 = enemyDir.rotateLeftRads(radAdjust);
                } else {
                    enemyDir2 = enemyDir.rotateRightRads(radAdjust);
                }
            } else {
                enemyDir2 = enemyDir;
            }
            bot.rc.fireSingleShot(enemyDir2);
            lastFiredDir = enemyDir;
            return true;
        }
        return false;
    }

    public static final RobotInfo prioritizedEnemy(final BotBase bot, final RobotInfo[] enemies) {
        // Choose units in lethal order
        RobotInfo worstEnemy = null;
        float worstScore = 0;
        for (final RobotInfo enemy : enemies) {
            final float typeScore;
            switch (enemy.type) {
            case SOLDIER:
            case TANK:
                typeScore = 400;
                break;
            case LUMBERJACK:
                typeScore = 350; // close lumberjack takes precedence over far soldier/tank
                break;
            case SCOUT:
                typeScore = 150;
                break;
            case GARDENER:
                typeScore = 100;
                break;
            case ARCHON:
                final boolean shouldAttackArchon = bot.rc.getRoundNum() >= 1000 || bot.myType == RobotType.LUMBERJACK
                        || ((bot.myType == RobotType.SOLDIER || bot.myType == RobotType.TANK)
                                && bot.rc.getTreeCount() >= 5);
                if (shouldAttackArchon) {
                    typeScore = -100;
                    break;
                } else {
                    // we don't want to attack archons that early since it is a waste of bullets
                    continue;
                }
            default:
                typeScore = 0;
                break;
            }
            final float enemyDistance = enemy.location.distanceTo(bot.myLoc);
            final float distanceScore;
            if (enemyDistance <= HARRASS_RANGE * 1.5) {
                distanceScore = 100;
            } else if (enemyDistance <= HARRASS_RANGE * 2.5) {
                distanceScore = 50;
            } else {
                distanceScore = 1;
            }
            final float score = typeScore + distanceScore;
            if (worstEnemy == null || score > worstScore) {
                worstEnemy = enemy;
                worstScore = score;
            }
        }
        return worstEnemy;
    }

    public static final boolean willBulletCollideWithFriendlies(final BotBase bot, final Direction bulletDir,
            final float enemyDistance, final float enemyRadius)
            throws GameActionException {
        // Note: this needs to detect friendlies, so we can't just use the navi version of this
        // Also, we don't expect too many friendlies so this is more optimized
        final RobotInfo[] friendlies = bot.rc.senseNearbyRobots(enemyDistance, bot.myTeam);
        return willObjectCollideWithSpecifiedRobots(bot, bulletDir, enemyDistance - enemyRadius, 0.0f, friendlies);
    }

    public static final boolean willObjectCollideWithSpecifiedRobots(final BotBase bot, final Direction objectDir,
            final float totalDistance, final float objectRadius, final RobotInfo[] robots) {
        // Note: robots array might have distance in sorted order from specified center location,
        // not necessarily from bot.myLoc
        for (final RobotInfo robot : robots) {
            if (willObjectCollideWithTreeOrRobot(bot, objectDir, totalDistance, objectRadius, robot.location,
                    robot.getRadius(), /* isTargetRobot= */true)) {
                Debug.debug_dot(bot, robot.location, 0, 0, 0);
                return true;
            }
        }
        return false;
    }

    public static final boolean willBulletCollideWithTrees(final BotBase bot, final Direction bulletDir,
            final float enemyDistance, final float enemyRadius) {
        // This is similar to willRobotCollideWithTreesNavi, but doesn't include enemy trees.
        final float totalDistance = enemyDistance - enemyRadius;
        final Direction objectDir = bulletDir;
        final float objectRadius = 0.0f;

        // Also, we only optimize this if needed.
        final TreeInfo[] neutralTrees0 = bot.rc.senseNearbyTrees(totalDistance, Team.NEUTRAL);
        final TreeInfo[] myTrees0 = bot.rc.senseNearbyTrees(totalDistance, bot.myTeam);
        if (neutralTrees0.length + myTrees0.length > PROCESS_OBJECT_LIMIT) {
            // we use overlapping circles that cover a rectangular box with robot width and length of travel
            // to find potential obstacles.
            final float boxRadius = Math.max(objectRadius, 1.0f); // use 1.0f for bullets
            final float startDist = boxRadius; // start point of the centers of circles
            final float endDist = totalDistance + boxRadius; // end point of centers of circles, no need to go
                                                             // further than this
            final float senseRadius = Math.max(boxRadius, (float) (objectRadius * Math.sqrt(2)));
            final MapLocation currLoc = bot.myLoc;
            float centerDist = startDist;
            while (centerDist <= endDist) {
                final MapLocation centerLoc = currLoc.add(objectDir, centerDist);
                final TreeInfo[] neutralTrees = bot.rc.senseNearbyTrees(centerLoc, senseRadius, Team.NEUTRAL);
                if (Combat.willObjectCollideWithSpecifiedTrees(bot, objectDir, totalDistance, objectRadius,
                        neutralTrees)) {
                    return true;
                }
                final TreeInfo[] myTrees = bot.rc.senseNearbyTrees(centerLoc, senseRadius, bot.myTeam);
                if (Combat.willObjectCollideWithSpecifiedTrees(bot, objectDir, totalDistance, objectRadius,
                        myTrees)) {
                    return true;
                }
                centerDist += 2 * boxRadius;
            }
        } else {
            if (Combat.willObjectCollideWithSpecifiedTrees(bot, objectDir, totalDistance, objectRadius,
                    neutralTrees0)) {
                return true;
            }
            if (Combat.willObjectCollideWithSpecifiedTrees(bot, objectDir, totalDistance, objectRadius, myTrees0)) {
                return true;
            }
        }
        return false;
    }

    public static final boolean willObjectCollideWithTreeOrRobot(final BotBase bot, final Direction objectDir,
            final float totalDistance, final float objectRadius,
            final MapLocation targetLoc, final float targetRadius, final boolean isTargetRobot) {
        // System.out .println("willObjectCollide " + objectDir + " " + totalDistance + " " + targetLoc + " " +
        // targetRadius);
        // System.out.println("myLoc " + bot.myLoc);
        // System.out.println("goto " + bot.myLoc.add(objectDir, totalDistance));
        // Debug.debug_dot(bot, bot.myLoc.add(objectDir, totalDistance), 0, 255, 0);
        // System.out.println("apart " + bot.myLoc.add(objectDir, totalDistance).distanceTo(targetLoc));
        final float targetDist = bot.myLoc.distanceTo(targetLoc);
        // System.out.println("targetDist=" + targetDist);
        if (!isTargetRobot && targetDist <= targetRadius + objectRadius + EPS) { // scout over a tree
            // check if the bullet coming from scout's edge will overlap with tree
            final MapLocation bulletLoc = bot.myLoc.add(objectDir, bot.myType.bodyRadius);
            if (bulletLoc.distanceTo(targetLoc) < targetRadius - EPS) {
                // System.out.println("collide1 " + bulletLoc.distanceTo(targetLoc) + "/" + targetRadius);
                return true; // will collide
            } else {
                return false; // won't collide
            }
        }
        final double theta = Math.asin((objectRadius + targetRadius) / targetDist);
        final float diffRad = objectDir.radiansBetween(bot.myLoc.directionTo(targetLoc));
        if (Math.abs(diffRad) > theta) {
            return false; // won't collide
        }
        final float limitDist = (float) (Math.cos(theta) * Math.cos(theta) * targetDist);
        final float straightDist = (float) Math.cos(diffRad) * totalDistance;
        if (straightDist >= limitDist) {
            // System.out.println( "collide3 " + straightDist + "/" + limitDist + " " + totalDistance + " " + diffRad +
            // " " + theta);
            return true; // will collide
        }
        final float apartDist = bot.myLoc.add(objectDir, totalDistance).distanceTo(targetLoc);
        if (apartDist <= targetRadius + objectRadius) {
            // System.out.println("collide4 " + apartDist + "/" + (targetRadius + objectRadius));
            return true; // will collide
        }
        return false;
    }

    public static final boolean willObjectCollideWithSpecifiedTrees(final BotBase bot, final Direction objectDir,
            final float totalDistance, final float objectRadius, final TreeInfo[] trees) {
        // Note: trees array might have distance in sorted order from specified center location,
        // not necessarily from bot.myLoc
        for (final TreeInfo tree : trees) {
            if (willObjectCollideWithTreeOrRobot(bot, objectDir, totalDistance, objectRadius, tree.location,
                    tree.getRadius(), /* isTargetRobot= */false)) {
                Debug.debug_dot(bot, tree.location, 0, 0, 0);
                // Debug.debug_print(bot, "colliding with tree " + tree);
                return true;
            }
        }
        return false;
    }

    public static final boolean willRobotCollideWithTreesNavi(final BotBase bot, final Direction objectDir,
            final float totalDistance, final float objectRadius) {
        final TreeInfo[] trees0 = TangentBugNavigator.getTreeObstacles(bot, bot.myLoc, totalDistance);
        if (trees0.length > PROCESS_OBJECT_LIMIT) {
            // we use overlapping circles that cover a rectangular box with robot width and length of travel
            // to find potential obstacles.
            final float boxRadius = Math.max(objectRadius, 1.0f); // use 1.0f for bullets
            final float startDist = boxRadius; // start point of the centers of circles
            final float endDist = totalDistance + boxRadius; // end point of centers of circles, no need to go
                                                             // further than this
            final float senseRadius = Math.max(boxRadius, (float) (objectRadius * Math.sqrt(2)));
            final MapLocation currLoc = bot.myLoc;
            float centerDist = startDist;
            while (centerDist <= endDist) {
                final MapLocation centerLoc = currLoc.add(objectDir, centerDist);
                final TreeInfo[] trees = TangentBugNavigator.getTreeObstacles(bot, centerLoc, senseRadius);
                if (Combat.willObjectCollideWithSpecifiedTrees(bot, objectDir, totalDistance, objectRadius, trees)) {
                    return true;
                }
                centerDist += 2 * boxRadius;
            }
        } else {
            if (Combat.willObjectCollideWithSpecifiedTrees(bot, objectDir, totalDistance, objectRadius, trees0)) {
                return true;
            }
        }
        return false;
    }

    public static final boolean willRobotCollideWithRobotsNavi(final BotBase bot, final Direction objectDir,
            final float totalDistance, final float objectRadius) {
        final RobotInfo[] robots0 = TangentBugNavigator.getRobotObstacles(bot, bot.myLoc, totalDistance);
        if (robots0.length > PROCESS_OBJECT_LIMIT) {
            // we use overlapping circles that cover a rectangular box with robot width and length of travel
            // to find potential obstacles.
            final float boxRadius = Math.max(objectRadius, 1.0f); // use 1.0f for bullets
            final float startDist = boxRadius; // start point of the centers of circles
            final float endDist = totalDistance + boxRadius; // end point of centers of circles, no need to go
                                                             // further than this
            final float senseRadius = Math.max(boxRadius, (float) (objectRadius * Math.sqrt(2)));
            final MapLocation currLoc = bot.myLoc;
            float centerDist = startDist;
            while (centerDist <= endDist) {
                final MapLocation centerLoc = currLoc.add(objectDir, centerDist);
                final RobotInfo[] robots = TangentBugNavigator.getRobotObstacles(bot, centerLoc, senseRadius);
                if (Combat.willObjectCollideWithSpecifiedRobots(bot, objectDir, totalDistance, objectRadius, robots)) {
                    return true;
                }
                centerDist += 2 * boxRadius;
            }
        } else {
            if (Combat.willObjectCollideWithSpecifiedRobots(bot, objectDir, totalDistance, objectRadius, robots0)) {
                return true;
            }
        }
        return false;
    }

    public static final boolean harrassEnemy(final BotBase bot) throws GameActionException {
        final RobotInfo[] enemies = bot.rc.senseNearbyRobots(-1, bot.enemyTeam);
        final RobotInfo worstEnemy = enemies.length == 0 ? null : prioritizedEnemy(bot, enemies);
        if (worstEnemy != null) {
            Messaging.broadcastEnemyRobot(bot, worstEnemy);
            Debug.debug_dot(bot, worstEnemy.location, 0, 0, 0);
            final float enemyRadius = worstEnemy.getRadius();
            final float enemyDistance = worstEnemy.location.distanceTo(bot.myLoc);
            final float minDistance = enemyDistance - enemyRadius - bot.myType.bodyRadius;
            final Direction enemyDir = bot.myLoc.directionTo(worstEnemy.location);
            final Direction rotateDir;
            final boolean rotated;
            if (minDistance <= HARRASS_RANGE + 1.0f
                    && (willBulletCollideWithFriendlies(bot, enemyDir, enemyDistance, enemyRadius)
                            || willBulletCollideWithTrees(bot, enemyDir, enemyDistance, enemyRadius))) {
                if (bot.preferRight) {
                    rotateDir = enemyDir.opposite()
                            .rotateRightRads(bot.myType.strideRadius / enemyDistance);
                } else {
                    rotateDir = enemyDir.opposite()
                            .rotateLeftRads(bot.myType.strideRadius / enemyDistance);
                }
                rotated = true;
            } else {
                rotateDir = enemyDir.opposite();
                rotated = false;
            }
            final float moveDist;
            final float harrassRange;
            switch (worstEnemy.type) {
            case SOLDIER:
            case TANK:
            case LUMBERJACK:
            case SCOUT:
                moveDist = bot.myType.strideRadius;
                harrassRange = HARRASS_RANGE;
                break;
            case GARDENER:
            default:
                if (enemyDistance <= HARRASS_RANGE + 1.0f) {
                    moveDist = bot.myType.bulletSpeed;
                } else {
                    moveDist = bot.myType.strideRadius;
                }
                harrassRange = EPS;
                break;
            case ARCHON:
                if (enemyDistance <= HARRASS_RANGE + 1.0f) {
                    moveDist = bot.myType.bulletSpeed;
                } else {
                    moveDist = bot.myType.strideRadius;
                }
                if (bot.myType == RobotType.SCOUT) {
                    harrassRange = HARRASS_RANGE; // give space for other units to come in
                } else {
                    harrassRange = EPS;
                }
            }
            final MapLocation moveLoc = worstEnemy.location.add(rotateDir,
                    harrassRange + enemyRadius + bot.myType.bodyRadius);
            if (!bot.tryMove(moveLoc, moveDist)) {
                if (rotated) {
                    bot.preferRight = !bot.preferRight;
                }
            }
            final Direction latestEnemyDir = bot.myLoc.directionTo(worstEnemy.location);
            final float latestEnemyDistance = worstEnemy.location.distanceTo(bot.myLoc);

            boolean distanceAttack = false;
            if (minDistance <= HARRASS_RANGE + 1) {
                if (!willBulletCollideWithFriendlies(bot, latestEnemyDir, latestEnemyDistance, enemyRadius)
                        && !willBulletCollideWithTrees(bot, latestEnemyDir, latestEnemyDistance, enemyRadius)) {
                    distanceAttack = true;
                }
            }
            if (bot.rc.canFireSingleShot() && (distanceAttack || minDistance < bot.myType.bodyRadius)) {
                bot.rc.fireSingleShot(latestEnemyDir);
            }
            return true;
        }
        return headTowardsBroadcastedEnemy(bot, ENEMY_REACTION_RANGE);
    }

    public static final boolean avoidEnemy(final BotBase bot) throws GameActionException {
        final RobotInfo[] enemies = bot.rc.senseNearbyRobots(-1, bot.enemyTeam);
        final RobotInfo worstEnemy = enemies.length == 0 ? null : prioritizedEnemy(bot, enemies);
        if (worstEnemy != null) {
            Messaging.broadcastEnemyRobot(bot, worstEnemy);
            avoidSpecificEnemy(bot, worstEnemy);
            return true;
        }
        return headTowardsBroadcastedEnemy(bot, ENEMY_REACTION_RANGE);
    }

    public static final void avoidSpecificEnemy(final BotBase bot, final RobotInfo worstEnemy)
            throws GameActionException {
        Debug.debug_dot(bot, worstEnemy.location, 0, 0, 0);
        final float enemyRadius = worstEnemy.getRadius();
        final float enemyDistance = worstEnemy.location.distanceTo(bot.myLoc);
        if (enemyDistance < AVOID_RANGE) {
            bot.fleeFromEnemy(worstEnemy.location);
        }
        final Direction enemyDir = bot.myLoc.directionTo(worstEnemy.location);
        final Direction rotateDir;
        final boolean rotated = true;
        if (bot.preferRight) {
            rotateDir = enemyDir.opposite()
                    .rotateRightRads(bot.myType.strideRadius / enemyDistance);
        } else {
            rotateDir = enemyDir.opposite()
                    .rotateLeftRads(bot.myType.strideRadius / enemyDistance);
        }
        final MapLocation moveLoc = worstEnemy.location.add(rotateDir,
                AVOID_RANGE + enemyRadius + bot.myType.bodyRadius);
        if (!bot.tryMove(moveLoc)) {
            if (rotated) {
                bot.preferRight = !bot.preferRight;
            }
        }
    }

    public static final boolean stationaryAttackEnemy(final x_Arc.BotArcBase bot) throws GameActionException {
        // See if enemy within sensor range
        final RobotInfo[] enemies = bot.rc.senseNearbyRobots(-1, bot.enemyTeam);
        for (final RobotInfo enemy : enemies) {
            Messaging.broadcastEnemyRobot(bot, enemy);
        }
        final RobotInfo nearestEnemy = enemies.length == 0 ? null : enemies[0];
        if (nearestEnemy != null) {
            final float enemyRadius = nearestEnemy.getRadius();
            final Direction latestEnemyDir = bot.myLoc.directionTo(nearestEnemy.location);

            final float minDistance = nearestEnemy.location.distanceTo(bot.myLoc) - enemyRadius - bot.myType.bodyRadius;
            boolean distanceAttack = false;
            if (minDistance <= DISTANCE_ATTACK_RANGE) {
                // Check if any friendly robots lie in path
                final RobotInfo[] friendlies = bot.rc.senseNearbyRobots(DISTANCE_ATTACK_RANGE, bot.myTeam);
                boolean willCollide = false;
                for (final RobotInfo friendly : friendlies) {
                    final float friendlyDist = bot.myLoc.distanceTo(friendly.location);
                    final float diffRad = latestEnemyDir.radiansBetween(bot.myLoc.directionTo(friendly.location));
                    if (Math.abs(diffRad) <= Math.PI / 3) {
                        final double perpDist = Math.abs(Math.sin(diffRad) * friendlyDist);
                        // TODO: make this have some buffer or account for trajectory
                        if (perpDist <= friendly.getRadius() + 0.1f) { // will collide
                            willCollide = true;
                            break;
                        }
                    }
                }
                if (!willCollide) {
                    distanceAttack = true;
                }
            }
            if (bot.rc.canFireSingleShot() && (distanceAttack || minDistance < bot.myType.bodyRadius)) {
                bot.rc.fireSingleShot(latestEnemyDir);
            }
            return true;
        }
        return false;
    }

    public static final boolean strikeEnemiesFromBehind(final BotLumberjack bot) throws GameActionException {
        // See if enemy within sensor range
        final RobotInfo[] enemies = bot.rc.senseNearbyRobots(-1, bot.enemyTeam);
        final RobotInfo worstEnemy = enemies.length == 0 ? null : prioritizedEnemy(bot, enemies);
        if (worstEnemy != null) {
            Messaging.broadcastEnemyRobot(bot, worstEnemy);
            final float enemyDistance = worstEnemy.location.distanceTo(bot.myLoc);
            final float enemyRadius = worstEnemy.getRadius();
            final RobotInfo[] friendlies = bot.rc.senseNearbyRobots(worstEnemy.location,
                    enemyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS, bot.myTeam);
            boolean isNearest = true;
            for (final RobotInfo friendly : friendlies) {
                if (friendly.type != RobotType.LUMBERJACK) {
                    continue;
                }
                if (worstEnemy.location.distanceTo(friendly.location) < enemyDistance) {
                    isNearest = false;
                    break;
                }
            }
            final MapLocation moveLoc;
            final Direction backDir = bot.getArcLoc().directionTo(bot.getNextArcLoc());
            if (isNearest) {
                // If I'm nearest lumberjack, or if there's no other lumberjack already striking distance,
                // then I'm going to try to get exactly behind enemy to strike them
                moveLoc = worstEnemy.location.add(backDir,
                        enemyRadius + bot.myType.bodyRadius + EPS);
                Debug.debug_dot(bot, moveLoc, 127, 127, 127);
            } else {
                // Otherwise, there's another lumberjack that can strike it. I will keep close, and
                // strike if enemy damage outweighs self damage
                final Direction enemyDir = worstEnemy.location.directionTo(bot.myLoc);
                final Direction sideDir; // side dir depends on which side of enemy we are on
                if (backDir.radiansBetween(enemyDir) < 0) {
                    sideDir = bot.arcDirection.opposite();
                } else {
                    sideDir = bot.arcDirection;
                }
                moveLoc = worstEnemy.location.add(sideDir,
                        enemyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS - EPS);
                Debug.debug_dot(bot, moveLoc, 255, 127, 0);
            }
            // Try to move first before attacking
            final float enemyDistance2;
            if (bot.tryMove(moveLoc)) {
                enemyDistance2 = worstEnemy.location.distanceTo(bot.myLoc);
            } else {
                enemyDistance2 = enemyDistance;
            }
            // Now check amount of damage dealt
            if (bot.rc.canStrike() && enemyDistance2 <= bot.myType.bodyRadius + bot.myType.strideRadius
                    + enemyRadius) {
                if (isNearest || getNetLumberjackHits(bot) >= 0) {
                    bot.rc.strike();
                }
            } else if (bot.rc.canStrike()) {
                // Consider the case where we are stuck and unable to get to worstEnemy.
                // We still might want to strike if netHits is positive.
                if (getNetLumberjackHits(bot) >= 1) {
                    bot.rc.strike();
                }
            }
            return true;
        }
        // Else head towards closest known broadcasted enemies
        return headTowardsBroadcastedEnemy(bot, ENEMY_REACTION_RANGE);
    }

    public static final boolean strikeEnemiesFromBehind2(final BotBase bot) throws GameActionException {
        // See if enemy within sensor range
        final RobotInfo[] enemies = bot.rc.senseNearbyRobots(-1, bot.enemyTeam);
        final RobotInfo worstEnemy = enemies.length == 0 ? null : prioritizedEnemy(bot, enemies);
        if (worstEnemy != null) {
            Messaging.broadcastEnemyRobot(bot, worstEnemy);
            final float enemyDistance = worstEnemy.location.distanceTo(bot.myLoc);
            if (worstEnemy.type != RobotType.GARDENER && worstEnemy.type != RobotType.ARCHON && enemyDistance > 4.0f) {
                final RobotInfo[] friendlies = bot.rc.senseNearbyRobots(-1, bot.myTeam);
                boolean hasSupport = false;
                outer: for (final RobotInfo friendly : friendlies) {
                    switch (friendly.type) {
                    case SOLDIER:
                    case TANK:
                        hasSupport = true;
                        break outer;
                    default:
                        break;
                    }
                }
                if (!hasSupport) {
                    bot.fleeFromEnemy(worstEnemy.location);
                    return true;
                }
            }
            strikeSpecificEnemy(bot, worstEnemy);
            return true;
        }
        return false;
    }

    public static final boolean strikePriorityEnemies(final BotBase bot) throws GameActionException {
        final MapLocation nearestEnemy = getNearestPriorityEnemy(bot);
        if (nearestEnemy != null) {
            if (bot.rc.canSenseLocation(nearestEnemy)) {
                final RobotInfo enemy = bot.rc.senseRobotAtLocation(nearestEnemy);
                if (enemy != null) {
                    strikeSpecificEnemy(bot, enemy);
                    return true;
                }
            }
            // head towards nearest enemy
            bot.nav.setDestination(nearestEnemy);
            if (!bot.tryMove(bot.nav.getNextLocation())) {
                bot.randomlyJitter();
            }

        }
        return false;
    }

    public static final boolean strikeSpecificEnemy(final BotBase bot, final RobotInfo worstEnemy)
            throws GameActionException {
        final float enemyDistance = worstEnemy.location.distanceTo(bot.myLoc);
        final float enemyRadius = worstEnemy.getRadius();
        final RobotInfo[] friendlies = bot.rc.senseNearbyRobots(worstEnemy.location,
                enemyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS, bot.myTeam);
        boolean isNearest = true;
        for (final RobotInfo friendly : friendlies) {
            // include all friendlies for now, given this is priority enemy or we flee without support
            // if (friendly.type != RobotType.LUMBERJACK) {
            // continue;
            // }
            if (worstEnemy.location.distanceTo(friendly.location) < enemyDistance) {
                isNearest = false;
                break;
            }
        }
        final MapLocation moveLoc;
        final Direction backDir = bot.formation.baseDir;
        if (isNearest) {
            // If I'm nearest lumberjack, or if there's no other lumberjack already striking distance,
            // then I'm going to try to get exactly behind enemy to strike them
            moveLoc = worstEnemy.location.add(backDir,
                    enemyRadius + bot.myType.bodyRadius + EPS);
            Debug.debug_dot(bot, moveLoc, 127, 127, 127);
        } else {
            // Otherwise, there's another lumberjack that can strike it. I will keep close, and
            // strike if enemy damage outweighs self damage
            final Direction enemyDir = worstEnemy.location.directionTo(bot.myLoc);
            final Direction sideDir; // side dir depends on which side of enemy we are on
            if (backDir.radiansBetween(enemyDir) < 0) {
                sideDir = backDir.rotateRightDegrees(90.0f);
            } else {
                sideDir = backDir.rotateLeftDegrees(90.0f);
            }
            moveLoc = worstEnemy.location.add(sideDir,
                    enemyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS - EPS);
            Debug.debug_dot(bot, moveLoc, 255, 127, 0);
        }
        // Try to move first before attacking
        final float enemyDistance2;
        if (bot.tryMove(moveLoc)) {
            enemyDistance2 = worstEnemy.location.distanceTo(bot.myLoc);
        } else {
            enemyDistance2 = enemyDistance;
        }
        // Now check amount of damage dealt
        if (bot.rc.canStrike() && enemyDistance2 <= bot.myType.bodyRadius + bot.myType.strideRadius
                + enemyRadius) {
            if (isNearest || getNetLumberjackHits(bot) >= 0) {
                bot.rc.strike();
                return true;
            }
        } else if (bot.rc.canStrike()) {
            // Consider the case where we are stuck and unable to get to worstEnemy.
            // We still might want to strike if netHits is positive.
            if (getNetLumberjackHits(bot) >= 1) {
                bot.rc.strike();
                return true;
            }
        }
        return false;
    }

    public static final int getNetLumberjackHits(final BotBase bot) {
        int netHits = 0;
        final RobotInfo[] robots = bot.rc
                .senseNearbyRobots(GameConstants.LUMBERJACK_STRIKE_RADIUS);
        for (final RobotInfo robot : robots) {
            if (robot.team == bot.myTeam) {
                netHits -= 1;
            } else if (robot.team == bot.enemyTeam) {
                netHits += 1;
            }
        }
        return netHits;
    }

    public static final MapLocation getNearestPriorityEnemy(final BotBase bot) throws GameActionException {
        final int numEnemies = Messaging.getPriorityEnemyRobots(bot.broadcastedPriorityEnemies, bot);
        MapLocation nearestEnemy = null;
        float nearestDist = 0;
        for (int i = 0; i < numEnemies; i++) {
            final MapLocation loc = bot.broadcastedPriorityEnemies[i];
            final float dist = (int) (bot.myLoc.distanceTo(loc) / 4); // smoothing
            if (nearestEnemy == null || dist < nearestDist) {
                nearestEnemy = loc;
                nearestDist = dist;
            }
        }
        return nearestEnemy;
    }

    public static final boolean headTowardsBroadcastedEnemy(final BotBase bot, final float reactionRange)
            throws GameActionException {
        // Head towards closest known broadcasted enemies
        // Assumes broadcasted priority enemies are handled already
        final int numEnemies = Messaging.getEnemyRobots(bot.broadcastedEnemies, bot);
        MapLocation nearestLoc = null;
        float minDistance = 0;
        for (int i = 0; i < numEnemies; i++) {
            final MapLocation enemyLoc = bot.broadcastedEnemies[i].location;
            final float distance = enemyLoc.distanceTo(bot.myLoc);
            if (nearestLoc == null || distance < minDistance) {
                nearestLoc = enemyLoc;
                minDistance = distance;
            }
        }
        if (nearestLoc != null && minDistance <= reactionRange) {
            bot.nav.setDestination(nearestLoc);
            if (!bot.tryMove(bot.nav.getNextLocation())) {
                bot.randomlyJitter();
            }
            return true;
        }
        return false;
    }

    public static void unseenDefense(final BotBase bot) throws GameActionException {
        if (!bot.rc.canFireSingleShot()) {
            return;
        }
        // Attack enemy that we might not be able to see
        final BulletInfo[] bullets = bot.rc.senseNearbyBullets();
        // Look at the furthest few bullets for any coming straight for us
        Direction chosenDir = null;
        int count = 0;
        for (int i = bullets.length - 1; i >= 0 && i >= bullets.length - 20; i--) {
            final BulletInfo bullet = bullets[i];
            final float bulletDistance = bot.myLoc.distanceTo(bullet.location);
            if (bulletDistance > bot.myType.sensorRadius - 1.0f && bot.willCollideWithMe(bullet)) {
                final Direction shootDir = bot.myLoc.directionTo(bullet.location);
                if (!willBulletCollideWithFriendlies(bot, shootDir, bulletDistance, 0f)
                        && !willBulletCollideWithTrees(bot, shootDir, bulletDistance, 0f)) {
                    chosenDir = shootDir;
                    count++;
                }
            }
        }
        if (chosenDir != null) {
            if (count > 2 && bot.rc.canFirePentadShot()) {
                bot.rc.firePentadShot(chosenDir);
                SprayCombat.debugPentad(bot, chosenDir);
                Debug.debug_line(bot, bot.myLoc, bot.myLoc.add(chosenDir, 7.0f), 255, 0, 255);
            } else if (count > 1 && bot.rc.canFireTriadShot()) {
                bot.rc.fireTriadShot(chosenDir);
                SprayCombat.debugTriad(bot, chosenDir);
                Debug.debug_line(bot, bot.myLoc, bot.myLoc.add(chosenDir, 7.0f), 255, 0, 255);
            } else {
                bot.rc.fireSingleShot(chosenDir);
                SprayCombat.debugSingle(bot, chosenDir);
                Debug.debug_line(bot, bot.myLoc, bot.myLoc.add(chosenDir, 7.0f), 255, 0, 255);
            }
        }
    }
}
