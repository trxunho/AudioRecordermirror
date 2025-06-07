package com.dimowner.audiorecorder.app.map;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.dimowner.audiorecorder.ARApplication;
import com.dimowner.audiorecorder.R;
import com.dimowner.audiorecorder.app.PlaybackService;
import com.dimowner.audiorecorder.data.database.LocalRepository;
import com.dimowner.audiorecorder.data.database.Record;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.ArrayList;
import java.util.List;
import timber.log.Timber;
import android.widget.Toast;

public class MapActivity extends AppCompatActivity implements OnMapReadyCallback, GoogleMap.OnMarkerClickListener {

    private GoogleMap googleMap;
    private LocalRepository localRepository;
    private ArrayList<Integer> specificRecordIdsToShow = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);
        localRepository = ARApplication.getInjector().provideLocalRepository(getApplicationContext());

        if (getIntent().hasExtra("RECORD_IDS")) {
            specificRecordIdsToShow = getIntent().getIntegerArrayListExtra("RECORD_IDS");
            Timber.d("MapActivity started with specific record IDs: %s", specificRecordIdsToShow.toString());
        }

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map_fragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        } else {
            Timber.e("MapFragment is null");
            // Handle error: show a message to the user or finish the activity
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        this.googleMap = googleMap;
        Timber.d("Map is ready.");
        this.googleMap.setOnMarkerClickListener(this);
        loadAndDisplayRecordings();
    }

    @Override
    public boolean onMarkerClick(Marker marker) {
        Integer recordId = (Integer) marker.getTag();
        if (recordId != null) {
            Timber.d("Marker clicked, record ID: " + recordId);
            // Start PlaybackService to play the record
            Intent intent = new Intent(MapActivity.this, PlaybackService.class);
            intent.setAction(PlaybackService.ACTION_PLAY);
            Record record = localRepository.getRecord(recordId);
            if (record != null) {
                intent.putExtra(PlaybackService.EXTRAS_KEY_RECORD_PATH, record.getPath());
                startService(intent);
            } else {
                Timber.e("Record not found for ID: " + recordId);
                // Optionally show a toast message
            }
        }
        return false; // Return false to allow default behavior (center camera on marker, show info window)
    }

    private void loadAndDisplayRecordings() {
        if (googleMap == null || localRepository == null) {
            Timber.e("Map or LocalRepository not initialized.");
            return;
        }

        List<Record> recordsToDisplay = new ArrayList<>();
        if (specificRecordIdsToShow != null && !specificRecordIdsToShow.isEmpty()) {
            for (Integer id : specificRecordIdsToShow) {
                Record record = localRepository.getRecord(id); // Assumes getRecord opens/closes repo or handles it.
                if (record != null) {
                    recordsToDisplay.add(record);
                } else {
                    Timber.w("Record with ID %d not found.", id);
                }
            }
            if (recordsToDisplay.isEmpty()) {
                 Timber.d("No specified records found or none had valid locations.");
                 // Show a toast or message? For now, map will be empty or default view.
                 Toast.makeText(this, getResources().getString(R.string.selected_records_not_found_or_no_location), Toast.LENGTH_LONG).show();
            }
        } else {
            // Load all records if no specific IDs are provided
            recordsToDisplay = localRepository.getAllRecords(); // Assumes getAllRecords opens/closes repo.
        }

        if (recordsToDisplay == null || recordsToDisplay.isEmpty()) {
            Timber.d("No records to display on map.");
            if (specificRecordIdsToShow != null && !specificRecordIdsToShow.isEmpty()) {
                // If specific IDs were requested but none were valid/found
                Toast.makeText(this, getResources().getString(R.string.selected_records_not_found_or_no_location), Toast.LENGTH_LONG).show();
            }
            LatLng defaultLocation = new LatLng(0, 0);
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 2));
            return;
        }

        LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
        boolean hasValidLocations = false;

        for (Record record : recordsToDisplay) {
            if (record.getLatitude() != 0.0 || record.getLongitude() != 0.0) { // Basic check for valid location
                LatLng location = new LatLng(record.getLatitude(), record.getLongitude());
                Marker marker = googleMap.addMarker(new MarkerOptions()
                        .position(location)
                        .title(record.getName()));
                if (marker != null) {
                    marker.setTag(record.getId()); // Store record ID
                }
                boundsBuilder.include(location);
                hasValidLocations = true;
            }
        }

        if (hasValidLocations) {
            LatLngBounds bounds = boundsBuilder.build();
            int padding = 100;
            googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding));
        } else {
            Timber.d("No records with valid locations found among the (potentially filtered) list.");
             if (specificRecordIdsToShow != null && !specificRecordIdsToShow.isEmpty()) {
                Toast.makeText(this, getResources().getString(R.string.selected_records_not_found_or_no_location), Toast.LENGTH_LONG).show();
            } else {
                // This case (all records have no location) might be rare if some recordings have locations
                Toast.makeText(this, getResources().getString(R.string.no_recordings_have_location), Toast.LENGTH_LONG).show();
            }
            LatLng defaultLocation = new LatLng(0, 0);
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 2));
        }
    }
}
