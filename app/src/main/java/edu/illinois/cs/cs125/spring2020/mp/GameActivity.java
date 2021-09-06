package edu.illinois.cs.cs125.spring2020.mp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.firebase.auth.FirebaseAuth;
import com.google.gson.JsonObject;
import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketCloseCode;

import java.util.ArrayList;
import java.util.List;

import edu.illinois.cs.cs125.spring2020.mp.logic.AreaGame;
import edu.illinois.cs.cs125.spring2020.mp.logic.Game;
import edu.illinois.cs.cs125.spring2020.mp.logic.GameStateID;
import edu.illinois.cs.cs125.spring2020.mp.logic.TargetGame;
import edu.illinois.cs.cs125.spring2020.mp.logic.TeamID;
import edu.illinois.cs.cs125.spring2020.mp.logic.WebApi;

/**
 * Represents the game activity, where the user plays the game and sees its state.
 */
public final class GameActivity extends AppCompatActivity {

    /** Tag for log entries. */
    private static final String TAG = "GameActivity";

    /** The radial location accuracy required to send a location update. */
    private static final float REQUIRED_LOCATION_ACCURACY = 28f;

    /** The handler for location updates sent by the location listener service. */
    private BroadcastReceiver locationUpdateReceiver;

    /** The current state of the game. */
    private int gameState = GameStateID.PAUSED;

    /** An object representing the game. */
    private Game game;

    /** A reference to the map control. */
    private GoogleMap map;

    /** Whether the user's location has been found and used to center the map. */
    private boolean centeredMap;

    /** The ID of the game being played. */
    private String gameId;

    /** The websocket used for gameplay events. */
    private WebSocket webSocket;

    /** Whether permission has been granted to access the phone's exact location. */
    private boolean hasLocationPermission;

    /**
     * Called by the Android system when the activity is to be set up.
     * @param savedInstanceState information from the previously terminated instance (unused)
     */
    @Override
    @SuppressWarnings("ConstantConditions")
    protected void onCreate(final Bundle savedInstanceState) {
        // The super.onCreate call is required for all activities
        super.onCreate(savedInstanceState);
        // Load the UI from a layout resource
        setContentView(R.layout.activity_game);
        Log.v(TAG, "Created");

        gameId = getIntent().getStringExtra("game");
        connectWebSocket();
        findViewById(R.id.reconnectWebsocket).setOnClickListener(unused -> connectWebSocket());
        findViewById(R.id.pauseUnpauseGame).setOnClickListener(unused -> toggleGameRunning());
        findViewById(R.id.endGame).setOnClickListener(unused -> endGame());

        // Start the process of getting a Google Maps object for the map
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.gameMap);
        mapFragment.getMapAsync(view -> {
            Log.v(TAG, "getMapAsync handler called");

            // Save the newly obtained map
            map = view;
            setUpMap();
        });

