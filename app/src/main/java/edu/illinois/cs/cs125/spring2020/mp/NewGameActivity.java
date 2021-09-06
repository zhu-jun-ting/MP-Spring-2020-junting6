package edu.illinois.cs.cs125.spring2020.mp;

import android.content.Intent;
import android.graphics.Point;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

import edu.illinois.cs.cs125.spring2020.mp.logic.GameSetup;
import edu.illinois.cs.cs125.spring2020.mp.logic.Invitee;
import edu.illinois.cs.cs125.spring2020.mp.logic.TeamID;
import edu.illinois.cs.cs125.spring2020.mp.logic.WebApi;

/**
 * Represents the game creation screen, where the user configures a new game.
 */
@SuppressWarnings("ConstantConditions")
public final class NewGameActivity extends AppCompatActivity {

    /** The list of invitees added so far. */
    private List<Invitee> invitees = new ArrayList<>();

    /** The Google Maps view used to set the area for area mode. */
    private GoogleMap areaMap;

    /** The Google Maps view used to manage targets for area mode. */
    private GoogleMap targetsMap;

    /** Markers on the target map representing targets. */
    private List<Marker> targets = new ArrayList<>();

    /** The group of radio buttons that allow setting the game mode. */
    private RadioGroup modeGroup;

    /** The target settings. */
    private LinearLayout targetSettings;

