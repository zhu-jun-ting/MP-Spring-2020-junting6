package edu.illinois.cs.cs125.spring2020.mp;

import android.app.Dialog;
import android.content.Intent;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import androidx.annotation.IdRes;
import androidx.test.core.app.ApplicationProvider;

import com.android.volley.Request;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.Polyline;
import com.google.firebase.auth.FirebaseAuth;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.rule.PowerMockRule;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowDialog;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import edu.illinois.cs.cs125.spring2020.mp.logic.GameSetup;
import edu.illinois.cs.cs125.spring2020.mp.logic.Invitee;
import edu.illinois.cs.cs125.spring2020.mp.logic.LatLngUtils;
import edu.illinois.cs.cs125.spring2020.mp.logic.LineCrossDetector;
import edu.illinois.cs.cs125.spring2020.mp.logic.Target;
import edu.illinois.cs.cs125.spring2020.mp.logic.TeamID;
import edu.illinois.cs.cs125.spring2020.mp.logic.WebApi;
import edu.illinois.cs.cs125.spring2020.mp.shadows.MockedWrapperInstantiator;
import edu.illinois.cs.cs125.spring2020.mp.shadows.ShadowGoogleMap;
import edu.illinois.cs.cs125.spring2020.mp.shadows.ShadowLog;
import edu.illinois.cs.cs125.spring2020.mp.shadows.ShadowMarker;
import edu.illinois.cs.cs125.spring2020.mp.shadows.ShadowSupportMapFragment;
import edu.illinois.cs.cs125.gradlegrader.annotations.Graded;
import edu.illinois.cs.cs125.robolectricsecurity.PowerMockSecurity;
import edu.illinois.cs.cs125.robolectricsecurity.Trusted;

