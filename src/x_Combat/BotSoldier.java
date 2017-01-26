package x_Combat;

import battlecode.common.BulletInfo;
import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.RobotController;
import x_Base.BotBase;
import x_Base.Combat;

public strictfp class BotSoldier extends BotBase {

    public BotSoldier(final RobotController rc) {
        super(rc);
        DEBUG = true;
    }

    public void run() throws GameActionException {
        while (true) {
            try {
                startLoop();
                final BulletInfo[] bullets = rc.senseNearbyBullets();
                final Direction dodgeDir = getDodgeDirection(bullets);
                if (dodgeDir != null) {
                    tryMove(myLoc.add(dodgeDir));
                }
                if (!Combat.seekAndAttackAndSurroundEnemy3(this)) {
                    moveTowardsTreeBorder();
                }

                Clock.yield();
            } catch (Exception e) {
                System.out.println("Soldier Exception");
                e.printStackTrace();
            }
        }
    }

}
