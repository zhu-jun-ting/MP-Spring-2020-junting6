package edu.illinois.cs.cs125.spring2020.mp.shadows;

import com.google.android.gms.maps.UiSettings;

import org.robolectric.annotation.Implements;

@Implements(UiSettings.class)
public class ShadowUiSettings {
    // Only exists so Robolectric will make UiSettings non-final
}
