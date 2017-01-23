package x_Base;

import java.util.HashSet;
import java.util.Set;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.TreeInfo;

public strictfp class TangentBugNavigator {

    private static final boolean DEBUG = true;
    private static final int MAX_WALLS = 100;
    private static final float EPS = 0.04f; // buffer to add to not always exactly touch edges
    private static final float LEAVE_THRESHOLD = 1.0f; // distance improvement before we leave obstacle

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
    RobotInfo[] nearbyRobots;
    TreeInfo[] nearbyTrees;

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

    private void reset() {
        lastMetObstacleLoc = null;
        lastMetObstacleDist = 0;
        followWallPoint = null;
        state = State.TOWARD_GOAL;
    }

    private final ObstacleInfo hasObstacleToward(final MapLocation currLoc, final MapLocation destLoc,
            final float maxDist) {
        final float destDistance = Math.min(currLoc.distanceTo(destLoc), maxDist);
        final ObstacleInfo obstacle = Combat.whichRobotOrTreeWillObjectCollideWith(bot, currLoc.directionTo(destLoc),
                destDistance, bot.myType.bodyRadius, nearbyRobots, nearbyTrees);
        return obstacle;
    }

    public MapLocation getNextLocation() {
        final MapLocation currLoc = rc.getLocation();
        final MapLocation destLoc = destination;
        Debug.debug_dot(bot, destLoc, 255, 255, 255);
        if (currLoc.equals(destLoc)) { // TODO: threshold for distance
            return null;
        }
        nearbyRobots = rc.senseNearbyRobots();
        nearbyTrees = rc.senseNearbyTrees();

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
                            new ObstacleInfo(currLoc, bot.myType.bodyRadius, true),
                            obstacleInfo, preferRight, true);
                    lastObstacleLoc = obstacleInfo.location;
                    lastMetObstacleLoc = obstacleInfo.location.add(obstacleInfo.location.directionTo(currLoc),
                            obstacleInfo.radius + bot.myType.bodyRadius);
                    lastMetObstacleDist = lastMetObstacleLoc.distanceTo(destLoc);
                    if (DEBUG) {
                        Debug.debug_print(bot,
                                "met obstacle " + lastObstacleLoc + " followWallPoint=" + followWallPoint);
                    }
                    continue;
                }
                if (DEBUG) {
                    Debug.debug_print(bot, "returning1 " + destLoc);
                }
                return destLoc;
            }
            case FOLLOW_OBSTACLE: {
                if (followWallPoint == null) {
                    throw new RuntimeException("followWallPoint should not be null");
                }
                // TODO: should we check that we reached the m-line first?
                // TODO: also check that way to goal is unimpeded?
                if (currLoc.distanceTo(destLoc) < Math.max(lastMetObstacleDist - LEAVE_THRESHOLD, EPS)) {
                    if (DEBUG) {
                        Debug.debug_print(bot,
                                "leaving obstacle at " + currLoc + " followWallPoint=" + followWallPoint);
                    }
                    state = State.TOWARD_GOAL;
                    followWallPoint = null;
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
                Debug.debug_dot(bot, followWallPoint.obstacle.location, 0, 255, 0); // start: green
                System.out.println("clock2 " + Clock.getBytecodeNum() + " " + Clock.getBytecodesLeft());
                for (int i = pointsSize - 1; i >= 0; i--) {
                    final FollowWallPoint candidate = pointsList[i];
                    final MapLocation edgeLoc = candidate.getEdgeLoc(currLoc, this);
                    Debug.debug_dot(bot, edgeLoc, 0, 0, 255);
                    boolean isClear = canPathTowardsLocation2(edgeLoc);
                    if (DEBUG) {
                        // Debug.debug_print(bot, " candidate wp " + candidate + " isClear=" + isClear + " edgeLoc=" +
                        // edgeLoc);
                    }
                    if (isClear) {
                        if (DEBUG) {
                            // Debug.debug_print(bot, " changing wp from " + followWallPoint + " to " + candidate);
                        }
                        followWallPoint = candidate;
                        break;
                    }
                    System.out.println("clock2a " + i + " " + Clock.getBytecodeNum() + " " + Clock.getBytecodesLeft());
                }
                System.out.println("clock3 " + Clock.getBytecodeNum() + " " + Clock.getBytecodesLeft());
                final MapLocation edgeLoc = followWallPoint.getEdgeLoc(currLoc, this);
                Debug.debug_dot(bot, followWallPoint.obstacle.location, 255, 0, 0); // end: red
                Debug.debug_dot(bot, edgeLoc, 255, 255, 0);
                if (DEBUG) {
                    Debug.debug_print(bot,
                            "returning2 " + edgeLoc + " followWallPoint=" + followWallPoint);
                }
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
        final Direction destDir = currLoc.directionTo(destLoc);
        if (Combat.willObjectCollideWithRobot(bot, destDir, destDistance, bot.myType.bodyRadius, nearbyRobots)) {
            // System.out.println("will collide with robot");
            return false;
        }
        if (Combat.willObjectCollideWithTree(bot, destDir, destDistance, bot.myType.bodyRadius, nearbyTrees)) {
            // System.out.println("will collide with tree");
            return false;
        }
        return true;
    }

    /**
     * Determine if we can greedily navigate towards destLoc, inclusive of whether destLoc is traversable. We need to be
     * within sensing range of destLoc for this to work.
     */
    private final boolean canPathTowardsLocation2(final MapLocation destLoc) {
        final MapLocation currLoc = bot.myLoc;
        final float destDistance = currLoc.distanceTo(destLoc);
        if (destDistance > bot.myType.sensorRadius - bot.myType.bodyRadius) {
            return false;
        }
        // we use overlapping circles that cover a rectangular box with robot width and length of travel
        // to find potential obstacles.
        final float startDist = bot.myType.bodyRadius; // start point of the centers of circles
        final float endDist = destDistance + bot.myType.bodyRadius; // end point of centers of circles, no need to go
                                                                    // further than this
        final float senseRadius = (float) (bot.myType.bodyRadius * Math.sqrt(2));
        final Direction destDir = currLoc.directionTo(destLoc);
        float centerDist = startDist;
        while (centerDist <= endDist) {
            final MapLocation centerLoc = currLoc.add(destDir, centerDist);
            final RobotInfo[] robots = bot.rc.senseNearbyRobots(centerLoc, senseRadius, null);
            if (Combat.willObjectCollideWithRobot(bot, destDir, destDistance, bot.myType.bodyRadius, robots)) {
                return false;
            }
            final TreeInfo[] trees = bot.rc.senseNearbyTrees(centerLoc, senseRadius, null);
            if (Combat.willObjectCollideWithTree(bot, destDir, destDistance, bot.myType.bodyRadius, trees)) {
                return false;
            }
            centerDist += 2 * bot.myType.bodyRadius;
        }
        return true;
    }

    private final int getObstaclesAroundObstacle(final ObstacleInfo obstacle, final ObstacleInfo[] obstacles) {
        int size = 0;
        final float senseRadius = obstacle.radius + bot.myType.bodyRadius * 2 + EPS;
        final RobotInfo[] robots = rc.senseNearbyRobots(obstacle.location, senseRadius, null);
        final TreeInfo[] trees = rc.senseNearbyTrees(obstacle.location, senseRadius, null);
        for (final RobotInfo robot : robots) {
            if (robot.location.equals(obstacle.location)) {
                continue;
            }
            obstacles[size++] = new ObstacleInfo(robot.location, robot.getRadius(), true);
        }
        for (final TreeInfo tree : trees) {
            if (tree.location.equals(obstacle.location)) {
                continue;
            }
            obstacles[size++] = new ObstacleInfo(tree.location, tree.radius, false);
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
        boolean found = true;
        boolean madeLoop = false;
        System.out.println("clock-1 " + Clock.getBytecodeNum() + " " + Clock.getBytecodesLeft());
        outer: while (found && Clock.getBytecodesLeft() >= 5000) {
            found = false;
            final float senseRadius = currObstacle.radius + bot.myType.bodyRadius * 2 + EPS;
            Debug.debug_line(bot, currObstacle.location,
                    currObstacle.location.add(currLoc.directionTo(currObstacle.location), senseRadius), 255, 0, 255);
            ObstacleInfo nextObstacle = null;
            // if archon or tree obstacle, has issues, we will need to supplement with another method
            final int size = getObstaclesAroundObstacle(currObstacle, sensedObstacles);
            // pick the obstacle that has smallest angle
            final Direction obstacleDir = currObstacle.location.directionTo(currLoc);
            float smallestAngle = 0;
            System.out.println("clock-2 " + Clock.getBytecodeNum() + " " + Clock.getBytecodesLeft());
            for (int i = 0; i < size; i++) {
                final ObstacleInfo candidateObstacle = sensedObstacles[i];
                // TODO: this needs to use the tangential edge angle, instead of the obstacle center angle
                final float radBetween = obstacleDir
                        .radiansBetween(currObstacle.location.directionTo(candidateObstacle.location));
                final float radBetween2;
                if (preferRight) {
                    if (radBetween < 0) {
                        radBetween2 = radBetween + (float) Math.PI * 2;
                    } else {
                        radBetween2 = radBetween;
                    }
                } else {
                    if (radBetween < 0) {
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
                final FollowWallPoint nextPoint = new FollowWallPoint(destination, currObstacle, nextObstacle,
                        preferRight, false);
                if (seen.contains(nextPoint)) {
                    // we've made a loop around an existing obstacle
                    if (DEBUG) {
                        Debug.debug_print(bot, "  made loop around");
                    }
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
                System.out.println("clock0 " + Clock.getBytecodeNum() + " " + Clock.getBytecodesLeft());
                continue outer;
            }
        }

        System.out.println("clock " + Clock.getBytecodeNum() + " " + Clock.getBytecodesLeft());
        if (madeLoop) {
            if (pointsSize > 0) {
                Debug.debug_line(bot, startPoint.obstacle.location, pointsList[0].obstacle.location, 255, 255, 255);
            }
            for (int i = 0; i < pointsSize - 1; i++) {
                Debug.debug_line(bot, pointsList[i].obstacle.location, pointsList[i + 1].obstacle.location, 255, 255,
                        255);
            }
            // Only take the list up to and including the furthest angle
            final Direction obstacleDir = currLoc.directionTo(startPoint.obstacle.location);
            int furthestIndex = -1;
            float furthestAngle = 0;
            for (int i = 0; i < pointsSize; i++) {
                final FollowWallPoint wp = pointsList[i];
                final Direction dir = currLoc.directionTo(wp.obstacle.location);
                final float radBetween = obstacleDir.radiansBetween(dir);
                final float radBetween2;
                if (preferRight) {
                    if (radBetween < 0) {
                        radBetween2 = -radBetween;
                    } else {
                        break;
                    }
                } else {
                    if (radBetween < 0) {
                        radBetween2 = radBetween + (float) Math.PI * 2;
                    } else {
                        break;
                    }
                }
                if (radBetween2 > furthestAngle) {
                    furthestAngle = radBetween2;
                    furthestIndex = i;
                }
            }
            if (DEBUG) {
                /*
                 * Debug.debug_print(bot, "  currLoc=" + currLoc + " furthestAngle=" + furthestAngle + " furthestIndex="
                 * + furthestIndex); Debug.debug_print(bot, "  trimming return list size from " + pointsSize + " to " +
                 * (furthestIndex + 1));
                 */
            }
            pointsSize = furthestIndex + 1;
        }
        if (pointsSize > 0) {
            Debug.debug_line(bot, startPoint.obstacle.location, pointsList[0].obstacle.location, 255, 255, 0);
        }
        for (int i = 0; i < pointsSize - 1; i++) {
            Debug.debug_line(bot, pointsList[i].obstacle.location, pointsList[i + 1].obstacle.location, 255, 255, 0);
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
         * Return location at edge of wall. This might change depending on current location, if we are at a sharp edge
         * (number of orthogonal adjacent obstacles is 1). This might also not return a traversable location.
         */
        final MapLocation getEdgeLoc(final MapLocation currLoc, TangentBugNavigator nav) {
            final float radiiSum = (nav.bot.myType.bodyRadius + obstacle.radius);
            final float obsDist = currLoc.distanceTo(obstacle.location);
            final Direction dir;
            // If we are practically adjacent to obstacle already, round around it
            if (obsDist <= radiiSum + EPS * 2) { // + nav.bot.myType.strideRadius?
                final float sine = nav.bot.myType.strideRadius / 2 / radiiSum;
                final float alpha = (float) Math.asin(sine) * 2;
                dir = preferRight ? obstacle.location.directionTo(currLoc).rotateLeftRads(alpha)
                        : obstacle.location.directionTo(currLoc).rotateRightRads(alpha);
            } else if (firstTime) {
                // If first time, we don't have any info about previous obstacle. Go to tangent point.
                final float cosine = radiiSum / obsDist;
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

    static class ObstacleInfo {
        final MapLocation location;
        final float radius;
        final boolean isRobot;

        ObstacleInfo(final MapLocation location, final float radius, boolean isRobot) {
            this.location = location;
            this.radius = radius;
            this.isRobot = isRobot;
        }

        @Override
        public int hashCode() {
            return location.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof ObstacleInfo)) {
                return false;
            }
            final ObstacleInfo p = (ObstacleInfo) o;
            return p.location.equals(location) && p.radius == radius && p.isRobot == isRobot;
        }

        @Override
        public String toString() {
            return "ObstacleInfo(" + location + ", " + radius + ", " + isRobot + ")";
        }

    }
}
