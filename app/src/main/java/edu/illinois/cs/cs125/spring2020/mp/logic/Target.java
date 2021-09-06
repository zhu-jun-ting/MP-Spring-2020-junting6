package edu.illinois.cs.cs125.spring2020.mp.logic;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

/**
 * Represents a target in an ongoing target-mode game and manages the marker displaying it.
 *
 * The marker's color (hue, technically) changes to indicate the team owning it. The Google Maps
 * marker's hue should be BitmapDescriptorFactory.HUE_RED for the red team, BitmapDescriptorFactory.
 * HUE_YELLOW for the yellow team, BitmapDescriptorFactory.HUE_GREEN for the green team,
 * BitmapDescriptorFactory.HUE_BLUE for the blue team, and BitmapDescriptorFactory.HUE_VIOLET if
 * unclaimed.
 */
public class Target {

    /**
     * the position.
     */
    private LatLng position;

    /**
     * the current team.
     */
    private int team;

    /**
     * the current marker.
     */
    private Marker marker;

    /**
     * Creates a target in a target-mode game by placing an appropriately colored marker on the map.
     *
     * The marker's hue should reflect the team (if any) currently owning the target. See the class
     * description for the hue values to use.
     *
     * @param setMap the map to render to
     * @param setPosition the position of the target
     * @param setTeam the TeamID code of the team currently owning the target
     */
    public Target(final GoogleMap setMap, final LatLng setPosition, final int setTeam) {

        position = setPosition;
        team = setTeam;

        MarkerOptions options = new MarkerOptions().position(setPosition);
        marker = setMap.addMarker(options);
        BitmapDescriptor icon;

        if (setTeam == TeamID.TEAM_RED) {
            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED);
        } else if (setTeam == TeamID.TEAM_YELLOW) {
            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW);
        } else if (setTeam == TeamID.TEAM_GREEN) {
            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN);
        } else if (setTeam == TeamID.TEAM_BLUE) {
            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE);
        } else {
            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET);
        }

        marker.setIcon(icon);

    }

    /**
     * Gets the position of the target.
     *
     * @return the coordinates of the target
     */
    public LatLng getPosition() {

        return position;

    }

    /**
     * Gets the ID of the team currently owning this target.
     *
     * @return the owning team ID or OBSERVER if unclaimed
     */
    public int getTeam() {

        return team;

    }

    /**
     * Updates the owning team of this target and updates the hue of the marker to match.
     *
     * @param newTeam the ID of the team that captured the target
     */
    public void setTeam(final int newTeam) {

        BitmapDescriptor icon;

        if (newTeam == TeamID.TEAM_RED) {
            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED);
        } else if (newTeam == TeamID.TEAM_YELLOW) {
            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW);
        } else if (newTeam == TeamID.TEAM_GREEN) {
            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN);
        } else if (newTeam == TeamID.TEAM_BLUE) {
            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE);
        } else {
            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET);
        }

        marker.setIcon(icon);

        team = newTeam;

    }

}
