package edu.illinois.cs.cs125.spring2020.mp;

import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.test.core.app.ApplicationProvider;

import com.android.volley.Request;
import com.android.volley.Response;
import com.firebase.ui.auth.AuthUI;
import com.google.firebase.auth.FirebaseAuth;
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
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowPackageManager;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import edu.illinois.cs.cs125.gradlegrader.annotations.Graded;
import edu.illinois.cs.cs125.robolectricsecurity.PowerMockSecurity;
import edu.illinois.cs.cs125.robolectricsecurity.Trusted;
import edu.illinois.cs.cs125.spring2020.mp.logic.GameStateID;
import edu.illinois.cs.cs125.spring2020.mp.logic.GameSummary;
import edu.illinois.cs.cs125.spring2020.mp.logic.PlayerStateID;
import edu.illinois.cs.cs125.spring2020.mp.logic.TeamID;
import edu.illinois.cs.cs125.spring2020.mp.logic.WebApi;
import edu.illinois.cs.cs125.spring2020.mp.shadows.ShadowLog;

@RunWith(RobolectricTestRunner.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@PowerMockIgnore({"org.mockito.*", "org.powermock.*", "org.robolectric.*", "android.*", "androidx.*", "com.google.android.*", "edu.illinois.cs.cs125.spring2020.mp.shadows.*"})
@PrepareForTest({WebApi.class, FirebaseAuth.class, AuthUI.class})
@Trusted
@Config(sdk = 28)
public class Checkpoint2Test {

    @Rule
    public PowerMockRule mockStaticClasses = new PowerMockRule();

    @Before
    public void setup() {
        PowerMockSecurity.secureMockMethodCache();
        FirebaseMocker.mock();
        FirebaseMocker.setEmail(null);
        WebApiMocker.interceptHttp();
        ShadowLog.loadConfig("../log.yaml");
    }

    @After
    public void teardown() {
        WebApiMocker.reset();
        FirebaseMocker.setBan(null);
    }

    @Test(timeout = 60000)
    @Graded(points = 5)
    public void testManifest() throws PackageManager.NameNotFoundException {
        // Make sure LaunchActivity is the startup activity
        Context appContext = ApplicationProvider.getApplicationContext();
        ShadowPackageManager packageManager = Shadows.shadowOf(appContext.getPackageManager());
        List<IntentFilter> filters = packageManager.getIntentFiltersForActivity(new ComponentName(appContext, LaunchActivity.class));
        Assert.assertEquals("LaunchActivity should have an <intent-filter> section in the manifest", 1, filters.size());
        IntentFilter filter = filters.get(0);
        Assert.assertNotEquals("LaunchActivity's <intent-filter> should specify the MAIN action", 0, filter.countActions());
        Assert.assertTrue("LaunchActivity should have the MAIN action in the manifest",
                IntStream.range(0, filter.countActions()).anyMatch(i -> filter.getAction(i).equals("android.intent.action.MAIN")));

        // Make sure MainActivity is no longer a startup activity
        filters = packageManager.getIntentFiltersForActivity(new ComponentName(appContext, MainActivity.class));
        Assert.assertEquals("MainActivity should no longer have an <intent-filter> section in the manifest", 0, filters.size());
    }

    @Test(timeout = 60000)
    @Graded(points = 20)
    public void testLaunchActivity() {
        // Start the app without having logged in
        FirebaseMocker.mockAuthUI();
        LaunchActivity activity = Robolectric.buildActivity(LaunchActivity.class).create().start().resume().get();
        Assert.assertFalse("LaunchActivity should not finish until a user has signed in", activity.isFinishing());
        ShadowActivity.IntentForResult ifr = Shadows.shadowOf(activity).getNextStartedActivityForResult();
        Assert.assertNotNull("LaunchActivity should start a Firebase Auth signin UI intent " +
                "(with startActivityForResult) if the user isn't logged in", ifr);
        Assert.assertNotEquals("LaunchActivity should not launch MainActivity before a user has signed in",
                new ComponentName(activity, MainActivity.class), ifr.intent.getComponent());
        Assert.assertNotEquals("LaunchActivity should never relaunch itself",
                new ComponentName(activity, LaunchActivity.class), ifr.intent.getComponent());
        Assert.assertTrue("The login intent should have been created by a SignInIntentBuilder",
                ifr.intent.hasExtra(FirebaseMocker.UI_INTENT_PROPERTY));
        Assert.assertEquals(FirebaseMocker.UI_INTENT_TOKEN, ifr.intent.getStringExtra(FirebaseMocker.UI_INTENT_PROPERTY));
        Assert.assertNull("LaunchActivity should not launch multiple intents simultaneously",
                Shadows.shadowOf(activity).getNextStartedActivityForResult());
        Shadows.shadowOf(activity).clearNextStartedActivities();

        // Cancel login
        Shadows.shadowOf(activity).callOnActivityResult(ifr.requestCode, LaunchActivity.RESULT_CANCELED, new Intent());
        Assert.assertFalse("LaunchActivity should not finish if the user cancels signin", activity.isFinishing());
        Assert.assertNull("LaunchActivity should not immediately retry signin if the user cancels",
                Shadows.shadowOf(activity).getNextStartedActivityForResult());
        Assert.assertNull("LaunchActivity should not launch another activity if the user cancels signin",
                Shadows.shadowOf(activity).getNextStartedActivity());

        // Press the button to retry login
        Button goLogin = activity.findViewById(IdLookup.require("goLogin"));
        Assert.assertEquals("The button to relaunch login should be visible after a canceled attempt",
                View.VISIBLE, goLogin.getVisibility());
        goLogin.performClick();
        ifr = Shadows.shadowOf(activity).getNextStartedActivityForResult();
        Assert.assertNotEquals("LaunchActivity should not relaunch itself",
                new ComponentName(activity, LaunchActivity.class), ifr.intent.getComponent());
        Assert.assertNotNull("Pressing the button should relaunch the Firebase Auth UI", ifr);
        Assert.assertEquals("The intent should have been created by a SignInIntentBuilder",
                FirebaseMocker.UI_INTENT_TOKEN, ifr.intent.getStringExtra(FirebaseMocker.UI_INTENT_PROPERTY));

        // Log in
        FirebaseMocker.setEmail(SampleData.USER_EMAIL);
        Shadows.shadowOf(activity).callOnActivityResult(ifr.requestCode, LaunchActivity.RESULT_OK, new Intent());
        Intent intent = Shadows.shadowOf(activity).getNextStartedActivity();
        Assert.assertNotNull("LaunchActivity should start the main activity once the user is logged in", intent);
        Assert.assertEquals("LaunchActivity should start MainActivity once the user is logged in",
                new ComponentName(activity, MainActivity.class), intent.getComponent());
        Assert.assertTrue("LaunchActivity should finish() once the user is logged in", activity.isFinishing());
        Shadows.shadowOf(activity).clearNextStartedActivities();

        // Start the app when the user is already logged in
        activity = Robolectric.buildActivity(LaunchActivity.class).create().start().resume().get();
        intent = Shadows.shadowOf(activity).getNextStartedActivity();
        Assert.assertNotNull("LaunchActivity should immediately launch the main activity if the user is logged in", intent);
        Assert.assertEquals("LaunchActivity should immediately start MainActivity (not login) if the user is already logged in",
                new ComponentName(activity, MainActivity.class), intent.getComponent());
        Assert.assertNull("LaunchActivity should not launch a login flow in the user is already logged in",
                Shadows.shadowOf(activity).getNextStartedActivity());
        Assert.assertTrue("LaunchActivity should immediately finish() if the user is already logged in", activity.isFinishing());
    }

    @Test(timeout = 60000)
    @Graded(points = 10)
    public void testSummaryGameInformation() {
        // Test one game
        JsonObject game = JsonHelper.game("NCC-1701-D", "picard@example.com", GameStateID.PAUSED, "target",
                JsonHelper.player("picard@example.com", TeamID.OBSERVER, PlayerStateID.ACCEPTED));
        GameSummary summary = new GameSummary(game);
        Assert.assertEquals("getId should return the game's ID",
                "NCC-1701-D", summary.getId());
        Assert.assertEquals("getOwner should return the game owner's email",
                "picard@example.com", summary.getOwner());
        Assert.assertEquals("getMode should return the game mode",
                "target", summary.getMode());

        // Test a different game with multiple players
        game = JsonHelper.game("NCC-1031", "pike@example.com", GameStateID.RUNNING, "area",
                JsonHelper.player("burnham@example.com", TeamID.TEAM_BLUE, PlayerStateID.ACCEPTED),
                JsonHelper.player("pike@example.com", TeamID.OBSERVER, PlayerStateID.PLAYING),
                JsonHelper.player("saru@example.com", TeamID.TEAM_YELLOW, PlayerStateID.INVITED));
        summary = new GameSummary(game);
        Assert.assertEquals("getId should return the game's ID",
                "NCC-1031", summary.getId());
        Assert.assertEquals("getOwner should return the game owner's email (not that of the first player entry)",
                "pike@example.com", summary.getOwner());
        Assert.assertEquals("getMode should return the game mode",
                "area", summary.getMode());

        // Test many random games
        for (int i = 0; i < 20; i++) {
            String id = RandomHelper.randomId();
            String mode = RandomHelper.randomMode();
            String owner = RandomHelper.randomEmail();
            game = JsonHelper.game(id, owner, RandomHelper.randomGameState(), mode,
                    JsonHelper.player(owner, RandomHelper.randomTeam(), RandomHelper.randomPlayerState()),
                    JsonHelper.player(RandomHelper.randomEmail(owner), RandomHelper.randomTeam(), RandomHelper.randomPlayerState()));
            summary = new GameSummary(game);
            Assert.assertEquals("Incorrect game ID", id, summary.getId());
            Assert.assertEquals("Incorrect game mode", mode, summary.getMode());
            Assert.assertEquals("Incorrect game owner", owner, summary.getOwner());
        }
    }

    @Test(timeout = 60000)
    @Graded(points = 10)
    public void testSummaryPlayerRole() {
        FirebaseMocker.setBan("GameSummary should use the specified email rather than asking Firebase");
        Context appContext = ApplicationProvider.getApplicationContext();

        // Test a game where the user is the only one involved
        JsonObject game = JsonHelper.game(RandomHelper.randomId(), SampleData.USER_EMAIL, GameStateID.PAUSED, "area",
                JsonHelper.player(SampleData.USER_EMAIL, TeamID.TEAM_BLUE, PlayerStateID.ACCEPTED));
        GameSummary summary = new GameSummary(game);
        Assert.assertEquals("getPlayerRole should return the user's team/role name",
                "Blue", summary.getPlayerRole(SampleData.USER_EMAIL, appContext));

        // Test a game with multiple users involved
        game = JsonHelper.game(RandomHelper.randomId(), "someone@illinois.edu", GameStateID.PAUSED, "area",
                JsonHelper.player("someone@illinois.edu", TeamID.TEAM_YELLOW, PlayerStateID.ACCEPTED),
                JsonHelper.player("rebo@example.com", TeamID.OBSERVER, PlayerStateID.ACCEPTED),
                JsonHelper.player("zooty@example.com", TeamID.TEAM_RED, PlayerStateID.ACCEPTED));
        summary = new GameSummary(game);
        Assert.assertEquals("getPlayerRole should return the specified user's team/role",
                "Yellow", summary.getPlayerRole("someone@illinois.edu", appContext));
        Assert.assertEquals("getPlayerRole should return the team/role of the specified user (not necessarily the owner)",
                "Observer", summary.getPlayerRole("rebo@example.com", appContext));
        Assert.assertEquals("getPlayerRole should return the team of the specified user (not necessarily the owner)",
                "Red", summary.getPlayerRole("zooty@example.com", appContext));

        // Randomized tests from JSON
        for (JsonObject test : JsonResourceLoader.loadArray("playersummary")) {
            summary = new GameSummary(test.getAsJsonObject("game"));
            Assert.assertEquals("Incorrect player team/role name",
                    test.get("role").getAsString(), summary.getPlayerRole(test.get("email").getAsString(), appContext));
        }
    }

    @Test(timeout = 60000)
    @Graded(points = 10)
    public void testSummaryGameClassification() {
        FirebaseMocker.setBan("GameSummary should use the specified email rather than asking Firebase");

        // Test an ongoing game where the user is the only one involved
        JsonObject game = JsonHelper.game(RandomHelper.randomId(), SampleData.USER_EMAIL, GameStateID.PAUSED, "area",
                JsonHelper.player(SampleData.USER_EMAIL, TeamID.OBSERVER, PlayerStateID.ACCEPTED));
        GameSummary summary = new GameSummary(game);
        Assert.assertTrue("Games where the user accepted their invitation are ongoing",
                summary.isOngoing(SampleData.USER_EMAIL));
        Assert.assertFalse("Games are only invitations if the user is invited",
                summary.isInvitation(SampleData.USER_EMAIL));

        // Test a game with multiple players
        game = JsonHelper.game(RandomHelper.randomId(), "xyz@example.com", GameStateID.RUNNING, "target",
                JsonHelper.player("xyz@example.com", TeamID.TEAM_BLUE, PlayerStateID.ACCEPTED),
                JsonHelper.player("someone@example.com", TeamID.OBSERVER, PlayerStateID.INVITED),
                JsonHelper.player(SampleData.USER_EMAIL, TeamID.TEAM_GREEN, PlayerStateID.PLAYING));
        summary = new GameSummary(game);
        Assert.assertFalse("Games where the specified user is invited are not ongoing",
                summary.isOngoing("someone@example.com"));
        Assert.assertTrue("Games where the specified user is invited are invitations",
                summary.isInvitation("someone@example.com"));
        Assert.assertTrue("Games where the specified user is playing are ongoing",
                summary.isOngoing(SampleData.USER_EMAIL));
        Assert.assertFalse("Games where the specified user is playing are not invitations",
                summary.isInvitation(SampleData.USER_EMAIL));

        // Test a historical game
        game = JsonHelper.game(RandomHelper.randomId(), "chuchu@example.com", GameStateID.ENDED, "area",
                JsonHelper.player("chuchu@example.com", TeamID.OBSERVER, PlayerStateID.ACCEPTED),
                JsonHelper.player(SampleData.USER_EMAIL, TeamID.TEAM_RED, PlayerStateID.INVITED));
        summary = new GameSummary(game);
        Assert.assertFalse("Ended games are not invitations", summary.isInvitation(SampleData.USER_EMAIL));
        Assert.assertFalse("Ended games are not ongoing", summary.isInvitation(SampleData.USER_EMAIL));
        Assert.assertFalse("Ended games are not invitations for any user", summary.isInvitation("chuchu@example.com"));
        Assert.assertFalse("Ended games are not ongoing for any user", summary.isInvitation("chuchu@example.com"));

        // Randomized tests from JSON
        for (JsonObject test : JsonResourceLoader.loadArray("playersummary")) {
            summary = new GameSummary(test.getAsJsonObject("game"));
            String email = test.get("email").getAsString();
            Assert.assertEquals("isOngoing was incorrect",
                    test.get("ongoing").getAsBoolean(), summary.isOngoing(email));
            Assert.assertEquals("isInvitation was incorrect",
                    test.get("invitation").getAsBoolean(), summary.isInvitation(email));
        }
    }

    @Test(timeout = 60000)
    @Graded(points = 15)
    @SuppressWarnings("ConstantConditions")
    public void testResponseButtons() {
        // Start the activity
        FirebaseMocker.setEmail(SampleData.USER_EMAIL);
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).create().start().resume().get();
        JsonObject response = SampleData.createGamesResponse();
        processGamesRequest(callback -> callback.onResponse(response.deepCopy()));

        // Check initial list sizes
        LinearLayout invitationsList = activity.findViewById(IdLookup.require("invitationsList"));
        LinearLayout ongoingGamesList = activity.findViewById(IdLookup.require("ongoingGamesList"));
        Assert.assertEquals("The invitations list should have one entry per pending invitation",
                2, invitationsList.getChildCount());
        Assert.assertEquals("The ongoing games list should have one entry per ongoing game",
                2, ongoingGamesList.getChildCount());

        // Decline an invitation
        ViewGroup gameInfoChunk = (ViewGroup) invitationsList.getChildAt(0);
        Button declineButton = findButtonWithText(gameInfoChunk, "Decline");
        ShadowDialog.reset();
        declineButton.performClick();
        Dialog dialog = ShadowDialog.getLatestDialog();
        if (dialog != null) {
            // If there's a confirmation dialog, press the positive button to confirm
            dialog.findViewById(android.R.id.button1).performClick();
        }
        String firstInviteId = response.getAsJsonArray("games").get(0).getAsJsonObject().get("id").getAsString();
        WebApiMocker.processOne("Clicking a Decline button should use the web API to decline the invitation",
                (path, method, body, callback, errorListener) -> {
                    Assert.assertEquals("Incorrect endpoint to decline invitation",
                            "/games/" + firstInviteId + "/decline", path);
                    Assert.assertEquals("Invitation responses should use POST requests", Request.Method.POST, method);
                    Assert.assertNull("Invitation responses should not have a body", body);
                    callback.onResponse(null);
                });
        response.getAsJsonArray("games").remove(0);
        processGamesRequest("The games lists should be fetched again after an invitation response request completes",
                callback -> callback.onResponse(response.deepCopy()));
        Assert.assertEquals("The invitations list should update after a response request completes",
                1, invitationsList.getChildCount());

        // Accept an invitation
        gameInfoChunk = (ViewGroup) invitationsList.getChildAt(0);
        Button acceptButton = findButtonWithText(gameInfoChunk, "Accept");
        acceptButton.performClick();
        String secondInviteId = response.getAsJsonArray("games").get(3).getAsJsonObject().get("id").getAsString();
        WebApiMocker.processOne("Clicking an Accept button should use the web API to accept the invitation",
                (path, method, body, callback, errorListener) -> {
                    Assert.assertEquals("Incorrect endpoint to accept invitation",
                            "/games/" + secondInviteId + "/accept", path);
                    Assert.assertEquals("Invitation responses should use POST requests", Request.Method.POST, method);
                    Assert.assertNull("Invitation responses should not have a body", body);
                    callback.onResponse(null);
                });
        response.getAsJsonArray("games").get(3).getAsJsonObject().getAsJsonArray("players")
                .get(2).getAsJsonObject().addProperty("state", PlayerStateID.ACCEPTED);
        processGamesRequest("The games lists should be fetched again after an invitation response request completes",
                callback -> callback.onResponse(response.deepCopy()));
        Assert.assertEquals("The ongoing games list should update after an invitation accept request completes",
                3, ongoingGamesList.getChildCount());

        // Leave a game
        ShadowDialog.reset();
        gameInfoChunk = (ViewGroup) ongoingGamesList.getChildAt(2);
        Button leaveButton = findButtonWithText(gameInfoChunk, "Leave");
        leaveButton.performClick();
        dialog = ShadowDialog.getLatestDialog();
        if (dialog != null) {
            // If there's a confirmation dialog, press the positive button to confirm
            dialog.findViewById(android.R.id.button1).performClick();
        }
        WebApiMocker.processOne("Clicking a Leave button should use the web API to leave the game",
                (path, method, body, callback, errorListener) -> {
                    Assert.assertEquals("Incorrect API endpoint to leave game",
                            "/games/" + secondInviteId + "/leave", path);
                    Assert.assertEquals("Leave-game commands should use POST requests", Request.Method.POST, method);
                    Assert.assertNull("Leave-game commands should not have a body", body);
                    callback.onResponse(null);
                });
        response.getAsJsonArray("games").remove(3);
        processGamesRequest("The games lists should be fetched again after a leave-game request completes",
                callback -> callback.onResponse(response.deepCopy()));
        Assert.assertEquals("The ongoing games list should update after a leave-game request completes",
                2, ongoingGamesList.getChildCount());
    }

    @Test(timeout = 60000)
    @Graded(points = 10)
    public void testGameIntent() {
        // Start the activity
        FirebaseMocker.setEmail(SampleData.USER_EMAIL);
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).create().start().resume().get();
        JsonObject response = SampleData.createGamesResponse();
        processGamesRequest(callback -> callback.onResponse(response.deepCopy()));
        LinearLayout ongoingGamesList = activity.findViewById(IdLookup.require("ongoingGamesList"));

        // Enter games
        for (int i = 0; i < 2; i++) {
            ViewGroup gameInfoChunk = (ViewGroup) ongoingGamesList.getChildAt(i);
            Button enterButton = findButtonWithText(gameInfoChunk, "Enter");
            Assert.assertNotNull("Ongoing game entries should each have an Enter button", enterButton);
            enterButton.performClick();
            Intent intent = Shadows.shadowOf(activity).getNextStartedActivity();
            Assert.assertNotNull("Pressing an ongoing game's Enter button should start the game activity", intent);
            Assert.assertEquals("Pressing Enter should launch GameActivity",
                    new ComponentName(activity, GameActivity.class), intent.getComponent());
            Assert.assertTrue("The intent should specify the game ID in the 'game' extra",
                    intent.hasExtra("game"));
            String gameId = response.getAsJsonArray("games").get(i + 1).getAsJsonObject().get("id").getAsString();
            Assert.assertEquals("Incorrect game ID in intent 'game' extra",
                    gameId, intent.getStringExtra("game"));
        }
    }

    private void processGamesRequest(String failMessage, Consumer<Response.Listener<JsonObject>> handler) {
        WebApiMocker.processOne(failMessage, (path, method, body, callback, errorListener) -> {
            Assert.assertEquals("Incorrect endpoint for game information", "/games", path);
            Assert.assertEquals("Game information should be fetched in a GET request", Request.Method.GET, method);
            Assert.assertNull("GET requests should not have a payload", body);
            handler.accept(callback);
        });
    }

    private void processGamesRequest(Consumer<Response.Listener<JsonObject>> handler) {
        processGamesRequest("MainActivity should immediately start a web request to fetch game information", handler);
    }

    private Button findButtonWithText(ViewGroup group, String text) {
        ArrayList<View> list = new ArrayList<>();
        group.findViewsWithText(list, text, View.FIND_VIEWS_WITH_TEXT);
        for (View v : list) {
            if (v instanceof Button) return (Button) v;
        }
        return null;
    }

}
