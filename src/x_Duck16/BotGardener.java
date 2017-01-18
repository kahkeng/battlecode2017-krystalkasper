package x_Duck16;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotType;

public strictfp class BotGardener extends x_Arc.BotGardener {

    public BotGardener(final RobotController rc) {
        super(rc);
    }

    public void run() throws GameActionException {
        try {
            earlyGame();
        } catch (Exception e) {
            System.out.println("Gardener Exception");
            e.printStackTrace();
        }
        while (true) {
            try {
                startLoop();
                // Messaging.broadcastGardener(this);
                // final MapLocation[] myArchons = Messaging.readArchonLocation(this);

                waterTrees();
                final MapLocation enemyLoc = buildCombatUnitsIfNeeded();
                if (enemyLoc != null) {
                    fleeFromEnemyAlongArc(enemyLoc);
                } else {
                    plantTreesInArc(0);
                    buildLumberjacksIfNeeded();
                }

                Clock.yield();

            } catch (Exception e) {
                System.out.println("Gardener Exception");
                e.printStackTrace();
            }
        }
    }

    public final void earlyGame() throws GameActionException {
        if (rc.getRoundNum() > 10) {
            return;
        }
        while (!tryBuildRobot(RobotType.SCOUT, formation.baseDir)) {
            startLoop();
            Clock.yield();
        }
        while (!tryBuildRobot(RobotType.SCOUT, formation.baseDir)) {
            startLoop();
            Clock.yield();
        }
        while (!tryBuildRobot(RobotType.SOLDIER, formation.baseDir)) {
            startLoop();
            Clock.yield();
        }
        harassmentLoop(true);
    }

    public final void harassmentLoop(final boolean withScout) {
        int buildIndex = 0;
        while (true) {
            try {
                startLoop();
                waterTrees();
                tryPlantTreesWithSpace();
                final RobotType buildType;
                if (withScout) {
                    switch (buildIndex) {
                    default:
                    case 0:
                        buildType = RobotType.SCOUT;
                        break;
                    case 1:
                        buildType = RobotType.SOLDIER;
                        break;
                    case 2: // TODO: based on tree density
                        buildType = RobotType.LUMBERJACK;
                        break;
                    }
                } else {
                    switch (buildIndex) {
                    default:
                    case 0:
                        buildType = RobotType.SOLDIER;
                        break;
                    case 1: // TODO: based on tree density
                        buildType = RobotType.LUMBERJACK;
                        break;
                    }
                }
                if (rc.getTeamBullets() >= buildType.bulletCost + buildCount * BUILD_PENALTY) {
                    if (tryBuildRobot(buildType, formation.baseDir)) {
                        if (withScout) {
                            buildIndex = (buildIndex + 1) % 3;
                        } else {
                            buildIndex = (buildIndex + 1) % 2;
                        }
                        buildCount = (buildCount + 1) % MAX_BUILD_PENALTY;
                    }
                }
                Clock.yield();
            } catch (Exception e) {
                System.out.println("Gardener Exception");
                e.printStackTrace();
            }
        }
    }

    public final boolean tryPlantTreesWithSpace() throws GameActionException {
        Direction dir = formation.baseDir;
        Direction plantDir = null;
        int canBuild = 0;
        for (int i = 0; i < 6; i++) {
            final MapLocation buildLoc = myLoc.add(dir, myType.bodyRadius * 2 + 0.01f);
            if (rc.senseNearbyRobots(buildLoc, myType.bodyRadius, null).length == 0
                    && rc.senseNearbyTrees(buildLoc, myType.bodyRadius, null).length == 0) {
                canBuild += 1;
                if (canBuild == 2) {
                    plantDir = dir;
                    break;
                }
            }
            dir = dir.rotateRightDegrees(60.0f);
        }
        if (plantDir != null && rc.canPlantTree(plantDir)) {
            rc.plantTree(plantDir);
            return true;
        }
        return false;
    }

}