    /** The area settings. */
    private LinearLayout areaSettings;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); // Required by Android
        setContentView(R.layout.activity_new_game); // Loads the UI, now findViewById can work
        setTitle(R.string.create_game);

        modeGroup = findViewById(R.id.gameModeGroup);

        // Register button click handlers on the add-invitee and create-game buttons
        findViewById(R.id.addInvitee).setOnClickListener(unused -> addInvitee());
        findViewById(R.id.createGame).setOnClickListener(unused -> tryCreate());
        // Prepare the area selection map
        SupportMapFragment areaMapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.areaSizeMap);
        areaMapFragment.getMapAsync(newMap -> {
            // This handler will run when the area map is ready
            // Store the map in an instance variable for later
            areaMap = newMap;
            // Center it on campustown
            centerMap(areaMap);
        });
        // Similarly prepare the target mode setup map
        SupportMapFragment targetMapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.targetsMap);
        targetMapFragment.getMapAsync(newMap -> {
            // This handler will run when the targets map is ready
            targetsMap = newMap;
            centerMap(targetsMap);
            // Now that we have a ready map to work with, set handlers on it to respond to use interactions
            targetsMap.setOnMapLongClickListener(point -> {
                // Pressing (long-clicking) a point on a map should add a target there
                // Place a marker on the map so the user can see their new target
                Marker marker = targetsMap.addMarker(new MarkerOptions().position(point).draggable(true));
                // Save the marker for later (so we can get targets' locations when creating the game)
                targets.add(marker);
            });
            targetsMap.setOnMarkerClickListener(marker -> {
                // Clicking a target marker should remove the target
                // Remove the marker from the map so the user sees the target was removed
                marker.remove();
                // Also remove the target from our list so our code knows the target was removed
                targets.remove(marker);
                // Suppress the default Google Maps pan
                return true;
            });
        });

        // At first, the only invitee is the user with role set to observer
        // Add them to the invitees list as such
        invitees.add(new Invitee(FirebaseAuth.getInstance().getCurrentUser().getEmail(), TeamID.OBSERVER));
        // Then populate the invitees UI so the user can see that
        updateInviteeUi();

        targetSettings = findViewById(R.id.targetSettings);
        areaSettings = findViewById(R.id.areaSettings);

        targetSettings.setVisibility(View.GONE);
        areaSettings.setVisibility(View.GONE);

        modeGroup = findViewById(R.id.gameModeGroup);
        modeGroup.setOnCheckedChangeListener((unused, checkedId) -> {
            // checkedId is the R.id constant of the currently checked RadioButton
            // Your code here: make only the selected mode's settings group visible

            targetSettings.setVisibility(View.GONE);
            areaSettings.setVisibility(View.GONE);

            if (checkedId == R.id.targetModeOption) {

                targetSettings.setVisibility((View.VISIBLE));

            } else if (checkedId == R.id.areaModeOption) {

                areaSettings.setVisibility((View.VISIBLE));

            }

        });

    }

    /**
     * Adds the just-entered player to the invitee list.
     */
    private void addInvitee() {
        // Find the email the user entered into the new-invitee text box
        EditText emailText = findViewById(R.id.newInviteeEmail);
        String email = emailText.getText().toString();

        if (!email.trim().equals("")) {
            // As long as the user entered something, we can add an invitee
            // Record the new invitee so our code can keep track of it
            invitees.add(new Invitee(email.toLowerCase().trim(), TeamID.OBSERVER));
            // Rerender the invitees list so the user sees the change
            updateInviteeUi();
            // Clear the text box so the user can enter another email easily
            emailText.setText("");
        }
    }

    /**
     * Updates the Players list from the invitees list instance variable so the user can see
     * the most current invitees list.
     */
    private void updateInviteeUi() {
        // Get a reference to the LinearLayout which will hold chunks of UI about the invitees
        LinearLayout inviteesList = findViewById(R.id.playersList);
        // We're going to completely re-set-up the layout here, so remove everything currently inside it
        inviteesList.removeAllViews();

        // We need to add an entry to the LinearLayout for each invitee
        for (int i = 0; i < invitees.size(); i++) {
            // Create a chunk of UI for this current invitee
            Invitee player = invitees.get(i);
            View chunk = getLayoutInflater().inflate(R.layout.chunk_invitee, inviteesList, false);

            // Display the invitee's email
            TextView emailText = chunk.findViewById(R.id.inviteeEmail);
            emailText.setText(player.getEmail());

            // Show and allow changing the user's team/role in a dropdown
            Spinner teamSpinner = chunk.findViewById(R.id.inviteeTeam);
            teamSpinner.setSelection(player.getTeamId());
            // Set a handler to run when the user changes the dropdown's selection
            teamSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(final AdapterView<?> parent, final View view,
                                           final int position, final long id) {
                    // Android already changed which item is shown in the dropdown list
                    // Record the new role in the Invitee object so our code can keep track of it
                    player.setTeamId(position);
                }
                @Override
                public void onNothingSelected(final AdapterView<?> parent) {
                    // Do nothing
                }
            });

            // Set up a button to allow removing the invitee
            Button removeButton = chunk.findViewById(R.id.removeInvitee);
            if (i == 0) {
                // The user is always the first invitee and should not be removable
                removeButton.setVisibility(View.GONE);
            } else {
                // Attach a click handler on the Remove button
                removeButton.setOnClickListener(unused -> {
                    // Remove the Invitee instance from the invitees list (for our code)
                    invitees.remove(player);
                    // Then update the UI (so the user can see the change)
                    updateInviteeUi();
                });
            }

            // Add the UI chunk to the list
            inviteesList.addView(chunk);
        }
    }

    /**
     * Sets up the area sizing map with initial settings: centering on campustown.
     * @param map the map to center
     */
    private void centerMap(final GoogleMap map) {
        // Bounds of campustown and some surroundings
        final double swLatitude = 40.098331;
        final double swLongitude = -88.246065;
        final double neLatitude = 40.116601;
        final double neLongitude = -88.213077;

        // Get the window dimensions (for the width)
        Point windowSize = new Point();
        getWindowManager().getDefaultDisplay().getSize(windowSize);

        // Convert 300dp (height of map control) to pixels
        final int mapHeightDp = 300;
        float heightPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, mapHeightDp,
                getResources().getDisplayMetrics());

        // Schedule the camera update
        final int paddingPx = 10;
        map.moveCamera(CameraUpdateFactory.newLatLngBounds(new LatLngBounds(
                new LatLng(swLatitude, swLongitude),
                new LatLng(neLatitude, neLongitude)), windowSize.x, (int) heightPx, paddingPx));
    }

    /**
     * Attempts to create the game, displaying a toast if there is a problem.
     */
    private void tryCreate() {
        String result = create();
        if (result != null) {
            Toast.makeText(this, result, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Attempts to create the game, returning information on any error that occurred.
     * @return a human-readable error message if there was a problem, null if everything is OK
     */
    private String create() {
        JsonObject request;
        // Determine which game mode, if any, is selected by looking at the ID of the chosen radio button
        switch (modeGroup.getCheckedRadioButtonId()) {
            case R.id.areaModeOption:
                int cellSize;
                try {
                    // A try block allows us to handle problems so they don't cause a crash
                    EditText cellSizeBox = findViewById(R.id.cellSize);
                    // Text boxes hold strings, so we need to parse the text as a number (which can fail)
                    cellSize = Integer.parseInt(cellSizeBox.getText().toString());
                } catch (NumberFormatException e) {
                    // If there is a problem (from parseInt in this case), a catch block can run
                    return "Cell size must be a valid number.";
                }
                request = GameSetup.areaMode(invitees,
                        areaMap.getProjection().getVisibleRegion().latLngBounds, cellSize);
                break;
            case R.id.targetModeOption:
                int proximityThreshold;
                try {
                    EditText proximityBox = findViewById(R.id.proximityThreshold);
                    proximityThreshold = Integer.parseInt(proximityBox.getText().toString());
                } catch (NumberFormatException e) {
                    return "Proximity threshold must be a valid number.";
                }
                List<LatLng> targetPositions = new ArrayList<>();
                for (Marker m : targets) {
                    targetPositions.add(m.getPosition());
                }
                request = GameSetup.targetMode(invitees, targetPositions, proximityThreshold);
                break;
            default:
                return "You must specify the game mode.";
        }
        if (request == null) {
            return "Game setup is invalid.";
        }

        // Now that the setup has been validated, we can try to ask the server to create the game
        Button createButton = findViewById(R.id.createGame);
        // Temporarily disable the button so the user doesn't press it again during the request
        createButton.setEnabled(false);
        // Start the POST request, uploading the game setup JSON created by GameSetup
        WebApi.startRequest(this, WebApi.API_BASE + "/games/create", Request.Method.POST, request,
            response -> {
                // Success - put the game ID in an intent to launch GameActivity
                Intent launchIntent = new Intent(this, GameActivity.class);
                launchIntent.putExtra("game", response.get("game").getAsString());
                startActivity(launchIntent);
                // End this game setup activity
                finish();
            },
            error -> {
                // Request failed - show the error and allow the user to try again
                createButton.setEnabled(true);
                Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
            });
        // Tell tryCreate that we successfully started the web request
        // The request may or may not receive a success response when it eventually completes
        return null;
    }

}
