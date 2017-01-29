package x_Base;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotInfo;
import battlecode.common.RobotType;

public strictfp class SprayCombat {

    public static final float EPS = Combat.EPS;
    public static final float SPRAY_DISTANCE_RANGE = 6.0f;
    public static final float SCOUT_DISTANCE_RANGE = 4.0f;
    public static final float DRAW_RANGE = 7.0f;

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

            // TODO: Calculate direction based on centroid? Also, where to move along arc to maximize fire
            // Also, where to dodge

            // Attack first before retreating
            boolean shouldAttack = true;
            if (Combat.willBulletCollideWithFriendlies(bot, enemyDir, enemyDistance, enemyRadius)
                    || Combat.willBulletCollideWithTrees(bot, enemyDir, enemyDistance, enemyRadius)) {
                shouldAttack = false;
            } else if (worstEnemy.type == RobotType.SCOUT && minDistance > 3.0f) {
                shouldAttack = false;
            }
            if (shouldAttack) {
                // TODO: choose pentad/triad based on span
                Combat.attackSpecificEnemy(bot, worstEnemy);
            }

            // Retreat if too close depending on type. Assume robot still has a turn to move after I do.
            switch (worstEnemy.type) {
            case SOLDIER:
            case TANK:
            case LUMBERJACK: {
                if (edgeDistance < SPRAY_DISTANCE_RANGE + worstEnemy.type.strideRadius + EPS) {
                    final MapLocation moveLoc = worstEnemy.location.subtract(enemyDir,
                            SPRAY_DISTANCE_RANGE + worstEnemy.type.strideRadius + enemyRadius + EPS);
                    bot.tryMove(moveLoc);
                }
                break;
            }
            case ARCHON:
            case GARDENER: {
                final MapLocation moveLoc = worstEnemy.location;
                bot.tryMove(moveLoc);
                break;
            }
            case SCOUT: {
                if (edgeDistance < SCOUT_DISTANCE_RANGE + worstEnemy.type.strideRadius + EPS) {
                    final MapLocation moveLoc = worstEnemy.location.subtract(enemyDir,
                            SCOUT_DISTANCE_RANGE + worstEnemy.type.strideRadius + enemyRadius + EPS);
                    bot.tryMove(moveLoc);
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
}
