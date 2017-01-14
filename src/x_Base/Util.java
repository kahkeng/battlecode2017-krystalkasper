package x_Base;

import battlecode.common.Direction;

public strictfp class Util {
    /**
     * Returns a random Direction
     * 
     * @return a random Direction
     */
    public static final Direction randomDirection() {
        return new Direction((float) Math.random() * 2 * (float) Math.PI);
    }

}
