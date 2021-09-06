package edu.illinois.cs.cs125.spring2020.mp.logic;

import com.google.android.gms.maps.model.LatLng;

/**
 * Holds a method to determine whether two lines cross.
 * <p>
 * The implementation given here works. You do not need to change it.
 */
public class LineCrossDetector {

    /**
     * Determines whether two lines cross on a map.
     * @param firstStart an endpoint of one line
     * @param firstEnd the other endpoint of that line
     * @param secondStart an endpoint of another line
     * @param secondEnd the other endpoint of that other line
     * @return whether the two lines cross
     */
    public static boolean linesCross(final LatLng firstStart, final LatLng firstEnd,
                                     final LatLng secondStart, final LatLng secondEnd) {
        if (LatLngUtils.same(firstEnd, secondStart) || LatLngUtils.same(secondEnd, firstStart)
                || LatLngUtils.same(firstStart, secondStart) || LatLngUtils.same(firstEnd, secondEnd)) {
            // The lines are just touching, not crossing each other
            return false;
        }

        boolean firstVertical = LatLngUtils.same(firstStart.longitude, firstEnd.longitude);
        boolean secondVertical = LatLngUtils.same(secondStart.longitude, secondEnd.longitude);
        if (firstVertical && secondVertical) {
            // They're parallel vertical lines
            return false;
        } else if (firstVertical) {
            return lineCrossesVertical(firstStart, firstEnd, secondStart, secondEnd);
        } else if (secondVertical) {
            return lineCrossesVertical(secondStart, secondEnd, firstStart, firstEnd);
        }

        double firstSlope = lineSlope(firstStart, firstEnd);
        double secondSlope = lineSlope(secondStart, secondEnd);
        if (LatLngUtils.same(firstSlope, secondSlope)) {
            // They're parallel
            return false;
        }

        double firstIntercept = firstStart.latitude - firstSlope * firstStart.longitude;
        double secondIntercept = secondStart.latitude - secondSlope * secondStart.longitude;
        double intersectionX = -(firstIntercept - secondIntercept) / (firstSlope - secondSlope);
        if (LatLngUtils.same(intersectionX, firstStart.longitude)
                || LatLngUtils.same(intersectionX, firstEnd.longitude)
                || LatLngUtils.same(intersectionX, secondStart.longitude)
                || LatLngUtils.same(intersectionX, secondEnd.longitude)) {
            // Endpoint of one line is in the middle of the other line
            return true;
        }
        boolean onFirst = intersectionX > Math.min(firstStart.longitude, firstEnd.longitude)
                && intersectionX < Math.max(firstStart.longitude, firstEnd.longitude);
        boolean onSecond = intersectionX > Math.min(secondStart.longitude, secondEnd.longitude)
                && intersectionX < Math.max(secondStart.longitude, secondEnd.longitude);
        return onFirst && onSecond;
    }

    /**
     * Determines if a non-vertical line crosses a vertical line.
     * @param verticalStart one endpoint of the vertical line
     * @param verticalEnd the other endpoint of the vertical line (same longitude as verticalStart)
     * @param lineStart one endpoint of the non-vertical line
     * @param lineEnd the other endpoint of the non-vertical line
     * @return whether the lines cross
     */
    private static boolean lineCrossesVertical(final LatLng verticalStart, final LatLng verticalEnd,
                                               final LatLng lineStart, final LatLng lineEnd) {
        if (Math.max(lineStart.longitude, lineEnd.longitude) < verticalStart.longitude
                || Math.min(lineStart.longitude, lineEnd.longitude) > verticalStart.longitude) {
            return false;
        }
        double slope = lineSlope(lineStart, lineEnd);
        double yAtVert = slope * (verticalStart.longitude - lineStart.longitude) + lineStart.latitude;
        if (LatLngUtils.same(yAtVert, verticalStart.latitude) || LatLngUtils.same(yAtVert, verticalEnd.latitude)) {
            // Ends on the middle of the non-vertical line
            return true;
        }
        return yAtVert > Math.min(verticalStart.latitude, verticalEnd.latitude)
                && yAtVert < Math.max(verticalStart.latitude, verticalEnd.latitude);
    }

    /**
     * Determines the slope of a non-vertical line.
     * @param start one endpoint of the line
     * @param end the other endpoint
     * @return the slope, treating longitude as X and latitude as Y
     */
    private static double lineSlope(final LatLng start, final LatLng end) {
        return (end.latitude - start.latitude) / (end.longitude - start.longitude);
    }

}
