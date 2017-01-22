package x_LandRush;

import battlecode.common.Clock;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import x_Base.Combat;

public strictfp class BotLumberjack extends x_Base.BotLumberjack {

    public BotLumberjack(final RobotController rc) {
        super(rc);
        DEBUG = true;
    }

    public void run() throws GameActionException {
        while (true) {
            try {
                startLoop();

                if (Combat.strikeEnemiesFromBehind(this)) {
                    // Make space for movement
                    chopAnyNearbyUnownedTrees();
                } else {
                    if (!clearObstructedArchonsAndGardeners()) {
                        if (!clearEnemyTrees()) {
                            nav.setDestination(enemyInitialArchonLocs[0]);
                            final MapLocation nextLoc = nav.getNextLocation();
                            if (!tryMove(nextLoc)) {
                                System.out.println("can't move to nextLoc " + nextLoc);
                            }
                        }
                    }
                }

                Clock.yield();
            } catch (Exception e) {
                System.out.println("Lumberjack Exception");
                e.printStackTrace();
            }
        }
    }

}
