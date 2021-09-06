package edu.illinois.cs.cs125.spring2020.mp;

import android.content.Context;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;
import com.google.gson.JsonObject;
import com.neovisionaries.ws.client.WebSocket;

import edu.illinois.cs.cs125.spring2020.mp.logic.Game;

public class InertGame extends Game {

    public InertGame(String setEmail, GoogleMap setMap, WebSocket setWebSocket, JsonObject initialState, Context setContext) {
        super(setEmail, setMap, setWebSocket, initialState, setContext);
    }

    @Override
    public int getTeamScore(int teamId) {
        return 0;
    }

    @Override
    public void locationUpdated(LatLng location) {
        super.locationUpdated(location);
    }
}
