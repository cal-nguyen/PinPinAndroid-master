package com.example.android.pinpin;

import com.google.android.gms.maps.model.LatLng;

public class Pin {
    LatLng coords;
    String need;

    public Pin(LatLng coords, String need) {
        this.coords = coords;
        this.need = need;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Pin)) {
            return false;
        }

        return ((Pin)other).coords.equals(this.coords) && ((Pin)other).need.equals(this.need);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + coords.hashCode();
        result = prime * result + need.hashCode();
        return result;
    }
}
