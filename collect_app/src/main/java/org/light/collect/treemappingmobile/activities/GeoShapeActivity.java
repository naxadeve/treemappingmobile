/*
 * Copyright (C) 2016 Nafundi
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.light.collect.treemappingmobile.activities;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.VisibleForTesting;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageButton;

import org.light.collect.treemappingmobile.R;
import org.light.collect.treemappingmobile.map.GoogleMapFragment;
import org.light.collect.treemappingmobile.map.MapFragment;
import org.light.collect.treemappingmobile.map.MapPoint;
import org.light.collect.treemappingmobile.map.OsmMapFragment;
import org.light.collect.treemappingmobile.preferences.GeneralKeys;
import org.light.collect.treemappingmobile.spatial.MapHelper;
import org.light.collect.treemappingmobile.utilities.ToastUtils;
import org.light.collect.treemappingmobile.widgets.GeoShapeWidget;
import org.osmdroid.tileprovider.IRegisterReceiver;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.light.collect.treemappingmobile.utilities.PermissionUtils.areLocationPermissionsGranted;

/** Activity for entering or editing a polygon on a map. */
public class GeoShapeActivity extends BaseGeoMapActivity implements IRegisterReceiver {
    public static final String PREF_VALUE_GOOGLE_MAPS = "google_maps";
    public static final String MAP_CENTER_KEY = "map_center";
    public static final String MAP_ZOOM_KEY = "map_zoom";
    public static final String POINTS_KEY = "points";

    private MapFragment map;
    private int featureId = -1;  // will be a positive featureId once map is ready
    private ImageButton zoomButton;
    private ImageButton clearButton;
    private AlertDialog zoomDialog;
    private View zoomDialogView;
    private Button zoomPointButton;
    private Button zoomLocationButton;
    private String originalShapeString = "";

