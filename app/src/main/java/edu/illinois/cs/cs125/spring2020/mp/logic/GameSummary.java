package edu.illinois.cs.cs125.spring2020.mp.logic;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * A sample of a json snippet.
 * GAMES.JSON."games"[]:
 *     {
 *       "id": "61172a252a3443cd8a69fd23564dd6c9",
 *       "owner": "someone.else@example.com",
 *       "state": 0,
 *       "mode": "target",
 *       "players": [
 *         {
 *           "email": "someone.else@example.com",
 *           "team": 1,
 *           "state": 2
 *         },
 *         {
 *           "email": "nobody@illinois.edu",
 *           "team": 0,
 *           "state": 0
 *         }
 *       ]
 *     }
 */
public class GameSummary {

    /**
     * data retrieved from the web.
     */
    private JsonObject data;

    /**
     * the ID of the game.
     */
    private String id;

    /**
     * the mode of the game.
     */
    private String mode;

    /**
     * the owner/creator of this game.
     */
    private String owner;

    /**
     * the JsonArray of players.
     */
    private JsonArray players;

    /**
     * Creates a game summary from JSON from the server.
     * @param infoFromServer a game JSON file.
     */
    public GameSummary(final com.google.gson.JsonObject infoFromServer) {

        data = infoFromServer;

        id = data.get("id").getAsString();
        mode = data.get("mode").getAsString();
        owner = data.get("owner").getAsString();
        players = data.get("players").getAsJsonArray();

    }

    /**
     * Gets the unique, server-assigned ID of this game.
     * @return the ID of the game.
     */
    public String getId() {

        return id;

    }

    /**
     * Gets the mode of this game, either area or target.
     * @return the mode of the game.
     */
    public String getMode() {

        return mode;

    }

    /**
     * Gets the owner/creator of this game.
     * @return the owner/creator of this game.
     */
    public String getOwner() {

        return owner;

    }

    /**
     * Gets the name of the user's team/role.
     * @param userEmail the logged-in user's email.
     * @param context an Android context (for access to resources).
     * @return the human-readable team/role name of the user in this game
     */
    public String getPlayerRole(final String userEmail,
                                final android.content.Context context) {

        for (int i = 0; i < players.size(); i++) {

            String name = players.get(i).getAsJsonObject().get("email").getAsString();

            if (name.equals(userEmail)) {

                int role = players.get(i).getAsJsonObject().get("team").getAsInt();

                if (role == TeamID.OBSERVER) {
                    return "Observer";
                } else if (role == TeamID.TEAM_RED) {
                    return "Red";
                } else if (role == TeamID.TEAM_YELLOW) {
                    return "Yellow";
                } else if (role == TeamID.TEAM_GREEN) {
                    return "Green";
                } else if (role == TeamID.TEAM_BLUE) {
                    return "Blue";
                } else {
                    return "User in the game bur not yet entered a team";
                }

            }

        }

        return "No such user in this game";

    }

    /**
     * Determines whether this game is an invitation to the user.
     * @param userEmail the logged-in user's email.
     * @return whether the user is invited to this game.
     */
    public boolean isInvitation(final java.lang.String userEmail) {

        return data.get("state").getAsInt() != GameStateID.ENDED
                && isState(userEmail, PlayerStateID.INVITED);

    }

    /**
     * Determines whether this game is on the specified state of the user.
     * @param userEmail the logged-in user's email.
     * @param state the state.
     * @return whether the user is invited to this game.
     */
    public boolean isState(final java.lang.String userEmail, final int state) {

        for (int i = 0; i < players.size(); i++) {

            String name = players.get(i).getAsJsonObject().get("email").getAsString();

            if (name.equals(userEmail)) {

                return  players.get(i).getAsJsonObject().get("state").getAsInt()
                        == state;

            }

        }

        return false;

    }

    /**
     * Determines whether the user is currently involved in this game.
     * For a game to be ongoing, it must not be over and the user must have accepted their
     * invitation to it.
     * @param userEmail the logged-in user's email.
     * @return whether this game is ongoing for the user.
     */
    public boolean isOngoing(final java.lang.String userEmail) {

        return data.get("state").getAsInt() != GameStateID.ENDED
                && (isState(userEmail, PlayerStateID.ACCEPTED)
                || isState(userEmail, PlayerStateID.PLAYING));

    }

}
