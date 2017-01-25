package x_Base;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.GameConstants;
import battlecode.common.MapLocation;
import battlecode.common.RobotInfo;
import battlecode.common.RobotType;
import battlecode.common.TreeInfo;
import x_Base.TangentBugNavigator.ObstacleInfo;

public strictfp class Combat {

    public static final float DISTANCE_ATTACK_RANGE = 5.0f;
    public static final float SURROUND_RANGE = 1.0f;
    public static final float ENEMY_REACTION_RANGE = 30.0f;
    public static final float HARRASS_RANGE = 3.0f; // range for harrassing
    public static final float EPS = 0.0001f;
    public static final float MAX_ROBOT_RADIUS = 2.0f;

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
        return headTowardsBroadcastedEnemy(bot);
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
                if (bot.rc.getRoundNum() < 1000 && bot.myType != RobotType.LUMBERJACK) {
                    // we don't want to attack archons that early since it is a waste of bullets
                    continue;
                } else {
                    typeScore = -100;
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

    public static final boolean willBulletCollideWithFriendlies(final BotBase bot, final Direction bulletDir,
            final float enemyDistance, final float enemyRadius)
            throws GameActionException {
        // Note: this needs to detect friendlies, so we can't just use willObjectCollideWithRobot2
        final RobotInfo[] friendlies = bot.rc.senseNearbyRobots(enemyDistance, bot.myTeam);
        return willObjectCollideWithRobots(bot, bulletDir, enemyDistance - enemyRadius, 0.0f, friendlies);
    }

    public static final boolean willObjectCollideWithRobots(final BotBase bot, final Direction objectDir,
            final float totalDistance, final float objectRadius, final RobotInfo[] robots) {
        // Note: trees array might have distance in sorted order from specified center location,
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
        return willObjectCollideWithTrees2(bot, bulletDir, enemyDistance - enemyRadius, 0.0f);
    }

    public static final boolean willObjectCollideWithTreeOrRobot(final BotBase bot, final Direction objectDir,
            final float totalDistance, final float objectRadius,
            final MapLocation targetLoc, final float targetRadius, final boolean isTargetRobot) {
        // System.out .println("willObjectCollide " + objectDir + " " + totalDistance + " " + targetLoc + " " +
        // targetRadius);
        // System.out.println("myLoc " + bot.myLoc);
        // System.out.println("goto " + bot.myLoc.add(objectDir, totalDistance));
        Debug.debug_dot(bot, bot.myLoc.add(objectDir, totalDistance), 0, 255, 0);
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

    public static final boolean willObjectCollideWithTrees(final BotBase bot, final Direction objectDir,
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

    public static final boolean willObjectCollideWithTrees2(final BotBase bot, final Direction objectDir,
            final float totalDistance, final float objectRadius) {
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
            final TreeInfo[] trees = bot.rc.senseNearbyTrees(centerLoc, senseRadius, null);
            if (Combat.willObjectCollideWithTrees(bot, objectDir, totalDistance, objectRadius, trees)) {
                return true;
            }
            centerDist += 2 * boxRadius;
        }
        return false;
    }

    public static final boolean willObjectCollideWithRobots2(final BotBase bot, final Direction objectDir,
            final float totalDistance, final float objectRadius) {
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
            final RobotInfo[] robots = bot.rc.senseNearbyRobots(centerLoc, senseRadius, null);
            if (Combat.willObjectCollideWithRobots(bot, objectDir, totalDistance, objectRadius, robots)) {
                return true;
            }
            centerDist += 2 * boxRadius;
        }
        return false;
    }

    public static final ObstacleInfo whichRobotOrTreeWillObjectCollideWith(final BotBase bot, final Direction objectDir,
            final float totalDistance, final float objectRadius) {
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
            final RobotInfo[] robots = bot.rc.senseNearbyRobots(centerLoc, senseRadius, null);
            final TreeInfo[] trees = bot.rc.senseNearbyTrees(centerLoc, senseRadius, null);
            // pick nearest by collision edge
            ObstacleInfo nearestObstacle = null;
            float nearestEdgeDist = 0f;
            for (final RobotInfo robot : robots) {
                final float robotRadius = robot.getRadius();
                if (willObjectCollideWithTreeOrRobot(bot, objectDir, totalDistance, objectRadius, robot.location,
                        robotRadius, /* isTargetRobot= */true)) {
                    final float edgeDist = currLoc.distanceTo(robot.location) - robotRadius;
                    if (nearestObstacle == null || edgeDist < nearestEdgeDist) {
                        nearestObstacle = new ObstacleInfo(robot.location, robotRadius, true, robot.ID);
                        nearestEdgeDist = edgeDist;
                    }
                }
            }
            for (final TreeInfo tree : trees) {
                final float treeRadius = tree.getRadius();
                if (willObjectCollideWithTreeOrRobot(bot, objectDir, totalDistance, objectRadius, tree.location,
                        treeRadius, /* isTargetRobot= */false)) {
                    final float edgeDist = currLoc.distanceTo(tree.location) - treeRadius;
                    if (nearestObstacle == null || edgeDist < nearestEdgeDist) {
                        nearestObstacle = new ObstacleInfo(tree.location, treeRadius, false, tree.ID);
                        nearestEdgeDist = edgeDist;
                    }
                }
            }
            if (nearestObstacle != null) {
                return nearestObstacle;
            }
            centerDist += 2 * boxRadius;
        }
        return null;
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
        return headTowardsBroadcastedEnemy(bot);
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

    public static boolean strikeEnemiesFromBehind(final BotLumberjack bot) throws GameActionException {
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
        return headTowardsBroadcastedEnemy(bot);
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
