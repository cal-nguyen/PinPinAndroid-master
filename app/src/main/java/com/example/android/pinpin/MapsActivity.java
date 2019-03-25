package com.example.android.pinpin;

import android.Manifest;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.ContextCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.ui.PlaceAutocompleteFragment;
import com.google.android.gms.location.places.ui.PlaceSelectionListener;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolygonOptions;
import com.google.android.gms.maps.model.Polygon;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.maps.android.PolyUtil;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashSet;
import java.util.Set;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener{

    private GoogleMap mMap;
    private GoogleApiClient client;
    private LatLng currLoc;
    private static final int REQUEST_LOCATION_CODE = 99;
    private boolean canPin = true;
    private boolean pinAdded = false;
    private boolean canNotify = true;
    private long pinCooldown;
    private NotificationManagerCompat notificationManager;
    Set<Pin> dbCoords = new HashSet<>();
    Set<Polygon> validAreas = new HashSet<>();
    private static Circle currLocCircle;
    private static final double VALID_RADIUS_METERS = 21.0;
    private static final double PIN_VIEW_RAD_METERS = 500.0;
    private static final int PIN_TIMER_SEC = 60;
    private static final int NOTIFY_TIMER_SEC = 600;
    private static final double MIN_CLICK_DIST = 0.03;
    private static final String CHANNEL_ID = "notification_id";
    private static final int NOTIFICATION_ID = 3000;

    // Reads in the coordinates from the database and adds/removes pins from the map
    final Handler timerHandler = new Handler();
    Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            System.out.println("NEW CYCLE");

            // Read and Send new coords in new thread
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        // Read in coordinates from the HTML Server
                        URLConnection c = new URL("http://129.65.221.101/php/getPinPinGPSdata.php").openConnection();
                        BufferedReader reader = new BufferedReader(new InputStreamReader(c.getInputStream()));

                        // Read in each pin coordinate from database
                        for (String line; (line = reader.readLine()) != null;) {
                            // Separate the given line by whitespace
                            String[] coords = line.split("\\s");

                            try {
                                Double lat = Double.parseDouble(coords[0]);
                                Double lng = Double.parseDouble(coords[1]);
                                LatLng l = new LatLng(lat, lng);

                                Pin p = new Pin(l, coords[2], Long.parseLong(coords[3]));

                                if (!dbCoords.contains(p)) {
                                    dbCoords.add(p);
                                }
                            } catch (NumberFormatException e) {
                                System.out.println("The coord in the database is not formatted correctly: " + line);
                            }
                        }

                        // Have to add and remove markers from map on main thread
                        Handler mainHandler = new Handler(Looper.getMainLooper());
                        Runnable myRunnable = new Runnable() {
                            @Override
                            public void run() {
                                // Clear all markers on the map first
                                mMap.clear();
                                addMarkers();
                            }
                        };
                        mainHandler.post(myRunnable);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });

            thread.start();
            timerHandler.postDelayed(this, 60000); // Update every minute
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkLocationPermission();
        }

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        // Google Places
        PlaceAutocompleteFragment autocompleteFragment = (PlaceAutocompleteFragment)
                getFragmentManager().findFragmentById(R.id.place_autocomplete_fragment);

        autocompleteFragment.setOnPlaceSelectedListener(new PlaceSelectionListener() {
            @Override
            public void onPlaceSelected(Place place) {
                currLoc = place.getLatLng();
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(place.getLatLng(), 17));
            }

            @Override
            public void onError(Status status) {
                System.out.println("GOOGLE PLACES ERROR");
            }
        });

        // Set to notification manager initialized in NotificationInitialize
        notificationManager = NotificationManagerCompat.from(this);

        // Read in coordinates from the database
        timerHandler.postDelayed(timerRunnable, 0);
    }

    // Creates a notification and places in the device's System tray
    private void sendNotification() {
        NotificationCompat.Builder notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.notification)
                .setContentTitle("Pin Nearby")
                .setContentText("A pin has been detected nearby you. Open app to see location?")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        notification.setAutoCancel(true);

        Intent intent = new Intent(this, MapsActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                intent, PendingIntent.FLAG_UPDATE_CURRENT);
        notification.setContentIntent(pendingIntent);

        notificationManager.notify(NOTIFICATION_ID, notification.build());
    }

    // Adds all the markers from the database onto the map
    private void addMarkers() {
        for (Pin p : dbCoords) {
            if (currLoc != null) {
                // Only show Pins within a certain radius of user.
                if (PIN_VIEW_RAD_METERS >= getDistance(currLoc.latitude, currLoc.longitude, p.coords.latitude, p.coords.longitude)) {
                    MarkerOptions mo = new MarkerOptions();
                    mo.position(p.coords);

                    switch (p.need) {
                        case "Food":
                            mo.icon(BitmapDescriptorFactory.fromResource(R.drawable.foodpin));
                            break;
                        case "Money":
                            mo.icon(BitmapDescriptorFactory.fromResource(R.drawable.moneypin));
                            break;
                        case "FirstAid":
                            mo.icon(BitmapDescriptorFactory.fromResource(R.drawable.firstaidpin));
                            break;
                        case "Ride":
                            mo.icon(BitmapDescriptorFactory.fromResource(R.drawable.ridepin));
                            break;
                    }
                    mMap.addMarker(mo);
                    pinAdded = true;
                }
            }
        }

        // Send a notification to the tray that a marker appeared nearby
        if (pinAdded && canNotify) {
            sendNotification();
            pinAdded = false;
            canNotify = false;

            // Cooldown before allowing another notification
            new CountDownTimer(1000 * NOTIFY_TIMER_SEC, 1000) {

                public void onTick(long millisUntilFinished) {}

                public void onFinish() {
                    canNotify = true;
                }
            }.start();
        }
    }

    // Highlight valid areas to place pins
    private void highlightAreas() {
        // Temporary values w/o database
        // Downtown SLO
        LatLng l1 = new LatLng(35.275774, -120.667078);
        LatLng l2 = new LatLng(35.281857, -120.664997);
        LatLng l3 = new LatLng(35.279882, -120.658361);

        // Google HQ
        LatLng l4 = new LatLng(37.423270, -122.084100);
        LatLng l5 = new LatLng(37.419606, -122.084347);
        LatLng l6 = new LatLng(37.422017, -122.087298);

        // Cal Poly
        LatLng l7 = new LatLng(35.303562, -120.667387);
        LatLng l8 = new LatLng(35.296452, -120.664426);
        LatLng l9 = new LatLng(35.299534, -120.655928);
        LatLng l10 = new LatLng(35.304122, -120.658932);

        // Foothill
        LatLng l11 = new LatLng(35.298238, -120.680862);
        LatLng l12 = new LatLng(35.290848, -120.678416);
        LatLng l13 = new LatLng(35.290462, -120.667645);
        LatLng l14 = new LatLng(35.295786, -120.668889);

        PolygonOptions highlight = new PolygonOptions();
        highlight.add(l1, l2, l3);
        highlight.strokeWidth(10);
        highlight.fillColor(Color.argb(35, 0, 0, 255));

        Polygon polygon = mMap.addPolygon(highlight);
        validAreas.add(polygon);

        PolygonOptions highlight2 = new PolygonOptions();
        highlight2.add(l4, l5, l6);
        highlight2.strokeWidth(10);
        highlight2.fillColor(Color.argb(35, 0, 0, 255));

        polygon = mMap.addPolygon(highlight2);
        validAreas.add(polygon);

        PolygonOptions highlight3 = new PolygonOptions();
        highlight3.add(l7, l8, l9, l10);
        highlight3.strokeWidth(10);
        highlight3.fillColor(Color.argb(35, 0, 0, 255));

        polygon = mMap.addPolygon(highlight3);
        validAreas.add(polygon);

        PolygonOptions highlight4 = new PolygonOptions();
        highlight4.add(l11, l12, l13, l14);
        highlight4.strokeWidth(10);
        highlight4.fillColor(Color.argb(35, 0, 0, 255));

        polygon = mMap.addPolygon(highlight4);
        validAreas.add(polygon);
    }

    private double degreesToRadians(double degrees) {
        return degrees * Math.PI / 180;
    }

    // Gets distance between 2 coords in km
    private double getDistance(double lat1, double lon1, double lat2, double lon2) {
        double earthRadiusKm = 6371;

        double dLat = degreesToRadians(lat2 - lat1);
        double dLon = degreesToRadians(lon2 - lon1);

        lat1 = degreesToRadians(lat1);
        lat2 = degreesToRadians(lat2);

        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                Math.sin(dLon/2) * Math.sin(dLon/2) * Math.cos(lat1) * Math.cos(lat2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        return earthRadiusKm * c;
    }

    // For handling permission request response
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch(requestCode) {
            case REQUEST_LOCATION_CODE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    //Permission is granted
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        if (client == null) {
                            buildGoogleApiClient();
                        }

                        mMap.setMyLocationEnabled(true);
                    }
                    //Permission denied
                    else {
                        Toast.makeText(this, "Permission Denied!", Toast.LENGTH_LONG).show();
                    }
                }
        }
    }

    public Boolean checkValidPin(AlertDialog.Builder builder, AlertDialog alertDialog, final LatLng pin) {
        // Cooldown timer condition
        if (!canPin) {
            builder.setTitle("Recently placed pin");
            builder.setMessage("Must wait " + pinCooldown + " seconds before placing a new pin");
            builder.setNeutralButton("Clear", null);
            alertDialog = builder.create();
            alertDialog.show();
            return false;
        }

        /*
        // Distance from user current position condition
        if (getDistance(currLoc.latitude, currLoc.longitude, pin.latitude, pin.longitude) >= VALID_RADIUS_METERS / 1000) {
            builder.setTitle("Invalid location");
            builder.setMessage("Pins must be placed in a 0.04km radius");
            builder.setNeutralButton("Clear", null);
            alertDialog = builder.create();
            alertDialog.show();
            return false;
        }

        // If the pin is not located in a valid area, do not allow placing of pin
        boolean goodArea = false;

        for (Polygon a : validAreas) {
            if (PolyUtil.containsLocation(pin, a.getPoints(), false)) {
                goodArea = true;
            }
        }


        if (!goodArea) {
            builder.setTitle("Invalid location");
            builder.setMessage("Pins can only be placed within the blue highlighted areas");
            builder.setNeutralButton("Clear", null);
            alertDialog = builder.create();
            alertDialog.show();
            return false;
        }
        */

        return true;
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(final GoogleMap googleMap) {
        mMap = googleMap;
        final String need[] = {"Food"};

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            buildGoogleApiClient();
            mMap.setMyLocationEnabled(true);
        }

        // Adds a marker on tap
        mMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
            @Override
            public void onMapClick(final LatLng pin) {
                AlertDialog.Builder builder = new AlertDialog.Builder(MapsActivity.this);
                AlertDialog alertDialog = builder.create();

                // Do not continue if pin is not valid for placement
                if (!checkValidPin(builder, alertDialog, pin)) { return; }

                final MarkerOptions mo = new MarkerOptions();
                mo.position(pin);
                String needsArr[] = {"\uD83C\uDF57     Food", "\uD83D\uDCB5     Money", "\uD83D\uDE91     First Aid", "\uD83D\uDE95     Ride"};

                builder.setTitle("Pick a Need");
                builder.setItems(needsArr, new DialogInterface.OnClickListener() {
                   public void onClick(DialogInterface dialog,  int which) {
                       switch(which) {
                           case 0:
                               need[0] = "Food";
                               mo.icon(BitmapDescriptorFactory.fromResource(R.drawable.foodpin));
                               break;
                           case 1:
                               need[0] = "Money";
                               mo.icon(BitmapDescriptorFactory.fromResource(R.drawable.moneypin));
                               break;
                           case 2:
                               need[0] = "FirstAid";
                               mo.icon(BitmapDescriptorFactory.fromResource(R.drawable.firstaidpin));
                               break;
                           case 3:
                               need[0] = "Ride";
                               mo.icon(BitmapDescriptorFactory.fromResource(R.drawable.ridepin));
                               break;
                       }

                       mMap.addMarker(mo);
                       mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pin, 17));

                       // Add the lat and lng to database
                       final String entry = "http://129.65.221.101/php/sendPinPinGPSdata.php?gps=" + pin.latitude + " " + pin.longitude + " " + need[0];
                       Thread thread = new Thread(new Runnable() {
                           @Override
                           public void run() {
                               try {
                                   URL send = new URL(entry);
                                   URLConnection connection = send.openConnection();
                                   InputStream in = connection.getInputStream();
                                   in.close();
                               } catch (Exception e) {
                                   e.printStackTrace();
                               }
                           }
                       });

                       canPin = false;

                       new CountDownTimer(1000 * PIN_TIMER_SEC, 1000) {

                           public void onTick(long millisUntilFinished) {
                               pinCooldown = millisUntilFinished / 1000;
                           }

                           public void onFinish() {
                               canPin = true;
                           }
                       }.start();

                       thread.start();
                   }
                });

                alertDialog = builder.create();
                alertDialog.show();
            }
        });

        // Delete a marker on hold.
        mMap.setOnMapLongClickListener(new GoogleMap.OnMapLongClickListener() {
            @Override
            public void onMapLongClick(final LatLng latLng) {

                // Can't get the marker's exact coords, so have to find the one nearest to the tap.
                Pin temp = null;
                double shortestDist = Double.POSITIVE_INFINITY;
                for (Pin pin : dbCoords) {
                    double distance = getDistance(pin.coords.latitude, pin.coords.longitude, latLng.latitude, latLng.longitude);
                    if (distance < shortestDist) {
                        shortestDist = distance;
                        temp = pin;
                    }
                }

                // If the user long clicked too far away, dont do anything.
                if (shortestDist > MIN_CLICK_DIST) {
                    return;
                }

                final Pin closest = temp;
                String pinOptions[] = {"Need Provided - Remove Pin", "Flag Pin"};

                // Calculate how long the pin has been placed in minutes
                long closestDuration = (System.currentTimeMillis() / 1000 - closest.timePlaced) / 60;

                AlertDialog.Builder builder = new AlertDialog.Builder(MapsActivity.this);
                builder.setTitle("Pin placed " + closestDuration + " minutes ago. What would you like to do?");
                builder.setItems(pinOptions, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int which) {
                        boolean removePin = false;

                        switch(which) {
                            case 0:
                                removePin = true;
                                break;
                            case 1:
                                removePin = true;
                                break;
                        }

                        // Remove the flagged marker from the map.
                        if (removePin) {
                            mMap.clear();
                            dbCoords.remove(closest);
                            addMarkers();
                            highlightAreas();
                        }

                        // Remove the flagged marker from the database.
                        final String delete = "http://129.65.221.101/php/deleteFlaggedEntry.php?gps=" + closest.coords.latitude +
                            " " + closest.coords.longitude;
                        Thread thread = new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    URL send = new URL(delete);
                                    URLConnection connection = send.openConnection();
                                    InputStream in = connection.getInputStream();
                                    in.close();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        });

                        thread.start();

                    }
                });
                builder.setNegativeButton("Cancel", null);

                AlertDialog alertDialog = builder.create();
                alertDialog.show();
            }
        });
    }

    protected synchronized void buildGoogleApiClient() {
        client = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();

        client.connect();
    }

    @Override
    public void onLocationChanged(Location location) {
        currLoc = new LatLng(location.getLatitude(), location.getLongitude());

        // Resolves issue with CameraUpdateFactory not being initialized
        try {
            MapsInitializer.initialize(this);
        }
        catch (Exception e) {
            Log.e("Location Error", "GoogleMaps Issue", e);
            return;
        }

        // Move map to current location
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currLoc, 17));

        if (client != null) {
            LocationServices.FusedLocationApi.removeLocationUpdates(client, this);
        }
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        LocationRequest locationRequest = new LocationRequest();

        locationRequest.setInterval(1000); //1000 milliseconds
        locationRequest.setFastestInterval(1000);
        locationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);

        Intent intent = new Intent(this, MyLocationService.class);
        startService(intent);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            LocationServices.FusedLocationApi.requestLocationUpdates(client, locationRequest, this);
        }
    }

    public void checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Check if user has given permission previously and denied request
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION_CODE);
            }
            // Ask user for permission
            else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION_CODE);
            }
        }
    }


    @Override
    public void onConnectionSuspended(int i) {
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }
}
