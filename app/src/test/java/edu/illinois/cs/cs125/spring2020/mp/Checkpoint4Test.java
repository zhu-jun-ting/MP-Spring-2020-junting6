package edu.illinois.cs.cs125.spring2020.mp;

import android.app.Dialog;
import android.content.Context;
import android.util.Pair;
import android.view.View;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.Polygon;
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
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowDialog;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import edu.illinois.cs.cs125.spring2020.mp.logic.AreaGame;
import edu.illinois.cs.cs125.spring2020.mp.logic.Game;
import edu.illinois.cs.cs125.spring2020.mp.logic.GameStateID;
import edu.illinois.cs.cs125.spring2020.mp.logic.LatLngUtils;
import edu.illinois.cs.cs125.spring2020.mp.logic.TargetGame;
import edu.illinois.cs.cs125.spring2020.mp.logic.TeamID;
import edu.illinois.cs.cs125.spring2020.mp.logic.WebApi;
import edu.illinois.cs.cs125.spring2020.mp.shadows.MockedWrapperInstantiator;
import edu.illinois.cs.cs125.spring2020.mp.shadows.ShadowGoogleMap;
import edu.illinois.cs.cs125.spring2020.mp.shadows.ShadowLocalBroadcastManager;
import edu.illinois.cs.cs125.spring2020.mp.shadows.ShadowLog;
import edu.illinois.cs.cs125.spring2020.mp.shadows.ShadowMarker;
import edu.illinois.cs.cs125.gradlegrader.annotations.Graded;
import edu.illinois.cs.cs125.robolectricsecurity.PowerMockSecurity;
import edu.illinois.cs.cs125.robolectricsecurity.Trusted;

