package x_Streets;

import battlecode.common.BulletInfo;
import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.RobotController;
import x_Base.BotBase;
import x_Base.Combat;
import x_Base.StrategyFeature;

public strictfp class BotTank extends BotBase {

    public BotTank(final RobotController rc) {
        super(rc);
        DEBUG = true;
    }

    public void run() throws GameActionException {
        while (true) {
            try {
                startLoop();
                if (StrategyFeature.IMPROVED_COMBAT1.enabled()) {
                    if (!Combat.seekAndAttackAndSurroundEnemy5(this)) {
                        moveTowardsTreeBorder();
                    }
                } else {
                    final BulletInfo[] bullets = rc.senseNearbyBullets();
                    final Direction dodgeDir = getDodgeDirection(bullets);
                    if (dodgeDir != null) {
                        tryMove(myLoc.add(dodgeDir));
                    }
                    if (!Combat.seekAndAttackAndSurroundEnemy3(this)) {
                        moveTowardsTreeBorder();
                    }
                }

                Clock.yield();
            } catch (Exception e) {
                System.out.println("Tank Exception");
                e.printStackTrace();
            }
        }
    }

}
