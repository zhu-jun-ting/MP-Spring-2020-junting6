package edu.illinois.cs.cs125.spring2020.mp.logic;

import android.graphics.Color;

import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Polygon;
import com.google.android.gms.maps.model.PolygonOptions;
import com.google.android.gms.maps.model.PolylineOptions;

/**
 * Divides a rectangular area into identically sized, roughly square cells.
 * <p>
 * Each cell is given an X and Y coordinate. X increases from the west boundary toward the east
 * boundary; Y increases from south to north. So (0, 0) is the cell in the southwest corner.
 * (0, 1) is the cell just north of the southwestern corner cell.
 * <p>
 * Instances of this class are created with a desired cell size. However, it is unlikely that the
 * area dimensions will be an exact multiple of that length, so placing fully sized cells would
 * leave a small "sliver" on the east or north side. Length should be redistributed so that each
 * cell is exactly the same size. If the area is 70 meters long in one dimension and the cell size
 * is 20 meters, there will be four cells in that dimension (there's room for three full cells
 * plus a 10m sliver), each of which is 70 / 4 = 17.5 meters long. Redistribution happens
 * independently for the two dimensions, so a 70x40 area would be divided into 17.5x20.0 cells
 * with a 20m cell size.
 * <p>
 * You may find Java's Math.ceil and Math.floor functions and our LatLngUtils.distance
 * function helpful.
 */
public final class AreaDivider {

    /**
     * north coordinate.
     */
    private static double north;

    /**
     * east coordinate.
     */
    private static double east;

    /**
     * south coordinate.
     */
    private static double south;

    /**
     * west coordinate.
     */
    private static double west;

    /**
     * sellSize the constructor gives to the class.
     */
    private static int cellSize;

    /**
     * the actual cellSize in longitudes.
     */
    private static double actualCellSizeLng;

    /**
     * the actual cellSize in latitudes.
     */
    private static double actualCellSizeLat;

    /**
     * the color of borders of the grid.
     */
    private static final int BLACK = Color.BLACK;

    /**
     * Creates an AreaDivider for an area.
     * <p>
     * Note the order of parameters carefully. A mismatch in interpretation of the arguments
     * between the constructor and its callers will result in unreasonable dimensions.
     * The isValid function can detect some such problems.
     * @param setNorth latitude of the north boundary
     * @param setEast longitude of the east boundary
     * @param setSouth latitude of the south boundary
     * @param setWest longitude of the east boundary
     * @param setCellSize the requested side length of each cell, in meters
     */
    public AreaDivider(final double setNorth,
                       final double setEast,
                       final double setSouth,
                       final double setWest,
                       final int setCellSize) {

        north = setNorth;
        east = setEast;
        south = setSouth;
        west = setWest;

        cellSize = setCellSize;

    }

    /**
     * Returns whether the configuration provided to the constructor is valid.
     * <p>
     * The configuration is valid if the cell size is positive the bounds delimit a region of
     * positive area. That is, the east boundary must be strictly further east than the west
     * boundary and the north boundary must be strictly further north than the south boundary.
     * @return whether the configuration provided to the constructor is valid.
     */
    public static boolean isValid() {

        return north > south && !LatLngUtils.same(north, south)
                && east > west && !LatLngUtils.same(east, west)
                && cellSize > 0;

    }

    /**
     * Gets the number of cells between the west and east boundaries.
     * @return the number of cells between the west and east boundaries.
     */
    public static int getXCells() {

        return (int) Math.ceil(LatLngUtils.distance(south, west, south, east) / cellSize);

    }

    /**
     * Gets the number of cells between the south and north boundaries.
     * @return the number of cells between the south and north boundaries.
     */
    public static int getYCells() {

        return (int) Math.ceil(LatLngUtils.distance(south, west, north, west) / cellSize);


    }

    /**
     * Gets the X coordinate of the cell containing the specified location.
     * <p>
     * The point is not necessarily within the area. If it is not, the return value must not
     * appear to be a valid cell index. For example, returning 0 for a point even slightly
     * west of the west boundary is not allowed.
     * @param location the location.
     * @return the number of cells between the south and north boundaries.
     */
    public static int getXIndex(final com.google.android.gms.maps.model.LatLng location) {

        actualCellSizeLng =  (east - west) / getXCells();
        return (int) Math.floor((location.longitude - west) / actualCellSizeLng);

    }

    /**
     * Gets the Y coordinate of the cell containing the specified location.
     * <p>
     * The point is not necessarily within the area. If it is not, the return value must not
     * appear to be a valid cell index. For example, returning 0 for a point even slightly
     * west of the west boundary is not allowed.
     * @param location the location.
     * @return the number of cells between the south and north boundaries.
     */
    public static int getYIndex(final com.google.android.gms.maps.model.LatLng location) {

        actualCellSizeLat =  (north - south) / getYCells();
        return (int) Math.floor((location.latitude - south) / actualCellSizeLat);

    }

