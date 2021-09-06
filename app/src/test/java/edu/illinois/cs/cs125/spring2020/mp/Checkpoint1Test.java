package edu.illinois.cs.cs125.spring2020.mp;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;

import androidx.test.core.app.ApplicationProvider;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.Polygon;
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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import edu.illinois.cs.cs125.spring2020.mp.logic.AreaDivider;
import edu.illinois.cs.cs125.spring2020.mp.logic.GameStateID;
import edu.illinois.cs.cs125.spring2020.mp.logic.LatLngUtils;
import edu.illinois.cs.cs125.spring2020.mp.logic.PlayerStateID;
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
public class Checkpoint1Test {

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
        ShadowLocalBroadcastManager.reset();
    }

    @Test
    @Graded(points = 10)
    public void testArgumentValidation() {
        // Test non-positive cell sizes
        Assert.assertFalse("The cell size cannot be negative for a valid area",
                getValid(40.107854, -88.224972, 40.107817, -88.225130, -50));
        Assert.assertFalse("The cell size must be positive for a valid area",
                getValid(40.107854, -88.224972, 40.107817, -88.225130, 0));
        Assert.assertFalse("The cell size must be positive",
                getValid(40.108986, -88.226489, 40.106274, -88.227814, 0));

        // Test reversed coordinates
        Assert.assertFalse("The west boundary cannot be east of the east boundary",
                getValid(40.108986, -88.227814, 40.106274, -88.226489, 50));
        Assert.assertFalse("The north boundary cannot be south of the south boundary",
                getValid(40.106274, -88.226489, 40.108986, -88.227814, 50));
        Assert.assertFalse("Reversed coordinates should not be considered valid",
                getValid(40.106274, -88.227814, 40.108986, -88.226489, 50));

        // Test zero-area regions
        Assert.assertFalse("Valid regions must have length in the X dimension",
                getValid(40.107854, -88.225241, 40.107817, -88.225241, 80));
        Assert.assertFalse("Valid regions must have length in the Y dimension",
                getValid(40.108201, -88.226489, 40.108201, -88.227814, 50));
        Assert.assertFalse("Points are not valid regions",
                getValid(40.103442, -88.219324, 40.103442, -88.219324, 65));

        // Test valid areas
        Assert.assertTrue("Valid configurations should be allowed",
                getValid(40.108986, -88.226489, 40.106274, -88.227814, 50));
        Assert.assertTrue("Large cell sizes should be allowed",
                getValid(40.108986, -88.226489, 40.106274, -88.227814, 160));
        for (JsonObject test : JsonResourceLoader.loadArray("areadivider")) {
            Assert.assertTrue("AreaDivider rejected a valid configuration", getValid(
                    test.get("north").getAsDouble(), test.get("east").getAsDouble(),
                    test.get("south").getAsDouble(), test.get("west").getAsDouble(), test.get("size").getAsInt()));
        }
    }

    @Test
    @Graded(points = 10)
    public void testCellCounts() {
        // Test a small one-cell area
        AreaDivider divider = new AreaDivider(40.107854, -88.224972, 40.107817, -88.225130, 80);
        Assert.assertEquals("Incorrect X cell count for small area", 1, divider.getXCells());
        Assert.assertEquals("Incorrect Y cell count for small area", 1, divider.getYCells());

        // Test a square area one mile on each side
        divider = new AreaDivider(40.098143, -88.257649, 40.083690, -88.276347, 40);
        Assert.assertEquals("Incorrect X cell count for square area", 40, divider.getXCells());
        Assert.assertEquals("Incorrect Y cell count for square area", 40, divider.getYCells());

        // Test the same area with a different cell size
        divider = new AreaDivider(40.098143, -88.257649, 40.083690, -88.276347, 100);
        Assert.assertEquals(16, divider.getXCells());
        Assert.assertEquals(16, divider.getYCells());

        // Test the main quad (which is rectangular)
        divider = new AreaDivider(40.108986, -88.226489, 40.106274, -88.227814, 50);
        Assert.assertEquals("Incorrect X cell count for a rectangle", 3, divider.getXCells());
        Assert.assertEquals("Incorrect Y cell count for a rectangle", 6, divider.getYCells());

        // Randomized tests (from JSON)
        for (JsonObject test : JsonResourceLoader.loadArray("areadivider")) {
            JsonObject answer = test.getAsJsonObject("answer");
            divider = new AreaDivider(test.get("north").getAsDouble(), test.get("east").getAsDouble(),
                    test.get("south").getAsDouble(), test.get("west").getAsDouble(), test.get("size").getAsInt());
            Assert.assertEquals("Incorrect X cell count", answer.get("width").getAsInt(), divider.getXCells());
            Assert.assertEquals("Incorrect Y cell count", answer.get("height").getAsInt(), divider.getYCells());
        }
    }

    @Test
    @Graded(points = 10)
    public void testCellIndexes() {
        // Test a small one-cell area
        AreaDivider divider = new AreaDivider(40.107854, -88.224972, 40.107817, -88.225130, 80);
        LatLng position = new LatLng(40.107839, -88.225066);
        Assert.assertEquals("Incorrect X index for point in small area", 0, divider.getXIndex(position));
        Assert.assertEquals("Incorrect Y index for point in small area", 0, divider.getYIndex(position));
        position = new LatLng(40.108144, -88.224391);
        Assert.assertNotEquals("getXIndex should not return valid-looking coordinates east of the area", 0, divider.getXIndex(position));
        Assert.assertNotEquals("getYIndex should not return valid-looking coordinates north of the area", 0, divider.getYIndex(position));
        position = new LatLng(40.106313, -88.226874);
        Assert.assertNotEquals("getXIndex should not return valid-looking indexes west of the area", 0, divider.getXIndex(position));
        Assert.assertNotEquals("getYIndex should not return valid-looking indexes south of the area", 0, divider.getYIndex(position));

        // Test a square area one mile on each side
        divider = new AreaDivider(40.098143, -88.257649, 40.083690, -88.276347, 40);
        position = new LatLng(40.083902, -88.276162);
        Assert.assertEquals("Incorrect X index for point in southwestern cell", 0, divider.getXIndex(position));
        Assert.assertEquals("Incorrect Y index for point in southwestern cell", 0, divider.getYIndex(position));
        position = new LatLng(40.083734, -88.266903);
        Assert.assertEquals("Incorrect X index for point near south border", 20, divider.getXIndex(position));
        Assert.assertEquals("Incorrect Y index for point near south border", 0, divider.getYIndex(position));
        position = new LatLng(40.098041, -88.257809);
        Assert.assertEquals("Incorrect X index for point in northeastern cell", 39, divider.getXIndex(position));
        Assert.assertEquals("Incorrect Y index for point in northeastern cell", 39, divider.getYIndex(position));
        position = new LatLng(40.090763, -88.276310);
        Assert.assertEquals("Incorrect X index for point near west border", 0, divider.getXIndex(position));
        Assert.assertEquals("Incorrect Y index for point near west border", 19, divider.getYIndex(position));
        position = new LatLng(40.091436, -88.257672);
        Assert.assertEquals("Incorrect X index for point near east border", 39, divider.getXIndex(position));
        Assert.assertEquals("Incorrect Y index for point near east border", 21, divider.getYIndex(position));
        position = new LatLng(40.1, -88.2);
        Assert.assertTrue("Points outside the area's X range should not receive valid-looking X indexes",
                divider.getXIndex(position) < 0 || divider.getXIndex(position) >= 40);
        Assert.assertTrue("Points outside the area's Y range should not receive valid-looking Y indexes",
                divider.getYIndex(position) < 0 || divider.getYIndex(position) >= 40);

        // Test the same area with a different cell size
        divider = new AreaDivider(40.098143, -88.257649, 40.083690, -88.276347, 100);
        position = new LatLng(40.098113, -88.265344);
        Assert.assertEquals("Incorrect X index for point near north border", 9, divider.getXIndex(position));
        Assert.assertEquals("Incorrect Y index for point near north border", 15, divider.getYIndex(position));

        // Test the main quad
        divider = new AreaDivider(40.108986, -88.226489, 40.106274, -88.227814, 50);
        position = new LatLng(40.107793, -88.227130);
        Assert.assertEquals("Incorrect X coordinate for point on the quad", 1, divider.getXIndex(position));
        Assert.assertEquals("Incorrect Y coordinate for point on the quad", 3, divider.getYIndex(position));

        // Randomized tests (loaded from a JSON file)
        for (JsonObject test : JsonResourceLoader.loadArray("areadivider")) {
            divider = new AreaDivider(test.get("north").getAsDouble(), test.get("east").getAsDouble(),
                    test.get("south").getAsDouble(), test.get("west").getAsDouble(), test.get("size").getAsInt());
            for (JsonElement st : test.getAsJsonArray("subtests")) {
                JsonObject subtest = st.getAsJsonObject();
                JsonObject subanswer = subtest.getAsJsonObject("answer");
                LatLng subtestPoint = new LatLng(subtest.get("lat").getAsDouble(), subtest.get("lng").getAsDouble());
                Assert.assertEquals("Incorrect cell X index",
                        subanswer.get("x").getAsInt(), divider.getXIndex(subtestPoint));
                Assert.assertEquals("Incorrect cell Y index",
                        subanswer.get("y").getAsInt(), divider.getYIndex(subtestPoint));
            }
        }
    }

    @Test
    @Graded(points = 10)
    public void testCellBounds() {
        // Test a small one-cell area
        AreaDivider divider = new AreaDivider(40.107854, -88.224972, 40.107817, -88.225130, 80);
        checkBoundsEqual("Incorrect cell bounds for single-cell area",
                new LatLngBounds(new LatLng(40.107817, -88.225130), new LatLng(40.107854, -88.224972)),
                divider.getCellBounds(0, 0));

        // Test the main quad (a modestly sized rectangle)
        divider = new AreaDivider(40.108986, -88.226489, 40.106274, -88.227814, 50);
        checkBoundsEqual("Incorrect cell bounds on the quad",
                new LatLngBounds(new LatLng(40.108082, -88.22693067), new LatLng(40.108534, -88.226489)),
                divider.getCellBounds(2, 4));

        // Test a square area one mile on each side
        divider = new AreaDivider(40.098143, -88.257649, 40.083690, -88.276347, 40);
        checkBoundsEqual("Incorrect cell bounds for corner cell",
                new LatLngBounds(new LatLng(40.08369, -88.276347), new LatLng(40.084051325, -88.27587955)),
                divider.getCellBounds(0, 0));
        checkBoundsEqual("Incorrect cell bounds for internal cell",
                new LatLngBounds(new LatLng(40.085496625, -88.2707376), new LatLng(40.08585795, -88.27027015)),
                divider.getCellBounds(12, 5));

        // Randomized tests (loaded from a JSON file)
        for (JsonObject test : JsonResourceLoader.loadArray("areadivider")) {
            divider = new AreaDivider(test.get("north").getAsDouble(), test.get("east").getAsDouble(),
                    test.get("south").getAsDouble(), test.get("west").getAsDouble(), test.get("size").getAsInt());
            for (JsonElement st : test.getAsJsonArray("subtests")) {
                JsonObject subtest = st.getAsJsonObject();
                JsonObject subanswer = subtest.getAsJsonObject("answer");
                LatLngBounds subtestBounds = divider.getCellBounds(subtest.get("x").getAsInt(), subtest.get("y").getAsInt());
                LatLngBounds answerBounds = new LatLngBounds(
                        new LatLng(subanswer.get("south").getAsDouble(), subanswer.get("west").getAsDouble()),
                        new LatLng(subanswer.get("north").getAsDouble(), subanswer.get("east").getAsDouble()));
                checkBoundsEqual("Incorrect cell bounds", answerBounds, subtestBounds);
            }
        }
    }

    @Test(timeout = 60000)
    @Graded(points = 10)
    public void testGrid() {
        // Test a small one-cell area (should only render outer border)
        for (int i = 0; i < 3; i++) {
            final double north = 40.107854;
            final double east = -88.224972;
            final double south = 40.107817;
            final double west = -88.225130;
            AreaDivider divider = new AreaDivider(north, east, south, west, 80 + i * 20);
            GoogleMap map = MockedWrapperInstantiator.create(GoogleMap.class);
            ShadowGoogleMap shadowMap = Shadow.extract(map);
            divider.renderGrid(map);
            Assert.assertEquals("One-cell areas should have four lines in their grid",
                    4, shadowMap.getPolylines().size());
            checkBorderLines(shadowMap, north, east, south, west);
        }

        // Test a 3 x 2 area
        double north = 40.105402;
        double east = -88.257836;
        double south = 40.101920;
        double west = -88.267111;
        AreaDivider divider = new AreaDivider(north, east, south, west, 270);
        GoogleMap map = MockedWrapperInstantiator.create(GoogleMap.class);
        ShadowGoogleMap shadowMap = Shadow.extract(map);
        divider.renderGrid(map);
        Assert.assertEquals("A 3x2 area should have 7 lines in the grid (4 border, 3 internal)",
                7, shadowMap.getPolylines().size());
        checkBorderLines(shadowMap, north, east, south, west);
        Assert.assertNotEquals("Missing/misplaced west vertical internal line", 0, shadowMap
                .getPolylinesConnecting(new LatLng(north, -88.2640193), new LatLng(south, -88.2640193)).size());
        Assert.assertNotEquals("Missing/misplaced east vertical internal line", 0, shadowMap
                .getPolylinesConnecting(new LatLng(north, -88.26092767), new LatLng(south, -88.26092767)).size());
        Assert.assertNotEquals("Missing/misplaced horizontal internal line", 0, shadowMap
                .getPolylinesConnecting(new LatLng(40.103661, west), new LatLng(40.103661, east)).size());

        // Randomized tests (loaded from JSON)
        for (JsonObject test : JsonResourceLoader.loadArray("areagrid")) {
            north = test.get("north").getAsDouble();
            east = test.get("east").getAsDouble();
            south = test.get("south").getAsDouble();
            west = test.get("west").getAsDouble();
            divider = new AreaDivider(north, east, south, west, test.get("size").getAsInt());
            map.clear();
            divider.renderGrid(map);
            checkBorderLines(shadowMap, north, east, south, west);
            JsonArray answer = test.getAsJsonArray("answer");
            for (JsonElement l : answer) {
                JsonObject line = l.getAsJsonObject();
                LatLng onePoint = new LatLng(line.get("lat1").getAsDouble(), line.get("lng1").getAsDouble());
                LatLng otherPoint = new LatLng(line.get("lat2").getAsDouble(), line.get("lng2").getAsDouble());
                Assert.assertNotEquals("Incorrect/missing internal line", 0, shadowMap
                        .getPolylinesConnecting(onePoint, otherPoint).size());
            }
        }
    }

    @Test(timeout = 60000)
    @Graded(points = 10)
    public void testProximityThreshold() throws IllegalAccessException, InvocationTargetException {
        // Test at several different thresholds
        for (int proximityThreshold = 50; proximityThreshold >= 20; proximityThreshold -= 10) {
            // Start the activity
            Intent intent = new Intent();
            String gameId = RandomHelper.randomId();
            intent.putExtra("game", gameId);
            intent.putExtra("mode", "target");
            intent.putExtra("proximityThreshold", proximityThreshold);
            WebSocketMocker webSocketControl = WebSocketMocker.expectConnection(); // Checkpoint 4 compatibility
            GameActivityLauncher launcher = new GameActivityLauncher(intent);
            ShadowGoogleMap map = Shadow.extract(launcher.getMap());

            // Check initial markers
            if (webSocketControl.isConnected()) {
                // Checkpoint 4 compatibility mode
                webSocketControl.sendData(createC4TargetGame(gameId, proximityThreshold));
                Assert.assertEquals("Incorrect target count", 2, map.getMarkers().size());
            } else {
                // Working on Checkpoint 1
                try {
                    Class<?> defaultTargetsClass = Class.forName("edu.illinois.cs.cs125.spring2020.mp.logic.DefaultTargets");
                    Method getPositionsMethod = defaultTargetsClass.getMethod("getPositions", Context.class);
                    LatLng[] positions = (LatLng[]) getPositionsMethod.invoke(null, ApplicationProvider.getApplicationContext());
                    Assert.assertNotNull("DefaultTargets.getPositions returned null (was modified?)", positions);
                    Assert.assertEquals("Incorrect target count", positions.length, map.getMarkers().size());
                } catch (ClassNotFoundException | NoSuchMethodException e) {
                    Assert.fail("DefaultTargets was removed but no Checkpoint 4 websocket connection was attempted");
                }
            }
            Assert.assertEquals("No targets should be claimed (green) yet",
                    0, map.getMarkersWithColor(BitmapDescriptorFactory.HUE_GREEN).size());

            // Approach the Armory
            double latitude = 40.104364;
            double longitude = -88.235512;
            LatLng armoryPos = new LatLng(40.104323, -88.231939);
            while (longitude < armoryPos.longitude) {
                LatLng position = new LatLng(latitude, longitude);
                launcher.sendLocationUpdate(position);
                Marker marker = map.getMarkerAt(armoryPos);
                ShadowMarker shadowMarker = Shadow.extract(marker);
                Assert.assertNotNull("Missing marker at the Armory target", marker);
                if (LatLngUtils.distance(position, armoryPos) < proximityThreshold) {
                    // Should be captured
                    Assert.assertEquals("The Armory should have been claimed at this distance and proximity threshold",
                            BitmapDescriptorFactory.HUE_GREEN, shadowMarker.getHue(), 1e-3);
                    break;
                } else {
                    // Shouldn't be captured yet
                    Assert.assertNotEquals("The Armory should not have been claimed yet at this distance and proximity threshold",
                            BitmapDescriptorFactory.HUE_GREEN, shadowMarker.getHue(), 1e-3);
                }
                longitude += 0.0002;
            }
            Assert.assertEquals("Only the Armory should have been claimed", 1, map.getMarkers().stream()
                    .filter(m -> Math.abs(Shadow.<ShadowMarker>extract(m).getHue() - BitmapDescriptorFactory.HUE_GREEN) < 1e-3).count());

            // Approach Vet-Med
            latitude = 40.090689;
            longitude = -88.217342;
            LatLng vetMedPos = new LatLng(40.092733, -88.220400);
            while (latitude < vetMedPos.latitude) {
                LatLng position = new LatLng(latitude, longitude);
                launcher.sendLocationUpdate(position);
                Marker marker = map.getMarkerAt(vetMedPos);
                ShadowMarker shadowMarker = Shadow.extract(marker);
                Assert.assertNotNull("Missing marker at the Vet-Med target", marker);
                if (LatLngUtils.distance(position, vetMedPos) < proximityThreshold) {
                    // Should be captured
                    Assert.assertEquals("Vet-Med should have been claimed at this distance and proximity threshold",
                            BitmapDescriptorFactory.HUE_GREEN, shadowMarker.getHue(), 1e-3);
                    break;
                } else {
                    // Shouldn't be captured yet
                    Assert.assertNotEquals("Vet-Med should not have been claimed yet at this distance and proximity threshold",
                            BitmapDescriptorFactory.HUE_GREEN, shadowMarker.getHue(), 1e-3);
                }
                latitude += 0.000136;
                longitude -= 0.0002;
            }
            Assert.assertEquals("Only the Armory and Vet-Med should have been claimed",
                    2, map.getMarkersWithColor(BitmapDescriptorFactory.HUE_GREEN).size());
        }
    }

    @Test(timeout = 60000)
    @Graded(points = 20)
    public void testAreaModeGameplay() {
        // Start the activity with a 10x8 area
        Intent intent = new Intent();
        String gameId = RandomHelper.randomId();
        intent.putExtra("game", gameId);
        intent.putExtra("mode", "area");
        intent.putExtra("areaNorth", 40.104144);
        intent.putExtra("areaEast", -88.224515);
        intent.putExtra("areaSouth", 40.100573);
        intent.putExtra("areaWest", -88.230183);
        intent.putExtra("cellSize", 50);
        WebSocketMocker webSocketControl = WebSocketMocker.expectConnection(); // Checkpoint 4 compatibility
        GameActivityLauncher launcher = new GameActivityLauncher(intent);
        ShadowGoogleMap map = Shadow.extract(launcher.getMap());

        // Check initial map
        if (webSocketControl.isConnected()) {
            // Checkpoint 4 compatibility
            webSocketControl.sendData(createC4AreaGame(gameId, 50));
        }
        Assert.assertEquals("No polygons should be on the map initially", 0, map.getPolygons().size());
        Assert.assertEquals("Grid wasn't rendered on the map correctly", 20, map.getPolylines().size());

        // Visit a cell
        launcher.sendLocationUpdate(new LatLng(40.101326, -88.229540)); // (1, 1)
        Assert.assertNotEquals("Capturing a cell should create a polygon", 0, map.getPolygons().size());
        int decorativePolygons = map.getPolygons().size() - 1; // To allow extra head indication
        LatLngBounds firstCaptureBounds = new LatLngBounds(new LatLng(40.101019375, -88.2296162),
                new LatLng(40.10146575, -88.2290494));
        Polygon polygon = map.getPolygonFilling(firstCaptureBounds);
        Assert.assertNotNull("The polygon didn't cover the captured cell", polygon);
        Assert.assertEquals("The polygon should be green", Color.GREEN & 0xFFFFFF, polygon.getFillColor() & 0xFFFFFF);
        Assert.assertNotEquals("The polygon is invisible (100% transparent)", 0, polygon.getFillColor() >> 24);

        // Visit an adjacent cell
        launcher.sendLocationUpdate(new LatLng(40.100701, -88.229402)); // (1, 0)
        Assert.assertEquals("Capturing another cell should create another polygon",
                decorativePolygons + 2, map.getPolygons().size());
        Assert.assertNotNull("Capturing another cell shouldn't affect the previous polygon",
                map.getPolygonFilling(firstCaptureBounds));
        Assert.assertNotNull("The new polygon didn't cover the newly captured cell", map.getPolygonFilling(new LatLngBounds(
                new LatLng(40.101019375, -88.2296162), new LatLng(40.10146575, -88.2290494))));

        // Meander around the outside of the area
        launcher.sendLocationUpdate(new LatLng(40.100139, -88.229335)); // (1, <0)
        Assert.assertEquals("Going past the south boundary shouldn't capture any cells",
                decorativePolygons + 2, map.getPolygons().size());
        launcher.sendLocationUpdate(new LatLng(40.100457, -88.230357)); // (-1, -1)
        Assert.assertEquals("Going southwest outside the area shouldn't capture any cells",
                decorativePolygons + 2, map.getPolygons().size());
        launcher.sendLocationUpdate(new LatLng(40.102621, -88.230451)); // (-1, 4)
        Assert.assertEquals("Going past the west boundary shouldn't capture any cells",
                decorativePolygons + 2, map.getPolygons().size());
        launcher.sendLocationUpdate(new LatLng(40.103812, -88.233710)); // farther west
        Assert.assertEquals("Going far outside the area shouldn't capture any cells",
                decorativePolygons + 2, map.getPolygons().size());
        launcher.sendLocationUpdate(new LatLng(40.104270, -88.230421)); // (-1, 8)
        Assert.assertEquals("Going northwest outside the area shouldn't capture any cells",
                decorativePolygons + 2, map.getPolygons().size());
        launcher.sendLocationUpdate(new LatLng(40.104297, -88.227827)); // (4, 8)
        Assert.assertEquals("Going past the north boundary shouldn't capture any cells",
                decorativePolygons + 2, map.getPolygons().size());
        launcher.sendLocationUpdate(new LatLng(40.104843, -88.227186)); // (5, 9)
        Assert.assertEquals("Going outside the area shouldn't capture any cells",
                decorativePolygons + 2, map.getPolygons().size());
        launcher.sendLocationUpdate(new LatLng(40.104449, -88.224126)); // (10, 9)
        Assert.assertEquals("Going northeast outside the area shouldn't capture any cells",
                decorativePolygons + 2, map.getPolygons().size());
        launcher.sendLocationUpdate(new LatLng(40.101687, -88.224401)); // (10, 2)
        Assert.assertEquals("Going past the east boundary shouldn't capture any cells",
                decorativePolygons + 2, map.getPolygons().size());
        launcher.sendLocationUpdate(new LatLng(40.100427, -88.224406)); // (10, -1)
        Assert.assertEquals("Going southeast of the area shouldn't capture any cells",
                decorativePolygons + 2, map.getPolygons().size());
        launcher.sendLocationUpdate(new LatLng(40.099040, -88.220298)); // far southeast
        Assert.assertEquals("Going far outside the area shouldn't capture any cells",
                decorativePolygons + 2, map.getPolygons().size());

        // Walk across the area without going near the captured cells
        for (double longitude = -88.224848; longitude > -88.230159; longitude -= 0.0002) {
            launcher.sendLocationUpdate(new LatLng(40.10259 + RandomHelper.randomPlusMinusRange(0.00001), longitude));
            Assert.assertEquals("It should not be possible to capture cells not adjacent to the previous capture",
                    decorativePolygons + 2, map.getPolygons().size());
        }

        // Revisit the previously captured cells
        launcher.sendLocationUpdate(new LatLng(40.100755, -88.229531)); // (1, 0)
        Assert.assertEquals("Revisiting the last captured cell should do nothing",
                decorativePolygons + 2, map.getPolygons().size());
        launcher.sendLocationUpdate(new LatLng(40.101326, -88.229540)); // (1, 1)
        Assert.assertEquals("Revisiting a previously captured cell should do nothing",
                decorativePolygons + 2, map.getPolygons().size());
        polygon = map.getPolygonFilling(firstCaptureBounds);
        Assert.assertNotNull("Ineffective walking shouldn't affect existing cell polygons", polygon);

        // Visit cells adjacent to the start of the path but not the end
        launcher.sendLocationUpdate(new LatLng(40.101786, -88.229392)); // (1, 2)
        Assert.assertEquals("Visiting a cell not adjacent to the most recent capture should do nothing",
                decorativePolygons + 2, map.getPolygons().size());
        launcher.sendLocationUpdate(new LatLng(40.101154, -88.230020)); // (0, 1)
        Assert.assertEquals("Visiting a cell only diagonally adjacent to the most recent capture should do nothing",
                decorativePolygons + 2, map.getPolygons().size());

        // Capture two more cells
        launcher.sendLocationUpdate(new LatLng(40.100724, -88.228973)); // (2, 0)
        Assert.assertEquals("Visiting a cell adjacent to the most recent capture should capture it",
                decorativePolygons + 3, map.getPolygons().size());
        Assert.assertNotNull("Capturing another cell should add a polygon on it", map.getPolygonFilling(new LatLngBounds(
                new LatLng(40.100573, -88.2290494), new LatLng(40.101019375, -88.2284826))));
        launcher.sendLocationUpdate(new LatLng(40.101211, -88.228816)); // (2, 1)
        Assert.assertEquals("Visiting a cell adjacent to the most recent capture should capture it",
                decorativePolygons + 4, map.getPolygons().size());
        Assert.assertNotNull("Capturing another cell should add a polygon on it", map.getPolygonFilling(new LatLngBounds(
                new LatLng(40.101019375, -88.2290494), new LatLng(40.10146575, -88.2284826))));

        // Capture a run of cells going all the way east
        for (double longitude = -88.228815; longitude < -88.224864; longitude += 0.0002) {
            launcher.sendLocationUpdate(new LatLng(40.10121 + RandomHelper.randomPlusMinusRange(0.000001), longitude));
        }
        Assert.assertEquals("Capturing additional cells should create additional polygons",
                decorativePolygons + 11, map.getPolygons().size());

        // Capture a run of cells going all the way north
        for (double latitude = 40.1013; latitude < 40.104079; latitude += 0.00003) {
            launcher.sendLocationUpdate(new LatLng(latitude, -88.224864 + RandomHelper.randomPlusMinusRange(0.00001)));
        }
        Assert.assertEquals("Capturing additional cells should create additional polygons",
                decorativePolygons + 17, map.getPolygons().size());

        // Capture a run of cells going all the way west
        for (double longitude = -88.224864; longitude > -88.230054; longitude -= 0.0002) {
            launcher.sendLocationUpdate(new LatLng(40.10408 + RandomHelper.randomPlusMinusRange(0.00001), longitude));
        }
        Assert.assertEquals("Capturing additional cells should create additional polygons",
                decorativePolygons + 26, map.getPolygons().size());

        // Capture a run of cells going all the way south
        for (double latitude = 40.10408; latitude > 40.10063; latitude -= 0.00003) {
            launcher.sendLocationUpdate(new LatLng(latitude, -88.23004 + RandomHelper.randomPlusMinusRange(0.00001)));
        }
        Assert.assertEquals("Capturing additional cells should create additional polygons",
                decorativePolygons + 33, map.getPolygons().size());

        // Run over the whole area now that the snake is stuck
        for (double latitude = 40.10063; latitude < 40.10408; latitude += 0.00015) {
            for (double longitude = -88.23004; longitude < -88.22486; longitude += 0.0009) {
                launcher.sendLocationUpdate(new LatLng(latitude, longitude));
                Assert.assertEquals("It should not be possible to capture more cells when stuck",
                        decorativePolygons + 33, map.getPolygons().size());
            }
        }

        // Make sure it respects the cell size setting
        intent.putExtra("cellSize", 85);
        webSocketControl = WebSocketMocker.expectConnection();
        launcher = new GameActivityLauncher(intent);
        map = Shadow.extract(launcher.getMap());
        if (webSocketControl.isConnected()) {
            webSocketControl.sendData(createC4AreaGame(gameId, 85));
        }
        Assert.assertEquals("Grid was incorrect with a different cell size", 13, map.getPolylines().size());
    }

    private boolean getValid(double north, double east, double south, double west, int cellSize) {
        AreaDivider divider;
        try {
            divider = new AreaDivider(north, east, south, west, cellSize);
        } catch (IllegalArgumentException e) {
            return false;
        }
        return divider.isValid();
    }

    private void checkBoundsEqual(String message, LatLngBounds expected, LatLngBounds actual) {
        Assert.assertEquals(message + ": Incorrect north boundary",
                expected.northeast.latitude, actual.northeast.latitude, 1e-7);
        Assert.assertEquals(message + ": Incorrect east boundary",
                expected.northeast.longitude, actual.northeast.longitude, 1e-7);
        Assert.assertEquals(message + ": Incorrect south boundary",
                expected.southwest.latitude, actual.southwest.latitude, 1e-7);
        Assert.assertEquals(message + ": Incorrect west boundary",
                expected.southwest.longitude, actual.southwest.longitude, 1e-7);
    }

    private void checkBorderLines(ShadowGoogleMap map, double north, double east, double south, double west) {
        Assert.assertTrue("A polyline is invisible (zero width)",
                map.getPolylines().stream().noneMatch(p -> p.getWidth() < 1e-5));
        Assert.assertNotEquals("Missing/incorrect north border", 0, map
                .getPolylinesConnecting(new LatLng(north, east), new LatLng(north, west)).size());
        Assert.assertNotEquals("Missing/incorrect east border", 0, map
                .getPolylinesConnecting(new LatLng(north, east), new LatLng(south, east)).size());
        Assert.assertNotEquals("Missing/incorrect south border", 0, map
                .getPolylinesConnecting(new LatLng(south, east), new LatLng(south, west)).size());
        Assert.assertNotEquals("Missing/incorrect west border", 0, map
                .getPolylinesConnecting(new LatLng(north, west), new LatLng(south, west)).size());
    }

    private JsonObject createC4Game(String id, String mode) {
        JsonObject fullUpdate = JsonHelper.game(id, SampleData.USER_EMAIL, GameStateID.RUNNING, mode,
                JsonHelper.player(SampleData.USER_EMAIL, TeamID.TEAM_GREEN, PlayerStateID.PLAYING));
        fullUpdate.addProperty("type", "full");
        return fullUpdate;
    }

    private JsonObject createC4AreaGame(String id, int cellSize) {
        JsonObject fullUpdate = createC4Game(id, "area");
        fullUpdate.addProperty("cellSize", cellSize);
        fullUpdate.addProperty("areaNorth", 40.104144);
        fullUpdate.addProperty("areaEast", -88.224515);
        fullUpdate.addProperty("areaSouth", 40.100573);
        fullUpdate.addProperty("areaWest", -88.230183);
        return fullUpdate;
    }

    private JsonObject createC4TargetGame(String id, int proximityThreshold) {
        JsonObject fullUpdate = createC4Game(id, "target");
        fullUpdate.addProperty("proximityThreshold", proximityThreshold);
        fullUpdate.add("targets", JsonHelper.arrayOf(
                JsonHelper.target("Armory", 40.104323, -88.231939, TeamID.OBSERVER),
                JsonHelper.target("VetMed", 40.092733, -88.220400, TeamID.OBSERVER)));
        return fullUpdate;
    }

}
