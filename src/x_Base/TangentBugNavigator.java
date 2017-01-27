package x_Base;

import java.util.HashSet;
import java.util.Set;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.RobotType;
import battlecode.common.TreeInfo;

public strictfp class TangentBugNavigator {

    private static final boolean DEBUG = true;
    private static final boolean DEBUG_WALLS = false;
    private static final int MAX_WALLS = 100;
    private static final float EPS = 0.04f; // buffer to add to not always exactly touch edges
    private static final float LEAVE_THRESHOLD = 0.0f; // distance improvement before we leave obstacle
    public static final float EFFECTIVE_DISTANCE = 5.0f;

    boolean preferRight;

    static enum State {
        TOWARD_GOAL, FOLLOW_OBSTACLE,
    }

    final BotBase bot;
    final RobotController rc;
    MapLocation destination;
    State state;
    MapLocation lastObstacleLoc; // location of last obstacle met
    MapLocation lastMetObstacleLoc; // location where last met obstacle, not obstacle itself
    float lastMetObstacleDist; // distance from lastMetObstacleLoc to destLoc
    FollowWallPoint followWallPoint = null; // point where we are following wall
    final FollowWallPoint[] pointsList = new FollowWallPoint[MAX_WALLS + 1];
    final ObstacleInfo[] sensedObstacles = new ObstacleInfo[MAX_WALLS + 1];
    int pointsSize = 0;

    public TangentBugNavigator(final BotBase bot) {
        this.bot = bot;
        this.rc = bot.rc;
        preferRight = true;
        reset();
    }

    public void setDestination(MapLocation destination) {
        if (this.destination == null || !this.destination.equals(destination)) {
            this.destination = destination;
            reset();
        }
    }

    public void reset() {
        lastMetObstacleLoc = null;
        lastMetObstacleDist = 0;
        followWallPoint = null;
        state = State.TOWARD_GOAL;
    }

    private final ObstacleInfo hasObstacleToward(final MapLocation currLoc, final MapLocation destLoc,
            final float maxDist) {
        final float destDistance = Math.min(currLoc.distanceTo(destLoc), maxDist);
        final ObstacleInfo obstacle = Combat.whichRobotOrTreeWillObjectCollideWith(bot, currLoc.directionTo(destLoc),
                destDistance, bot.myType.bodyRadius);
        // If our destination is inside the returned obstacle, then don't count it as one
        if (obstacle != null && destLoc.distanceTo(obstacle.location) <= obstacle.radius) {
            return null;
        }
        return obstacle;
    }

    public MapLocation getNextLocation() {
        final MapLocation currLoc = rc.getLocation();
        final MapLocation destLoc = destination;
        Debug.debug_line(bot, currLoc, destLoc, 255, 255, 255);
        final float destDist = currLoc.distanceTo(destLoc);
        if (destDist < bot.myType.strideRadius) {
            return destLoc;
        } else if (bot.myType != RobotType.GARDENER && bot.myType != RobotType.SCOUT
                && bot.myType != RobotType.LUMBERJACK && destDist < EFFECTIVE_DISTANCE
                && bot.rc.senseNearbyTrees(-1, bot.myTeam).length > 0) {
            // Soldiers and tanks are not effective using bug if too close to target
            return currLoc.add(currLoc.directionTo(destLoc), bot.myType.strideRadius);
        }

        // Bug 2 algorithm: https://www.cs.cmu.edu/~motionplanning/lecture/Chap2-Bug-Alg_howie.pdf
        // but missing some stuff about m-line checks, but seems to work
        for (;;) {
            // Maximum sensing range. This should reduce if we are close to destination or if we have ever been close
            float maxDist = Math.min(bot.myType.sensorRadius, currLoc.distanceTo(destLoc));
            // maxDist = Math.max(2, maxDist);

            switch (state) {
            case TOWARD_GOAL: {
                if (followWallPoint != null) {
                    throw new RuntimeException("followWallPoint should be null: " + followWallPoint);
                }
                final ObstacleInfo obstacleInfo = hasObstacleToward(currLoc, destLoc, maxDist);
                if (obstacleInfo != null) {
                    state = State.FOLLOW_OBSTACLE;
                    followWallPoint = new FollowWallPoint(destination,
                            new ObstacleInfo(currLoc, bot.myType.bodyRadius, true, 0),
                            obstacleInfo, preferRight, true);
                    lastObstacleLoc = obstacleInfo.location;
                    lastMetObstacleLoc = obstacleInfo.location.add(obstacleInfo.location.directionTo(currLoc),
                            obstacleInfo.radius + bot.myType.bodyRadius);
                    lastMetObstacleDist = lastMetObstacleLoc.distanceTo(destLoc);
                    if (DEBUG) {
                        // Debug.debug_print(bot, "met obstacle " + lastObstacleLoc + " followWallPoint=" +
                        // followWallPoint);
                    }
                    continue;
                }
                if (DEBUG) {
                    // Debug.debug_print(bot, "returning1 " + destLoc);
                }
                return destLoc;
            }
            case FOLLOW_OBSTACLE: {
                if (followWallPoint == null) {
                    throw new RuntimeException("followWallPoint should not be null");
                }
                // TODO: should we check that we reached the m-line first?
                // TODO: also check that way to goal is unimpeded?
                if (destDist < Math.max(lastMetObstacleDist - LEAVE_THRESHOLD, EPS)) {
                    if (DEBUG) {
                        // Debug.debug_print(bot, "leaving obstacle at " + currLoc + " followWallPoint=" +
                        // followWallPoint);
                    }
                    state = State.TOWARD_GOAL;
                    followWallPoint = null;
                    continue;
                }
                // Check if followWallPoint is still valid, esp if obstacle has moved away
                if (!followWallPoint.isValid(this)) {
                    Debug.debug_print(bot, "obstacle moved");
                    reset();
                    continue;
                }

                computeSubsequentWallPoints(followWallPoint, maxDist);
                if (DEBUG) {
                    /*
                     * for (int i = 0; i < pointsSize; i++) { Debug.debug_print(bot, "  wp=" + pointsList[i]); }
                     */
                }

                // Search through wall points in reverse order to find first one that has a greedy accurate path
                // to get to the edge location of the wall
                // TODO: do we need to go step by step in case we overshoot the goal?
                if (DEBUG_WALLS)
                    Debug.debug_dot(bot, followWallPoint.obstacle.location, 0, 255, 0); // start: green
                for (int i = pointsSize - 1; i >= 0; i--) {
                    if (Clock.getBytecodesLeft() < 3000) {
                        i = 0;
                    }
                    final FollowWallPoint candidate = pointsList[i];
                    final MapLocation edgeLoc = candidate.getEdgeLoc(currLoc, this);
                    if (DEBUG_WALLS)
                        Debug.debug_line(bot, candidate.obstacle.location, edgeLoc, 0, 0, 255);
                    // System.out.println(i + "/" + pointsSize + " " + Clock.getBytecodesLeft() + " " + candidate + " "
                    // + edgeLoc);
                    boolean isClear = canPathTowardsLocation(edgeLoc);
                    // if (DEBUG) {
                    // Debug.debug_print(bot, " candidate wp " + candidate + " isClear=" + isClear + " edgeLoc=" +
                    // edgeLoc);
                    // }
                    if (isClear) {
                        // if (DEBUG) {
                        // Debug.debug_print(bot, " changing wp from " + followWallPoint + " to " + candidate);
                        // }
                        followWallPoint = candidate;
                        break;
                    }
                    if (Clock.getBytecodesLeft() < 3000 && i > 1) {
                        i = 1;
                    }
                }
                final MapLocation edgeLoc = followWallPoint.getEdgeLoc(currLoc, this);
                // Check if one stride towards edgeLoc is off map, if so, we reverse direction
                if (bot.mapEdges.isOffMap(bot.myLoc.add(bot.myLoc.directionTo(edgeLoc), bot.myType.strideRadius))) {
                    Debug.debug_print(bot, "reversing");
                    preferRight = !preferRight;
                    reset();
                    continue;
                }
                if (DEBUG_WALLS) {
                    Debug.debug_dot(bot, followWallPoint.obstacle.location, 255, 0, 0); // end: red
                    Debug.debug_dot(bot, edgeLoc, 255, 255, 0);
                }
                // if (DEBUG) {
                // Debug.debug_print(bot, "returning2 " + edgeLoc + " followWallPoint=" + followWallPoint
                // + " preferRight=" + preferRight);
                // }
                return edgeLoc;
            }
            }
            break;
        }
        return null;

    }

    /**
     * Determine if we can greedily navigate towards destLoc, inclusive of whether destLoc is traversable. We need to be
     * within sensing range of destLoc for this to work.
     */
    private final boolean canPathTowardsLocation(final MapLocation destLoc) {
        final MapLocation currLoc = bot.myLoc;
        final float destDistance = currLoc.distanceTo(destLoc);
        if (destDistance > bot.myType.sensorRadius - bot.myType.bodyRadius) {
            return false;
        }
        if (destDistance <= bot.myType.strideRadius) {
            return rc.canMove(destLoc);
        }
        final Direction destDir = currLoc.directionTo(destLoc);
        if (Combat.willObjectCollideWithRobots2(bot, destDir, destDistance, bot.myType.bodyRadius)) {
            // System.out.println("will collide with robot");
            return false;
        }
        if (Combat.willObjectCollideWithTrees2(bot, destDir, destDistance, bot.myType.bodyRadius)) {
            // System.out.println("will collide with tree");
            return false;
        }
        return true;
    }

    private final int getObstaclesAroundObstacle(final ObstacleInfo obstacle, final float senseRadius,
            final ObstacleInfo[] obstacles) {
        int size = 0;
        final RobotInfo[] robots = rc.senseNearbyRobots(obstacle.location, senseRadius, null);
        final TreeInfo[] trees;
        switch (bot.myType) {
        case SCOUT: // scouts aren't blocked by trees
            trees = new TreeInfo[0];
            break;
        case LUMBERJACK: // lumberjacks should ignore neutral/enemy trees
            trees = rc.senseNearbyTrees(obstacle.location, senseRadius, bot.myTeam);
            break;
        default:
            trees = rc.senseNearbyTrees(obstacle.location, senseRadius, null);
            break;
        }
        for (final RobotInfo robot : robots) {
            if (robot.location.equals(obstacle.location)) {
                continue;
            }
            obstacles[size++] = new ObstacleInfo(robot.location, robot.getRadius(), true, robot.ID);
        }
        for (final TreeInfo tree : trees) {
            if (tree.location.equals(obstacle.location)) {
                continue;
            }
            obstacles[size++] = new ObstacleInfo(tree.location, tree.radius, false, tree.ID);
        }
        return size;
    }

    /**
     * Return list of all wall points in connected order that are within maxDist away.
     */
    private final void computeSubsequentWallPoints(final FollowWallPoint startPoint, final float maxDist) {
        pointsSize = 0;

        if (DEBUG) {
            // Debug.debug_print(bot, " getting wp start=" + startPoint + " maxDist=" + maxDist);
        }
        final MapLocation currLoc = bot.myLoc;

        final Set<FollowWallPoint> seen = new HashSet<FollowWallPoint>();
        seen.add(startPoint);
        ObstacleInfo currObstacle = startPoint.obstacle;
        Direction obstacleDir = currLoc.directionTo(currObstacle.location);
        boolean found = true;
        boolean madeLoop = false;
        outer: while (found
                && (Clock.getBytecodesLeft() >= 5000 || currLoc.distanceTo(currObstacle.location) < currObstacle.radius
                        + bot.myType.bodyRadius + bot.myType.strideRadius)) {
            found = false;
            final float senseRadius = currObstacle.radius + bot.myType.bodyRadius * 2 + EPS;
            if (DEBUG_WALLS)
                Debug.debug_line(bot, currObstacle.location,
                        currObstacle.location.add(currLoc.directionTo(currObstacle.location), senseRadius), 255, 0,
                        255);
            ObstacleInfo nextObstacle = null;
            // if archon or tree obstacle, has issues, we will need to supplement with another method
            final int size = getObstaclesAroundObstacle(currObstacle, senseRadius, sensedObstacles);
            // pick the obstacle that has smallest angle to current obstacle dir
            float smallestAngle = 0;
            for (int i = 0; i < size; i++) {
                final ObstacleInfo candidateObstacle = sensedObstacles[i];
                // TODO: this needs to use the tangential edge angle, instead of the obstacle center angle
                final float radBetween = obstacleDir.opposite()
                        .radiansBetween(currObstacle.location.directionTo(candidateObstacle.location));
                final float radBetween2;
                if (preferRight) {
                    if (radBetween <= EPS) {
                        radBetween2 = radBetween + (float) Math.PI * 2;
                    } else {
                        radBetween2 = radBetween;
                    }
                } else {
                    if (radBetween <= -EPS) {
                        radBetween2 = -radBetween;
                    } else {
                        radBetween2 = (float) Math.PI * 2 - radBetween;
                    }
                }
                if (nextObstacle == null || radBetween2 < smallestAngle) {
                    nextObstacle = candidateObstacle;
                    smallestAngle = radBetween2;
                }
            }
            if (nextObstacle != null) {
                // reject and stop if we are no longer in sensor range and the angle has turned away from us
                if (bot.myLoc.distanceTo(nextObstacle.location) + senseRadius > bot.myType.sensorRadius) {
                    final Direction currToNextObstacleDir = currObstacle.location.directionTo(nextObstacle.location);
                    final float radBetween = obstacleDir.radiansBetween(currToNextObstacleDir);
                    if (preferRight && radBetween > 0 || !preferRight && radBetween < 0) {
                        break outer;
                    }
                }
                obstacleDir = currLoc.directionTo(nextObstacle.location);

                final FollowWallPoint nextPoint = new FollowWallPoint(destination, currObstacle, nextObstacle,
                        preferRight, false);
                if (seen.contains(nextPoint)) {
                    // we've made a loop around an existing obstacle
                    // if (DEBUG) {
                    // Debug.debug_print(bot, " made loop around");
                    // }
                    madeLoop = true;
                    break outer;
                }
                seen.add(nextPoint);
                pointsList[pointsSize++] = nextPoint;
                found = true;
                currObstacle = nextObstacle;
                if (DEBUG) {
                    // Debug.debug_print(bot, " added " + nextPoint);
                }
                continue outer;
            }
        }

        if (madeLoop || true) {
            if (DEBUG_WALLS) {
                if (pointsSize > 0) {
                    Debug.debug_line(bot, startPoint.obstacle.location, pointsList[0].obstacle.location, 255, 255, 255);
                }
                for (int i = 0; i < pointsSize - 1; i++) {
                    Debug.debug_line(bot, pointsList[i].obstacle.location, pointsList[i + 1].obstacle.location, 255,
                            255,
                            255);
                }
            }
            // Only take the list up to and including the furthest angle
            final Direction startObstacleDir = currLoc.directionTo(startPoint.obstacle.location);
            int furthestIndex = -1;
            float furthestAngle = 0;
            for (int i = 0; i < pointsSize; i++) {
                final FollowWallPoint wp = pointsList[i];
                final Direction dir = currLoc.directionTo(wp.obstacle.location);
                final float radBetween = startObstacleDir.radiansBetween(dir);
                final float radBetween2;
                if (preferRight) {
                    if (radBetween < 0) {
                        radBetween2 = -radBetween;
                    } else {
                        break;
                    }
                } else {
                    if (radBetween < 0) {
                        break;
                    } else {
                        radBetween2 = radBetween;
                    }
                }
                if (radBetween2 > furthestAngle) {
                    furthestAngle = radBetween2;
                    furthestIndex = i;
                }
            }
            // if (DEBUG) {
            /*
             * Debug.debug_print(bot, "  currLoc=" + currLoc + " furthestAngle=" + furthestAngle + " furthestIndex=" +
             * furthestIndex); Debug.debug_print(bot, "  trimming return list size from " + pointsSize + " to " +
             * (furthestIndex + 1));
             */
            // }
            pointsSize = furthestIndex + 1;
        }
        if (DEBUG_WALLS) {
            if (pointsSize > 0) {
                Debug.debug_line(bot, startPoint.obstacle.location, pointsList[0].obstacle.location, 255, 255, 0);
            }
            for (int i = 0; i < pointsSize - 1; i++) {
                Debug.debug_line(bot, pointsList[i].obstacle.location, pointsList[i + 1].obstacle.location, 255, 255,
                        0);
            }
        }
    }

    /**
     * Class representing point where we are following the wall.
     * 
     * Defined by a previousLoc and an obstacleLoc, so that we have a sense of direction that we are traversing along
     * the wall edge. If we are meeting the wall for the first time, previousLoc would be initialized with currentLoc.
     */
    static class FollowWallPoint {
        final MapLocation intendedDestination;
        final ObstacleInfo previousObstacle;
        final ObstacleInfo obstacle;
        final boolean preferRight, firstTime;

        FollowWallPoint(final MapLocation intendedDestination, final ObstacleInfo previousObstacle,
                final ObstacleInfo obstacle, final boolean preferRight, final boolean firstTime) {
            this.intendedDestination = intendedDestination;
            this.previousObstacle = previousObstacle;
            this.obstacle = obstacle;
            this.preferRight = preferRight;
            this.firstTime = firstTime;
        }

        /**
         * Determine if this wall point obstacle is still valid, or if it might have moved.
         */
        final boolean isValid(final TangentBugNavigator nav) {
            if (!nav.bot.rc.canSenseLocation(obstacle.location)) {
                return true;
            }
            try {
                if (obstacle.isRobot && nav.bot.rc.senseRobotAtLocation(obstacle.location) == null ||
                        !obstacle.isRobot && nav.bot.rc.senseTreeAtLocation(obstacle.location) == null) {
                    return false;
                }
            } catch (GameActionException e) {
                return false;
            }
            return true;
        }

        /**
         * Return location at edge of wall. This might change depending on current location, if we are at a sharp edge
         * (number of orthogonal adjacent obstacles is 1). This might also not return a traversable location.
         */
        final MapLocation getEdgeLoc(final MapLocation currLoc, final TangentBugNavigator nav) {
            final float radiiSum = (nav.bot.myType.bodyRadius + obstacle.radius);
            final float obsDist = currLoc.distanceTo(obstacle.location);
            final Direction dir;
            // If we are practically adjacent to obstacle already, round around it
            if (obsDist <= radiiSum + EPS * 2) { // + nav.bot.myType.strideRadius?
                // The divisor has to include the buffer that we will be giving ourselves away from obstacle
                final float sine = nav.bot.myType.strideRadius / 2 / (radiiSum + EPS * 2);
                final float alpha = (float) Math.asin(sine) * 2;
                dir = preferRight ? obstacle.location.directionTo(currLoc).rotateLeftRads(alpha)
                        : obstacle.location.directionTo(currLoc).rotateRightRads(alpha);
            } else if (firstTime) {
                // If first time, we don't have any info about previous obstacle. Go to tangent point.
                final float cosine = (radiiSum + EPS * 2) / obsDist;
                final float theta = (float) Math.acos(cosine);
                dir = preferRight ? obstacle.location.directionTo(currLoc).rotateLeftRads(theta)
                        : obstacle.location.directionTo(currLoc).rotateRightRads(theta);

            } else {
                // We have some previous obstacle info. We aim to be at a right angle to the "wall".
                // This means the position is independent of where we are and is less subject to weird issues like
                // having the robot collide into the previous or next obstacle (and thus get rejected as a candidate)
                dir = preferRight ? previousObstacle.location.directionTo(obstacle.location).rotateRightDegrees(90.0f)
                        : previousObstacle.location.directionTo(obstacle.location).rotateLeftDegrees(90.0f);
            }
            return obstacle.location.add(dir, radiiSum + EPS);
        }

        @Override
        public int hashCode() {
            return previousObstacle.hashCode() * 37 + obstacle.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof FollowWallPoint)) {
                return false;
            }
            final FollowWallPoint p = (FollowWallPoint) o;
            return p.previousObstacle.equals(previousObstacle) && p.obstacle.equals(obstacle)
                    && p.firstTime == firstTime;
        }

        @Override
        public String toString() {
            return "FollowWallPoint(" + previousObstacle + "->" + obstacle + ")";
        }
    }

    public static class ObstacleInfo {
        public final MapLocation location;
        public final float radius;
        public final boolean isRobot;
        public final int id;

        ObstacleInfo(final MapLocation location, final float radius, final boolean isRobot, final int id) {
            this.location = location;
            this.radius = radius;
            this.isRobot = isRobot;
            this.id = id;
        }

        @Override
        public int hashCode() {
            return location.hashCode() + id;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof ObstacleInfo)) {
                return false;
            }
            final ObstacleInfo p = (ObstacleInfo) o;
            return p.location.equals(location) && p.radius == radius && p.isRobot == isRobot && p.id == id;
        }

        @Override
        public String toString() {
            return "ObstacleInfo(" + location + ", " + radius + ", " + isRobot + ", " + id + ")";
        }

    }
}