    /**
     * Gets the boundaries of the specified cell as a Google Maps LatLngBounds object.
     * <p>
     * Note that the LatLngBounds constructor takes the southwest and northeast points of the
     * rectangular region as LatLng objects.
     * @param x the cell's X coordinate
     * @param y the cell's Y coordinate
     * @return the LatLngBounds object of the boundaries of specified block
     */
    public static com.google.android.gms.maps.model.LatLngBounds getCellBounds(final int x,
                                                                        final int y) {

        LatLng sw = new LatLng(south + y * (north - south) / getYCells(),
                west + x * (east - west) / getXCells());

        LatLng ne = new LatLng(south + (y + 1) * (north - south) / getYCells(),
                west + (x + 1) * (east - west) / getXCells());

        return new LatLngBounds(sw, ne);

    }

    /**
     * Draws the grid to a map using solid black polylines.
     * <p>
     * There should be one line on each of the four boundaries of the overall area and as many
     * internal lines as necessary to divide the rows and columns of the grid. Each line should
     * span the whole width or height of the area rather than the side of just one cell.
     * For example, an area divided into a 2x3 grid would be drawn with 7 lines total: 4
     * for the outer boundaries, 1 vertical line to divide the west half from the east half
     * (2 columns), and 2 horizontal lines to divide the area into 3 rows.
     * <p>
     * See the provided addLine function from GameActivity for how to add a line to the map.
     * Since these lines should be black, they should not be paired with any extra "border" lines.
     * <p>
     * If equality comparisons of double variables do not work as expected, consider taking
     * advantage of our LatLngUtils.same function.
     * @param map the map
     */
    public static void renderGrid(final com.google.android.gms.maps.GoogleMap map) {

        /**
         * render the vertical lines.
         */
        for (int i = 0; i <= getXCells(); i++) {

            double lng = west + i * (east - west) / getXCells();
            addLine(south, lng, north, lng, BLACK, map);

//            System.out.print(i);
//            System.out.println("vertical");

        }

        /**
         * render the horizontal lines.
         */
        for (int j = 0; j <= getYCells(); j++) {

            double lat = south + j * (north - south) / getYCells();
            addLine(lat, west, lat, east, BLACK, map);

//            System.out.print(j);
//            System.out.println("horizontal");

        }

    }

    /**
     * According to the addLine function in GameActivity, AreaDivider.addLine deleted the border
     * part that satisfies the border lines of the area mode.
     * @param startLat the latitude of one endpoint of the line
     * @param startLng the longitude of that endpoint
     * @param endLat the latitude of the other endpoint of the line
     * @param endLng the longitude of that other endpoint
     * @param color the color to fill the line with
     * @param map the google map object
     */
    public static void addLine(final double startLat, final double startLng,
                        final double endLat, final double endLng, final int color,
                        final com.google.android.gms.maps.GoogleMap map) {

        // Package the loose coordinates into LatLng objects usable by Google Maps
        LatLng start = new LatLng(startLat, startLng);
        LatLng end = new LatLng(endLat, endLng);

        // Configure and add a colored line
        final int lineThickness = 12;
        PolylineOptions fill = new PolylineOptions().add(start, end).color(color).width(lineThickness).zIndex(1);
        map.addPolyline(fill);

    }

    /**
     * Add a polygon at the x, y position and return the polygon reference.
     * @param x x.
     * @param y y.
     * @param color the color.
     * @param map the- map.
     * @return the polygon option.
     */
    public static Polygon addPolygon(final int x, final int y, final int color,
                           final com.google.android.gms.maps.GoogleMap map) {

        LatLng a = new LatLng(south + y * (north - south) / getYCells(),
                west + x * (east - west) / getXCells());
        LatLng b = new LatLng(south + (y + 1) * (north - south) / getYCells(),
                west + x * (east - west) / getXCells());
        LatLng c = new LatLng(south + (y + 1) * (north - south) / getYCells(),
                west + (x + 1) * (east - west) / getXCells());
        LatLng d = new LatLng(south + y * (north - south) / getYCells(),
                west + (x + 1) * (east - west) / getXCells());

        Polygon polygon = map.addPolygon(new PolygonOptions()
                .add(a, b, c, d)
                .fillColor(color));

        return polygon;

    }

    /**
     * to find the south west corner of a certain grid.
     * @param x x coordinate
     * @param y y coordinate
     * @return the LatLng object of the south west corner
     */
    public static com.google.android.gms.maps.model.LatLng getSouthWest(final int x, final int y) {

        return new LatLng(
                south + y * (north - south) / getYCells(),
                west + x * (east - west) / getXCells());

    }


}