    // restored from savedInstanceState
    private MapPoint restoredMapCenter;
    private Double restoredMapZoom;
    private List<MapPoint> restoredPoints;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            restoredMapCenter = savedInstanceState.getParcelable(MAP_CENTER_KEY);
            restoredMapZoom = savedInstanceState.getDouble(MAP_ZOOM_KEY);
            restoredPoints = savedInstanceState.getParcelableArrayList(POINTS_KEY);
        }

        if (!areLocationPermissionsGranted(this)) {
            finish();
            return;
        }

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setTitle(getString(R.string.geoshape_title));
        setContentView(R.layout.geoshape_layout);
        createMapFragment().addTo(this, R.id.map_container, this::initMap);
    }

    public MapFragment createMapFragment() {
        String mapSdk = getIntent().getStringExtra(GeneralKeys.KEY_MAP_SDK);
        return (mapSdk == null || mapSdk.equals(PREF_VALUE_GOOGLE_MAPS)) ?
            new GoogleMapFragment() : new OsmMapFragment();
    }

    @Override protected void onStart() {
        super.onStart();
        if (map != null) {
            map.setGpsLocationEnabled(true);
        }
    }

    @Override protected void onStop() {
        map.setGpsLocationEnabled(false);
        super.onStop();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putParcelable(MAP_CENTER_KEY, map.getCenter());
        state.putDouble(MAP_ZOOM_KEY, map.getZoom());
        state.putParcelableArrayList(POINTS_KEY, new ArrayList<>(map.getPointsOfPoly(featureId)));
    }

    @Override public void onBackPressed() {
        if (!formatPoints(map.getPointsOfPoly(featureId)).equals(originalShapeString)) {
            showBackDialog();
        } else {
            finish();
        }
    }

    @Override public void destroy() { }

    private void initMap(MapFragment newMapFragment) {
        if (newMapFragment == null) {
            finish();
            return;
        }

        map = newMapFragment;
        map.setLongPressListener(this::addVertex);

        if (map instanceof GoogleMapFragment) {
            helper = new MapHelper(this, ((GoogleMapFragment) map).getGoogleMap(), selectedLayer);
        } else if (map instanceof OsmMapFragment) {
            helper = new MapHelper(this, ((OsmMapFragment) map).getMapView(), this, selectedLayer);
        }
        helper.setBasemap();

        zoomButton = findViewById(R.id.gps);
        zoomButton.setOnClickListener(v -> showZoomDialog());

        clearButton = findViewById(R.id.clear);
        clearButton.setOnClickListener(v -> showClearDialog());

        ImageButton saveButton = findViewById(R.id.save);
        saveButton.setOnClickListener(v -> finishWithResult());

        ImageButton layersButton = findViewById(R.id.layers);
        layersButton.setOnClickListener(v -> helper.showLayersDialog());

        List<MapPoint> points = new ArrayList<>();
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra(GeoShapeWidget.SHAPE_LOCATION)) {
            originalShapeString = intent.getStringExtra(GeoShapeWidget.SHAPE_LOCATION);
            points = parsePoints(originalShapeString);
        }
        if (restoredPoints != null) {
            points = restoredPoints;
        }
        featureId = map.addDraggablePoly(points, true);
        zoomButton.setEnabled(!points.isEmpty());
        clearButton.setEnabled(!points.isEmpty());

        zoomDialogView = getLayoutInflater().inflate(R.layout.geo_zoom_dialog, null);

        zoomLocationButton = zoomDialogView.findViewById(R.id.zoom_location);
        zoomLocationButton.setOnClickListener(v -> {
            map.zoomToPoint(map.getGpsLocation());
            zoomDialog.dismiss();
        });

        zoomPointButton = zoomDialogView.findViewById(R.id.zoom_saved_location);
        zoomPointButton.setOnClickListener(v -> {
            map.zoomToBoundingBox(map.getPointsOfPoly(featureId), 0.6);
            zoomDialog.dismiss();
        });

        map.setGpsLocationEnabled(true);
        if (restoredMapCenter != null && restoredMapZoom != null) {
            map.zoomToPoint(restoredMapCenter, restoredMapZoom);
        } else if (!points.isEmpty()) {
            map.zoomToBoundingBox(points, 0.6);
        } else {
            map.runOnGpsLocationReady(this::onGpsLocationReady);
        }
    }

    @SuppressWarnings("unused")  // the "map" parameter is intentionally unused
    private void onGpsLocationReady(MapFragment map) {
        zoomButton.setEnabled(true);
        if (getWindow().isActive()) {
            showZoomDialog();
        }
    }

    private void addVertex(MapPoint point) {
        map.appendPointToPoly(featureId, point);
        clearButton.setEnabled(true);
        zoomButton.setEnabled(true);
    }

    private void clear() {
        map.clearFeatures();
        featureId = map.addDraggablePoly(new ArrayList<>(), true);
        clearButton.setEnabled(false);
    }

    private void showClearDialog() {
        if (!map.getPointsOfPoly(featureId).isEmpty()) {
            new AlertDialog.Builder(this)
                .setMessage(R.string.geo_clear_warning)
                .setPositiveButton(R.string.clear, (dialog, id) -> clear())
                .setNegativeButton(R.string.cancel, null)
                .show();
        }
    }

    private void showBackDialog() {
        new AlertDialog.Builder(this)
            .setMessage(getString(R.string.geo_exit_warning))
            .setPositiveButton(R.string.discard, (dialog, id) -> finish())
            .setNegativeButton(R.string.cancel, null)
            .show();

    }

    private void finishWithResult() {
        List<MapPoint> points = map.getPointsOfPoly(featureId);

        // Allow an empty result (no points), or a polygon with at least
        // 3 points, but no degenerate 1-point or 2-point polygons.
        if (!points.isEmpty() && points.size() < 3) {
            ToastUtils.showShortToastInMiddle(getString(R.string.polygon_validator));
            return;
        }

        setResult(RESULT_OK, new Intent().putExtra(
            FormEntryActivity.GEOSHAPE_RESULTS, formatPoints(points)));
        finish();
    }

    /**
     * Parses a form result string, as previously formatted by formatPoints,
     * into a list of polygon vertices.
     */
    private List<MapPoint> parsePoints(String coords) {
        List<MapPoint> points = new ArrayList<>();
        for (String vertex : (coords == null ? "" : coords).split(";")) {
            String[] words = vertex.trim().split(" ");
            if (words.length >= 2) {
                double lat;
                double lon;
                try {
                    lat = Double.parseDouble(words[0]);
                    lon = Double.parseDouble(words[1]);
                } catch (NumberFormatException e) {
                    continue;
                }
                points.add(new MapPoint(lat, lon));
            }
        }
        // Polygons are stored with a last point that duplicates the first
        // point.  To prepare the polygon for display and editing, we need
        // to remove this duplicate point.
        int count = points.size();
        if (count > 1 && points.get(0).equals(points.get(count - 1))) {
            points.remove(count - 1);
        }
        return points;
    }

    /**
     * Serializes a list of polygon vertices into a string, in the format
     * appropriate for storing as the result of this form question.
     */
    private String formatPoints(List<MapPoint> points) {
        // Polygons are stored with a last point that duplicates the
        // first point.  Add this extra point if it's not already present.
        if (points.size() > 1 && !points.get(0).equals(points.get(points.size() - 1))) {
            points.add(points.get(0));
        }
        String result = "";
        for (MapPoint point : points) {
            // TODO(ping): Remove excess precision when we're ready for the output to change.
            result += String.format(Locale.US, "%s %s 0.0 0.0;",
                Double.toString(point.lat), Double.toString(point.lon));
        }
        return result.trim();
    }

    private void showZoomDialog() {
        if (zoomDialog == null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(getString(R.string.zoom_to_where));
            builder.setView(zoomDialogView)
                .setNegativeButton(R.string.cancel, (dialog, id) -> dialog.cancel())
                .setOnCancelListener(dialog -> {
                    dialog.cancel();
                    zoomDialog.dismiss();
                });
            zoomDialog = builder.create();
        }

        if (map.getGpsLocation() != null) {
            zoomLocationButton.setEnabled(true);
            zoomLocationButton.setBackgroundColor(Color.parseColor("#50cccccc"));
            zoomLocationButton.setTextColor(themeUtils.getPrimaryTextColor());
        } else {
            zoomLocationButton.setEnabled(false);
            zoomLocationButton.setBackgroundColor(Color.parseColor("#50e2e2e2"));
            zoomLocationButton.setTextColor(Color.parseColor("#FF979797"));
        }

        if (!map.getPointsOfPoly(featureId).isEmpty()) {
            zoomPointButton.setEnabled(true);
            zoomPointButton.setBackgroundColor(Color.parseColor("#50cccccc"));
            zoomPointButton.setTextColor(themeUtils.getPrimaryTextColor());
        } else {
            zoomPointButton.setEnabled(false);
            zoomPointButton.setBackgroundColor(Color.parseColor("#50e2e2e2"));
            zoomPointButton.setTextColor(Color.parseColor("#FF979797"));
        }
        zoomDialog.show();
    }

    @VisibleForTesting public boolean isGpsButtonEnabled() {
        return zoomButton != null && zoomButton.isEnabled();
    }

    @VisibleForTesting public boolean isZoomDialogShowing() {
        return zoomDialog != null && zoomDialog.isShowing();
    }

    @VisibleForTesting public MapFragment getMapFragment() {
        return map;
    }
}
