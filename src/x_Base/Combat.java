package x_Base;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotInfo;
import battlecode.common.RobotType;

public strictfp class Combat {

    public static final float DISTANCE_ATTACK_RANGE = 5.0f;
    public static final float SURROUND_RANGE = 1.0f;
    public static final float ENEMY_REACTION_RANGE = 10.0f;

    public static final MapLocation senseNearbyEnemies(final BotBase bot) throws GameActionException {
        final RobotInfo[] enemyRobots = bot.rc.senseNearbyRobots(-1, bot.enemyTeam);
        if (enemyRobots.length > 0) {
            for (final RobotInfo enemy : enemyRobots) {
                Messaging.broadcastEnemyRobot(bot, enemy);
            }
            return enemyRobots[0].location;
        }
        final int numEnemies = Messaging.getEnemyRobots(bot.broadcastedEnemies, bot);
        MapLocation nearestLoc = null;
        float minDistance = 0;
        for (int i = 0; i < numEnemies; i++) {
            final MapLocation enemyLoc = bot.broadcastedEnemies[i];
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
        RobotInfo nearestEnemy = enemies.length == 0 ? null : enemies[0];
        if (nearestEnemy != null) {
            final float minDistance = nearestEnemy.location.distanceTo(bot.myLoc) - nearestEnemy.getRadius()
                    - bot.myType.bodyRadius;
            final Direction enemyDir = bot.myLoc.directionTo(nearestEnemy.location);
            float moveDist = Math.min(minDistance - 0.01f, bot.myType.strideRadius);
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
                        if (perpDist <= friendly.getRadius() + 0.1f) { // will collide
                            willCollide = true;
                            break;
                        }
                    }
                }
                if (!willCollide) {
                    // Don't move faster than your own bullet or it will hit you
                    moveDist = Math.min(moveDist, bot.myType.bulletSpeed - 0.01f);
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
        return headTowardsBroadcastedEnemy(bot);
    }

    public static final boolean seekAndAttackAndSurroundEnemy(final x_Arc.BotArcBase bot) throws GameActionException {
        // See if enemy within sensor range
        final RobotInfo[] enemies = bot.rc.senseNearbyRobots(-1, bot.enemyTeam);
        for (final RobotInfo enemy : enemies) {
            Messaging.broadcastEnemyRobot(bot, enemy);
        }
        RobotInfo nearestEnemy = enemies.length == 0 ? null : enemies[0];
        if (nearestEnemy != null) {
            final float enemyRadius = nearestEnemy.getRadius();
            final Direction enemyDir = bot.myLoc.directionTo(nearestEnemy.location);

            final Direction backDir = bot.getArcLoc().directionTo(bot.getNextArcLoc());
            final Direction sideDir; // side dir depends on which side of enemy we are on
            if (backDir.radiansBetween(enemyDir) > 0) {
                sideDir = bot.arcDirection.opposite();
            } else {
                sideDir = bot.arcDirection;
            }
            final MapLocation moveLoc = nearestEnemy.location.add(sideDir,
                    enemyRadius + SURROUND_RANGE + bot.myType.bodyRadius - 0.01f);

            // move first before attacking
            bot.tryMove(moveLoc);
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
        // Else head towards closest known broadcasted enemies
        return headTowardsBroadcastedEnemy(bot);
    }

    public static final boolean stationaryAttackEnemy(final x_Arc.BotArcBase bot) throws GameActionException {
        // See if enemy within sensor range
        final RobotInfo[] enemies = bot.rc.senseNearbyRobots(-1, bot.enemyTeam);
        for (final RobotInfo enemy : enemies) {
            Messaging.broadcastEnemyRobot(bot, enemy);
        }
        RobotInfo nearestEnemy = enemies.length == 0 ? null : enemies[0];
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

    public static boolean strikeEnemiesFromBehind(final BotLumberjack bot) throws GameActionException {
        // See if enemy within sensor range
        final RobotInfo[] enemies = bot.rc.senseNearbyRobots(-1, bot.enemyTeam);
        RobotInfo nearestEnemy = enemies.length == 0 ? null : enemies[0];
        if (nearestEnemy != null) {
            final float enemyDistance = nearestEnemy.location.distanceTo(bot.myLoc);
            final RobotInfo[] friendlies = bot.rc.senseNearbyRobots(nearestEnemy.location,
                    bot.myType.bodyRadius + bot.myType.strideRadius, bot.myTeam);
            boolean isNearest = true;
            for (final RobotInfo friendly : friendlies) {
                if (friendly.type != RobotType.LUMBERJACK) {
                    continue;
                }
                if (nearestEnemy.location.distanceTo(friendly.location) < enemyDistance) {
                    isNearest = false;
                    break;
                }
            }
            final float enemyRadius = nearestEnemy.getRadius();
            final MapLocation moveLoc;
            final Direction backDir = bot.getArcLoc().directionTo(bot.getNextArcLoc());
            if (isNearest) {
                // If I'm nearest lumberjack, or if there's no other lumberjack already striking distance,
                // then I'm going to try to get exactly behind enemy to strike them
                moveLoc = nearestEnemy.location.add(backDir,
                        enemyRadius + bot.myType.bodyRadius + 0.01f);
            } else {
                // Otherwise, there's another lumberjack that can strike it. I will keep close, and
                // strike if enemy damage outweighs self damage
                final Direction enemyDir = nearestEnemy.location.directionTo(bot.myLoc);
                final Direction sideDir; // side dir depends on which side of enemy we are on
                if (backDir.radiansBetween(enemyDir) < 0) {
                    sideDir = bot.arcDirection.opposite();
                } else {
                    sideDir = bot.arcDirection;
                }
                moveLoc = nearestEnemy.location.add(sideDir,
                        enemyRadius + bot.myType.strideRadius + bot.myType.bodyRadius - 0.01f);
            }
            // Try to move first before attacking
            final float enemyDistance2;
            if (bot.tryMove(moveLoc)) {
                enemyDistance2 = nearestEnemy.location.distanceTo(bot.myLoc);
            } else {
                enemyDistance2 = enemyDistance;
            }
            // Now check amount of damage dealt
            if (bot.rc.canStrike() && enemyDistance2 <= bot.myType.bodyRadius + bot.myType.strideRadius
                    + enemyRadius) {
                int netHits = 0;
                if (!isNearest) {
                    final RobotInfo[] robots = bot.rc
                            .senseNearbyRobots(bot.myType.bodyRadius + bot.myType.strideRadius);
                    for (final RobotInfo robot : robots) {
                        if (robot.team == bot.myTeam) {
                            netHits -= 1;
                        } else if (robot.team == bot.enemyTeam) {
                            netHits += 1;
                        }
                    }
                }
                if (netHits >= 0) {
                    bot.rc.strike();
                }
            }
            return true;
        }
        // Else head towards closest known broadcasted enemies
        return headTowardsBroadcastedEnemy(bot);
    }

    public static final boolean headTowardsBroadcastedEnemy(final BotBase bot) throws GameActionException {
        // Head towards closest known broadcasted enemies
        final int numEnemies = Messaging.getEnemyRobots(bot.broadcastedEnemies, bot);
        MapLocation nearestLoc = null;
        float minDistance = 0;
        for (int i = 0; i < numEnemies; i++) {
            final MapLocation enemyLoc = bot.broadcastedEnemies[i];
            final float distance = enemyLoc.distanceTo(bot.myLoc);
            if (nearestLoc == null || distance < minDistance) {
                nearestLoc = enemyLoc;
                minDistance = distance;
            }
        }
        if (nearestLoc != null && minDistance <= ENEMY_REACTION_RANGE) {
            final Direction enemyDir = bot.myLoc.directionTo(nearestLoc);
            bot.tryMove(enemyDir);
            return true;
        }
        return false;
    }
}
