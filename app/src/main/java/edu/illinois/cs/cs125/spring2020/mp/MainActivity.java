package edu.illinois.cs.cs125.spring2020.mp;

import android.content.Intent;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.google.firebase.auth.FirebaseAuth;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import edu.illinois.cs.cs125.spring2020.mp.logic.GameSummary;
import edu.illinois.cs.cs125.spring2020.mp.logic.WebApi;

/**
 * Represents the main screen of the app, where the user can view and enter games.
 */
public final class MainActivity extends AppCompatActivity {

    /**
     * Called by the Android system when the activity is to be set up.
     * @param savedInstanceState info from the previously terminated instance (unused)
     */
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        // The super.onCreate call is required for all activities
        super.onCreate(savedInstanceState);
        // Set up the UI from the activity_main.xml layout resource
        setContentView(R.layout.activity_main);
        // Now that setContentView has been called, findViewById can find views

        findViewById(R.id.createGame).setOnClickListener(unused -> startActivity(
                new Intent(this, NewGameActivity.class)));

        findViewById(R.id.retryConnectButton).setOnClickListener(unused -> connect());
        connect();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        connect();
    }

    /**
     * Starts an attempt to connect to the server to fetch games.
     */
    private void connect() {
        findViewById(R.id.retryConnectButton).setVisibility(View.GONE);
        findViewById(R.id.invitationsGroup).setVisibility(View.GONE);
        findViewById(R.id.ongoingGamesGroup).setVisibility(View.GONE);
        findViewById(R.id.progressSpinner).setVisibility(View.VISIBLE);
        findViewById(R.id.loadGroup).setVisibility(View.VISIBLE);
        WebApi.startRequest(this, WebApi.API_BASE + "/games",
            response -> {
                findViewById(R.id.loadGroup).setVisibility(View.GONE);
                setUpUi(response);
            },
            error -> {
                findViewById(R.id.progressSpinner).setVisibility(View.GONE);
                findViewById(R.id.retryConnectButton).setVisibility(View.VISIBLE);
                Toast.makeText(this, R.string.connection_failed,
                        Toast.LENGTH_LONG).show();
            }
        );
    }

    /**
     * Sets up the UI with data retrieved from the server.
     * @param result parsed JSON from the server
     */
    private void setUpUi(final JsonObject result) {
        // Make the ongoing games group (which contains the Create Game button) visible
        // The invitations group will be made visible later if there are any invitations
        // Both were previously made gone by connect()
        findViewById(R.id.ongoingGamesGroup).setVisibility(View.VISIBLE);
        // Get references to, then clear, the games lists
        LinearLayout invitationsLayout = findViewById(R.id.invitationsList);
        invitationsLayout.removeAllViews();
        LinearLayout ongoingLayout = findViewById(R.id.ongoingGamesList);
        ongoingLayout.removeAllViews();

        String myEmail = FirebaseAuth.getInstance().getCurrentUser().getEmail();
        JsonArray games = result.getAsJsonArray("games");
        for (JsonElement g : games) {
            JsonObject game = g.getAsJsonObject();

            // Extract game information from the JSON using GameSummary
            GameSummary summary = new GameSummary(game);
            String gameId = summary.getId();
            String gameMode = summary.getMode();
            String gameOwner = summary.getOwner();
            String myRole = summary.getPlayerRole(myEmail, this);

            // Create the chunk according to the kind of game
            View chunk;
            if (summary.isOngoing(myEmail)) {
                // Use chunk_ongoing_game.xml for ongoing game entries
                chunk = getLayoutInflater().inflate(R.layout.chunk_ongoing_game, ongoingLayout,
                        false);
                // Add it to the ongoing games list
                ongoingLayout.addView(chunk);
                // Get buttons specific to ongoing games
                Button enter = chunk.findViewById(R.id.enterGame);

                enter.setOnClickListener(v -> {

                    Intent intent = new Intent(this, GameActivity.class);

                    intent.putExtra("game", gameId);

                    startActivity(intent);

                    finish();

                });

                Button leave = chunk.findViewById(R.id.leaveGame);

                leave.setOnClickListener(v -> {

                    WebApi.startRequest(this, WebApi.API_BASE + "/games/"
                            + gameId + "/leave", Request.Method.POST, null, response -> {
                            // response code handler similar to a GET request
                            connect();
                        }, error -> {
                            Toast.makeText(this, error.getMessage(),
                                    Toast.LENGTH_LONG).show();
                        });



                });



                // The Leave button should be gone if the user owns the game
                if (gameOwner.equals(myEmail)) {
                    leave.setVisibility(View.GONE);
                }
                // The labels (which are the same as in invitations) will be set later
            } else if (summary.isInvitation(myEmail)) {
                // There is at least one invitation, so make the invitations group visible
                findViewById(R.id.invitationsGroup).setVisibility(View.VISIBLE);
                // Use chunk_invitation.xml for invitation entries
                chunk = getLayoutInflater().inflate(R.layout.chunk_invitation, invitationsLayout,
                        false);
                // Add it to the invitations list
                invitationsLayout.addView(chunk);
                // Get buttons specific to invitations?
                Button invite = chunk.findViewById(R.id.acceptInvite);
                Button decline = chunk.findViewById(R.id.declineInvite);

                invite.setOnClickListener(v -> {

                    WebApi.startRequest(this, WebApi.API_BASE + "/games/" + gameId
                                    + "/accept", Request.Method.POST,
                            null, response -> {
                        // response code handler similar to a GET request
                            connect();
                        }, error -> {
                            Toast.makeText(this, error.getMessage(),
                                    Toast.LENGTH_LONG).show();
                        });



                });

                decline.setOnClickListener(v -> {

                    WebApi.startRequest(this, WebApi.API_BASE + "/games/" + gameId
                            + "/decline", Request.Method.POST, null, response -> {
                        // response code handler similar to a GET request
                            connect();

                        }, error -> {
                            Toast.makeText(this, error.getMessage(),
                                    Toast.LENGTH_LONG).show();
                        });



                });


            } else {
                // Avoid the label-setting code below, since no chunk was created
                continue;
            }

            // The labels for game owner and role are common to both chunks, so we can reduce code
            // duplication
            TextView ownerLabel = chunk.findViewById(R.id.gameOwner);
            ownerLabel.setText(getResources().getString(R.string.game_manager, gameOwner));
            TextView infoLabel = chunk.findViewById(R.id.gameRole); // Shows both the user's role
            // and the game mode
            infoLabel.setText(getResources().getString(R.string.game_role, myRole, gameMode));
        }
    }

}
