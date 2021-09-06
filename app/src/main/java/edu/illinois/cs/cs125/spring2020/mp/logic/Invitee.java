package edu.illinois.cs.cs125.spring2020.mp.logic;

/**
 * Represents a person invited to a game in the game setup activity.
 */
public final class Invitee {

    /** Which team/role the player is assigned, one of the TeamID constants. */
    private int teamId;

    /** The invitee's email address. */
    private String email;

    /**
     * Creates a new Invitee (player record).
     * @param setEmail email address
     * @param setTeamId team ID, 0 for observer, positive for player
     */
    public Invitee(final String setEmail, final int setTeamId) {
        email = setEmail;
        teamId = setTeamId;
    }

    /**
     * Gets the team/role.
     * @return the TeamID
     */
    public int getTeamId() {
        return teamId;
    }

    /**
     * Sets the team/role.
     * @param newTeamId the TeamID
     */
    public void setTeamId(final int newTeamId) {
        teamId = newTeamId;
    }

    /**
     * Gets the email address.
     * @return the email address
     */
    public String getEmail() {
        return email;
    }

    /**
     * Sets the email address.
     * @param newEmail the new email address
     */
    public void setEmail(final String newEmail) {
        email = newEmail;
    }

}
