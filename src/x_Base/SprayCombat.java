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
    public static final float ENGAGE_DISTANCE_RANGE = 9.0f; // we will engage enemies starting from this far away
    public static final float SCOUT_DISTANCE_RANGE = 4.0f;
    public static final float DRAW_RANGE = 7.0f;
    public static final float[] DODGE_DELTAS = { 0.251f, 0.501f };
    public static final int MAX_DODGE_CANDIDATES = 50;
    public static int numCandidates = 0;
    public static final MapLocation[] dodgeCandidateLocs = new MapLocation[MAX_DODGE_CANDIDATES + 1];
    public static final float[] dodgeCandidateScores = new float[MAX_DODGE_CANDIDATES + 1];
    public static final RobotInfo[] engageDistanceEnemies = new RobotInfo[Messaging.MAX_ENEMY_ROBOTS + 1]; // temp
                                                                                                           // storage
    public static RobotInfo[] lastSensedEnemies = null; // enemies sensed in last round

    public static final boolean sprayEnemy1(final BotBase bot) throws GameActionException {
        // TODO: include remembered enemies
        final RobotInfo[] allEnemies;
        if (StrategyFeature.COMBAT_BROADCAST.enabled()) {
            // include broadcasted enemies that are close
            final RobotInfo[] sensedEnemies;
            if (StrategyFeature.COMBAT_LAST_SENSED.enabled()) {
                final RobotInfo[] sensedEnemies0 = bot.rc.senseNearbyRobots(-1, bot.enemyTeam);
                sensedEnemies = sensedEnemies0.length == 0 ? lastSensedEnemies : sensedEnemies0;
            } else {
                sensedEnemies = bot.rc.senseNearbyRobots(-1, bot.enemyTeam);
            }
            final int numEnemies = Messaging.getEnemyRobots(bot.broadcastedEnemies, bot);
            int numEngageEnemies = 0;
            for (int i = 0; i < numEnemies; i++) {
                final RobotInfo enemy = bot.broadcastedEnemies[i];
                if (enemy.location.distanceTo(bot.myLoc) <= ENGAGE_DISTANCE_RANGE) {
                    engageDistanceEnemies[numEngageEnemies++] = enemy;
                }
            }
            allEnemies = new RobotInfo[sensedEnemies.length + numEngageEnemies];
            int count = 0;
            for (int i = 0; i < sensedEnemies.length; i++) {
                allEnemies[count++] = sensedEnemies[i];
            }
            for (int i = 0; i < numEngageEnemies; i++) {
                allEnemies[count++] = engageDistanceEnemies[i];
            }
        } else {
            allEnemies = bot.rc.senseNearbyRobots(-1, bot.enemyTeam);
        }
        final RobotInfo worstEnemy = allEnemies.length == 0 ? null : Combat.prioritizedEnemy(bot, allEnemies);
        if (worstEnemy != null) {
            if (worstEnemy.attackCount != Messaging.BROADCASTED_ROBOT_SENTINEL) {
                Messaging.broadcastEnemyRobot(bot, worstEnemy);
            }
            spraySpecificEnemy(bot, worstEnemy, allEnemies);
            return true;
        }
        return false;
    }

    public final static void getEnemySpan(final BotBase bot, final RobotInfo worstEnemy, final RobotInfo[] allEnemies) {
    }

    public final static void spraySpecificEnemy(final BotBase bot, final RobotInfo worstEnemy,
            final RobotInfo[] allEnemies)
            throws GameActionException {
        for (int i = 0; i < allEnemies.length; i++) {
            Debug.debug_dot(bot, allEnemies[i].location, 255, 0, 0);
        }
        final float enemyDistance = bot.myLoc.distanceTo(worstEnemy.location);
        final float enemyRadius = worstEnemy.getRadius();
        final float edgeDistance = enemyDistance - enemyRadius;
        final float minDistance = edgeDistance - bot.myType.bodyRadius;
        final Direction enemyDir = bot.myLoc.directionTo(worstEnemy.location);
        debugSpray(bot, worstEnemy.location);
        // Debug.debug_print(bot, "enemyDistance=" + enemyDistance + " edgeDistance=" + edgeDistance + "
        // minDistance=" + minDistance);

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
            if (!Combat.attackSpecificEnemy(bot, worstEnemy)) {
                // didn't manage to attack worst enemy. pick any other enemy to attack
                Combat.attackAnyOtherEnemy(bot, worstEnemy, allEnemies);
            }
        }

        // Retreat if too close depending on type. Assume robot still has a turn to move after I do.
        switch (worstEnemy.type) {
        case SOLDIER:
        case TANK:
        case LUMBERJACK: {
            if (!isSafeDistance) {
                final MapLocation moveLoc = worstEnemy.location.subtract(enemyDir, safeDistance);
                bot.tryMove(moveLoc);
                // TODO: what about dodging? adding that seems to hurt a lot
            }
            break;
        }
        default:
            break;
        }
    }

    public final static void addDodgeCandidateLoc(final BotBase bot, final MapLocation loc) {
        if (numCandidates >= MAX_DODGE_CANDIDATES) {
            return;
        }
        if (bot.canMove(loc)) {
            dodgeCandidateLocs[numCandidates] = loc;
            dodgeCandidateScores[numCandidates] = 0;
            numCandidates++;
        }
    }

    public final static MapLocation getDodgeLocation(final BotBase bot, final MapLocation enemyLoc,
            final float safeDistance) {
        if (bot.myType == RobotType.TANK) {
            return null;
        }
        final float enemyDistance = bot.myLoc.distanceTo(enemyLoc);
        final Direction enemyDir = bot.myLoc.directionTo(enemyLoc);
        final BulletInfo[] bullets = bot.rc.senseNearbyBullets(bot.myLoc.add(enemyDir, enemyDistance / 2),
                enemyDistance / 2);
        if (bullets.length == 0) {
            return null;
        }
        // final float hopDistance = bot.myType.bodyRadius / 4 + 0.007f + EPS;

        // Add the tangent left/right spots
        // final float sine = hopDistance / 2 / enemyDistance;
        // final float theta = (float) (Math.asin(sine) * 2);
        numCandidates = 0;
        addDodgeCandidateLoc(bot, bot.myLoc);
        /*
         * dodgeCandidateLocs[size++] = enemyLoc.subtract(enemyDir.rotateLeftRads(theta), safeDistance);
         * dodgeCandidateLocs[size++] = enemyLoc.subtract(enemyDir.rotateRightRads(theta), safeDistance); final
         * MapLocation safeLoc = enemyLoc.subtract(enemyDir, safeDistance); if (!bot.myLoc.equals(safeLoc)) {
         * dodgeCandidateLocs[size++] = safeLoc; }
         */

        // final float hopDistance2 = bot.myType.bodyRadius / 4 + 0.007f + EPS;
        final float strideRadius = bot.myType.strideRadius;
        final float sine2 = strideRadius / 2 / enemyDistance;
        final float theta2 = (float) (Math.asin(sine2) * 2);

        addDodgeCandidateLoc(bot, enemyLoc.subtract(enemyDir.rotateLeftRads(theta2), safeDistance));
        addDodgeCandidateLoc(bot, enemyLoc.subtract(enemyDir.rotateRightRads(theta2), safeDistance));

        addDodgeCandidateLoc(bot, enemyLoc.subtract(enemyDir.rotateLeftRads(theta2 * 0.66f), safeDistance));
        addDodgeCandidateLoc(bot, enemyLoc.subtract(enemyDir.rotateRightRads(theta2 * 0.66f), safeDistance));

        if (Math.abs(safeDistance - enemyDistance) > EPS) {
            addDodgeCandidateLoc(bot, enemyLoc.subtract(enemyDir.rotateLeftRads(theta2), enemyDistance));
            addDodgeCandidateLoc(bot, enemyLoc.subtract(enemyDir.rotateRightRads(theta2), enemyDistance));
        }
        // addDodgeCandidateLoc(bot, bot.myLoc.subtract(enemyDir, strideRadius));
        // addDodgeCandidateLoc(bot, bot.myLoc.subtract(enemyDir.rotateLeftDegrees(30.0f), strideRadius));
        // addDodgeCandidateLoc(bot, bot.myLoc.subtract(enemyDir.rotateRightDegrees(30.0f), strideRadius));
        // addDodgeCandidateLoc(bot, bot.myLoc.add(enemyDir.rotateLeftDegrees(30.0f), strideRadius));
        // addDodgeCandidateLoc(bot, bot.myLoc.add(enemyDir.rotateRightDegrees(30.0f), strideRadius));
        // addDodgeCandidateLoc(bot, bot.myLoc.subtract(enemyDir.rotateLeftDegrees(60.0f), strideRadius));
        // addDodgeCandidateLoc(bot, bot.myLoc.subtract(enemyDir.rotateRightDegrees(60.0f), strideRadius));
        // addDodgeCandidateLoc(bot, bot.myLoc.add(enemyDir.rotateLeftDegrees(60.0f), strideRadius));
        // addDodgeCandidateLoc(bot, bot.myLoc.add(enemyDir.rotateRightDegrees(60.0f), strideRadius));
        addDodgeCandidateLoc(bot, bot.myLoc.subtract(enemyDir, strideRadius * 0.5f));
        addDodgeCandidateLoc(bot, bot.myLoc.subtract(enemyDir.rotateLeftDegrees(30.0f), strideRadius * 0.5f));
        addDodgeCandidateLoc(bot, bot.myLoc.subtract(enemyDir.rotateRightDegrees(30.0f), strideRadius * 0.5f));
        // addDodgeCandidateLoc(bot, bot.myLoc.add(enemyDir.rotateLeftDegrees(30.0f), strideRadius * 0.5f));
        // addDodgeCandidateLoc(bot, bot.myLoc.add(enemyDir.rotateRightDegrees(30.0f), strideRadius * 0.5f));
        addDodgeCandidateLoc(bot, bot.myLoc.subtract(enemyDir.rotateLeftDegrees(60.0f), strideRadius * 0.5f));
        addDodgeCandidateLoc(bot, bot.myLoc.subtract(enemyDir.rotateRightDegrees(60.0f), strideRadius * 0.5f));
        // addDodgeCandidateLoc(bot, bot.myLoc.add(enemyDir.rotateLeftDegrees(60.0f), strideRadius * 0.5f));
        // addDodgeCandidateLoc(bot, bot.myLoc.add(enemyDir.rotateRightDegrees(60.0f), strideRadius * 0.5f));

        final int size = numCandidates;
        outer: for (final BulletInfo bullet : bullets) {
            // System.out.println(Clock.getBytecodesLeft());
            for (int i = 0; i < size; i++) {
                if (Clock.getBytecodesLeft() < 2000) {
                    break outer;
                }
                final MapLocation candidateLoc = dodgeCandidateLocs[i];
                float score = 0;
                final float damage = bullet.damage;
                final float bulletDist = bullet.location.distanceTo(candidateLoc);
                final float alpha = bullet.location.directionTo(candidateLoc).radiansBetween(bullet.dir);
                final float tangentDist = Math.abs((float) (Math.sin(alpha) * bulletDist));
                final float cosine = (float) Math.cos(alpha);
                if (cosine < 0) {
                    continue;
                }
                final int roundsToHit = (int) ((cosine * bulletDist - EPS) / bullet.speed);
                final float escapeDist = bot.myType.bodyRadius - tangentDist;
                switch (roundsToHit) {
                case 0: {
                    if (escapeDist >= 0) {
                        score += damage;
                    }
                    break;
                }
                case 1: {
                    if (escapeDist >= strideRadius) {
                        score += damage;
                    } else if (escapeDist >= 0) {
                        score += (damage / 2) * (escapeDist / strideRadius);
                    }
                    break;
                }
                case 2: {
                    score += (damage / 4) * (escapeDist / (strideRadius * 2));
                    break;
                }
                case 3: {
                    score += (damage / 8) * (escapeDist / (strideRadius * 3));
                    break;
                }

                default:
                    break;
                }
                // System.out.println("rounds " + roundsToHit + " " + tangentDist);
                dodgeCandidateScores[i] += score;
            }
        }
        MapLocation bestLoc = null;
        float bestScore = 0;
        for (int i = 0; i < size; i++) {
            final float score = dodgeCandidateScores[i];
            // System.out.println("cand " + i + " " + score);
            if (bestLoc == null || score < bestScore) {
                bestLoc = dodgeCandidateLocs[i];
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
