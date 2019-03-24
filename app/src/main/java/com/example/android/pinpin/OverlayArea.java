package com.example.android.pinpin;

import com.google.android.gms.maps.model.LatLng;

// Used for limiting the possible locations that a pin can be placed
public class OverlayArea {
    LatLng coords1, coords2, coords3;

    public OverlayArea(LatLng coords1, LatLng coords2, LatLng coords3) {
        this.coords1 = coords1;
        this.coords2 = coords2;
        this.coords3 = coords3;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof OverlayArea)) {
            return false;
        }

        return ((OverlayArea)other).coords1.equals(this.coords1) &&
                ((OverlayArea)other).coords2.equals(this.coords2) &&
                ((OverlayArea)other).coords2.equals(this.coords3);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + coords1.hashCode();
        result = prime * result + coords2.hashCode();
        result = prime * result + coords3.hashCode();
        return result;
    }
}
