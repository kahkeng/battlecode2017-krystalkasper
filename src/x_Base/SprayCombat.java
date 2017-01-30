package x_Base;

import battlecode.common.BulletInfo;
import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotInfo;
import battlecode.common.RobotType;

public strictfp class SprayCombat {

    public static final float EPS = Combat.EPS;
    public static final float SPRAY_DISTANCE_RANGE = 6.1f;
    public static final float SCOUT_DISTANCE_RANGE = 4.0f;
    public static final float DRAW_RANGE = 7.0f;
    public static final float[] DODGE_DELTAS = { 0.251f, 0.501f };

    public static final boolean sprayEnemy1(final BotBase bot) throws GameActionException {
        // TODO: include remembered enemies
        final RobotInfo[] enemies = bot.rc.senseNearbyRobots(-1, bot.enemyTeam);
        final RobotInfo worstEnemy = enemies.length == 0 ? null : Combat.prioritizedEnemy(bot, enemies);
        if (worstEnemy != null) {
            Messaging.broadcastEnemyRobot(bot, worstEnemy);
            final float enemyDistance = bot.myLoc.distanceTo(worstEnemy.location);
            final float enemyRadius = worstEnemy.getRadius();
            final float edgeDistance = enemyDistance - enemyRadius;
            final float minDistance = edgeDistance - bot.myType.bodyRadius;
            final Direction enemyDir = bot.myLoc.directionTo(worstEnemy.location);
            debugSpray(bot, worstEnemy.location);
            Debug.debug_print(bot,
                    "enemyDistance=" + enemyDistance + " edgeDistance=" + edgeDistance + " minDistance=" + minDistance);

            // TODO: Calculate direction based on centroid? Also, where to move along arc to maximize fire
            // Also, where to dodge

            final boolean isSafeDistance = edgeDistance >= SPRAY_DISTANCE_RANGE + worstEnemy.type.strideRadius - EPS;
            final float safeDistance = SPRAY_DISTANCE_RANGE + worstEnemy.type.strideRadius + enemyRadius + EPS;
            // Move first before attacking
            switch (worstEnemy.type) {
            case SCOUT:
            case ARCHON:
            case GARDENER: {
                final MapLocation moveLoc = worstEnemy.location;
                bot.tryMove(moveLoc);
                break;
            }
            case SOLDIER:
            case TANK:
            case LUMBERJACK: {
                if (isSafeDistance) {
                    if (StrategyFeature.COMBAT_DODGE1.enabled()) {
                        final Direction rotateDir = enemyDir
                                .rotateLeftRads((bot.myType.bodyRadius) / enemyDistance / 3.95f);
                        final MapLocation moveLoc = worstEnemy.location.subtract(rotateDir, safeDistance);
                        bot.tryMove(moveLoc);
                    } else if (StrategyFeature.COMBAT_DODGE2.enabled()) {
                        // TODO: incorporate bullet sensing
                        final MapLocation dodgeLoc = getDodgeLocation(bot, worstEnemy.location, safeDistance);
                        if (dodgeLoc != null) {
                            bot.tryMove(dodgeLoc);
                        }
                    }
                }
                break;
            }
            default:
                break;
            }

            // Attack first before retreating
            boolean shouldAttack = true;
            if (Combat.willBulletCollideWithFriendlies(bot, enemyDir, enemyDistance, enemyRadius)
                    || Combat.willBulletCollideWithTrees(bot, enemyDir, enemyDistance, enemyRadius)) {
                shouldAttack = false;
            } else if (worstEnemy.type == RobotType.SCOUT && minDistance > 3.0f) {
                shouldAttack = false;
            }
            if (shouldAttack) {
                // TODO: choose pentad/triad based on span, and also randomness for making hard dodging
                Combat.attackSpecificEnemy(bot, worstEnemy);
            }

            // Retreat if too close depending on type. Assume robot still has a turn to move after I do.
            switch (worstEnemy.type) {
            case SOLDIER:
            case TANK:
            case LUMBERJACK: {
                if (!isSafeDistance) {
                    final MapLocation moveLoc = worstEnemy.location.subtract(enemyDir, safeDistance);
                    bot.tryMove(moveLoc);
                    // TODO: incorporate dodging
                }
                break;
            }
            default:
                break;
            }

            return true;
        }
        if (!StrategyFeature.COMBAT_SNIPE_BASES.enabled()) {
            // Else head towards closest known broadcasted enemies
            return Combat.headTowardsBroadcastedEnemy(bot, 100.0f);
        } else {
            return false;
        }
    }

    public final static MapLocation getDodgeLocation(final BotBase bot, final MapLocation enemyLoc,
            final float safeDistance) {
        final float enemyDistance = bot.myLoc.distanceTo(enemyLoc);
        final Direction enemyDir = bot.myLoc.directionTo(enemyLoc);
        final BulletInfo[] bullets = bot.rc.senseNearbyBullets(bot.myLoc.add(enemyDir, enemyDistance / 2),
                enemyDistance / 2);
        MapLocation bestLoc = null;
        float bestScore = 0;
        final float hopDistance = bot.myType.bodyRadius / 4 + EPS;
        final int size = 2;
        final MapLocation[] candidateLocs = new MapLocation[size];
        final float[] scores = new float[size];

        // Add the tangent left/right spots
        final float sine = hopDistance / 2 / enemyDistance;
        final float theta = (float) (Math.asin(sine) * 2);
        candidateLocs[0] = enemyLoc.subtract(enemyDir.rotateLeftRads(theta), safeDistance);
        candidateLocs[1] = enemyLoc.subtract(enemyDir.rotateRightRads(theta), safeDistance);
        for (final BulletInfo bullet : bullets) {
            System.out.println(Clock.getBytecodesLeft());
            if (Clock.getBytecodesLeft() < 2000) {
                break;
            }
            for (int i = 0; i < size; i++) {
                final MapLocation candidateLoc = candidateLocs[i];
                float score = 0;
                final float damage = bullet.damage;
                // t=1
                final MapLocation loc1 = bullet.location.add(bullet.dir, bullet.speed);
                final float dist1 = candidateLoc.distanceTo(loc1);
                final float escapeDist1 = bot.myType.bodyRadius - dist1;
                if (escapeDist1 > 0) {
                    score += damage;
                }
                // t=2
                final MapLocation loc2 = loc1.add(bullet.dir, bullet.speed);
                final float dist2 = candidateLoc.distanceTo(loc1);
                final float escapeDist2 = bot.myType.bodyRadius - dist2;
                if (escapeDist2 > hopDistance) { // need 2 hops to escape
                    score += damage;
                } else if (escapeDist2 > 0) { // need 1 hop to escape
                    score += damage / 2;
                } else if (escapeDist2 > -hopDistance) { // will affect flexibility of future hops
                    score += damage / 4;
                }
                scores[i] += score;
            }
        }
        for (int i = 0; i < size; i++) {
            final float score = scores[i];
            if (bestLoc == null || score < bestScore) {
                bestLoc = candidateLocs[i];
                bestScore = score;
            }
        }
        return bestLoc;
    }

    public static final void debugSpray(final BotBase bot, final MapLocation enemyLoc) {
        final Direction enemyDir = bot.myLoc.directionTo(enemyLoc);
        Debug.debug_line(bot, bot.myLoc, bot.myLoc.add(enemyDir.rotateLeftRads(Combat.PENTAD_RADIANS), DRAW_RANGE), 255,
                128, 0);
        Debug.debug_line(bot, bot.myLoc, bot.myLoc.add(enemyDir.rotateRightRads(Combat.PENTAD_RADIANS), DRAW_RANGE),
                255, 128, 0);
        Debug.debug_line(bot, bot.myLoc, bot.myLoc.add(enemyDir.rotateLeftRads(Combat.TRIAD_RADIANS), DRAW_RANGE), 255,
                255, 0);
        Debug.debug_line(bot, bot.myLoc, bot.myLoc.add(enemyDir.rotateRightRads(Combat.TRIAD_RADIANS), DRAW_RANGE), 255,
                255, 0);

    }

    public static final void debugPentad(final BotBase bot, final Direction enemyDir) {
        Debug.debug_dot(bot, bot.myLoc.add(enemyDir.rotateLeftRads(Combat.PENTAD_RADIANS), DRAW_RANGE), 255,
                128, 0);
        Debug.debug_dot(bot, bot.myLoc.add(enemyDir.rotateRightRads(Combat.PENTAD_RADIANS), DRAW_RANGE), 255,
                128, 0);
    }

    public static final void debugTriad(final BotBase bot, final Direction enemyDir) {
        Debug.debug_dot(bot, bot.myLoc.add(enemyDir.rotateLeftRads(Combat.TRIAD_RADIANS), DRAW_RANGE), 255,
                128, 0);
        Debug.debug_dot(bot, bot.myLoc.add(enemyDir.rotateRightRads(Combat.TRIAD_RADIANS), DRAW_RANGE), 255,
                128, 0);
    }

    public static final void debugSingle(final BotBase bot, final Direction enemyDir) {
        Debug.debug_dot(bot, bot.myLoc.add(enemyDir, DRAW_RANGE), 255,
                128, 0);
    }

}