@RunWith(RobolectricTestRunner.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@PowerMockIgnore({"org.mockito.*", "org.powermock.*", "org.robolectric.*", "android.*", "androidx.*", "com.google.android.*", "edu.illinois.cs.cs125.spring2020.mp.shadows.*"})
@PrepareForTest({WebApi.class, FirebaseAuth.class})
@Trusted
@Config(sdk = 28)
public class Checkpoint3Test {

    @Rule
    public PowerMockRule mockStaticClasses = new PowerMockRule();

    @Before
    public void setup() {
        PowerMockSecurity.secureMockMethodCache();
        FirebaseMocker.mock();
        FirebaseMocker.setEmail(SampleData.USER_EMAIL);
        WebApiMocker.interceptHttp();
        ShadowLog.loadConfig("../log.yaml");
    }

    @After
    public void teardown() {
        WebApiMocker.reset();
    }

    @Test(timeout = 60000)
    @Graded(points = 15)
    public void testTargetClass() {
        // Create the map
        GoogleMap map = MockedWrapperInstantiator.create(GoogleMap.class);
        ShadowGoogleMap shadowMap = Shadow.extract(map);

        // Check class design
        for (Field field : Target.class.getDeclaredFields()) {
            Assert.assertTrue("Target's fields should be private", Modifier.isPrivate(field.getModifiers()));
            if (!Modifier.isFinal(field.getModifiers())) {
                Assert.assertFalse("Target's variables should not be static", Modifier.isStatic(field.getModifiers()));
            }
        }

        // Test constructor/getters
        LatLng position = new LatLng(40.097249, -88.229229);
        Target target = new Target(map, position, TeamID.OBSERVER);
        Assert.assertEquals("Target didn't remember the team", TeamID.OBSERVER, target.getTeam());
        Assert.assertTrue("Target didn't remember the position", LatLngUtils.same(position, target.getPosition()));

        // Make sure it added a marker to the map
        Assert.assertEquals("Creating a Target should add a marker to the map", 1, shadowMap.getMarkers().size());
        Marker marker = shadowMap.getMarkers().get(0);
        Assert.assertTrue("Creating a Target placed the marker at the wrong location", LatLngUtils.same(position, marker.getPosition()));
        ShadowMarker shadowMarker = Shadow.extract(marker);
        Assert.assertEquals("Unclaimed target markers should be violet",
                BitmapDescriptorFactory.HUE_VIOLET, shadowMarker.getHue(), 1e-3);

        // Claim the target
        target.setTeam(TeamID.TEAM_BLUE);
        Assert.assertEquals("Target didn't remember the updated team", TeamID.TEAM_BLUE, target.getTeam());
        Assert.assertEquals("Updating a Target's team should not create duplicate markers", 1, shadowMap.getMarkers().size());
        marker = shadowMap.getMarkers().get(0);
        Assert.assertTrue("Updating a Target's team should not change the location", LatLngUtils.same(position, marker.getPosition()));
        shadowMarker = Shadow.extract(marker);
        Assert.assertEquals("Setting a target's team should turn the marker the team color",
                BitmapDescriptorFactory.HUE_BLUE, shadowMarker.getHue(), 1e-3);

        // Try other colors
        target.setTeam(TeamID.TEAM_YELLOW);
        shadowMarker = Shadow.extract(shadowMap.getMarkers().get(0));
        Assert.assertEquals("Changing a target's team should turn the marker the new team color",
                BitmapDescriptorFactory.HUE_YELLOW, shadowMarker.getHue(), 1e-3);
        target.setTeam(TeamID.TEAM_GREEN);
        shadowMarker = Shadow.extract(shadowMap.getMarkers().get(0));
        Assert.assertEquals("Changing a target's team should turn the marker the new team color",
                BitmapDescriptorFactory.HUE_GREEN, shadowMarker.getHue(), 1e-3);
        target.setTeam(TeamID.TEAM_RED);
        shadowMarker = Shadow.extract(shadowMap.getMarkers().get(0));
        Assert.assertEquals("Changing a target's team should turn the marker the new team color",
                BitmapDescriptorFactory.HUE_RED, shadowMarker.getHue(), 1e-3);
        Assert.assertEquals("Changing a Target's team should not create duplicate markers",
                1, shadowMap.getMarkers().size());

        // Try several markers
        for (int i = 2; i <= 25; i++) {
            LatLng randomPos = new LatLng(RandomHelper.randomLat(), RandomHelper.randomLng());
            int team = RandomHelper.randomTeam();
            target = new Target(map, randomPos, team);
            Assert.assertEquals("Target didn't remember the team", team, target.getTeam());
            Assert.assertTrue("Target didn't remember the position", LatLngUtils.same(randomPos, target.getPosition()));
            Assert.assertEquals("Creating more targets shouldn't create duplicates or affect existing targets", i, shadowMap.getMarkers().size());
            marker = shadowMap.getMarkerAt(randomPos);
            Assert.assertNotNull("Creating another Target placed the marker at the wrong location", marker);
            shadowMarker = Shadow.extract(marker);
            Assert.assertNotEquals("Target markers should be the team color", BitmapDescriptorFactory.HUE_VIOLET, shadowMarker.getHue());
        }
    }

    @Test(timeout = 60000)
    @Graded(points = 15)
    @SuppressWarnings({"JavaReflectionMemberAccess", "ConstantConditions"})
    public void testLatLngRefactor() throws Throwable {
        // Make sure addLine was refactored or removed
        try {
            GameActivity.class.getMethod("addLine", double.class, double.class, double.class, double.class, int.class);
            Assert.fail("addLine's four double parameters should be refactored into two LatLng parameters");
        } catch (NoSuchMethodException e) {
            // Good - the old method should no longer exist
        }

        // Start the activity
        WebSocketMocker webSocketControl = WebSocketMocker.expectConnection();
        Intent intent = new Intent();
        intent.putExtra("game", RandomHelper.randomId());
        intent.putExtra("mode", "target");
        intent.putExtra("proximityThreshold", 15);
        GameActivityLauncher launcher = new GameActivityLauncher(intent);

        // Test addLine
        if (webSocketControl.isConnected()) {
            // Working on a late Checkpoint 4 submission
            testLatLngRefactorC4(webSocketControl, launcher);
        } else {
            // Working on Checkpoint 3
            Method refactoredAddLine;
            try {
                refactoredAddLine = GameActivity.class.getMethod("addLine", LatLng.class, LatLng.class, int.class);
                int[] teamColors = ApplicationProvider.getApplicationContext().getResources().getIntArray(R.array.team_colors);
                ShadowGoogleMap map = Shadow.extract(launcher.getMap());
                for (int i = 0; i < 10; i++) {
                    LatLng start = new LatLng(RandomHelper.randomLat(), RandomHelper.randomLng());
                    LatLng end = new LatLng(RandomHelper.randomLat(), RandomHelper.randomLng());
                    int color = teamColors[RandomHelper.randomTeam()];
                    refactoredAddLine.invoke(launcher.getActivity(), start, end, color);
                    Assert.assertNotEquals("addLine should add a line to the map", 0, map.getPolylines().size());
                    Assert.assertEquals("Each line should consist of two Polyline objects", (i + 1) * 2, map.getPolylines().size());
                    Assert.assertEquals("addLine didn't add the line with the correct endpoints", 2, map.getPolylinesConnecting(start, end).size());
                }
            } catch (NoSuchMethodException e) {
                // Actually a malfunctioning Checkpoint 4 submission
                Assert.fail("No known addLine signature was found and no Checkpoint 4 websocket connection was attempted");
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }

        // Make sure linesCross was refactored
        try {
            String linesCrossFail = "The refactored linesCross function is incorrect";
            Method linesCrossMethod = LineCrossDetector.class.getMethod("linesCross", LatLng.class, LatLng.class, LatLng.class, LatLng.class);
            Assert.assertFalse(linesCrossFail, (Boolean) linesCrossMethod.invoke(null,
                    new LatLng(40.1, -88.6), new LatLng(40.5, -89.0),
                    new LatLng(40.6, -88.1), new LatLng(40.9, -88.2)));
            Assert.assertTrue(linesCrossFail, (Boolean) linesCrossMethod.invoke(null,
                    new LatLng(40.2, -88.3), new LatLng(40.2, -88.7),
                    new LatLng(40.1, -88.5), new LatLng(40.3, -88.5)));

            // Randomized tests (from JSON)
            for (JsonObject test : JsonResourceLoader.loadArray("linescross")) {
                double startLat1 = test.get("sla1").getAsDouble();
                double startLng1 = test.get("sln1").getAsDouble();
                double endLat1 = test.get("ela1").getAsDouble();
                double endLng1 = test.get("eln1").getAsDouble();
                double startLat2 = test.get("sla2").getAsDouble();
                double startLng2 = test.get("sln2").getAsDouble();
                double endLat2 = test.get("ela2").getAsDouble();
                double endLng2 = test.get("eln2").getAsDouble();
                Assert.assertEquals(linesCrossFail, test.get("answer").getAsBoolean(),
                        linesCrossMethod.invoke(null, new LatLng(startLat1, startLng1), new LatLng(endLat1, endLng1),
                                new LatLng(startLat2, startLng2), new LatLng(endLat2, endLng2)));
            }
        } catch (NoSuchMethodException e) {
            Assert.fail("LineCrossDetector.linesCross should now take four LatLng parameters");
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
        for (Method method : LineCrossDetector.class.getDeclaredMethods()) {
            int coordParams = 0;
            for (Class<?> paramType : method.getParameterTypes()) {
                if (paramType.equals(double.class) || paramType.equals(float.class)) coordParams++;
            }
            Assert.assertFalse("The line-crossing detection function that took eight loose coordinates should be removed", coordParams >= 8);
        }
    }

    private void testLatLngRefactorC4(WebSocketMocker webSocketControl, GameActivityLauncher launcher) {
        StackTraceElement[][] stackTraceHolder = new StackTraceElement[1][];
        Shadow.<ShadowGoogleMap>extract(launcher.getMap()).setComponentAdditionListener(obj -> {
            if (!(obj instanceof Polyline)) return;
            stackTraceHolder[0] = Thread.currentThread().getStackTrace();
            boolean foundAddPolyline = false;
            for (StackTraceElement frame : stackTraceHolder[0]) {
                if (frame.getMethodName().equals("addPolyline")) {
                    foundAddPolyline = true;
                } else if (foundAddPolyline) {
                    Assert.assertNotEquals("GameActivity should no longer be directly responsible for rendering the map",
                            GameActivity.class.getName(), frame.getClassName());
                    return;
                }
            }
            Assert.fail("Impossible: addPolyline not found in stack trace of polyline addition");
        });
        webSocketControl.sendData(SampleData.createTargetModeTestGame());
        Assert.assertNotNull("A Checkpoint 4 websocket connection was made but no path polylines were drawn", stackTraceHolder[0]);
    }

    @Test(timeout = 60000)
    @Graded(points = 20, friendlyName = "testTargetPresets (optional)")
    @SuppressWarnings("ConstantConditions")
    public void testTargetPresets_extraCredit() {
        // Get IDs
        @IdRes int rIdTargetsMap = IdLookup.require("targetsMap");
        @IdRes int rIdTargetModeOption = IdLookup.require("targetModeOption");
        @IdRes int rIdLoadPresetTargets = IdLookup.require("loadPresetTargets");

        // Start the activity
        NewGameActivity activity = Robolectric.buildActivity(NewGameActivity.class).create().start().resume().get();
        ShadowSupportMapFragment mapFragment = Shadow.extract(activity.getSupportFragmentManager()
                .findFragmentById(rIdTargetsMap));
        mapFragment.notifyMapReady();
        ShadowGoogleMap map = Shadow.extract(mapFragment.getMap());

        // Select target mode
        RadioButton targetOption = activity.findViewById(rIdTargetModeOption);
        targetOption.setChecked(true);

        // Click the Presets button to open the dialog
        JsonObject presetsResponse = SampleData.createTargetPresetsResponse();
        Button loadPreset = activity.findViewById(rIdLoadPresetTargets);
        Assert.assertEquals("The Load Preset button should be visible when target mode is selected",
                View.VISIBLE, loadPreset.getVisibility());
        ShadowDialog.reset();
        loadPreset.performClick();
        WebApiMocker.processOne("Clicking Load Preset should request the target presets from the server",
                (path, method, body, callback, errorListener) -> {
                    Assert.assertEquals("Incorrect API endpoint for getting target presets", "/presets", path);
                    Assert.assertEquals("/presets should be accessed with a GET request", Request.Method.GET, method);
                    Assert.assertNull("No request body may be specified for /presets", body);
                    callback.onResponse(presetsResponse.deepCopy());
                });
        Dialog dialog = ShadowDialog.getLatestDialog();
        Assert.assertNotNull("A dialog should show the available presets", dialog);
        @IdRes int rIdPresetOptions = IdLookup.require("presetOptions");
        RadioGroup radioGroup = dialog.findViewById(rIdPresetOptions);
        Assert.assertNotNull("The dialog should have a RadioGroup to hold the preset options", radioGroup);
        Assert.assertEquals("The RadioGroup should have one entry per preset", 2, radioGroup.getChildCount());
        RadioButton presetOption = (RadioButton) radioGroup.getChildAt(0);
        Assert.assertEquals("Each RadioButton should show the name of its preset", "Empty", presetOption.getText().toString());
        presetOption = (RadioButton) radioGroup.getChildAt(1);
        Assert.assertEquals("Each RadioButton should show the name of its preset", "Walmart Stores", presetOption.getText().toString());
        Button positiveButton = dialog.findViewById(android.R.id.button1);
        Assert.assertEquals("The presets dialog's positive button's title is incorrect",
                "LOAD", positiveButton.getText().toString().toUpperCase());
        Button neutralButton = dialog.findViewById(android.R.id.button3);
        if (neutralButton != null) {
            Assert.assertEquals("The presets dialog should not have a neutral button", View.GONE, neutralButton.getVisibility());
        }

        // Select the second preset
        presetOption.setChecked(true);
        positiveButton.performClick();
        Assert.assertTrue("Choosing a preset and pressing Load should dismiss the dialog", Shadows.shadowOf(dialog).hasBeenDismissed());
        Assert.assertEquals("Loading a preset should add its targets to the map", 3, map.getMarkers().size());
        for (LatLng position : new LatLng[] {
                new LatLng(40.146879, -88.254737),
                new LatLng(40.048544, -88.254927),
                new LatLng(40.111625, -88.159373)}) {
            Marker marker = map.getMarkerAt(position);
            Assert.assertNotNull("Loading a preset should place markers at the targets' positions", marker);
        }

        // Select an empty preset
        loadPreset.performClick();
        WebApiMocker.process((path, method, body, callback, errorListener) -> {
            Assert.assertEquals("Incorrect path for target presets endpoint", "/presets", path);
            callback.onResponse(presetsResponse);
        });
        dialog = ShadowDialog.getLatestDialog();
        radioGroup = dialog.findViewById(rIdPresetOptions);
        presetOption = (RadioButton) radioGroup.getChildAt(0);
        presetOption.setChecked(true);
        dialog.findViewById(android.R.id.button1).performClick();
        Assert.assertTrue("Pressing Load with a selected preset should dismiss the dialog", Shadows.shadowOf(dialog).hasBeenDismissed());
        Assert.assertEquals("Loading a preset should clear previously set targets", 0, map.getMarkers().size());

        // Test randomized presets
        for (int run = 0; run < 10; run++) {
            // Start the activity and get controls
            activity = Robolectric.buildActivity(NewGameActivity.class).create().start().resume().get();
            mapFragment = Shadow.extract(activity.getSupportFragmentManager().findFragmentById(rIdTargetsMap));
            mapFragment.notifyMapReady();
            map = Shadow.extract(mapFragment.getMap());
            targetOption = activity.findViewById(rIdTargetModeOption);
            targetOption.setChecked(true);
            loadPreset = activity.findViewById(rIdLoadPresetTargets);

            // Create test data
            Random random = new Random();
            JsonObject randomPresetsResponse = new JsonObject();
            JsonArray presets = new JsonArray();
            int presetsCount = random.nextInt(10) + 1;
            int selectedPreset = random.nextInt(presetsCount);
            LatLng[] selectedTargetPositions = null;
            String selectedPresetName = null;
            for (int i = 0; i < presetsCount; i++) {
                JsonObject preset = new JsonObject();
                String name = RandomHelper.randomId();
                preset.addProperty("name", name);
                JsonArray targets = new JsonArray();
                int targetsCount = random.nextInt(196);
                if (i == selectedPreset) {
                    selectedTargetPositions = new LatLng[targetsCount];
                    selectedPresetName = name;
                }
                for (int j = 0; j < targetsCount; j++) {
                    double lat = RandomHelper.randomLat();
                    double lng = RandomHelper.randomLat();
                    targets.add(JsonHelper.position(lat, lng));
                    if (i == selectedPreset) {
                        selectedTargetPositions[j] = new LatLng(lat, lng);
                    }
                }
                preset.add("targets", targets);
                presets.add(preset);
            }
            randomPresetsResponse.add("presets", presets);

            // Use the dialog
            ShadowDialog.reset();
            loadPreset.performClick();
            WebApiMocker.process((path, method, body, callback, errorListener) -> {
                Assert.assertEquals("Incorrect path for target presets endpoint", "/presets", path);
                callback.onResponse(randomPresetsResponse);
            });
            dialog = ShadowDialog.getLatestDialog();
            radioGroup = dialog.findViewById(rIdPresetOptions);
            Assert.assertEquals("The presets RadioGroup should have one entry per preset",
                    presetsCount, radioGroup.getChildCount());
            presetOption = (RadioButton) radioGroup.getChildAt(selectedPreset);
            Assert.assertEquals("Each preset RadioButton should have the preset's name",
                    selectedPresetName, presetOption.getText().toString());
            presetOption.setChecked(true);
            dialog.findViewById(android.R.id.button1).performClick();

            // Check the map
            Assert.assertEquals("Loading a preset should place its targets on the map",
                    selectedTargetPositions.length, map.getMarkers().size());
            for (LatLng pos : selectedTargetPositions) {
                Marker marker = map.getMarkerAt(pos);
                Assert.assertNotNull("Loading a preset should place markers on the targets' positions on the map", marker);
            }
        }
    }

    @Test(timeout = 60000)
    @Graded(points = 25)
    public void testJsonTargetMode() {
        // Test argument validation
        Assert.assertNull("targetMode should return null if there are no invitees", GameSetup.targetMode(
                new ArrayList<>(), Collections.singletonList(RandomHelper.randomPoint()), 50));
        Assert.assertNull("targetMode should return null if there are no targets", GameSetup.targetMode(
                Collections.singletonList(new Invitee(SampleData.USER_EMAIL, RandomHelper.randomRole())),
                new ArrayList<>(), 50));
        Assert.assertNull("targetMode should return null if the proximity threshold is invalid", GameSetup.targetMode(
                Collections.singletonList(new Invitee(SampleData.USER_EMAIL, RandomHelper.randomRole())),
                Collections.singletonList(RandomHelper.randomPoint()), -10));
        Assert.assertNull("targetMode should return null if the proximity threshold is invalid", GameSetup.targetMode(
                Collections.singletonList(new Invitee(SampleData.USER_EMAIL, RandomHelper.randomRole())),
                Collections.singletonList(RandomHelper.randomPoint()), 0));
        Assert.assertNull("targetMode should return null if the configuration is invalid", GameSetup.targetMode(
                new ArrayList<>(), new LinkedList<>(), 0));

        // Test a simple setup
        JsonObject result = GameSetup.targetMode(
                Collections.singletonList(new Invitee(SampleData.USER_EMAIL, TeamID.OBSERVER)),
                Collections.singletonList(new LatLng(40.108585, -88.228874)), 50);
        Assert.assertNotNull("targetMode should not return null when the configuration is valid", result);
        Assert.assertTrue("The game setup JSON should specify the game mode", result.has("mode"));
        Assert.assertEquals("The mode should be 'target' for target mode",
                "target", result.get("mode").getAsString());
        Assert.assertTrue("Target mode JSON should specify the proximity threshold",
                result.has("proximityThreshold"));
        Assert.assertTrue("The 'proximityThreshold' property should hold an integer",
                result.get("proximityThreshold").isJsonPrimitive() && result.getAsJsonPrimitive("proximityThreshold").isNumber());
        Assert.assertEquals("Incorrect proximity threshold in target mode JSON",
                50, result.get("proximityThreshold").getAsInt());
        Assert.assertTrue("Target mode JSON should have a 'targets' property",
                result.has("targets"));
        Assert.assertTrue("The 'targets' property should hold a JSON array",
                result.get("targets").isJsonArray());
        JsonArray targets = result.getAsJsonArray("targets");
        Assert.assertNotEquals("The targets array should not be empty", 0, targets.size());
        Assert.assertEquals("The targets array had extra entries", 1, targets.size());
        Assert.assertTrue("The targets array should contain objects", targets.get(0).isJsonObject());
        JsonObject target = targets.get(0).getAsJsonObject();
        Assert.assertTrue("Target entries should have a 'latitude' property", target.has("latitude"));
        Assert.assertTrue("Target entries should have a 'longitude' property", target.has("longitude"));
        Assert.assertEquals("Incorrect target latitude",
                40.108585, target.get("latitude").getAsDouble(), 1e-7);
        Assert.assertEquals("Incorrect target longitude",
                -88.228874, target.get("longitude").getAsDouble(), 1e-7);
        Assert.assertTrue("The game setup JSON should have an 'invitees' property", result.has("invitees"));
        Assert.assertTrue("The 'invitees' property should hold a JSON array", result.get("invitees").isJsonArray());
        JsonArray invitees = result.getAsJsonArray("invitees");
        Assert.assertNotEquals("The invitees array should not be empty", 0, invitees.size());
        Assert.assertEquals("The invitees array had extra entries", 1, invitees.size());
        Assert.assertTrue("The invitees array should contain objects", invitees.get(0).isJsonObject());
        JsonObject invitee = invitees.get(0).getAsJsonObject();
        Assert.assertTrue("Invitee entries should have an 'email' property", invitee.has("email"));
        Assert.assertEquals("Incorrect invitee email address",
                SampleData.USER_EMAIL, invitee.get("email").getAsString());
        Assert.assertTrue("Invitee entries should have a 'team' property for the role", invitee.has("team"));
        Assert.assertEquals("Incorrect invitee team/role",
                TeamID.OBSERVER, invitee.get("team").getAsInt());
        for (String unneededProperty : new String[] {"cellSize", "areaNorth", "areaEast", "areaSouth", "areaWest"}) {
            Assert.assertFalse(unneededProperty + " should not be specified for target mode", result.has(unneededProperty));
        }

        // Test a game with multiple invitees and multiple targets
        List<Invitee> inputInvitees = new ArrayList<>();
        inputInvitees.add(new Invitee("stamets@example.com", TeamID.TEAM_RED));
        inputInvitees.add(new Invitee("culber@example.com", TeamID.TEAM_GREEN));
        List<LatLng> inputTargets = new LinkedList<>();
        inputTargets.add(new LatLng(40.15, -88.29));
        inputTargets.add(new LatLng(40.52, -88.01));
        inputTargets.add(new LatLng(40.93, -88.82));
        result = GameSetup.targetMode(inputInvitees, new LinkedList<>(inputTargets), 323);
        Assert.assertNotNull("Multiple invitees and targets is a valid configuration", result);
        Assert.assertEquals("Incorrect proximity threshold", 323, result.get("proximityThreshold").getAsInt());
        invitees = result.getAsJsonArray("invitees");
        Assert.assertEquals("Incorrect number of invitees in JSON", 2, invitees.size());
        checkConfiguredInvitee(invitees, "stamets@example.com", TeamID.TEAM_RED);
        checkConfiguredInvitee(invitees, "culber@example.com", TeamID.TEAM_GREEN);
        targets = result.getAsJsonArray("targets");
        Assert.assertEquals("Incorrect number of targets in JSON", 3, targets.size());
        for (LatLng t : inputTargets) {
            checkConfiguredTargetAt(targets, t);
        }

        // Test several random configurations
        for (int g = 0; g < 10; g++) {
            int inviteesCount = g / 2 + 2;
            inputInvitees = new ArrayList<>();
            List<Invitee> originalInvitees = new ArrayList<>();
            String[] usedEmails = new String[inviteesCount];
            for (int i = 0; i < inviteesCount; i++) {
                String email = RandomHelper.randomEmail(usedEmails);
                usedEmails[i] = email;
                int role = RandomHelper.randomRole();
                inputInvitees.add(new Invitee(email, role));
                originalInvitees.add(new Invitee(email, role));
            }
            inputTargets = new ArrayList<>();
            for (int t = 0; t < g + 3; t++) {
                inputTargets.add(RandomHelper.randomPoint());
            }
            int proximityThreshold = 60 + (int) RandomHelper.randomPlusMinusRange(40);
            result = GameSetup.targetMode(
                    new LinkedList<>(inputInvitees), new LinkedList<>(inputTargets), proximityThreshold);
            Assert.assertNotNull("targetMode returned null for a valid configuration", result);
            Assert.assertEquals("Incorrect proximity threshold",
                    proximityThreshold, result.get("proximityThreshold").getAsInt());
            invitees = result.getAsJsonArray("invitees");
            for (Invitee i : originalInvitees) {
                checkConfiguredInvitee(invitees, i.getEmail(), i.getTeamId());
            }
            targets = result.getAsJsonArray("targets");
            for (LatLng t : inputTargets) {
                checkConfiguredTargetAt(targets, t);
            }
        }
    }

    @Test(timeout = 60000)
    @Graded(points = 25)
    public void testJsonAreaMode() {
        // Test argument validation
        Assert.assertNull("areaMode should return null if there are no invitees", GameSetup.areaMode(
                new ArrayList<>(), RandomHelper.randomBounds(), 40));
        Assert.assertNull("areaMode should return null if the cell size is invalid", GameSetup.areaMode(
                Collections.singletonList(new Invitee(SampleData.USER_EMAIL, RandomHelper.randomRole())),
                RandomHelper.randomBounds(), -5));
        Assert.assertNull("areaMode should return null if the cell size is invalid", GameSetup.areaMode(
                Collections.singletonList(new Invitee(SampleData.USER_EMAIL, RandomHelper.randomRole())),
                RandomHelper.randomBounds(), 0));
        Assert.assertNull("areaMode should return null if the configuration is invalid", GameSetup.areaMode(
                new ArrayList<>(), RandomHelper.randomBounds(), 0));

        // Test a simple setup
        LatLngBounds quad = new LatLngBounds(new LatLng(40.106296, -88.227801), new LatLng(40.108971, -88.226481));
        JsonObject result = GameSetup.areaMode(
                Collections.singletonList(new Invitee(SampleData.USER_EMAIL, TeamID.TEAM_YELLOW)), quad, 25);
        Assert.assertNotNull("areaMode should not return null when the configuration is valid", result);
        Assert.assertTrue("The game setup JSON should specify the game mode", result.has("mode"));
        Assert.assertEquals("The mode should be 'area' for area mode",
                "area", result.get("mode").getAsString());
        Assert.assertTrue("Area mode JSON should specify the cell size",
                result.has("cellSize"));
        Assert.assertTrue("The 'cellSize' property should hold an integer",
                result.get("cellSize").isJsonPrimitive() && result.getAsJsonPrimitive("cellSize").isNumber());
        Assert.assertEquals("Incorrect cell size in area mode JSON",
                25, result.get("cellSize").getAsInt());
        for (String bound : new String[] {"North", "East", "South", "West"}) {
            String property = "area" + bound;
            Assert.assertTrue(bound + " boundary ('" + property + "' property) must be specified for area mode",
                    result.has(property));
            Assert.assertTrue("The '" + property + "' property should hold a double",
                    result.get(property).isJsonPrimitive() && result.getAsJsonPrimitive(property).isNumber());
        }
        Assert.assertEquals("Area mode JSON should use the north boundary from the LatLngBounds",
                40.108971, result.get("areaNorth").getAsDouble(), 1e-7);
        Assert.assertEquals("Area mode JSON should use the east boundary from the LatLngBounds",
                -88.226481, result.get("areaEast").getAsDouble(), 1e-7);
        Assert.assertEquals("Area mode JSON should use the south boundary from the LatLngBounds",
                40.106296, result.get("areaSouth").getAsDouble(), 1e-7);
        Assert.assertEquals("Area mode JSON should use the west boundary from the LatLngBounds",
                -88.227801, result.get("areaWest").getAsDouble(), 1e-7);
        Assert.assertTrue("The game setup JSON should have an 'invitees' property", result.has("invitees"));
        Assert.assertTrue("The 'invitees' property should hold a JSON array", result.get("invitees").isJsonArray());
        JsonArray invitees = result.getAsJsonArray("invitees");
        Assert.assertNotEquals("The invitees array should not be empty", 0, invitees.size());
        Assert.assertEquals("The invitees array had extra entries", 1, invitees.size());
        checkConfiguredInvitee(invitees, SampleData.USER_EMAIL, TeamID.TEAM_YELLOW);
        for (String unneededProperty : new String[] {"proximityThreshold", "targets"}) {
            Assert.assertFalse(unneededProperty + " should not be specified for area mode", result.has(unneededProperty));
        }

        // Test multiple random games with multiple invitees
        for (int g = 0; g < 10; g++) {
            int inviteesCount = g / 2 + 2;
            List<Invitee> inputInvitees = new ArrayList<>();
            List<Invitee> originalInvitees = new ArrayList<>();
            String[] usedEmails = new String[inviteesCount];
            for (int i = 0; i < inviteesCount; i++) {
                String email = RandomHelper.randomEmail(usedEmails);
                usedEmails[i] = email;
                int role = RandomHelper.randomRole();
                inputInvitees.add(new Invitee(email, role));
                originalInvitees.add(new Invitee(email, role));
            }
            LatLngBounds bounds = RandomHelper.randomBounds();
            int cellSize = 50 + (int) RandomHelper.randomPlusMinusRange(35);
            result = GameSetup.areaMode(new LinkedList<>(inputInvitees), bounds, cellSize);
            Assert.assertNotNull("areaMode returned null for a valid configuration", result);
            Assert.assertEquals("Incorrect cell size", cellSize, result.get("cellSize").getAsInt());
            Assert.assertEquals("Incorrect north boundary",
                    bounds.northeast.latitude, result.get("areaNorth").getAsDouble(), 1e-7);
            Assert.assertEquals("Incorrect east boundary",
                    bounds.northeast.longitude, result.get("areaEast").getAsDouble(), 1e-7);
            Assert.assertEquals("Incorrect south boundary",
                    bounds.southwest.latitude, result.get("areaSouth").getAsDouble(), 1e-7);
            Assert.assertEquals("Incorrect west boundary",
                    bounds.southwest.longitude, result.get("areaWest").getAsDouble(), 1e-7);
            invitees = result.getAsJsonArray("invitees");
            for (Invitee i : originalInvitees) {
                checkConfiguredInvitee(invitees, i.getEmail(), i.getTeamId());
            }
        }
    }

    @Test(timeout = 60000)
    @Graded(points = 10)
    public void testSettingsGroupVisibility() {
        // Get IDs
        @IdRes int rIdTargetsMap = IdLookup.require("targetsMap");
        @IdRes int rIdAreaSizeMap = IdLookup.require("areaSizeMap");

        // Start the activity
        NewGameActivity activity = Robolectric.buildActivity(NewGameActivity.class).create().start().resume().get();
        ShadowSupportMapFragment targetMapFragment = Shadow.extract(activity.getSupportFragmentManager().findFragmentById(rIdTargetsMap));
        targetMapFragment.notifyMapReady();
        ShadowSupportMapFragment areaMapFragment = Shadow.extract(activity.getSupportFragmentManager().findFragmentById(rIdAreaSizeMap));
        areaMapFragment.notifyMapReady();

        // Make sure neither group is visible at first
        View targetSettings = activity.findViewById(R.id.targetSettings);
        Assert.assertEquals("The target mode settings group should be gone initially",
                View.GONE, targetSettings.getVisibility());
        View areaSettings = activity.findViewById(R.id.areaSettings);
        Assert.assertEquals("The area mode settings group should be gone initially",
                View.GONE, areaSettings.getVisibility());

        // Select target mode
        RadioButton targetModeOption = activity.findViewById(R.id.targetModeOption);
        targetModeOption.performClick();
        Assert.assertEquals("The target mode settings group should be visible when target mode is selected",
                View.VISIBLE, targetSettings.getVisibility());
        Assert.assertEquals("The area mode settings group should be gone when target mode is selected",
                View.GONE, areaSettings.getVisibility());

        // Select area mode
        RadioButton areaModeOption = activity.findViewById(R.id.areaModeOption);
        areaModeOption.performClick();
        Assert.assertEquals("The target mode settings group should be gone when area mode is selected",
                View.GONE, targetSettings.getVisibility());
        Assert.assertEquals("The area mode settings group should be visible when area mode is selected",
                View.VISIBLE, areaSettings.getVisibility());

        // Select target mode again
        targetModeOption.performClick();
        Assert.assertEquals("The target mode settings group should be visible after reselecting target mode",
                View.VISIBLE, targetSettings.getVisibility());
        Assert.assertEquals("The area mode settings group should be gone after reselecting target mode",
                View.GONE, areaSettings.getVisibility());
    }

    private void checkConfiguredTargetAt(JsonArray targets, LatLng position) {
        for (JsonElement t : targets) {
            JsonObject target = t.getAsJsonObject();
            Assert.assertTrue("Each target should have a latitude", target.has("latitude"));
            Assert.assertTrue("Each target should have a longitude", target.has("longitude"));
            LatLng targetPos = new LatLng(target.get("latitude").getAsDouble(), target.get("longitude").getAsDouble());
            if (LatLngUtils.same(targetPos, position)) {
                return;
            }
        }
        Assert.fail("Incorrect target coordinates: no target found at " + position.latitude + ", " + position.longitude);
    }

    private void checkConfiguredInvitee(JsonArray invitees, String email, int team) {
        for (JsonElement i : invitees) {
            JsonObject invitee = i.getAsJsonObject();
            Assert.assertTrue("Each invitee should have an email", invitee.has("email"));
            Assert.assertTrue("Each invitee should have a team", invitee.has("team"));
            String inviteeEmail = invitee.get("email").getAsString();
            if (inviteeEmail.equals(email)) {
                Assert.assertEquals("Incorrect team for " + email, team, invitee.get("team").getAsInt());
                return;
            }
        }
        Assert.fail("Missing invitees entry for " + email);
    }

}