        // Prepare a handler that will be called when location updates are available
        locationUpdateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(final Context context, final Intent intent) {
                Location location = intent.getParcelableExtra(LocationListenerService.UPDATE_DATA_ID);
                if (map != null && location != null && location.hasAccuracy()
                        && location.getAccuracy() < REQUIRED_LOCATION_ACCURACY) {
                    ensureMapCentered(location);
                    onLocationUpdate(location);
                }
            }
        };
        // Register (activate) it
        LocalBroadcastManager.getInstance(this).registerReceiver(locationUpdateReceiver,
                new IntentFilter(LocationListenerService.UPDATE_ACTION));

        // See if we still need the location permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            // We don't have it yet - request it
            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.ACCESS_FINE_LOCATION}, 0);
            Log.v(TAG, "Requested location permission");
        } else {
            // We do have it - activate the features that require location
            Log.v(TAG, "Already had location permission");
            hasLocationPermission = true;
            startLocationWatching();
        }
    }

    /**
     * Called by the Android system when the activity is shut down and cannot be returned to.
     */
    @Override
    protected void onDestroy() {
        // The super call is required for all activities
        super.onDestroy();
        // Stop the location service
        stopLocationWatching();
        // Unregister this activity's location listener
        LocalBroadcastManager.getInstance(this).unregisterReceiver(locationUpdateReceiver);
        if (webSocket != null) {
            webSocket.disconnect(WebSocketCloseCode.AWAY);
        }
        Log.v(TAG, "Destroyed");
    }

    /**
     * Called by the Android system when the user has responded to a permissions request.
     * @param requestCode the request code passed to requestPermissions
     * @param permissions which permission(s) this notification is about
     * @param grantResults whether the user granted the permission(s)
     */
    @Override
    @SuppressLint("MissingPermission")
    public void onRequestPermissionsResult(final int requestCode, final @NonNull String[] permissions,
                                           final @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults); // Required by Android
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // We only ever request the location permission, so if we got here, the user granted that one
            Log.v(TAG, "User granted location permission");
            hasLocationPermission = true;
            if (map != null) {
                Log.v(TAG, "onRequestPermissionsResult enabled My Location");
                map.setMyLocationEnabled(true);
            }
            // Start the location listener service
            startLocationWatching();
        } else {
            Log.v(TAG, "Location permission was not granted");
        }
    }

    /**
     * Sets up the Google map.
     */
    @SuppressWarnings("MissingPermission")
    private void setUpMap() {
        // Enable the My Location blue dot if possible
        if (hasLocationPermission) {
            Log.v(TAG, "setUpMap enabled My Location");
            map.setMyLocationEnabled(true);
        }

        // Remove some UI that gets in the way
        map.getUiSettings().setIndoorLevelPickerEnabled(false);
        map.getUiSettings().setMapToolbarEnabled(false);

        // This function is no longer responsible for rendering game-specific elements
        // That's taken care of by the Game subclasses
    }

    /**
     * Centers the map on the user's location if the map hasn't been centered yet.
     * @param location the current location
     */
    private void ensureMapCentered(final Location location) {
        if (location != null && !centeredMap) {
            final float defaultMapZoom = 18f;
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(
                    new LatLng(location.getLatitude(), location.getLongitude()), defaultMapZoom));
            centeredMap = true;
            if (game != null && game.getMyTeam() == TeamID.OBSERVER) {
                stopLocationWatching();
            }
        }
    }

    /**
     * Called when a high-confidence location update is available.
     * @param location the phone's current location, not null
     */
    private void onLocationUpdate(final Location location) {
        // If the game object or websocket haven't been set yet, return (nothing can be done)
        // If the user is only an observer in the game, return (their movements don't matter)

        if (game == null || webSocket == null) {

            return;

        }

        if (game.getMyTeam() == TeamID.OBSERVER) {

            return;

        }

        if (gameState == GameStateID.RUNNING) {

            // Notify the server of the movement - start by creating a Gson JSON object representing the message
            JsonObject locUpdate = new JsonObject();
            // You need to fill the object out with the properties of a location update
            // Once the object is ready, convert it to a JSON string and send it over the websocket

            locUpdate.addProperty("type", "locationUpdate");
            locUpdate.addProperty("latitude", location.getLatitude());
            locUpdate.addProperty("longitude", location.getLongitude());

            webSocket.sendText(locUpdate.toString());

            // Call the logic that updates gameplay based on the user's movements

            game.locationUpdated(new LatLng(location.getLatitude(), location.getLongitude()));

        } else if (gameState == GameStateID.PAUSED) {

            // Notify the server of the movement - start by creating a Gson JSON object representing the message
            JsonObject locUpdate = new JsonObject();
            // You need to fill the object out with the properties of a location update
            // Once the object is ready, convert it to a JSON string and send it over the websocket

            locUpdate.addProperty("type", "locationUpdate");
            locUpdate.addProperty("latitude", location.getLatitude());
            locUpdate.addProperty("longitude", location.getLongitude());

            webSocket.sendText(locUpdate.toString());

        }


    }

    /**
     * Starts watching for location changes if possible under the current permissions.
     */
    @SuppressWarnings("MissingPermission")
    private void startLocationWatching() {
        if (!hasLocationPermission) {
            return;
        }
        if (map != null) {
            Log.v(TAG, "startLocationWatching enabled My Location");
            map.setMyLocationEnabled(true);
        }
        ContextCompat.startForegroundService(this, new Intent(this, LocationListenerService.class));
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    /**
     * Unregisters the location listener.
     */
    private void stopLocationWatching() {
        stopService(new Intent(this, LocationListenerService.class));
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    /**
     * Called when a message is received from the server.
     * <p>
     * You should fill out this function to react to game data, gameplay events, and game state changes.
     * @param message the parsed JSON from the server
     */
    private void receivedData(final JsonObject message) {
        String type = message.get("type").getAsString();
        switch (type) {
            case "full":
                // The full update contains the entire current state of the game
                String myEmail = FirebaseAuth.getInstance().getCurrentUser().getEmail();
                if (message.get("owner").getAsString().equals(myEmail)) {
                    findViewById(R.id.gameOwnerControls).setVisibility(View.VISIBLE);
                }

                // You need to fill this in to load the game progress into the game variable
                // Initialize the game instance variable with an instance of the Game subclass appropriate for the mode
                if (message.get("mode").getAsString().equals("target")) {

                    game = new TargetGame(myEmail, map, webSocket, message, this);

                } else if (message.get("mode").getAsString().equals("area")) {

                    game = new AreaGame(myEmail, map, webSocket, message, this);

                } else {

                    return;

                }

                if (game != null) {
                    if (game.getMyTeam() == TeamID.OBSERVER && centeredMap) {
                        // Observers don't need to have their location tracked
                        stopLocationWatching();
                    }
                    // Update the game state label
                    updateGameState(message.get("state").getAsInt());
                    // Update the scores label
                    updateScores();
                }
                break;
            case "gameState":
                int newState = message.get("state").getAsInt();
                if (newState != GameStateID.ENDED) {
                    // Since the game isn't over yet, update the scores label
                    updateGameState(newState);
                } else {

                    String winningTeam = game.getWinningTeamString();

                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setMessage(winningTeam + " wins the game!");
                    builder.setNegativeButton(R.string.cancel, (dialog, id) -> {
                        // User cancelled the dialog
                        finish();
                    });
                    builder.setOnDismissListener(unused -> {
                        finish();
                    });

                    builder.create().show();

                }
                // Near the end of the checkpoint, add an else branch to handle the end of the game
                break;
            default:
                // Fill this in to forward the event to the Game instance
                webSocket.sendText(message.toString());

                game.handleMessage(message);

                // Then refresh the scores label in case someone captured an objective
                updateScores();
        }
    }

    /**
     * Attempts to connect to the server via websocket.
     */
    private void connectWebSocket() {
        findViewById(R.id.reconnectWebsocket).setVisibility(View.GONE);
        findViewById(R.id.gameOwnerControls).setVisibility(View.GONE);
        TextView gameStateLabel = findViewById(R.id.gameState);
        gameStateLabel.setText(R.string.connecting);
        webSocket = null;
        WebApi.connectWebSocket(WebApi.WEBSOCKET_BASE + "/games/" + gameId + "/play",
            data -> runOnUiThread(() -> receivedData(data)),
            ws -> webSocket = ws,
            () -> runOnUiThread(this::connectWebSocket),
            error -> runOnUiThread(() -> {
                findViewById(R.id.reconnectWebsocket).setVisibility(View.VISIBLE);
                gameStateLabel.setText(R.string.connection_lost);
            }));
    }

    /**
     * Updates UI and listeners according to the state of the ongoing game.
     * @param newState the game's current state (PAUSED or RUNNING)
     */
    private void updateGameState(final int newState) {
        gameState = newState;
        TextView gameStateLabel = findViewById(R.id.gameState);
        Button pausePlayButton = findViewById(R.id.pauseUnpauseGame);
        if (newState == GameStateID.PAUSED) {
            gameStateLabel.setText(R.string.paused);
            pausePlayButton.setText(R.string.resume);
        } else if (newState == GameStateID.RUNNING) {
            gameStateLabel.setText(R.string.running);
            pausePlayButton.setText(R.string.pause);
        }
    }

    /**
     * Prompts the user (who is the game owner) whether to end the game.
     */
    private void endGame() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.end_game_confirmation);
        builder.setNegativeButton(R.string.cancel, null);
        builder.setPositiveButton(R.string.end, (unused1, unused2) -> gameLifecycleControl("end"));
        builder.create().show();
    }

    /**
     * Called when the user (who is the game owner) presses the Pause/Resume button.
     */
    private void toggleGameRunning() {
        if (gameState == GameStateID.PAUSED) {
            gameLifecycleControl("resume");
        } else {
            gameLifecycleControl("pause");
        }
    }

    /**
     * Makes an API call to control the lifecycle of the game.
     * @param action the game sub-endpoint: "resume", "pause", or "end"
     */
    private void gameLifecycleControl(final String action) {
        WebApi.startRequest(this, WebApi.API_BASE + "/games/" + gameId + "/" + action, Request.Method.POST, null,
            unused -> { }, error -> Toast.makeText(this, R.string.connection_failed, Toast.LENGTH_LONG).show());
    }

    /**
     * Updates the scores label according to the current game information.
     */
    private void updateScores() {
        if (game == null) {
            return;
        }
        String[] teamNames = getResources().getStringArray(R.array.team_choices);
        TextView scoresLabel = findViewById(R.id.gameScores);
        List<String> teamScores = new ArrayList<>();
        for (int t = TeamID.MIN_TEAM; t <= TeamID.MAX_TEAM; t++) {
            int score = game.getTeamScore(t);
            if (score > 0) {
                teamScores.add(teamNames[t] + ": " + score);
            }
        }
        if (teamScores.size() == 0) {
            scoresLabel.setText(R.string.no_scores);
        } else {
            scoresLabel.setText(TextUtils.join(", ", teamScores.toArray()));
        }
    }

}
