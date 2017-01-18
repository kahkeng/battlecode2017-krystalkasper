package x_Base;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.GameConstants;
import battlecode.common.MapLocation;
import battlecode.common.RobotInfo;
import battlecode.common.RobotType;
import battlecode.common.TreeInfo;

public strictfp class Combat {

    public static final float DISTANCE_ATTACK_RANGE = 5.0f;
    public static final float SURROUND_RANGE = 1.0f;
    public static final float ENEMY_REACTION_RANGE = 30.0f;
    public static final float HARRASS_RANGE = 3.0f; // range for harrassing

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

    public static final RobotInfo prioritizedEnemy(final BotBase bot, final RobotInfo[] enemies) {
        // Choose units in lethal order
        RobotInfo worstEnemy = null;
        float worstScore = 0;
        for (final RobotInfo enemy : enemies) {
            final float typeScore;
            switch (enemy.type) {
            case SOLDIER:
            case TANK:
                typeScore = 200;
                break;
            case LUMBERJACK:
            case SCOUT:
                typeScore = 150;
                break;
            case GARDENER:
                typeScore = 100;
                break;
            case ARCHON:
                if (bot.numInitialArchons > 1) {
                    continue; // don't count archon as enemy unless it is the only one, to ensure patrol
                } else {
                    typeScore = 1;
                    break;
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

    public static final boolean willCollideWithFriendly(final BotBase bot, final Direction bulletDir,
            final float enemyDistance)
            throws GameActionException {
        final RobotInfo[] friendlies = bot.rc.senseNearbyRobots(enemyDistance, bot.myTeam);
        for (final RobotInfo friendly : friendlies) {
            final float diffRad = bulletDir.radiansBetween(bot.myLoc.directionTo(friendly.location));
            if (Math.abs(diffRad) <= Math.PI / 2) {
                // TODO: make this have some buffer or account for trajectory
                final float friendlyDist = bot.myLoc.distanceTo(friendly.location);
                if (Math.abs(Math.sin(diffRad) * friendlyDist) <= friendly.getRadius() + 0.1f) {
                    final float distForward = (float) Math.cos(diffRad) * friendlyDist;
                    if (distForward >= 0 && distForward <= enemyDistance) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static final boolean willCollideWithTree(final BotBase bot, final Direction bulletDir,
            final float enemyDistance) {
        final TreeInfo[] trees = bot.rc.senseNearbyTrees(enemyDistance);
        for (final TreeInfo tree : trees) {
            final float diffRad = bulletDir.radiansBetween(bot.myLoc.directionTo(tree.location));
            if (Math.abs(diffRad) <= Math.PI / 2) {
                // TODO: make this have some buffer or account for trajectory
                final float treeDist = bot.myLoc.distanceTo(tree.location);
                if (Math.abs(Math.sin(diffRad) * treeDist) <= tree.getRadius() + 0.1f) {
                    final float distForward = (float) Math.cos(diffRad) * treeDist;
                    if (distForward >= 0 && distForward <= enemyDistance) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static final boolean harrassEnemy(final x_Arc.BotArcBase bot) throws GameActionException {
        final RobotInfo[] enemies = bot.rc.senseNearbyRobots(-1, bot.enemyTeam);
        RobotInfo worstEnemy = enemies.length == 0 ? null : prioritizedEnemy(bot, enemies);
        if (worstEnemy != null) {
            Messaging.broadcastEnemyRobot(bot, worstEnemy);
            Debug.debug_dot(bot, worstEnemy.location, 0, 0, 0);
            final float enemyRadius = worstEnemy.getRadius();
            final float enemyDistance = worstEnemy.location.distanceTo(bot.myLoc);
            final float minDistance = enemyDistance - enemyRadius - bot.myType.bodyRadius;
            final Direction enemyDir = bot.myLoc.directionTo(worstEnemy.location);
            final Direction rotateDir;
            final boolean rotated;
            if (minDistance <= HARRASS_RANGE + 1.0f && (willCollideWithFriendly(bot, enemyDir, enemyDistance)
                    || willCollideWithTree(bot, enemyDir, enemyDistance))) {
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
            case ARCHON:
            default:
                if (enemyDistance <= HARRASS_RANGE + 1.0f) {
                    moveDist = bot.myType.bulletSpeed;
                } else {
                    moveDist = bot.myType.strideRadius;
                }
                harrassRange = 0.01f;
                break;
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
                if (!willCollideWithFriendly(bot, latestEnemyDir, latestEnemyDistance)
                        && !willCollideWithTree(bot, latestEnemyDir, latestEnemyDistance)) {
                    distanceAttack = true;
                }
            }
            if (bot.rc.canFireSingleShot() && (distanceAttack || minDistance < bot.myType.bodyRadius)) {
                bot.rc.fireSingleShot(latestEnemyDir);
            }
            return true;
        }
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
                        enemyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS - 0.01f);
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
