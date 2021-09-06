package edu.illinois.cs.cs125.spring2020.mp.logic;

/**
 * Holds team/role ID constants.
 * <p>
 * STOP! Do not modify this file. Changes will be overwritten during official grading.
 */
public final class TeamID {

    /** An observer, not actually playing.
     * When used as the claimant of a target or area, indicates that the objective is uncaptured. */
    public static final int OBSERVER = 0;

    /** A player on the red team. */
    public static final int TEAM_RED = 1;

    /** A player on the yellow team. */
    public static final int TEAM_YELLOW = 2;

    /** A player on the green team. */
    public static final int TEAM_GREEN = 3;

    /** A player on the blue team. */
    public static final int TEAM_BLUE = 4;

    /** The smallest valid (non-observer) team ID. */
    public static final int MIN_TEAM = 1;

    /** The largest valid team ID (inclusive). */
    public static final int MAX_TEAM = 4;

    /** How many teams there can be in a game. */
    public static final int NUM_TEAMS = 4;

}
