package x_Streets;

import battlecode.common.RobotController;
import x_Base.StrategyFeature;

public strictfp class BotTank extends BotSoldier {

    public BotTank(final RobotController rc) {
        super(rc);
        StrategyFeature.initialize(rc);
        DEBUG = true;
    }

}
