package edu.illinois.cs.cs125.spring2020.mp.logic;

import android.content.Context;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Polygon;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.neovisionaries.ws.client.WebSocket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.illinois.cs.cs125.spring2020.mp.R;

/**
 * Represents an area mode game. Keeps track of cells and the player's most recent capture.
 * <p>
 * All these functions are stubs that you need to implement.
 * Feel free to add any private helper functions that would be useful.
 * See {@link TargetGame} for an example of how multiplayer games are handled.
 */
public final class AreaGame extends Game {

    // You will probably want some instance variables to keep track of the game state
    // (similar to the area mode gameplay logic you previously wrote in GameActivity)

    /**
     * north.
     */
    private double north;

    /**
     * east.
     */
    private double east;

    /**
     * south.
     */
    private double south;

    /**
     * west.
     */
    private double west;

    /**
     * cellSize.
     */
    private int cellSize;

    /**
     * cells.
     */
    private List<Cell> cells = new ArrayList<>();

    /**
     * Cells each players has captured (email - JsonObject.x / .y).
     */
    private Map<String, List<Cell>> cellPaths = new HashMap<>();

    /**
     * Instance of AreaDivider.
     */
    private AreaDivider areaDivider;

    /**
     * Creates a game in area mode.
     * <p>
     * Loads the current game state from JSON into instance variables and populates the map
     * to show existing cell captures.
     * @param email the user's email
     * @param map the Google Maps control to render to
     * @param webSocket the websocket to send updates to
     * @param fullState the "full" update from the server
     * @param context the Android UI context
     */
    public AreaGame(final String email, final GoogleMap map, final WebSocket webSocket,
                    final JsonObject fullState, final Context context) {

        super(email, map, webSocket, fullState, context);

        north = fullState.get("areaNorth").getAsDouble();
        east = fullState.get("areaEast").getAsDouble();
        south = fullState.get("areaSouth").getAsDouble();
        west = fullState.get("areaWest").getAsDouble();

        cellSize = fullState.get("cellSize").getAsInt();

        // Initialize AreaDivider reference.
        areaDivider = new AreaDivider(north, east, south, west, cellSize);
        areaDivider.renderGrid(getMap());

        for (JsonElement t : fullState.getAsJsonArray("cells")) {
            JsonObject cellsInfo = t.getAsJsonObject();

            Cell cell = new Cell(cellsInfo.get("x").getAsInt(), cellsInfo.get("y").getAsInt(),
                    cellsInfo.get("email").getAsString(), cellsInfo.get("team").getAsInt(),
                    null);

            cell.setPolygon(drawCell(cell));

            cells.add(cell);

        }

        for (JsonElement p : fullState.get("players").getAsJsonArray()) {
            JsonObject player = p.getAsJsonObject();
            int team = player.get("team").getAsInt();
            String playerEmail = player.get("email").getAsString();

            List<Cell> path = new ArrayList<>();

            // Examine each target in the player entry's path
            for (JsonElement t : player.getAsJsonArray("path")) {
                Cell cell = getCell(t.getAsJsonObject());
                cell.setTeam(team);
                path.add(cell);
            }

            cellPaths.put(playerEmail, path);

        }

        for (int x = 0; x < areaDivider.getXCells(); x++) {
            for (int y = 0; y < areaDivider.getYCells(); y++) {

                if (getCell(x, y) == null) {

                    Cell cell = new Cell(x, y, "-", TeamID.OBSERVER, null);
                    cells.add(cell);

                }
            }
        }



    }

    /**
     * Called when the user's location changes.
     * <p>
     * Area mode games detect whether the player is in an uncaptured cell. Capture is possible if
     * the player has no captures yet or if the cell shares a side with the previous cell captured by
     * the player. If capture occurs, a polygon with the team color is added to the cell on the map
     * and a cellCapture update is sent to the server.
     * @param location the player's most recently known location
     */
    @Override
    public void locationUpdated(final LatLng location) {

//        System.out.println("location update");

        super.locationUpdated(location);

//        Cell x = getCell(AreaDivider.getXIndex(location), AreaDivider.getYIndex(location));
//
//        System.out.print(x.getX());
//        System.out.print(x.getY());
//        System.out.print(x.getTeam());

        if (location.longitude > east || location.longitude < west
                || location.latitude > north || location.latitude < south) {

//            System.out.println("out bound");

            return;

        } else if (cellPaths.get(getEmail()).isEmpty()) {

//            System.out.println("empty");

            Cell tryVisit = getCell(AreaDivider.getXIndex(location), AreaDivider.getYIndex(location));
            cellPaths.get(getEmail()).add(tryVisit);

            tryVisit.setTeam(getMyTeam());
            tryVisit.setPolygon(drawCell(tryVisit));

            JsonObject data = new JsonObject();
            data.addProperty("type", "cellCapture");
            data.addProperty("x", tryVisit.getX());
            data.addProperty("y", tryVisit.getY());

            sendMessage(data);

        } else {

            Cell tryVisit = getCell(AreaDivider.getXIndex(location), AreaDivider.getYIndex(location));
            Cell lastVisit = cellPaths.get(getEmail()).get(cellPaths.get(getEmail()).size() - 1);

//            System.out.print(tryVisit.getX());
//            System.out.print(tryVisit.getY());
//            System.out.println(tryVisit.getTeam());
//
//            System.out.print(lastVisit.getX());
//            System.out.print(lastVisit.getY());
//            System.out.println(lastVisit.getTeam());

            if (lastVisit.checkAreaModeRule(tryVisit)) {

                cellPaths.get(getEmail()).add(tryVisit);

                tryVisit.setTeam(getMyTeam());
                tryVisit.setPolygon(drawCell(tryVisit));

//                System.out.print(tryVisit.getX());
//                System.out.print(tryVisit.getY());
//                System.out.print(tryVisit.getTeam());
//                System.out.print(" | ");

                JsonObject data = new JsonObject();
                data.addProperty("type", "cellCapture");
                data.addProperty("x", tryVisit.getX());
                data.addProperty("y", tryVisit.getY());

                sendMessage(data);

            }

        }

    }

