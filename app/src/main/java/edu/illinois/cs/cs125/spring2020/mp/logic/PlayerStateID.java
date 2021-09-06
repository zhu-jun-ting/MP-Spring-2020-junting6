package edu.illinois.cs.cs125.spring2020.mp.logic;

/**
 * Holds constant values for possible player states.
 * <p>
 * STOP! Do not modify this file. Changes will be overwritten during official grading.
 */
public final class PlayerStateID {

    /** Invited to the game but has not yet responded to the invitation. */
    public static final int INVITED = 0;

    /** Declined the invitation or left the game. */
    public static final int REMOVED = 1;

    /** Involved in the game (accepted the invitation) but not currently playing. */
    public static final int ACCEPTED = 2;

    /** Using the app to play this game right now. */
    public static final int PLAYING = 3;

}
