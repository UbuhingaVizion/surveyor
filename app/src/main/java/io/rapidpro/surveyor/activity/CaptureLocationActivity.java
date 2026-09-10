package io.rapidpro.surveyor.activity;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import java.util.ArrayList;
import java.util.List;

import io.rapidpro.surveyor.Logger;
import io.rapidpro.surveyor.R;
import io.rapidpro.surveyor.ui.IconTextView;

/**
 * Activity for capturing a GPS location
 *
 * Uses Google Play Services (FusedLocationProvider) when available, and falls back to the
 * platform LocationManager for devices without Google Play Services (e.g. low cost field phones).
 */
public class CaptureLocationActivity extends BaseActivity {

    private FusedLocationProviderClient locationApiClient;
    private LocationCallback locationCallback;
    private LocationManager locationManager;
    private LocationListener locationListener;
    private Location lastLocation;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        setContentView(R.layout.activity_capture_location);

        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                R.string.permission_location, new PermissionCallback() {
                    @Override
                    public void onPermissionsResult(boolean allGranted) {
                        if (allGranted) {
                            onPermissionsGranted();
                        } else {
                            finish();
                        }
                    }
                });

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult != null) {
                    onLocationUpdate(locationResult.getLastLocation());
                }
            }
        };
    }

    @Override
    public boolean requireLogin() {
        return false;
    }

    @Override
    public void onPause() {
        super.onPause();

        stopLocationUpdates();
    }

    protected void onPermissionsGranted() {
        if (isGooglePlayServicesAvailable()) {
            startLocationUpdates();
        } else {
            startLocationManagerUpdates();
        }
    }

    /**
     * Gets whether Google Play Services is available on this device
     */
    protected boolean isGooglePlayServicesAvailable() {
        return GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this) == ConnectionResult.SUCCESS;
    }

    /**
     * Fallback for devices without Google Play Services - uses the platform location providers
     */
    @SuppressWarnings("MissingPermission")
    protected void startLocationManagerUpdates() {
        Logger.d("Starting location manager updates...");

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                onLocationUpdate(location);
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {
            }

            @Override
            public void onProviderEnabled(String provider) {
            }

            @Override
            public void onProviderDisabled(String provider) {
            }
        };

        List<String> providers = new ArrayList<>();
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            providers.add(LocationManager.GPS_PROVIDER);
        }
        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            providers.add(LocationManager.NETWORK_PROVIDER);
        }

        if (providers.isEmpty()) {
            Logger.d("No location providers enabled");
            showToast(R.string.location_unavailable);
            finish();
            return;
        }

        try {
            for (String provider : providers) {
                locationManager.requestLocationUpdates(provider, 2000, 0, locationListener, Looper.getMainLooper());

                Location lastKnown = locationManager.getLastKnownLocation(provider);
                if (lastKnown != null) {
                    onLocationUpdate(lastKnown);
                }
            }
        } catch (SecurityException e) {
            Logger.e("Unable to request location updates", e);
            showToast(R.string.error_google_api);
            finish();
        }
    }

    /**
     * Start receiving location updates from Google Play Services
     */
    @SuppressWarnings("ResourceType")
    private void startLocationUpdates() {
        Logger.d("Starting location updates...");

        IconTextView button = (IconTextView) getViewCache().getView(R.id.button_capture);
        button.setText(R.string.icon_gps_not_fixed);

        LocationRequest request = new LocationRequest();
        request.setInterval(2000);
        request.setFastestInterval(500);
        request.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        locationApiClient = LocationServices.getFusedLocationProviderClient(this);

        try {
            locationApiClient.requestLocationUpdates(request, locationCallback, null);
        } catch (SecurityException e) {
            Logger.e("Unable to request fused location updates", e);
            showToast(R.string.error_google_api);
            finish();
        }
    }

    private void onLocationUpdate(Location location) {
        Logger.d("Received location update: " + location.toString());

        lastLocation = location;

        IconTextView button = (IconTextView) getViewCache().getView(R.id.button_capture);
        button.setText(R.string.icon_gps_fixed);

        TextView coordinates = (TextView) getViewCache().getView(R.id.text_coordinates);
        coordinates.setText(getString(R.string.latitude_longitude, location.getLatitude(), location.getLongitude()));

        TextView accuracy = (TextView) getViewCache().getView(R.id.text_accuracy);
        accuracy.setVisibility(View.VISIBLE);
        accuracy.setText(getString(R.string.accuracy_meters, (int) location.getAccuracy()));
    }

    /**
     * User clicked the capture button
     *
     * @param view the button
     */
    public void onActionCapture(View view) {
        if (lastLocation != null) {
            Intent data = new Intent();
            data.putExtra("latitude", lastLocation.getLatitude());
            data.putExtra("longitude", lastLocation.getLongitude());
            setResult(RESULT_OK, data);
        } else {
            setResult(RESULT_CANCELED);
        }
        finish();
    }

    /**
     * Stop receiving location updates
     */
    protected void stopLocationUpdates() {
        if (locationApiClient != null) {
            locationApiClient.removeLocationUpdates(locationCallback);
            locationApiClient = null;
        }
        if (locationManager != null && locationListener != null) {
            locationManager.removeUpdates(locationListener);
        }
    }
}