@RunWith(RobolectricTestRunner.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@PowerMockIgnore({"org.mockito.*", "org.powermock.*", "org.robolectric.*", "android.*", "androidx.*", "com.google.android.*", "edu.illinois.cs.cs125.spring2020.mp.shadows.*"})
@PrepareForTest({WebApi.class, FirebaseAuth.class})
@Trusted
@Config(sdk = 28)
public class Checkpoint4Test {

    private static final Predicate<JsonObject> VALID = message -> {
        if (!message.has("type")) {
            throw new IllegalStateException("All updates must have a 'type' property");
        }
        return true;
    };

    private static final Predicate<JsonObject> EXCEPT_LOCATION_UPDATE = VALID
            .and(message -> !message.get("type").getAsString().equals("locationUpdate"));

    private Context appContext;

    @Rule
    public PowerMockRule mockStaticClasses = new PowerMockRule();

    @Before
    public void setup() {
        PowerMockSecurity.secureMockMethodCache();
        FirebaseMocker.mock();
        FirebaseMocker.setEmail(SampleData.USER_EMAIL);
        appContext = ApplicationProvider.getApplicationContext();
        try {
            Class<?> defaultTargetsClass = Class.forName("edu.illinois.cs.cs125.spring2020.mp.logic.DefaultTargets");
            defaultTargetsClass.getMethod("disable").invoke(null);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            // Expected
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
        ShadowLog.loadConfig("../log.yaml");
    }

    @After
    public void teardown() {
        WebApiMocker.reset();
        ShadowLocalBroadcastManager.reset();
        FirebaseMocker.setBan(null);
    }

    @Test(timeout = 60000)
    @Graded(points = 15)
    public void testTargetMode() {
        // Create the game
        FirebaseMocker.setBan("TargetGame should use the Game getEmail instance method rather than asking FirebaseAuth");
        GoogleMap map = MockedWrapperInstantiator.create(GoogleMap.class);
        ShadowGoogleMap shadowMap = Shadow.extract(map);
        WebSocketMocker webSocketControl = new WebSocketMocker();
        JsonObject gameConfig = SampleData.createTargetModeTestGame();
        Game game = new TargetGame(SampleData.USER_EMAIL, map, webSocketControl.getWebSocket(), gameConfig.deepCopy(), appContext);

        // Check initial target colors
        JsonArray targets = gameConfig.getAsJsonArray("targets");
        LatLng[] targetPos = new LatLng[targets.size()];
        for (int t = 0; t < targets.size(); t++) {
            JsonObject target = targets.get(t).getAsJsonObject();
            LatLng position = new LatLng(target.get("latitude").getAsDouble(), target.get("longitude").getAsDouble());
            targetPos[t] = position;
            Marker marker = shadowMap.getMarkerAt(position);
            Assert.assertNotNull("TargetGame did not create/position markers for targets", marker);
            ShadowMarker shadowMarker = Shadow.extract(marker);
            switch (target.get("team").getAsInt()) {
                case TeamID.TEAM_RED:
                    Assert.assertEquals("Incorrect marker hue for red-claimed target", BitmapDescriptorFactory.HUE_RED, shadowMarker.getHue(), 1e-3);
                    break;
                case TeamID.TEAM_YELLOW:
                    Assert.assertEquals("Incorrect marker hue for yellow-claimed target", BitmapDescriptorFactory.HUE_YELLOW, shadowMarker.getHue(), 1e-3);
                    break;
                default:
                    Assert.assertEquals("Incorrect marker hue for unclaimed target", BitmapDescriptorFactory.HUE_VIOLET, shadowMarker.getHue(), 1e-3);
            }
        }

        // Check initial lines (essentially tests the line-drawing helper function)
        List<Polyline> polylines = shadowMap.getPolylinesConnecting(targetPos[0], targetPos[1]);
        Assert.assertNotEquals("No polyline connects the first two points in the user's path", 0, polylines.size());
        int yellow = appContext.getColor(R.color.yellow);
        Assert.assertTrue("The user's path polyline does not have the team color",
                polylines.stream().anyMatch(p -> p.getColor() == yellow));
        polylines = shadowMap.getPolylinesConnecting(targetPos[1], targetPos[2]);
        Assert.assertNotEquals("No polyline connects the last two points in the user's path", 0, polylines.size());
        Assert.assertTrue("The user's path polyline does not have the team color",
                polylines.stream().anyMatch(p -> p.getColor() == yellow));
        for (int i = 3; i < targets.size(); i++) {
            polylines = shadowMap.getPolylinesConnecting(targetPos[2], targetPos[i]);
            Assert.assertEquals("A polyline connects two unrelated targets", 0, polylines.size());
        }
        polylines = shadowMap.getPolylinesConnecting(targetPos[3], targetPos[4]);
        Assert.assertNotEquals("No polyline connects the two points in noone@illinois.edu's path", 0, polylines.size());
        Assert.assertTrue("noone@illinois.edu's polyline does not have the team color",
                polylines.stream().anyMatch(p -> p.getColor() == yellow));
        webSocketControl.assertNoMessagesMatch("Instantiating TargetGame should not send any messages", VALID);

        // Try to claim a target on the opposite side of a teammate's path
        game.locationUpdated(targetPos[6]); // FarDown
        webSocketControl.assertNoMessagesMatch("It should not be possible to cross another player's path", EXCEPT_LOCATION_UPDATE);
        polylines = shadowMap.getPolylinesConnecting(targetPos[2], targetPos[6]);
        Assert.assertEquals("Failed target claims should not create polylines", 0, polylines.size());

        // Try to claim an available target
        game.locationUpdated(targetPos[7]); // FarUp
        webSocketControl.processOneMessage("Visiting a target should produce a targetVisit event", EXCEPT_LOCATION_UPDATE,
                message -> {
                    Assert.assertEquals("Incorrect target visit update type", "targetVisit", message.get("type").getAsString());
                    Assert.assertTrue("targetVisit updates should have a 'targetId' property", message.has("targetId"));
                    Assert.assertEquals("Incorrect target ID in update", "FarUp", message.get("targetId").getAsString());
                });
        long markerCountAtClaimedPos = shadowMap.getMarkers().stream().filter(m -> LatLngUtils.same(m.getPosition(), targetPos[7])).count();
        Assert.assertNotEquals("Claiming a target should not remove the marker", 0, markerCountAtClaimedPos);
        Assert.assertEquals("Claiming a target should not create duplicate markers", 1, markerCountAtClaimedPos);
        Marker marker = shadowMap.getMarkerAt(targetPos[7]);
        ShadowMarker shadowMarker = Shadow.extract(marker);
        Assert.assertEquals("Claiming a target should turn it the team color", BitmapDescriptorFactory.HUE_YELLOW, shadowMarker.getHue(), 1e-3);
        polylines = shadowMap.getPolylinesConnecting(targetPos[2], targetPos[7]);
        Assert.assertNotEquals("Claiming a target should add a polyline", 0, polylines.size());
        Assert.assertTrue("The new polyline does not have the team color", polylines.stream().anyMatch(p -> p.getColor() == yellow));

        // Test a newly started game
        targets = gameConfig.getAsJsonArray("targets");
        SampleData.resetGame(gameConfig);
        map = MockedWrapperInstantiator.create(GoogleMap.class);
        shadowMap = Shadow.extract(map);
        webSocketControl = new WebSocketMocker();
        game = new TargetGame(SampleData.USER_EMAIL, map, webSocketControl.getWebSocket(), gameConfig.deepCopy(), appContext);
        Assert.assertEquals("TargetGame should create one marker per target", targets.size(), shadowMap.getMarkers().size());
        Assert.assertEquals("No targets should be claimed yet",
                targets.size(), shadowMap.getMarkersWithColor(BitmapDescriptorFactory.HUE_VIOLET).size());
        Assert.assertEquals("There should be no paths when no targets have been captured", 0, shadowMap.getPolylines().size());
        LatLng upperLeft = new LatLng(40.108765, -88.227945); // UpperLeft
        game.locationUpdated(upperLeft);
        webSocketControl.processOneMessage("Visiting a target should capture it", EXCEPT_LOCATION_UPDATE, message -> {
            Assert.assertEquals("Incorrect message type", "targetVisit", message.get("type").getAsString());
            Assert.assertEquals("Incorrect target ID in update", "UpperLeft", message.get("targetId").getAsString());
        });
        shadowMarker = Shadow.extract(shadowMap.getMarkerAt(upperLeft));
        Assert.assertEquals("The user capturing a target should turn it the team color",
                BitmapDescriptorFactory.HUE_YELLOW, shadowMarker.getHue(), 1e-3);
        Assert.assertEquals("A path of one point should have no connecting lines", 0, shadowMap.getPolylines().size());
    }

    @Test(timeout = 60000)
    @Graded(points = 10)
    public void testTargetModeMultiplePlayers() {
        // Create the game
        FirebaseMocker.setBan("TargetGame should use the Game getEmail instance method rather than asking FirebaseAuth");
        GoogleMap map = MockedWrapperInstantiator.create(GoogleMap.class);
        ShadowGoogleMap shadowMap = Shadow.extract(map);
        WebSocketMocker webSocketControl = new WebSocketMocker();
        JsonObject gameConfig = SampleData.createTargetModeTestGame();
        Game game = new TargetGame(SampleData.USER_EMAIL, map, webSocketControl.getWebSocket(), gameConfig.deepCopy(), appContext);
        JsonArray targets = gameConfig.getAsJsonArray("targets");
        LatLng[] targetPos = new LatLng[targets.size()];
        for (int t = 0; t < targets.size(); t++) {
            JsonObject target = targets.get(t).getAsJsonObject();
            targetPos[t] = new LatLng(target.get("latitude").getAsDouble(), target.get("longitude").getAsDouble());
        }

        // Make sure it only handles known events
        Assert.assertFalse("TargetGame should not handle area mode events",
                game.handleMessage(JsonHelper.updatePlayerCellCapture("noone@illinois.edu", TeamID.TEAM_RED, 1, 2)));
        JsonObject nonsenseUpdate = new JsonObject();
        nonsenseUpdate.addProperty("type", "nonsense");
        Assert.assertFalse("TargetGame should not handle unknown events", game.handleMessage(nonsenseUpdate));
        Assert.assertTrue("TargetGame should handle common events via the superclass",
                game.handleMessage(JsonHelper.updatePlayerLocation("opponent@example.com", TeamID.TEAM_RED, 40.097819, -88.247736)));

        // Extend a teammate's path
        Assert.assertTrue("TargetGame should handle playerTargetVisit",
                game.handleMessage(JsonHelper.updatePlayerTargetVisit("noone@illinois.edu", TeamID.TEAM_YELLOW, "FarDown")));
        Marker marker = shadowMap.getMarkerAt(targetPos[6]);
        ShadowMarker shadowMarker = Shadow.extract(marker);
        Assert.assertEquals("A teammate claiming a target should turn it the team color",
                BitmapDescriptorFactory.HUE_YELLOW, shadowMarker.getHue(), 1e-3);
        List<Polyline> polylines = shadowMap.getPolylinesConnecting(targetPos[4], targetPos[6]);
        Assert.assertNotEquals("A teammate claiming another target should create a polyline extending their path",
                0, polylines.size());
        int yellow = appContext.getColor(R.color.yellow);
        Assert.assertTrue("The polyline created when a teammate claims another target should be the team color",
                polylines.stream().anyMatch(p -> p.getColor() == yellow));

        // Extend another player's path
        game.handleMessage(JsonHelper.updatePlayerTargetVisit("opponent@example.com", TeamID.TEAM_RED, "Other1"));
        marker = shadowMap.getMarkerAt(targetPos[8]);
        shadowMarker = Shadow.extract(marker);
        Assert.assertEquals("Another player claiming a target should turn it that player's team color", BitmapDescriptorFactory.HUE_RED, shadowMarker.getHue(), 1e-3);
        polylines = shadowMap.getPolylinesConnecting(targetPos[5], targetPos[8]);
        Assert.assertNotEquals("Another player claiming another target should create a polyline", 0, polylines.size());
        int red = appContext.getColor(R.color.red);
        Assert.assertTrue("The polyline created when another player claims a target should have that player's team color",
                polylines.stream().anyMatch(p -> p.getColor() == red));

        // Start yet another player's path
        int polylinesCount = shadowMap.getPolylines().size();
        game.handleMessage(JsonHelper.updatePlayerTargetVisit("another@example.com", TeamID.TEAM_GREEN, "Other2"));
        marker = shadowMap.getMarkerAt(targetPos[9]);
        shadowMarker = Shadow.extract(marker);
        Assert.assertEquals("Another player claiming a target should turn it that player's team color", BitmapDescriptorFactory.HUE_GREEN, shadowMarker.getHue(), 1e-3);
        Assert.assertEquals("A player claiming their first target should not create a polyline", polylinesCount, shadowMap.getPolylines().size());
        webSocketControl.assertNoMessagesMatch("Handling received messages should not send new messages", VALID);

        // Try visiting a target captured by another team
        game.locationUpdated(targetPos[8]); // Other1
        webSocketControl.assertNoMessagesMatch("Visiting a target claimed by a different team should do nothing", EXCEPT_LOCATION_UPDATE);
        polylines = shadowMap.getPolylinesConnecting(targetPos[7], targetPos[8]);
        Assert.assertEquals("Trying to visit a target claimed by another team should not create a polyline", 0, polylines.size());

        // Test a newly started game
        SampleData.resetGame(gameConfig);
        map = MockedWrapperInstantiator.create(GoogleMap.class);
        shadowMap = Shadow.extract(map);
        webSocketControl = new WebSocketMocker();
        game = new TargetGame(SampleData.USER_EMAIL, map, webSocketControl.getWebSocket(), gameConfig.deepCopy(), appContext);
        game.handleMessage(JsonHelper.updatePlayerTargetVisit("noone@illinois.edu", TeamID.TEAM_YELLOW, "LowerLeft"));
        marker = shadowMap.getMarkerAt(new LatLng(40.106388, -88.227814)); // LowerLeft
        Assert.assertEquals("A teammate capturing a target should turn it the team color",
                BitmapDescriptorFactory.HUE_YELLOW, Shadow.<ShadowMarker>extract(marker).getHue(), 1e-3);
        Assert.assertEquals("A player's first capture should not create a line", 0, shadowMap.getPolylines().size());
        game.handleMessage(JsonHelper.updatePlayerTargetVisit("opponent@example.com", TeamID.TEAM_RED, "UpperMiddle"));
        marker = shadowMap.getMarkerAt(new LatLng(40.108930, -88.226455)); // UpperMiddle
        Assert.assertEquals("A player capturing a target should turn it that player's team color",
                BitmapDescriptorFactory.HUE_RED, Shadow.<ShadowMarker>extract(marker).getHue(), 1e-3);
        Assert.assertEquals("Captures by different players should not be connected by lines", 0, shadowMap.getPolylines().size());
    }

    @Test(timeout = 60000)
    @Graded(points = 10)
    public void testAreaModeLoading() {
        // Create the game
        FirebaseMocker.setBan("AreaGame should use the Game getEmail instance method rather than asking FirebaseAuth");
        GoogleMap map = MockedWrapperInstantiator.create(GoogleMap.class);
        ShadowGoogleMap shadowMap = Shadow.extract(map);
        WebSocketMocker webSocketControl = new WebSocketMocker();
        JsonObject gameConfig = SampleData.createAreaModeTestGame();
        @SuppressWarnings("unused") Game game =
                new AreaGame(SampleData.USER_EMAIL, map, webSocketControl.getWebSocket(), gameConfig.deepCopy(), appContext);

        // Check for a grid
        int linesCount = shadowMap.getPolylines().size();
        Assert.assertNotEquals("AreaGame should draw a grid of the area", 0, linesCount);
        Assert.assertEquals("AreaGame did not draw the correct grid", 13, linesCount);
        Assert.assertNotEquals("AreaGame did not draw the grid at the correct position", 0,
                shadowMap.getPolylinesConnecting(new LatLng(40.116319, -88.223576), new LatLng(40.116319, -88.228933)).size());
        Assert.assertNotEquals("AreaGame did not draw the grid at the correct position", 0,
                shadowMap.getPolylinesConnecting(new LatLng(40.112905, -88.228933), new LatLng(40.116319, -88.228933)).size());
        Assert.assertEquals("AreaGame should not add markers to the map", 0, shadowMap.getMarkers().size());

        // Check initial polygons
        int polygonCount = shadowMap.getPolygons().size();
        Assert.assertTrue("Very many polygons were added", polygonCount <= 10);
        Assert.assertNotEquals("No polygons were added", 0, polygonCount);
        Assert.assertTrue("There should be one polygon per captured cell", polygonCount >= 7);
        LatLngBounds playerCell = new LatLngBounds(new LatLng(40.1135878, -88.22536167), new LatLng(40.1142706, -88.22446883));
        List<Polygon> polygons = shadowMap.getPolygonsFilling(playerCell); // (4, 1)
        Assert.assertNotEquals("No polygon shows the cell captured by the player", 0, polygons.size());
        int red = appContext.getColor(R.color.red);
        Assert.assertTrue("The polygon for the cell captured by the player should be the player's team color",
                polygons.stream().anyMatch(p -> solidColorOf(p) == red));
        LatLngBounds unusedCell = new LatLngBounds(new LatLng(40.1135878, -88.2262545), new LatLng(40.1142706, -88.22536167));
        Assert.assertNull("There should be no polygon on uncaptured cells", shadowMap.getPolygonFilling(unusedCell));
        List<Pair<LatLngBounds, Integer>> expectedCells = new ArrayList<>();
        expectedCells.add(new Pair<>(new LatLngBounds(new LatLng(40.1149534, -88.2262545), new LatLng(40.1156362, -88.22536167)), R.color.red)); // (3, 3)
        expectedCells.add(new Pair<>(new LatLngBounds(new LatLng(40.1149534, -88.22536167), new LatLng(40.1156362, -88.22446883)), R.color.red)); // (4, 3)
        expectedCells.add(new Pair<>(new LatLngBounds(new LatLng(40.1149534, -88.228933), new LatLng(40.1156362, -88.228040167)), R.color.yellow)); // (0, 3)
        expectedCells.add(new Pair<>(new LatLngBounds(new LatLng(40.1149534, -88.228040167), new LatLng(40.1156362, -88.2271473)), R.color.yellow)); // (1, 3)
        expectedCells.add(new Pair<>(new LatLngBounds(new LatLng(40.1142706, -88.228040167), new LatLng(40.1149534, -88.2271473)), R.color.yellow)); // (1, 2)
        expectedCells.add(new Pair<>(new LatLngBounds(new LatLng(40.112905, -88.228933), new LatLng(40.1135878, -88.228040167)), R.color.blue)); // (0, 0)
        for (Pair<LatLngBounds, Integer> pair : expectedCells) {
            Polygon polygon = shadowMap.getPolygonFilling(pair.first);
            Assert.assertNotNull("AreaGame did not correctly position polygons on captured cells", polygon);
            int expectedColor = appContext.getColor(pair.second);
            Assert.assertEquals("Captured cell polygons should be the capturing team's color", expectedColor, solidColorOf(polygon));
        }
    }

    @Test(timeout = 60000)
    @Graded(points = 10)
    public void testAreaModeMovement() {
        // Create the game
        FirebaseMocker.setBan("AreaGame should use the Game getEmail instance method rather than asking FirebaseAuth");
        GoogleMap map = MockedWrapperInstantiator.create(GoogleMap.class);
        ShadowGoogleMap shadowMap = Shadow.extract(map);
        WebSocketMocker webSocketControl = new WebSocketMocker();
        JsonObject gameConfig = SampleData.createAreaModeTestGame();
        Game game = new AreaGame(SampleData.USER_EMAIL, map, webSocketControl.getWebSocket(), gameConfig.deepCopy(), appContext);
        webSocketControl.assertNoMessagesMatch("Instantiating AreaGame should not send any messages", VALID);

        // Try to capture a non-adjacent cell
        int originalPolygonCount = shadowMap.getPolygons().size();
        game.locationUpdated(new LatLng(40.1141, -88.2263)); // (2, 1)
        webSocketControl.assertNoMessagesMatch("Visiting a cell far away from the path should do nothing", EXCEPT_LOCATION_UPDATE);
        Assert.assertEquals("Failed cell claims should not create polygons", originalPolygonCount, shadowMap.getPolygons().size());

        // Capture an adjacent cell
        game.locationUpdated(new LatLng(40.1136, -88.2258)); // (3, 1)
        webSocketControl.processOneMessage("Visiting a capturable cell should produce an event", EXCEPT_LOCATION_UPDATE,
                message -> {
                    Assert.assertEquals("Incorrect message type for cell capture", "cellCapture", message.get("type").getAsString());
                    Assert.assertTrue("cellCapture updates should have an 'x' property", message.has("x"));
                    Assert.assertEquals("Incorrect X coordinate in cell capture update", 3, message.get("x").getAsInt());
                    Assert.assertTrue("cellCapture updates should have a 'y' property", message.has("y"));
                    Assert.assertEquals("Incorrect Y coordinate in cell capture update", 1, message.get("y").getAsInt());
                });
        LatLngBounds playerSecondCell = new LatLngBounds(new LatLng(40.1135878, -88.2262545), new LatLng(40.1142706, -88.22536167));
        int red = appContext.getColor(R.color.red);
        List<Polygon> polygons = shadowMap.getPolygonsFilling(playerSecondCell);
        Assert.assertNotEquals("Capturing a cell should add a polygon", 0, polygons.size());
        Assert.assertEquals("Capturing a cell should add exactly one polygon", originalPolygonCount + 1, shadowMap.getPolygons().size());
        Assert.assertTrue("The added polygon should have the team color", polygons.stream().anyMatch(p -> solidColorOf(p) == red));
        LatLngBounds playerStartCell = new LatLngBounds(new LatLng(40.1135878, -88.22536167), new LatLng(40.1142706, -88.22446883));
        Polygon polygon = shadowMap.getPolygonFilling(playerStartCell);
        Assert.assertNotNull("The previously captured cell should still have a polygon", polygon);
        Assert.assertEquals("The previously captured cell's polygon should still have the team color", red, solidColorOf(polygon));

        // Try to capture a cell adjacent to the starting point
        game.locationUpdated(new LatLng(40.1132, -88.2245)); // (4, 0)
        webSocketControl.assertNoMessagesMatch("Visiting a cell that doesn't share a side with the most recent capture should do nothing", EXCEPT_LOCATION_UPDATE);

        // Try moving outside the area entirely
        game.locationUpdated(new LatLng(40.1121, -88.2246)); // (4, <0)
        webSocketControl.assertNoMessagesMatch("Going south out of bounds should do nothing", EXCEPT_LOCATION_UPDATE);
        game.locationUpdated(new LatLng(40.1183, -88.2246)); // (4, 7)
        webSocketControl.assertNoMessagesMatch("Going north out of bounds should do nothing", EXCEPT_LOCATION_UPDATE);
        game.locationUpdated(new LatLng(40.1154, -88.2225)); // (7, 3)
        webSocketControl.assertNoMessagesMatch("Going east out of bounds should do nothing", EXCEPT_LOCATION_UPDATE);
        game.locationUpdated(new LatLng(40.1154, -88.2291)); // (<0, 3)
        webSocketControl.assertNoMessagesMatch("Going west out of bounds should do nothing", EXCEPT_LOCATION_UPDATE);

        // Try revisiting a previously captured cell
        game.locationUpdated(new LatLng(40.1140, -88.2245)); // (4, 1)
        webSocketControl.assertNoMessagesMatch("Revisiting a captured cell should do nothing", EXCEPT_LOCATION_UPDATE);

        // Try to capture a cell adjacent to a teammate's most recent capture
        game.locationUpdated(new LatLng(40.1158, -88.2249)); // (4, 4)
        webSocketControl.assertNoMessagesMatch("Visiting a cell adjacent to a teammate's (but not one's own) most recent capture should do nothing", EXCEPT_LOCATION_UPDATE);

        // Capture another cell
        game.locationUpdated(new LatLng(40.1142, -88.2264)); // (2, 1)
        webSocketControl.processOneMessage("Visiting a capturable cell should produce an event", EXCEPT_LOCATION_UPDATE,
                message -> {
                    Assert.assertEquals("Incorrect message type for cell capture", "cellCapture", message.get("type").getAsString());
                    Assert.assertEquals("Incorrect X coordinate in cell capture update", 2, message.get("x").getAsInt());
                    Assert.assertEquals("Incorrect Y coordinate in cell capture update", 1, message.get("y").getAsInt());
                });
        polygons = shadowMap.getPolygonsFilling(new LatLngBounds(new LatLng(40.1135878, -88.2271473), new LatLng(40.1142706, -88.2262545)));
        Assert.assertNotEquals("Capturing another cell should create another polygon", 0, polygons.size());
        Assert.assertTrue("The added polygon should have the team color", polygons.stream().anyMatch(p -> solidColorOf(p) == red));

        // Capture yet another cell
        game.locationUpdated(new LatLng(40.1139, -88.2273)); // (1, 1)
        webSocketControl.processOneMessage("Visiting a capturable cell should produce an event", EXCEPT_LOCATION_UPDATE,
                message -> {
                    Assert.assertEquals("Incorrect message type for cell capture", "cellCapture", message.get("type").getAsString());
                    Assert.assertEquals("Incorrect X coordinate in cell capture update", 1, message.get("x").getAsInt());
                    Assert.assertEquals("Incorrect Y coordinate in cell capture update", 1, message.get("y").getAsInt());
                });

        // Capture a cell taken by another team
        game.locationUpdated(new LatLng(40.1145, -88.2263)); // (1, 2)
        webSocketControl.assertNoMessagesMatch("Visiting a cell captured by another player should do nothing", EXCEPT_LOCATION_UPDATE);

        // Start a new game
        SampleData.resetGame(gameConfig);
        map = MockedWrapperInstantiator.create(GoogleMap.class);
        shadowMap = Shadow.extract(map);
        webSocketControl = new WebSocketMocker();
        game = new AreaGame(SampleData.USER_EMAIL, map, webSocketControl.getWebSocket(), gameConfig.deepCopy(), appContext);
        Assert.assertTrue("A newly started game should not have polygons indicating cell capture", shadowMap.getPolygons().size() <= 1);

        // Try going out of bounds
        game.locationUpdated(new LatLng(40.1121, -88.2246)); // (4, <0)
        webSocketControl.assertNoMessagesMatch("Going outside the bounds of the game should do nothing", EXCEPT_LOCATION_UPDATE);

        // Capture the southwest cell
        game.locationUpdated(new LatLng(40.1130, -88.2288)); // (0, 0)
        webSocketControl.processOneMessage("It should be possible to capture the southwest cell", EXCEPT_LOCATION_UPDATE,
                message -> {
                    Assert.assertEquals("Incorrect message type for cell capture", "cellCapture", message.get("type").getAsString());
                    Assert.assertEquals("Incorrect X coordinate in cell capture update", 0, message.get("x").getAsInt());
                    Assert.assertEquals("Incorrect Y coordinate in cell capture update", 0, message.get("y").getAsInt());
                });

        // Visit a far-away cell
        game.locationUpdated(new LatLng(40.1158, -88.2249)); // (4, 4)
        webSocketControl.assertNoMessagesMatch("The snake rule should apply even when the last cell captured is the southwest", EXCEPT_LOCATION_UPDATE);

        // Capture an adjacent cell
        game.locationUpdated(new LatLng(40.1131, -88.2273)); // (1, 0)
        webSocketControl.processOneMessage("AreaGame rejected a valid capture", EXCEPT_LOCATION_UPDATE,
                message -> {
                    Assert.assertEquals("Incorrect message type for cell capture", "cellCapture", message.get("type").getAsString());
                    Assert.assertEquals("Incorrect X coordinate in cell capture update", 1, message.get("x").getAsInt());
                    Assert.assertEquals("Incorrect Y coordinate in cell capture update", 0, message.get("y").getAsInt());
                });
    }

    @Test(timeout = 60000)
    @Graded(points = 10)
    public void testAreaModeMultiplePlayers() {
        // Create the game
        FirebaseMocker.setBan("AreaGame should use the Game getEmail instance method rather than asking FirebaseAuth");
        GoogleMap map = MockedWrapperInstantiator.create(GoogleMap.class);
        ShadowGoogleMap shadowMap = Shadow.extract(map);
        WebSocketMocker webSocketControl = new WebSocketMocker();
        JsonObject gameConfig = SampleData.createAreaModeTestGame();
        Game game = new AreaGame(SampleData.USER_EMAIL, map, webSocketControl.getWebSocket(), gameConfig.deepCopy(), appContext);

        // Make sure it only handles known events
        Assert.assertFalse("AreaGame should not handle target mode events",
                game.handleMessage(JsonHelper.updatePlayerTargetVisit("noone@illinois.edu", TeamID.TEAM_RED, RandomHelper.randomId())));
        JsonObject nonsenseUpdate = new JsonObject();
        nonsenseUpdate.addProperty("type", "nonsense");
        Assert.assertFalse("AreaGame should not handle unknown events", game.handleMessage(nonsenseUpdate));
        Assert.assertTrue("AreaGame should handle common events (e.g. playerLocation) via the superclass",
                game.handleMessage(JsonHelper.updatePlayerLocation("another@example.com", TeamID.TEAM_BLUE, 40.119986, -88.211989)));
        Assert.assertTrue("AreaGame should handle common events via the superclass",
                game.handleMessage(JsonHelper.updatePlayerExit("another@example.com")));

        // Start another player's path
        Assert.assertTrue("AreaGame should handle playerCellCapture",
                game.handleMessage(JsonHelper.updatePlayerCellCapture("late@example.com", TeamID.TEAM_GREEN, 4, 2)));
        Polygon polygon = shadowMap.getPolygonFilling(new LatLngBounds(new LatLng(40.1142706, -88.2253617), new LatLng(40.1149534, -88.22446883)));
        Assert.assertNotNull("Another player capturing a cell should create a polygon", polygon);
        Assert.assertEquals("Polygons for cells captured by other teams should have the capturing team's color",
                appContext.getColor(R.color.green), solidColorOf(polygon));
        webSocketControl.assertNoMessagesMatch("Processing received messages should not send new ones", VALID);

        // Try to capture that cell that was just taken
        game.locationUpdated(new LatLng(40.1143, -88.2253)); // (4, 2)
        webSocketControl.assertNoMessagesMatch("Visiting a cell newly captured by another player should do nothing", EXCEPT_LOCATION_UPDATE);

        // Extend another player's path
        LatLngBounds blueCaptureBounds = new LatLngBounds(new LatLng(40.1135878, -88.228933), new LatLng(40.1142706, -88.228040167));
        polygon = shadowMap.getPolygonFilling(blueCaptureBounds);
        Assert.assertNull(polygon);
        game.handleMessage(JsonHelper.updatePlayerCellCapture("another@example.com", TeamID.TEAM_BLUE, 0, 1));
        polygon = shadowMap.getPolygonFilling(blueCaptureBounds);
        Assert.assertNotNull("Other players capturing additional cells should create additional polygons", polygon);
        Assert.assertEquals("Polygons for cells captured by other teams should have the capturing team's color",
                appContext.getColor(R.color.blue), solidColorOf(polygon));
    }

    @Test(timeout = 60000)
    @Graded(points = 10)
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void testScoring() {
        // Exercise TargetGame#getTeamScore
        FirebaseMocker.setBan("TargetGame should use the Game getEmail instance method rather than asking FirebaseAuth");
        WebSocketMocker webSocketControl = new WebSocketMocker();
        Game tGame = new TargetGame(SampleData.USER_EMAIL, MockedWrapperInstantiator.create(GoogleMap.class),
                webSocketControl.getWebSocket(), SampleData.createTargetModeTestGame(), appContext);
        final String initialTargetScoreMessage = "TargetGame's getTeamScore didn't reflect the game state provided by the server";
        Assert.assertEquals(initialTargetScoreMessage, 1, tGame.getTeamScore(TeamID.TEAM_RED));
        Assert.assertEquals(initialTargetScoreMessage, 5, tGame.getTeamScore(TeamID.TEAM_YELLOW));
        Assert.assertEquals(initialTargetScoreMessage, 0, tGame.getTeamScore(TeamID.TEAM_GREEN));
        Assert.assertEquals(initialTargetScoreMessage, 0, tGame.getTeamScore(TeamID.TEAM_BLUE));
        final String playerCaptureTargetScoreMessage = "TargetGame's getTeamScore was incorrect after the player captured a target";
        tGame.locationUpdated(new LatLng(40.111915, -88.226418)); // FarUp
        Assert.assertEquals(playerCaptureTargetScoreMessage, 0, tGame.getTeamScore(TeamID.TEAM_BLUE));
        Assert.assertEquals(playerCaptureTargetScoreMessage, 6, tGame.getTeamScore(TeamID.TEAM_YELLOW));
        Assert.assertEquals(playerCaptureTargetScoreMessage, 1, tGame.getTeamScore(TeamID.TEAM_RED));
        Assert.assertEquals(playerCaptureTargetScoreMessage, 0, tGame.getTeamScore(TeamID.TEAM_GREEN));
        JsonObject greenCapture1 = JsonHelper.updatePlayerTargetVisit("another@example.com", TeamID.TEAM_GREEN, "Other1");
        final String updatedTargetScoreMessage = "TargetGame's getTeamScore was incorrect after another player captured a target";
        tGame.handleMessage(greenCapture1.deepCopy());
        Assert.assertEquals(updatedTargetScoreMessage, 0, tGame.getTeamScore(TeamID.TEAM_BLUE));
        Assert.assertEquals(updatedTargetScoreMessage, 1, tGame.getTeamScore(TeamID.TEAM_RED));
        Assert.assertEquals(updatedTargetScoreMessage, 1, tGame.getTeamScore(TeamID.TEAM_GREEN));
        Assert.assertEquals(updatedTargetScoreMessage, 6, tGame.getTeamScore(TeamID.TEAM_YELLOW));
        JsonObject greenCapture2 = greenCapture1.deepCopy();
        greenCapture2.addProperty("targetId", "Other2");
        final String secondUpdatedTargetScoreMessage = "TargetGame's getTeamScore was incorrect after another player captured another target";
        tGame.handleMessage(greenCapture2.deepCopy());
        Assert.assertEquals(secondUpdatedTargetScoreMessage, 2, tGame.getTeamScore(TeamID.TEAM_GREEN));
        Assert.assertEquals(secondUpdatedTargetScoreMessage, 1, tGame.getTeamScore(TeamID.TEAM_RED));
        Assert.assertEquals(secondUpdatedTargetScoreMessage, 0, tGame.getTeamScore(TeamID.TEAM_BLUE));
        Assert.assertEquals(secondUpdatedTargetScoreMessage, 6, tGame.getTeamScore(TeamID.TEAM_YELLOW));
        tGame.locationUpdated(new LatLng(40.108930, -88.226455)); // UpperMiddle
        Assert.assertEquals("TargetGame's getTeamScore should not have changed after the player revisited a target",
                6, tGame.getTeamScore(TeamID.TEAM_YELLOW));

        // Exercise AreaGame#getTeamScore
        FirebaseMocker.setBan("AreaGame should use the Game getEmail instance method rather than asking FirebaseAuth");
        webSocketControl = new WebSocketMocker();
        Game aGame = new AreaGame(SampleData.USER_EMAIL, MockedWrapperInstantiator.create(GoogleMap.class),
                webSocketControl.getWebSocket(), SampleData.createAreaModeTestGame(), appContext);
        final String initialAreaScoreMessage = "AreaGame's getTeamScore didn't reflect the game state provided by the server";
        Assert.assertEquals(initialAreaScoreMessage, 3, aGame.getTeamScore(TeamID.TEAM_RED));
        Assert.assertEquals(initialAreaScoreMessage, 3, aGame.getTeamScore(TeamID.TEAM_YELLOW));
        Assert.assertEquals(initialAreaScoreMessage, 0, aGame.getTeamScore(TeamID.TEAM_GREEN));
        Assert.assertEquals(initialAreaScoreMessage, 1, aGame.getTeamScore(TeamID.TEAM_BLUE));
        JsonObject redCapture = JsonHelper.updatePlayerCellCapture("noone@illinois.edu", TeamID.TEAM_RED, 4, 2);
        final String updatedAreaScoreMessage = "AreaGame's getTeamScore was incorrect after a different player captured a cell";
        aGame.handleMessage(redCapture.deepCopy());
        Assert.assertEquals(updatedAreaScoreMessage, 0, aGame.getTeamScore(TeamID.TEAM_GREEN));
        Assert.assertEquals(updatedAreaScoreMessage, 1, aGame.getTeamScore(TeamID.TEAM_BLUE));
        Assert.assertEquals(updatedAreaScoreMessage, 3, aGame.getTeamScore(TeamID.TEAM_YELLOW));
        Assert.assertEquals(updatedAreaScoreMessage, 4, aGame.getTeamScore(TeamID.TEAM_RED));
        final String playerCaptureAreaScoreMessage = "AreaGame's getTeamScore was incorrect after the player captured a cell";
        aGame.locationUpdated(new LatLng(40.1132, -88.2245)); // (4, 0)
        Assert.assertEquals(playerCaptureAreaScoreMessage, 1, aGame.getTeamScore(TeamID.TEAM_BLUE));
        Assert.assertEquals(playerCaptureAreaScoreMessage, 0, aGame.getTeamScore(TeamID.TEAM_GREEN));
        Assert.assertEquals(playerCaptureAreaScoreMessage, 5, aGame.getTeamScore(TeamID.TEAM_RED));
        Assert.assertEquals(playerCaptureAreaScoreMessage, 3, aGame.getTeamScore(TeamID.TEAM_YELLOW));
        final String secondUpdatedAreaScoreMessage = "AreaGame's getTeamScore was incorrect after a different team captured a cell";
        aGame.handleMessage(JsonHelper.updatePlayerCellCapture("opponent@example.com", TeamID.TEAM_YELLOW, 1, 1));
        Assert.assertEquals(secondUpdatedAreaScoreMessage, 4, aGame.getTeamScore(TeamID.TEAM_YELLOW));
        Assert.assertEquals(secondUpdatedAreaScoreMessage, 1, aGame.getTeamScore(TeamID.TEAM_BLUE));
        Assert.assertEquals(secondUpdatedAreaScoreMessage, 0, aGame.getTeamScore(TeamID.TEAM_GREEN));
        Assert.assertEquals(secondUpdatedAreaScoreMessage, 5, aGame.getTeamScore(TeamID.TEAM_RED));
        aGame.locationUpdated(new LatLng(40.1140, -88.2245)); // (4, 1)
        Assert.assertEquals("AreaGame's getTeamScore should not have changed after the player revisited an old cell",
                5, aGame.getTeamScore(TeamID.TEAM_RED));

        // Test getWinningTeam on the above example games
        Assert.assertEquals("TargetGame's getWinningTeam was incorrect", TeamID.TEAM_YELLOW, tGame.getWinningTeam());
        Assert.assertEquals("AreaGame's getWinningTeam was incorrect", TeamID.TEAM_RED, aGame.getWinningTeam());

        // Test getWinningTeam on a new target mode game
        JsonObject gameConfig = SampleData.createTargetModeTestGame();
        SampleData.resetGame(gameConfig);
        Game game = new TargetGame(SampleData.USER_EMAIL, MockedWrapperInstantiator.create(GoogleMap.class),
                new WebSocketMocker().getWebSocket(), gameConfig, appContext);
        game.getWinningTeam(); // Ignore the result, just make sure it doesn't crash
        game.handleMessage(greenCapture1);
        Assert.assertEquals("TargetGame's getWinningTeam was incorrect for a recently started game",
                TeamID.TEAM_GREEN, game.getWinningTeam());
        game.handleMessage(JsonHelper.updatePlayerTargetVisit("opponent@example.com", TeamID.TEAM_RED, "Other2"));
        game.handleMessage(JsonHelper.updatePlayerTargetVisit("opponent@example.com", TeamID.TEAM_RED, "Other3"));
        game.handleMessage(JsonHelper.updatePlayerTargetVisit("noone@illinois.edu", TeamID.TEAM_YELLOW, "Other1"));
        Assert.assertEquals("TargetGame's getWinningTeam was incorrect after the red team made a comeback",
                TeamID.TEAM_RED, game.getWinningTeam());

        // Test getWinningTeam on a new area mode game
        gameConfig = SampleData.createAreaModeTestGame();
        SampleData.resetGame(gameConfig);
        game = new AreaGame(SampleData.USER_EMAIL, MockedWrapperInstantiator.create(GoogleMap.class),
                new WebSocketMocker().getWebSocket(), gameConfig, appContext);
        game.getWinningTeam(); // Ignore the result, just make sure it doesn't crash
        game.handleMessage(JsonHelper.updatePlayerCellCapture("noone@illinois.edu", TeamID.TEAM_RED, 0, 0));
        Assert.assertEquals("AreaGame's getWinningTeam was incorrect for a recently started game",
                TeamID.TEAM_RED, game.getWinningTeam());
        game.handleMessage(JsonHelper.updatePlayerCellCapture("late@example.com", TeamID.TEAM_GREEN, 2, 1));
        game.handleMessage(JsonHelper.updatePlayerCellCapture("late@example.com", TeamID.TEAM_GREEN, 1, 1));
        Assert.assertEquals("AreaGame's getWinningTeam was incorrect after the green team scored more points",
                TeamID.TEAM_GREEN, game.getWinningTeam());
        game.handleMessage(JsonHelper.updatePlayerCellCapture("another@example.com", TeamID.TEAM_BLUE, 2, 3));
        Assert.assertEquals("AreaGame's getWinningTeam was incorrect after the blue team scored a point",
                TeamID.TEAM_GREEN, game.getWinningTeam());
        game.handleMessage(JsonHelper.updatePlayerCellCapture("another@example.com", TeamID.TEAM_BLUE, 2, 2));
        game.handleMessage(JsonHelper.updatePlayerCellCapture("another@example.com", TeamID.TEAM_BLUE, 1, 2));
        Assert.assertEquals("AreaGame's getWinningTeam was incorrect after the blue team scored more points",
                TeamID.TEAM_BLUE, game.getWinningTeam());
        game.handleMessage(JsonHelper.updatePlayerCellCapture("opponent@example.com", TeamID.TEAM_YELLOW, 4, 3));
        game.handleMessage(JsonHelper.updatePlayerCellCapture("opponent@example.com", TeamID.TEAM_YELLOW, 5, 3));
        Assert.assertEquals("AreaGame's getWinningTeam was incorrect after the yellow team scored some points",
                TeamID.TEAM_BLUE, game.getWinningTeam());
        game.handleMessage(JsonHelper.updatePlayerCellCapture("opponent@example.com", TeamID.TEAM_YELLOW, 5, 4));
        game.handleMessage(JsonHelper.updatePlayerCellCapture("opponent@example.com", TeamID.TEAM_YELLOW, 4, 4));
        Assert.assertEquals("AreaGame's getWinningTeam was incorrect after the yellow team scored more points",
                TeamID.TEAM_YELLOW, game.getWinningTeam());
    }

    @Test(timeout = 60000)
    @Graded(points = 10)
    public void testGameActivityIntegration() {
        // Start the activity
        WebSocketMocker webSocketControl = WebSocketMocker.expectConnection();
        GameActivityLauncher launcher = new GameActivityLauncher();
        ShadowGoogleMap map = Shadow.extract(launcher.getMap());
        Assert.assertEquals("No lines should be on the map before game information is received", 0, map.getPolylines().size());
        Assert.assertEquals("No polygons should be on the map before game information is received", 0, map.getPolygons().size());
        Assert.assertEquals("No markers should be on the map before game information is received", 0, map.getMarkers().size());

        // Update the location before sending the game information
        for (int i = 0; i < 10; i++) {
            launcher.sendLocationUpdate(new LatLng(40.06929 + i * 0.0001, RandomHelper.randomLng()));
        }
        webSocketControl.assertNoMessagesMatch("Location updates should not be sent before game information is received", VALID);

        // Send a game where the user is an observer
        webSocketControl.sendData(SampleData.createMinimalTestGame(TeamID.OBSERVER));
        Assert.assertEquals("GameActivity should instantiate a TargetGame when receiving target game information", 1, map.getMarkers().size());
        Assert.assertEquals("Receiving a target game should not draw unnecessary lines", 0, map.getPolylines().size());

        // Send location updates
        for (int i = 0; i < 10; i++) {
            launcher.sendLocationUpdate(new LatLng(RandomHelper.randomLat(), -88.23298 - i * 0.0001));
        }
        webSocketControl.assertNoMessagesMatch("Location updates should not be sent when the user is an observer", VALID);
        LatLng singularTargetPos = new LatLng(40.090818, -88.227208);
        launcher.sendLocationUpdate(singularTargetPos);
        webSocketControl.assertNoMessagesMatch("Location updates should not affect the game when the user is an observer", VALID);

        // Start the activity with a game in which the user is a player
        webSocketControl = WebSocketMocker.expectConnection();
        launcher = new GameActivityLauncher();
        webSocketControl.sendData(SampleData.createMinimalTestGame(TeamID.TEAM_RED));

        // Send location updates
        launcher.sendLocationUpdate(singularTargetPos);
        webSocketControl.assertNoMessagesMatch("The player's movements should not affect the game when the game is paused", EXCEPT_LOCATION_UPDATE);
        for (int i = 0; i < 10; i++) {
            LatLng position = new LatLng(RandomHelper.randomLat(), -88.23298 - i * 0.0001);
            launcher.sendLocationUpdate(position);
            webSocketControl.processOneMessage("Location updates should be sent via websocket when the user is a player", VALID,
                    message -> {
                        Assert.assertEquals("Incorrect location update message type", "locationUpdate", message.get("type").getAsString());
                        Assert.assertTrue("locationUpdate updates should have a 'latitude' property", message.has("latitude"));
                        Assert.assertEquals("Incorrect position in location update", position.latitude, message.get("latitude").getAsDouble(), 1e-7);
                        Assert.assertTrue("locationUpdate updates should have a 'longitude' property", message.has("longitude"));
                        Assert.assertEquals("Incorrect position in location update", position.longitude, message.get("longitude").getAsDouble(), 1e-7);
                    });
            if (i == 4) {
                // Location updates should be sent in both PAUSED and RUNNING states
                webSocketControl.sendData(JsonHelper.updateGameState(GameStateID.RUNNING));
            }
        }
        launcher.sendLocationUpdate(singularTargetPos);
        webSocketControl.processOneMessage("The player's movements should be forwarded to the Game instance when the game is running", EXCEPT_LOCATION_UPDATE,
                message -> Assert.assertEquals("The TargetGame instance should have generated a targetVisit update", "targetVisit", message.get("type").getAsString()));

        // Create an activity with an area mode game
        webSocketControl = WebSocketMocker.expectConnection();
        launcher = new GameActivityLauncher();
        map = Shadow.extract(launcher.getMap());
        webSocketControl.sendData(SampleData.createAreaModeTestGame());
        Assert.assertEquals("GameActivity should instantiate an AreaGame upon receiving area game information", 13, map.getPolylines().size());
        Assert.assertEquals("Markers should not be created when receiving an area game", 0, map.getMarkers().size());
        int originalPolygonsCount = map.getPolygons().size();
        Assert.assertNotEquals("Creating an AreaGame should place polygons on existing captures", 0, originalPolygonsCount);

        // Send an update about another player capturing a cell
        webSocketControl.sendData(JsonHelper.updatePlayerCellCapture("another@example.com", TeamID.TEAM_BLUE, 0, 1));
        Assert.assertEquals("Received gameplay updates should be forwarded to the Game instance", originalPolygonsCount + 1, map.getPolygons().size());
        int blue = appContext.getColor(R.color.blue);
        Assert.assertEquals("Incorrect new polygon color", 2, map.getPolygons().stream().filter(p -> solidColorOf(p) == blue).count());
    }

    @Test(timeout = 60000)
    @Graded(points = 5)
    public void testGameOver() {
        // Set up the activity
        WebSocketMocker webSocketControl = WebSocketMocker.expectConnection();
        GameActivityLauncher launcher = new GameActivityLauncher();
        webSocketControl.sendData(SampleData.createTargetModeTestGame());
        ShadowDialog.reset();

        // Send the game-over update
        webSocketControl.sendData(JsonHelper.updateGameState(GameStateID.ENDED));
        String message = getGameOverPopupMessage();
        Assert.assertTrue("The game-over popup should state the winning team", message.contains("Yellow wins"));
        Assert.assertFalse(message.contains("Red wins"));
        Assert.assertFalse(message.contains("Green wins"));
        Assert.assertFalse(message.contains("Blue wins"));

        // Start and end several games with different winners
        String[] teamNames = appContext.getResources().getStringArray(R.array.team_choices);
        for (int i = 0; i < 10; i++) {
            launcher = new GameActivityLauncher();
            int winningTeam = RandomHelper.randomTeam();
            JsonObject fullUpdate = SampleData.createTargetModeTestGame();
            for (JsonElement p : fullUpdate.getAsJsonArray("players")) {
                p.getAsJsonObject().addProperty("team", winningTeam);
            }
            for (JsonElement t : fullUpdate.getAsJsonArray("targets")) {
                JsonObject target = t.getAsJsonObject();
                if (target.get("team").getAsInt() != TeamID.OBSERVER) {
                    target.addProperty("team", winningTeam);
                }
            }
            webSocketControl.sendData(fullUpdate);
            ShadowDialog.reset();
            webSocketControl.sendData(JsonHelper.updateGameState(GameStateID.ENDED));
            message = getGameOverPopupMessage();
            Assert.assertTrue("The game-over popup should name the winning team", message.contains(teamNames[winningTeam] + " wins"));
            for (int team = TeamID.MIN_TEAM; team <= TeamID.MAX_TEAM; team++) {
                if (team != winningTeam) {
                    Assert.assertFalse("The game-over popup should not name losing teams", message.contains(teamNames[team]));
                }
            }
        }

        // Dismiss the dialog
        ShadowDialog.getLatestDialog().dismiss();
        Assert.assertTrue("The activity should finish() after the game-over popup is dismissed", launcher.getActivity().isFinishing());
    }

    private String getGameOverPopupMessage() {
        Dialog dialog = ShadowDialog.getLatestDialog();
        Assert.assertNotNull("A popup should appear to announce the winner when the game ends", dialog);
        TextView messageView = dialog.findViewById(android.R.id.message);
        Assert.assertNotNull("The game-over popup should have a message", messageView);
        Assert.assertEquals("The game-over popup should show a message", View.VISIBLE, messageView.getVisibility());
        return messageView.getText().toString();
    }

    private int solidColorOf(Polygon polygon) {
        return polygon.getFillColor() | (0xFF << 24);
    }

}
