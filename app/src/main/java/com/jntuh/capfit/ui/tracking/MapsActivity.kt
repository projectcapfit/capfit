package com.jntuh.capfit.ui.tracking

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.animation.ObjectAnimator
import android.animation.AnimatorListenerAdapter
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.floatingactionbutton.FloatingActionButton
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jntuh.capfit.R
import com.jntuh.capfit.data.TrackPoint
import com.jntuh.capfit.data.TrackingSession
import com.jntuh.capfit.service.TrackingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * MapsActivity
 *
 * All fixes applied:
 *  EC2. Nearby territories + own past territories fetched on map ready (before workout)
 *  EC3. Own past territories shown in distinct blue on map
 *  EC6. Zero-area sessions filtered everywhere
 *  UX1. Camera jumps to user location on map ready
 *  UX2. All territory layers refresh after workout completes
 *  UX3. Own territories stay visible during workout (not cleared on start)
 *  UX4. Broadcast receiver also handles ACTION_OWN_TERRITORIES_UPDATED
 */
class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private val TAG = "asasas"

    // ─── Map ──────────────────────────────────────────────────────────────────
    private lateinit var googleMap: GoogleMap
    private var isFirstLocation = true

    // Map overlay layers — kept separate so each can be updated independently
    private var currentTrailPolyline: Polyline? = null
    private val myTerritoryPolygons = mutableListOf<com.google.android.gms.maps.model.Polygon>()  // EC3: own = blue
    private val nearbyPolygons = mutableListOf<com.google.android.gms.maps.model.Polygon>()       // others = unique colors

    // ─── UI ───────────────────────────────────────────────────────────────────
    private lateinit var btnStart: FloatingActionButton
    private lateinit var btnStop: FloatingActionButton
    private lateinit var tvStatusPill: TextView
    private lateinit var statusDot: View
    private lateinit var statsSheet: View
    private lateinit var tvDistance: TextView
    private lateinit var tvTime: TextView
    private lateinit var tvArea: TextView
    private var sessionStartTime = 0L

    // ─── Service ──────────────────────────────────────────────────────────────
    private var trackingService: TrackingService? = null
    private var isServiceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            trackingService = (binder as TrackingService.LocalBinder).getService()
            isServiceBound = true
            observeServiceStateFlows()

            // If map is already ready, load territories now that service is bound
            if (::googleMap.isInitialized) {
                val loc = googleMap.cameraPosition.target
                if (loc.latitude != 0.0 || loc.longitude != 0.0) {
                    trackingService?.fetchNearbyForLocation(loc.latitude, loc.longitude)
                }
            }

            // Check if a session completed while activity was gone (missed broadcast)
            val service = trackingService
            val missedSession = service?.lastCompletedSessionId?.value
            if (service != null && missedSession != null && !service.isTracking.value) {
                onWorkoutComplete(missedSession)
            }
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            trackingService = null
            isServiceBound = false
        }
    }

    // ─── Broadcast Receiver ───────────────────────────────────────────────────
    private val trackingReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {

                TrackingService.ACTION_LOCATION_UPDATE -> {
                    val lat = intent.getDoubleExtra(TrackingService.EXTRA_LAT, 0.0)
                    val lng = intent.getDoubleExtra(TrackingService.EXTRA_LNG, 0.0)
                    updateCameraOnFirstLocation(lat, lng)
                }

                TrackingService.ACTION_WORKOUT_COMPLETE -> {
                    val sessionId = intent.getStringExtra(TrackingService.EXTRA_SESSION_ID) ?: run {
                        return
                    }
                    onWorkoutComplete(sessionId)
                }

                TrackingService.ACTION_SESSION_TERMINATED -> {
                    val reason = intent.getStringExtra(TrackingService.EXTRA_TERMINATION_REASON)
                    onSessionTerminated(reason)
                }

                // During workout — nearby others updated by service
                TrackingService.ACTION_NEARBY_TERRITORIES_UPDATED -> {
                    trackingService?.nearbyTerritories?.value?.let { drawOthersTerritories(it) }
                }

                // EC3: Own territories updated by service
                TrackingService.ACTION_OWN_TERRITORIES_UPDATED -> {
                    trackingService?.ownTerritories?.value?.let { drawOwnTerritories(it) }
                }
            }
        }
    }

    private val LOCATION_PERMISSION_CODE = 100

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_maps)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupUI()
        requestLocationPermission()
        registerTrackingReceiver()  // register once for full lifetime of activity

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        bindAndStartTrackingService()
    }

    override fun onResume() {
        super.onResume()
        // Receiver registered in onCreate — nothing to do here
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(trackingReceiver)
        } catch (e: IllegalArgumentException) {
        }
        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
        }
    }

    // ─── UI Setup ─────────────────────────────────────────────────────────────

    private fun setupUI() {
        btnStart     = findViewById(R.id.btn_start)
        btnStop      = findViewById(R.id.btn_stop)
        tvStatusPill = findViewById(R.id.tv_status_pill)
        statusDot    = findViewById(R.id.status_dot)
        statsSheet   = findViewById(R.id.stats_sheet)
        tvDistance   = findViewById(R.id.tv_distance)
        tvTime       = findViewById(R.id.tv_time)
        tvArea       = findViewById(R.id.tv_area)

        btnStop.visibility = View.GONE
        statsSheet.visibility = View.GONE

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }
        btnStart.setOnClickListener {
            if (checkLocationPermission()) startWorkout() else requestLocationPermission()
        }
        btnStop.setOnClickListener { stopWorkout() }
    }

    private fun showStatsSheet(distance: Double, area: Double, durationMs: Long) {
        val distStr = if (distance >= 1000) String.format("%.2f km", distance / 1000)
        else String.format("%.0f m", distance)
        val areaStr = String.format("%.0f m²", area)
        val totalSecs = (durationMs / 1000).toInt()
        val hrs  = totalSecs / 3600
        val mins = (totalSecs % 3600) / 60
        val secs = totalSecs % 60
        val timeStr = when {
            hrs  > 0 -> "${hrs}h ${mins}m ${secs}s"
            mins > 0 -> "${mins}m ${secs}s"
            else     -> "${secs}s"
        }

        tvDistance.text = distStr
        tvTime.text     = timeStr
        tvArea.text     = areaStr

        statsSheet.visibility = View.VISIBLE

        statsSheet.post {
            val sheetH = statsSheet.height.toFloat()
            ObjectAnimator.ofFloat(statsSheet, "translationY", sheetH, 0f).apply {
                duration = 500
                interpolator = DecelerateInterpolator(2f)
                start()
            }
            val fabLift = sheetH + 24f
            btnStart.animate().translationY(-fabLift).setDuration(500)
                .setInterpolator(DecelerateInterpolator(2f)).start()
            btnStop.animate().translationY(-fabLift).setDuration(500)
                .setInterpolator(DecelerateInterpolator(2f)).start()
        }
    }

    private fun hideStatsSheet() {
        statsSheet.post {
            val sheetH = statsSheet.height.toFloat()
            ObjectAnimator.ofFloat(statsSheet, "translationY", 0f, sheetH).apply {
                duration = 350
                interpolator = DecelerateInterpolator()
                start()
            }.also {
                it.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        statsSheet.visibility = View.GONE
                    }
                })
            }
            btnStart.animate().translationY(0f).setDuration(350)
                .setInterpolator(DecelerateInterpolator()).start()
            btnStop.animate().translationY(0f).setDuration(350)
                .setInterpolator(DecelerateInterpolator()).start()
        }
    }

    // ─── Broadcast Receiver Registration ─────────────────────────────────────

    private fun registerTrackingReceiver() {
        val filter = IntentFilter().apply {
            addAction(TrackingService.ACTION_LOCATION_UPDATE)
            addAction(TrackingService.ACTION_WORKOUT_COMPLETE)
            addAction(TrackingService.ACTION_SESSION_TERMINATED)
            addAction(TrackingService.ACTION_NEARBY_TERRITORIES_UPDATED)
            addAction(TrackingService.ACTION_OWN_TERRITORIES_UPDATED)
        }
        ContextCompat.registerReceiver(this, trackingReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

    }

    // ─── Map Ready ────────────────────────────────────────────────────────────

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap.mapType = GoogleMap.MAP_TYPE_NORMAL
        googleMap.uiSettings.apply {
            isZoomControlsEnabled   = true
            isMyLocationButtonEnabled = true
            isCompassEnabled        = true
        }

        if (checkLocationPermission()) {
            googleMap.isMyLocationEnabled = true

            // UX1: Jump camera to user's location immediately
            // EC2: Then fetch and display all territories (own + others) before any workout
            jumpToLocationAndLoadTerritories()
        }
    }

    /**
     * UX1 + EC2:
     * 1. Instantly moves camera to last known location (no world view)
     * 2. Fetches nearby others' territories
     * 3. Fetches own past territories
     * All happen before workout starts so user can plan their route.
     */
    private fun jumpToLocationAndLoadTerritories() {
        try {
            LocationServices.getFusedLocationProviderClient(this)
                .lastLocation
                .addOnSuccessListener { location ->
                    if (location == null) {
                        // lastLocation is null when GPS hasn't been used yet since reboot.
                        // Fall back to requesting a fresh current location.
                        requestFreshLocationAndJump()
                        return@addOnSuccessListener
                    }

                    moveCameraAndLoadTerritories(location.latitude, location.longitude)
                }
        } catch (e: SecurityException) {
        }
    }

    /**
     * Called when lastLocation is null (GPS cold start).
     * Requests a single fresh location update and jumps camera there.
     */
    private fun requestFreshLocationAndJump() {
        if (!checkLocationPermission()) return
        try {
            val cts = com.google.android.gms.tasks.CancellationTokenSource()
            LocationServices.getFusedLocationProviderClient(this)
                .getCurrentLocation(
                    com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
                    cts.token
                )
                .addOnSuccessListener { location ->
                    if (location != null) {
                        moveCameraAndLoadTerritories(location.latitude, location.longitude)
                    }
                }
        } catch (e: Exception) {
        }
    }

    private fun moveCameraAndLoadTerritories(lat: Double, lng: Double) {
        if (!::googleMap.isInitialized) return
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 17f))
        isFirstLocation = false
        val service = trackingService
        if (service != null) {
            service.fetchNearbyForLocation(lat, lng)
        } else {
            fetchAllTerritoriesForLocation(lat, lng)
        }
    }

    // ─── Service Binding ──────────────────────────────────────────────────────

    private fun bindAndStartTrackingService() {
        val intent = Intent(this, TrackingService::class.java)
        startService(intent)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    // ─── Observe Service StateFlows ───────────────────────────────────────────

    private fun observeServiceStateFlows() {
        val service = trackingService ?: return

        // Trail polyline during workout
        lifecycleScope.launch {
            service.rawTrackPoints.collectLatest { points ->
                if (points.isNotEmpty()) drawTrailPolyline(points)
            }
        }

        // Other users' territories
        lifecycleScope.launch {
            service.nearbyTerritories.collectLatest { drawOthersTerritories(it) }
        }

        // EC3: Own past territories
        lifecycleScope.launch {
            service.ownTerritories.collectLatest { drawOwnTerritories(it) }
        }

        // Tracking state → button visibility
        lifecycleScope.launch {
            service.isTracking.collectLatest { tracking ->
                runOnUiThread {
                    btnStart.visibility = if (tracking) View.GONE else View.VISIBLE
                    btnStop.visibility  = if (tracking) View.VISIBLE else View.GONE
                }
            }
        }
    }

    // ─── Workout Controls ─────────────────────────────────────────────────────

    private fun startWorkout() {
        trackingService?.startTracking()
        sessionStartTime = System.currentTimeMillis()
        btnStart.visibility = View.GONE
        btnStop.visibility  = View.VISIBLE
        statusDot.setBackgroundResource(R.drawable.circle_red)
        tvStatusPill.text = "Recording"
        hideStatsSheet()
        currentTrailPolyline?.remove()
        currentTrailPolyline = null
    }

    private fun stopWorkout() {
        trackingService?.stopTracking()
        btnStop.isEnabled = false
        tvStatusPill.text = "Processing..."
    }

    // ─── Camera: First Location During Workout ────────────────────────────────

    /**
     * Only moves camera on the very first GPS update during an active workout,
     * if jumpToLocationAndLoadTerritories() didn't already move it.
     */
    private fun updateCameraOnFirstLocation(lat: Double, lng: Double) {
        if (!::googleMap.isInitialized) return
        if (!isFirstLocation) return

        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 17f))
        isFirstLocation = false
    }

    // ─── Draw Trail (during workout) ──────────────────────────────────────────

    private fun drawTrailPolyline(points: List<TrackPoint>) {
        if (!::googleMap.isInitialized) return
        runOnUiThread {
            currentTrailPolyline?.remove()
            val latLngs = points.map { LatLng(it.lat, it.lng) }
            currentTrailPolyline = googleMap.addPolyline(
                PolylineOptions()
                    .addAll(latLngs)
                    .color(Color.parseColor("#2196F3"))
                    .width(8f)
                    .geodesic(true)
            )
        }
    }

    // ─── EC3: Draw Own Past Territories (blue, same as active workout color) ──

    /**
     * EC3: Draws the current user's own past territories in blue.
     * Distinct from other users (who get unique hash-derived colors).
     * Shown both before workout starts and after workout completes.
     *
     * O7: Data preparation on background thread, only addPolygon on main thread.
     */
    private fun drawOwnTerritories(sessions: List<TrackingSession>) {
        if (!::googleMap.isInitialized) return

        lifecycleScope.launch {
            val renderData = withContext(Dispatchers.Default) {
                sessions
                    .filter { it.area > 0.0 && it.points.isNotEmpty() }
                    .flatMap { session ->
                        splitPolygonPoints(session.points).filter { it.outerRing.size >= 3 }
                    }
            }

            myTerritoryPolygons.forEach { it.remove() }
            myTerritoryPolygons.clear()

            for (data in renderData) {
                val opts = PolygonOptions()
                    .addAll(data.outerRing)
                    .fillColor(Color.argb(80, 33, 150, 243))
                    .strokeColor(Color.parseColor("#2196F3"))
                    .strokeWidth(3f)
                data.holes.forEach { hole -> opts.addHole(hole) }  // holes = cutouts
                myTerritoryPolygons.add(googleMap.addPolygon(opts))
            }
        }
    }

    // ─── Draw Others' Territories (unique color per user) ────────────────────

    /**
     * Draws other users' nearby territories, each in a unique color derived from userId.
     * O7: Data preparation on background thread.
     */
    private fun drawOthersTerritories(territories: List<TrackingSession>) {
        if (!::googleMap.isInitialized) return

        lifecycleScope.launch {
            val renderData = withContext(Dispatchers.Default) {
                territories
                    .filter { it.area > 0.0 && it.points.isNotEmpty() }
                    .flatMap { session ->
                        val color = generateUserColor(session.userId)
                        val fill  = Color.argb(60, Color.red(color), Color.green(color), Color.blue(color))
                        splitPolygonPoints(session.points)
                            .filter { it.outerRing.size >= 3 }
                            .map { pd -> Triple(pd, fill, color) }
                    }
            }

            nearbyPolygons.forEach { it.remove() }
            nearbyPolygons.clear()

            for ((pd, fill, stroke) in renderData) {
                val opts = PolygonOptions()
                    .addAll(pd.outerRing)
                    .fillColor(fill)
                    .strokeColor(stroke)
                    .strokeWidth(2f)
                pd.holes.forEach { hole -> opts.addHole(hole) }  // holes = cutouts
                nearbyPolygons.add(googleMap.addPolygon(opts))
            }
        }
    }

    // ─── Workout Complete ─────────────────────────────────────────────────────

    /**
     * UX2: After workout completes:
     * 1. Draws the new session territory
     * 2. Refreshes own territory layer (includes merged territories)
     * 3. Refreshes others' territory layer (some may have been trimmed)
     * 4. Zooms camera to fit new territory
     */
    private fun onWorkoutComplete(sessionId: String) {
        FirebaseFirestore.getInstance()
            .collection(TrackingService.COL_SESSIONS)
            .document(sessionId)
            .get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    resetUIAfterWorkout(0.0, 0.0)
                    return@addOnSuccessListener
                }

                val area      = doc.getDouble("area")      ?: 0.0
                val distance  = doc.getDouble("distance")  ?: 0.0
                val xMin      = doc.getDouble("xMin")      ?: 0.0
                val xMax      = doc.getDouble("xMax")      ?: 0.0
                val yMin      = doc.getDouble("yMin")      ?: 0.0
                val yMax      = doc.getDouble("yMax")      ?: 0.0
                val startTime = doc.getLong("startTime")   ?: 0L
                val endTime   = doc.getLong("endTime")     ?: 0L

                // Use endTime - startTime from Firestore for accurate duration.
                // Avoids drift from System.currentTimeMillis() - sessionStartTime
                // which breaks when session is merged (survivor has older startTime)
                // or when app restarts and sessionStartTime resets to 0.
                val durationMs = if (startTime > 0L && endTime > startTime)
                    endTime - startTime
                else
                    System.currentTimeMillis() - sessionStartTime

                @Suppress("UNCHECKED_CAST")
                val rawPoints = doc.get("points") as? List<Map<String, Any>> ?: emptyList()
                val points = rawPoints.mapNotNull { map ->
                    val lat  = (map["lat"]  as? Number)?.toDouble() ?: return@mapNotNull null
                    val lng  = (map["lng"]  as? Number)?.toDouble() ?: return@mapNotNull null
                    val time = (map["time"] as? Number)?.toLong()   ?: 0L
                    TrackPoint(lat, lng, time)
                }

                runOnUiThread {
                    resetUIAfterWorkout(distance, area, durationMs)


                    val centerLat = if (yMin != 0.0 || yMax != 0.0) (yMin + yMax) / 2
                    else googleMap.cameraPosition.target.latitude
                    val centerLng = if (xMin != 0.0 || xMax != 0.0) (xMin + xMax) / 2
                    else googleMap.cameraPosition.target.longitude

                    val fakeSession = TrackingSession(
                        sessionId = sessionId, area = area, distance = distance,
                        points = points, xMin = xMin, xMax = xMax, yMin = yMin, yMax = yMax
                    )
                    refreshAllTerritories(centerLat, centerLng, fakeSession)
                }
            }
            .addOnFailureListener { e ->
                runOnUiThread { resetUIAfterWorkout(0.0, 0.0) }
            }
    }

    private fun resetUIAfterWorkout(distance: Double, area: Double, durationMs: Long = 0L) {
        btnStart.isEnabled  = true
        btnStart.visibility = View.VISIBLE
        btnStop.visibility  = View.GONE
        btnStop.isEnabled   = true
        statusDot.setBackgroundResource(R.drawable.circle_green)
        tvStatusPill.text = "Saved ✓"
        currentTrailPolyline?.remove()
        currentTrailPolyline = null
        showStatsSheet(distance, area, durationMs)
    }

    /**
     * UX2: Full territory refresh after workout completes.
     * Fetches fresh data from Firestore for all layers.
     * Zooms camera to fit new territory if it has area.
     */
    private fun refreshAllTerritories(centerLat: Double, centerLng: Double, newSession: TrackingSession) {
        // Option A: Clear all stale own territory polygons from the map FIRST
        // so ghost polygons (absorbed/merged sessions) never appear
        myTerritoryPolygons.forEach { it.remove() }
        myTerritoryPolygons.clear()

        // Immediately draw ONLY the new/merged session — not the stale cache
        // This gives instant visual feedback without showing ghost old territories
        if (newSession.area > 0 && newSession.points.isNotEmpty()) {
            val currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            val sessionWithUser = newSession.copy(userId = currentUserId ?: newSession.userId)
            drawOwnTerritories(listOf(sessionWithUser))

            // Zoom to fit the new territory
            val validPoints = newSession.points.filter { it.time >= 0L }
            val bounds = buildLatLngBounds(validPoints)
            if (bounds != null) {
                googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 120))
            }
        }

        // Then re-fetch from Firestore to get the true final state
        // (all own territories correctly merged, others correctly subtracted)
        val service = trackingService
        if (service != null) {
            service.fetchNearbyForLocation(centerLat, centerLng)
        } else {
            fetchAllTerritoriesForLocation(centerLat, centerLng)
        }
    }

    // ─── EC2: Direct Firestore fetch (when service not yet bound) ─────────────

    /**
     * EC2: Called when map is ready but service isn't bound yet,
     * or after workout when service has stopped.
     * Directly queries Firestore for both own and others' territories.
     */
    private fun fetchAllTerritoriesForLocation(lat: Double, lng: Double) {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val pad = 0.01
        val db = FirebaseFirestore.getInstance()

        // Use geohash for efficient query
        val queryPrefix = com.jntuh.capfit.engine.GeoHashUtil.encode(lat, lng,
            com.jntuh.capfit.engine.GeoHashUtil.QUERY_PRECISION)
        val (rangeStart, rangeEnd) = com.jntuh.capfit.engine.GeoHashUtil.queryRange(queryPrefix)

        db.collection(TrackingService.COL_SESSIONS)
            .whereEqualTo("isLive", false)
            .whereGreaterThanOrEqualTo("geohash", rangeStart)
            .whereLessThan("geohash", rangeEnd)
            .get()
            .addOnSuccessListener { snapshot ->
                val all = snapshot.documents.mapNotNull { doc ->
                    sessionFromDoc(doc)
                }.filter { s ->
                    s.area > 0.0 &&
                            s.xMax > lng - pad && s.xMin < lng + pad &&
                            s.yMax > lat - pad && s.yMin < lat + pad
                }

                val own    = all.filter { it.userId == currentUserId }
                val others = all.filter { it.userId != currentUserId }

                drawOwnTerritories(own)
                drawOthersTerritories(others)
            }
    }

    // ─── Session Terminated ───────────────────────────────────────────────────

    private fun onSessionTerminated(reason: String?) {
        runOnUiThread {
            btnStart.visibility = View.VISIBLE
            btnStop.visibility  = View.GONE
            btnStop.isEnabled   = true
            statusDot.setBackgroundResource(R.drawable.circle_green)
            tvStatusPill.text   = "GPS Active"
            currentTrailPolyline?.remove()
            currentTrailPolyline = null
            when (reason) {
                "malpractice" -> {
                    Toast.makeText(this, "🚨 Speed limit exceeded! Session deleted.", Toast.LENGTH_SHORT).show()
                }
                "too_short" -> {
                    Toast.makeText(this, "Not enough points recorded. Walk longer next time!", Toast.LENGTH_SHORT).show()
                }
                else -> Toast.makeText(this, "Session ended.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    data class PolygonData(
        val outerRing: List<LatLng>,
        val holes: List<List<LatLng>> = emptyList()
    )

    private fun splitPolygonPoints(points: List<TrackPoint>): List<PolygonData> {
        val polygons = mutableListOf<PolygonData>()
        var outerRing = mutableListOf<LatLng>()
        val holes = mutableListOf<List<LatLng>>()
        var currentHole = mutableListOf<LatLng>()
        var inHole = false

        fun finishCurrentPolygon() {
            if (inHole && currentHole.isNotEmpty()) holes.add(currentHole.toList())
            if (outerRing.isNotEmpty()) polygons.add(PolygonData(outerRing.toList(), holes.toList()))
            outerRing = mutableListOf(); holes.clear(); currentHole = mutableListOf(); inHole = false
        }

        for (point in points) {
            when (point.time) {
                -1L -> finishCurrentPolygon()           // polygon separator
                -2L -> {                                 // hole marker
                    if (inHole && currentHole.isNotEmpty()) holes.add(currentHole.toList())
                    currentHole = mutableListOf(); inHole = true
                }
                else -> {
                    val ll = LatLng(point.lat, point.lng)
                    if (inHole) currentHole.add(ll) else outerRing.add(ll)
                }
            }
        }
        finishCurrentPolygon()
        return polygons
    }

    private fun sessionFromDoc(doc: com.google.firebase.firestore.DocumentSnapshot): TrackingSession? {
        return try {
            val sessionId = doc.getString("sessionId") ?: return null
            val userId    = doc.getString("userId")    ?: return null
            val area      = doc.getDouble("area")      ?: 0.0
            val distance  = doc.getDouble("distance")  ?: 0.0
            val geohash   = doc.getString("geohash")   ?: ""
            val xMin      = doc.getDouble("xMin")      ?: 0.0
            val xMax      = doc.getDouble("xMax")      ?: 0.0
            val yMin      = doc.getDouble("yMin")      ?: 0.0
            val yMax      = doc.getDouble("yMax")      ?: 0.0
            val startTime = doc.getLong("startTime")   ?: 0L
            val endTime   = doc.getLong("endTime")     ?: 0L

            @Suppress("UNCHECKED_CAST")
            val rawPoints = doc.get("points") as? List<Map<String, Any>> ?: emptyList()
            val points = rawPoints.mapNotNull { map ->
                val lat  = (map["lat"]  as? Number)?.toDouble() ?: return@mapNotNull null
                val lng  = (map["lng"]  as? Number)?.toDouble() ?: return@mapNotNull null
                val time = (map["time"] as? Number)?.toLong()   ?: 0L
                TrackPoint(lat, lng, time)
            }

            TrackingSession(
                sessionId = sessionId, userId = userId, area = area,
                distance = distance, geohash = geohash, points = points,
                startTime = startTime, endTime = endTime,
                xMin = xMin, xMax = xMax, yMin = yMin, yMax = yMax,
                isLive = false
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun generateUserColor(userId: String): Int {
        val hue = ((userId.hashCode() and 0xFFFFFF) % 360).toFloat()
        // Avoid blue (200-240°) — reserved for own territories
        val adjustedHue = if (hue in 200f..240f) hue + 60f else hue
        return Color.HSVToColor(floatArrayOf(adjustedHue % 360f, 0.75f, 0.9f))
    }

    private fun buildLatLngBounds(points: List<TrackPoint>): LatLngBounds? {
        if (points.isEmpty()) return null
        return try {
            val builder = LatLngBounds.Builder()
            points.forEach { builder.include(LatLng(it.lat, it.lng)) }
            builder.build()
        } catch (e: Exception) { null }
    }

    // ─── Permissions ──────────────────────────────────────────────────────────

    private fun checkLocationPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    private fun requestLocationPermission() {
        if (!checkLocationPermission()) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                LOCATION_PERMISSION_CODE
            )
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (::googleMap.isInitialized) {
                    googleMap.isMyLocationEnabled = true
                    jumpToLocationAndLoadTerritories()
                }
            } else {
                Toast.makeText(this, "Location permission required for tracking", Toast.LENGTH_SHORT).show()
            }
        }
    }
}