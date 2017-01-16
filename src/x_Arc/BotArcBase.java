package x_Arc;

import battlecode.common.Direction;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import x_Base.Formations;

public strictfp class BotArcBase extends x_Base.BotBase {

    public final Formations formation;
    public static Direction arcDirection;
    public static float radianStep; // positive is rotating right relative to enemy base

    public BotArcBase(final RobotController rc) {
        super(rc);
        formation = new Formations(this);
        myLoc = rc.getLocation();
        arcDirection = formation.getArcDir(myLoc);
    }

    public final void advanceArcDirection() {
        arcDirection = arcDirection.rotateRightRads(radianStep * 2);
    }

    public final void reverseArcDirection() {
        radianStep = -radianStep;
        advanceArcDirection();
    }

    public final MapLocation getArcLoc() {
        return formation.getArcLoc(arcDirection);
    }

    public final MapLocation getNextArcLoc() {
        return formation.getArcLoc(arcDirection.rotateRightRads(radianStep * 2));
    }

}