    /**
     * Processes an update from the server.
     * <p>
     * Since playerCellCapture events are specific to area mode games, this function handles those
     * by placing a polygon of the capturing player's team color on the newly captured cell and
     * recording the cell's new owning team.
     * All other message types are delegated to the superclass.
     * @param message JSON from the server (the "type" property indicates the update type)
     * @return whether the message type was recognized
     */
    @Override
    public boolean handleMessage(final JsonObject message) {

        // Some messages are common to all games - see if the superclass can handle it
        if (super.handleMessage(message)) {
            // If it took care of the update, this class's implementation doesn't need to do anything
            // Inform the caller that the update was handled
            return true;
        }

        if (message.get("type").getAsString().equals("playerCellCapture")) {

            String playerEmail = message.get("email").getAsString();
            int playerTeam = message.get("team").getAsInt();
            int x = message.get("x").getAsInt();
            int y = message.get("y").getAsInt();

            Cell cell = getCell(x, y);
            cell.setTeam(playerTeam);
            cell.setEmail(playerEmail);

//            System.out.println(cell.getX());
//            System.out.println(cell.getY());
//            System.out.println(cell.getTeam());

            cell.setPolygon(drawCell(cell));

            return true;

        } else {

            return false;

        }

    }

    /**
     * Gets a team's score in this area mode game.
     * @param teamId the team ID
     * @return the number of cells owned by the team
     */
    @Override
    public int getTeamScore(final int teamId) {

        int count = 0;

//        System.out.println(getCell(4, 0).getTeam());

        for (Cell cell : cells) {
            if (cell.getTeam() == teamId) {
                count++;
            }
        }

        return count;
    }

    /**
     * get cell object by x and y.
     * @param x x.
     * @param y y.
     * @return the cell object.
     */
    private Cell getCell(final int x, final int y) {
        for (Cell cell : cells) {
            if (cell.getX() == x && cell.getY() == y) {
                return cell;
            }
        }
        return null;
    }

    /**
     * get cell object by x and y.
     * @param xy the jsonObject contains x and y.
     * @return the cell object.
     */
    private Cell getCell(final JsonObject xy) {
        return getCell(xy.get("x").getAsInt(), xy.get("y").getAsInt());
    }


    /**
     * draw the polygon in the cell.
     * @param cell the cell.
     * @return the polygon drawn.
     */
    private Polygon drawCell(final Cell cell) {
        return AreaDivider.addPolygon(cell.getX(), cell.getY(),
                getContext().getResources()
                        .getIntArray(R.array.team_colors)[cell.getTeam()],
                getMap());
    }







    /**
     * Cell.
     */
    public class Cell {

        /**
         * x coordinate.
         */
        private int x;

        /**
         * y coordinate.
         */
        private int y;

        /**
         * email.
         */
        private String email;

        /**
         * team number.
         */
        private int team;

        /**
         * polygon of the cuurent grid.
         */
        private Polygon polygon;

        /**
         * Constructor.
         * @param setX x.
         * @param setY y.
         * @param setEmail email.
         * @param setTeam team.
         * @param setPolygon polygon.
         */
        public Cell(final int setX, final int setY, final String setEmail, final int setTeam,
                    final Polygon setPolygon) {

            x = setX;
            y = setY;
            email = setEmail;
            team = setTeam;

            polygon = setPolygon;

        }

        /**
         * get x.
         * @return x.
         */
        public int getX() {
            return x;
        }

        /**
         * get y.
         * @return y.
         */
        public int getY() {
            return y;
        }

        /**
         * get email.
         * @return email.
         */
        public String getEmail() {
            return email;
        }

        /**
         * get team.
         * @return team.
         */
        public int getTeam() {
            return team;
        }

        /**
         * get polygon.
         * @return polygon.
         */
        public Polygon getPolygon() {
            return polygon;
        }

        /**
         * Set the polygon.
         * @param p polygon.
         */
        public void setPolygon(final Polygon p) {
            polygon = p;
        }

        /**
         * Set the team of the cell and change the color of the polygon.
         * @param toSet team.
         */
        public void setTeam(final int toSet) {
            team = toSet;
        }

        /**
         * Set the email.
         * @param toSet email.
         */
        public void setEmail(final String toSet) {
            email = toSet;
        }

        /**
         * Check the area play rule of two cells.
         * @param b cell b.
         * @return can b be captured or not.
         */
        public boolean checkAreaModeRule(final Cell b) {

            if (b.getTeam() != TeamID.OBSERVER) {
                return false;
            }

            return (((this.getX() == b.getX() - 1) && this.getY() == b.getY())
                    || ((this.getX() == b.getX() + 1) && this.getY() == b.getY())
                    || (this.getX() == b.getX()) && (this.getY() == b.getY() - 1)
                    || (this.getX() == b.getX()) && (this.getY() == b.getY() + 1));
        }
    }


}
