package edu.illinois.cs.cs125.spring2020.mp.logic;

import android.content.Context;
import android.graphics.Color;

import androidx.annotation.CallSuper;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.neovisionaries.ws.client.WebSocket;

import java.util.HashMap;
import java.util.Map;

import edu.illinois.cs.cs125.spring2020.mp.R;

/**
 * Represents a multiplayer game, providing or defining methods common to all game modes.
 * <p>
 * This is only used starting in Checkpoint 4.
 */
public abstract class Game {

    /** The current user's email. */
    private String email;

    /** The Google Maps view to render to. */
    private GoogleMap map;

    /** The websocket for sending data to the server. */
    private WebSocket websocket;

    /** The Android UI context. */
    private Context context;

    /** All participants' team IDs. */
    private Map<String, Integer> playerTeams = new HashMap<>();

    /** The map indicators for other players. */
    private Map<String, Circle> otherPlayerCircles = new HashMap<>();

    /**
     * Sets up this Game.
     * @param setEmail the player's email (from Firebase)
     * @param setMap the Google Maps view to render to
     * @param setWebSocket the websocket to send events to
     * @param initialState the "full" update from the server
     * @param setContext the Android UI context
     */
    public Game(final String setEmail, final GoogleMap setMap, final WebSocket setWebSocket,
                final JsonObject initialState, final Context setContext) {
        email = setEmail;
        map = setMap;
        websocket = setWebSocket;
        context = setContext;

        map.clear();
        otherPlayerCircles.clear();
        for (JsonElement p : initialState.getAsJsonArray("players")) {
            JsonObject player = p.getAsJsonObject();
            String playerEmail = player.get("email").getAsString();
            int playerTeam = player.get("team").getAsInt();
            int playerState = player.get("state").getAsInt();
            playerTeams.put(playerEmail, playerTeam);
            if (!playerEmail.equals(email) && playerTeam != TeamID.OBSERVER && playerState == PlayerStateID.PLAYING
                    && player.has("lastLatitude")) {
                updateOtherPlayerPosition(player);
            }
        }
        if (!playerTeams.containsKey(email)) {
            throw new IllegalArgumentException("The user specified by setEmail is not in the game");
        }
    }

    /**
     * Gets the user's email address.
     * @return the current user's email
     */
    protected final String getEmail() {
        return email;
    }

    /**
     * Gets the Google Maps view used by this Game.
     * @return the Google Maps control to render to
     */
    protected final GoogleMap getMap() {
        return map;
    }

    /**
     * Gets the UI context.
     * @return an Android UI context
     */
    protected final Context getContext() {
        return context;
    }

    /**
     * Sends a message to the server.
     * @param message JSON object to send
     */
    protected final void sendMessage(final JsonObject message) {
        websocket.sendText(message.toString());
    }

    /**
     * Processes a location change and makes appropriate changes/notifications.
     * @param location the player's most recently known location
     */
    @CallSuper
    public void locationUpdated(final LatLng location) {
        // No-op unless doing the apocryphal Checkpoint 5
    }

    /**
     * Gets a team's score.
     * @param teamId the team ID
     * @return how many objectives the team has captured
     */
    public abstract int getTeamScore(int teamId);

    /**
     * Determines which team has the most points.
     * <p>
     * You do not need to consider ties - you may do anything you think is reasonable in that case.
     * @return the TeamID code of the team with the highest score
     */
    public final int getWinningTeam() {
        // For you to implement

        int[] scores = new int[]{0, getTeamScore(TeamID.TEAM_RED), getTeamScore(TeamID.TEAM_YELLOW),
                getTeamScore(TeamID.TEAM_GREEN), getTeamScore(TeamID.TEAM_BLUE)};

        int winningTeam = 0;

        for (int x = 1; x < scores.length; x++) {

            if (scores[x] > scores[winningTeam]) {

                winningTeam = x;

            }

        }

        return winningTeam;
    }

    /**
     * Processes an update from the server.
     * @param message JSON from the server (the "type" property indicates the type)
     * @return whether the message was handled
     */
    @CallSuper
    public boolean handleMessage(final JsonObject message) {
        switch (message.get("type").getAsString()) {
            case "playerLocation":
                updateOtherPlayerPosition(message);
                return true;
            case "playerExit":
                Circle c = otherPlayerCircles.remove(message.get("email").getAsString());
                if (c != null) {
                    c.remove();
                }
                return true;
            default:
                return false;
        }
    }

    /**
     * Gets the user's team ID in this game.
     * @return team ID as defined in TeamID
     */
    @SuppressWarnings("ConstantConditions")
    public final int getMyTeam() {
        return playerTeams.get(email);
    }

    /**
     * Updates the map indicator of another player.
     * @param player parsed JSON from a full update or a player location update
     */
    @SuppressWarnings("ConstantConditions")
    private void updateOtherPlayerPosition(final JsonObject player) {
        String playerEmail = player.get("email").getAsString();
        LatLng location = new LatLng(player.get("lastLatitude").getAsDouble(),
                player.get("lastLongitude").getAsDouble());
        int[] colors = getContext().getResources().getIntArray(R.array.team_colors);
        final double circleRadius = 4.0;
        CircleOptions c = new CircleOptions().center(location)
                .radius(circleRadius)
                .fillColor(colors[playerTeams.get(playerEmail)])
                .zIndex(2.0f)
                .strokeColor(Color.BLACK)
                .strokeWidth(2);
        Circle old = otherPlayerCircles.put(playerEmail, map.addCircle(c));
        if (old != null) {
            old.remove();
        }
    }

    /**
     * get the winning team as a string.
     * @return winning team as a string.
     */
    public String getWinningTeamString() {

        return context.getResources().getStringArray(R.array.team_choices)
                [getWinningTeam()];

    }



}
