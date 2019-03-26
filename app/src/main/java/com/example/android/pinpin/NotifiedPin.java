package com.example.android.pinpin;

// Used for check when a notification should occur for a pin
public class NotifiedPin {
    private final Pin pin;
    private final long timeMilli;

    public NotifiedPin(Pin pin, long time) {
        this.pin = pin;
        this.timeMilli = time;
    }

    public Pin getPin() {
        return pin;
    }

    public long getTimeMilli() {
        return timeMilli;
    }

    @Override
    public int hashCode() {
        return pin.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof NotifiedPin)) {
            return false;
        }

        NotifiedPin other = (NotifiedPin) o;

        return this.pin.equals(other.getPin());
    }
}
