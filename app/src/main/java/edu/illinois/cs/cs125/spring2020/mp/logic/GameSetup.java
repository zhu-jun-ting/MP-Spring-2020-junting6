package edu.illinois.cs.cs125.spring2020.mp.logic;


import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;

/**
 * Provides static methods to convert game information to JSON payloads that can be POSTed to the
 * server's /games/create endpoint to create a multi player game.
 */
public class GameSetup {

    /**
     * Creates a JSON object representing the configuration of a multi player area mode game.
     * Refer to our API documentation for the structure of the output JSON.
     *
     * The configuration is valid if there is at least one invitee and a positive
     * (larger than zero) cell size.
     *
     * a JSON object usable by the /games/create endpoint or null if the configuration is invalid
     *
     * @param invitees all players involved in the game (never null)
     * @param area the area boundaries
     * @param cellSize the desired cell size in meters
     * @return a JSON object usable by the /games/create endpoint or null if the configuration is
     * invalid
     */
    public static JsonObject areaMode(final List<Invitee> invitees, final LatLngBounds area,
                                      final int cellSize) {

        if (invitees.size() == 0  || cellSize <= 0) {

            return null;

        }

        JsonObject data = new JsonObject();

        data.addProperty("mode", "area");
        data.addProperty("cellSize", cellSize);
        data.addProperty("areaNorth", area.northeast.latitude);
        data.addProperty("areaEast", area.northeast.longitude);
        data.addProperty("areaSouth", area.southwest.latitude);
        data.addProperty("areaWest", area.southwest.longitude);

        JsonArray i = new JsonArray();

        for (Invitee player : invitees) {

            JsonObject entry = new JsonObject();

            entry.addProperty("email", player.getEmail());
            entry.addProperty("team", player.getTeamId());

            i.add(entry);

        }

        data.add("invitees", i);

        return data;

    }

    /**
     * Creates a JSON object representing the configuration of a multi player target mode game.
     * Refer to our API documentation for the structure of the output JSON.
     *
     * The configuration is valid if there is at least one invitee, at least one target, and a
     * positive (larger than zero) proximity threshold. If the configuration is invalid,
     * this function returns null.
     *
     * @param invitees all players involved in the game (never null)
     * @param targets the positions of all targets (never null)
     * @param proximityThreshold the proximity threshold in meters
     * @return a JSON object usable by the /games/create endpoint or null if the configuration is
     * invalid
     */
    public static JsonObject targetMode(final List<Invitee> invitees, final List<LatLng> targets,
                                        final int proximityThreshold) {

        if (invitees.size() == 0 || targets.size() == 0 || proximityThreshold <= 0) {

            return null;

        }

        JsonObject data = new JsonObject();

        data.addProperty("mode", "target");
        data.addProperty("proximityThreshold", proximityThreshold);

        JsonArray t = new JsonArray();

        for (LatLng point : targets) {

            JsonObject entry = new JsonObject();

            entry.addProperty("latitude", point.latitude);
            entry.addProperty("longitude", point.longitude);

            t.add(entry);

        }

        data.add("targets", t);

        JsonArray i = new JsonArray();

        for (Invitee player : invitees) {

            JsonObject entry = new JsonObject();

            entry.addProperty("email", player.getEmail());
            entry.addProperty("team", player.getTeamId());

            i.add(entry);

        }

        data.add("invitees", i);

        return data;

    }

}
