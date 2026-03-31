package com.jntuh.capfit.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.jntuh.capfit.R
import com.jntuh.capfit.data.TrackPoint
import com.jntuh.capfit.data.TrackingSession
import com.jntuh.capfit.engine.AreaCalculator
import com.jntuh.capfit.engine.GeoHashUtil
import com.jntuh.capfit.repository.SeasonDataManager
import com.jntuh.capfit.repository.AchievementManager
import com.jntuh.capfit.repository.UserGameDataManager
import com.jntuh.capfit.ui.tracking.MapsActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import org.locationtech.jts.geom.*
import javax.inject.Inject

/**
 * TrackingService
 *
 * Edge cases handled:
 *  EC1. Same user overlapping own territory → merge (union) own sessions, no double counting
 *  EC2. Nearby territories fetched on app open (via fetchNearbyForLocation public function)
 *  EC3. Own past territories shown on map (separate query, drawn in distinct blue)
 *  EC4. capturedArea correctly updated for own territory merges
 *  EC5. Stale isLive sessions cleaned up on service start
 *  EC6. Zero-area sessions filtered out everywhere
 */
@AndroidEntryPoint
class TrackingService : Service() {

    inner class LocalBinder : Binder() {
        fun getService(): TrackingService = this@TrackingService
    }
    private val binder = LocalBinder()
    override fun onBind(intent: Intent?): IBinder = binder

    @Inject lateinit var userGameDataManager: UserGameDataManager
    @Inject lateinit var achievementManager: AchievementManager
    @Inject lateinit var seasonDataManager: SeasonDataManager

    companion object {
        private const val TAG = "asasas"
        const val CHANNEL_ID = "tracking_channel"
        const val NOTIFICATION_ID = 1

        private const val GPS_INTERVAL_MS = 5_000L
        private const val GPS_FASTEST_INTERVAL_MS = 3_000L
        private const val MIN_POINT_DISTANCE_M = 5.0
        private const val FIREBASE_SYNC_INTERVAL_MS = 60_000L
        private const val MAL_SPEED_KMH = 25.0
        private const val MAL_DIST_5_POINTS_M = 120.0
        private const val GPS_ACCURACY_THRESHOLD_M = 20f
        private const val NEARBY_RADIUS_DEG = 0.01

        // Throttle nearby territory fetches — no need to query every GPS point
        private const val NEARBY_FETCH_INTERVAL_MS = 60_000L  // every 60s during workout

        /**
         * Minimum territory area in m² to be worth keeping.
         * Pieces smaller than this after a split/trim are deleted — too small to recapture.
         * Based on minimum meaningful loop (~4 GPS points, ~72m perimeter ≈ 324m² square).
         * 100m² gives a comfortable margin while removing obvious slivers.
         */
        private const val MIN_TERRITORY_AREA_M2 = 100.0

        const val COL_SESSIONS = "sessions"
        const val COL_USERS = "users"
        const val COL_USER_STATE = "userSessionState"

        const val ACTION_LOCATION_UPDATE = "com.jntuh.capfit.LOCATION_UPDATE"
        const val ACTION_SESSION_TERMINATED = "com.jntuh.capfit.SESSION_TERMINATED"
        const val ACTION_WORKOUT_COMPLETE = "com.jntuh.capfit.WORKOUT_COMPLETE"
        const val ACTION_NEARBY_TERRITORIES_UPDATED = "com.jntuh.capfit.NEARBY_UPDATED"
        const val ACTION_OWN_TERRITORIES_UPDATED = "com.jntuh.capfit.OWN_TERRITORIES_UPDATED"

        const val EXTRA_LAT = "extra_lat"
        const val EXTRA_LNG = "extra_lng"
        const val EXTRA_TERMINATION_REASON = "extra_reason"
        const val EXTRA_SESSION_ID = "extra_session_id"
    }

    // ─── Public State ─────────────────────────────────────────────────────────
    private val _currentLocation = MutableStateFlow<LatLng?>(null)
    val currentLocation: StateFlow<LatLng?> = _currentLocation

    private val _rawTrackPoints = MutableStateFlow<List<TrackPoint>>(emptyList())
    val rawTrackPoints: StateFlow<List<TrackPoint>> = _rawTrackPoints

    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking

    // Exposed so MapsActivity can recover if it missed the WORKOUT_COMPLETE broadcast
    private val _lastCompletedSessionId = MutableStateFlow<String?>(null)
    val lastCompletedSessionId: StateFlow<String?> = _lastCompletedSessionId

    private val _nearbyTerritories = MutableStateFlow<List<TrackingSession>>(emptyList())
    val nearbyTerritories: StateFlow<List<TrackingSession>> = _nearbyTerritories

    private val _ownTerritories = MutableStateFlow<List<TrackingSession>>(emptyList())
    val ownTerritories: StateFlow<List<TrackingSession>> = _ownTerritories

    // ─── Internal Session State ───────────────────────────────────────────────
    private val collectedPoints = mutableListOf<TrackPoint>()
    private var totalDistanceM = 0.0
    private var lastRawPoint: TrackPoint? = null
    private var lastKeptPoint: TrackPoint? = null
    private var sessionId: String = ""
    private var sessionStartTime: Long = 0L
    private var lastFirebaseSyncTime: Long = 0L
    private var lastNearbyFetchTime: Long = 0L
    private var groupId: String? = null
    private var currentUserName: String = "Unknown"

    // ─── Dependencies ─────────────────────────────────────────────────────────
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // ─── Lifecycle ────────────────────────────────────────────────────────────
    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
        setupLocationCallback()

        // EC5: Clean up any stale isLive sessions from previous crashes on startup
        cleanupStaleSessions()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification("Ready"))
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        // Always remove location updates on destroy — prevents GPS leaking
        // into the next session or running while on HomePage
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            Log.d(TAG, "onDestroy: location updates removed")
        } catch (e: Exception) {
            Log.e(TAG, "onDestroy: failed to remove location updates: ${e.message}")
        }
        serviceScope.cancel()
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    fun startTracking(groupId: String? = null) {
        if (_isTracking.value) return
        val userId = auth.currentUser?.uid ?: return

        serviceScope.launch {
            // Use cached UserGameData — no extra Firestore call needed
            currentUserName = try {
                userGameDataManager.getUserGameData().userName.ifBlank {
                    auth.currentUser?.displayName ?: "Unknown"
                }
            } catch (e: Exception) {
                auth.currentUser?.displayName ?: "Unknown"
            }
            withContext(Dispatchers.Main) { startTrackingInternal(userId, groupId) }
        }
    }

    private fun startTrackingInternal(userId: String, groupId: String?) {
        this.groupId = groupId
        sessionId = db.collection(COL_SESSIONS).document().id
        sessionStartTime = System.currentTimeMillis()
        lastFirebaseSyncTime = sessionStartTime
        lastNearbyFetchTime = 0L  // force immediate fetch on first point
        collectedPoints.clear()
        totalDistanceM = 0.0
        lastRawPoint = null
        lastKeptPoint = null
        _isTracking.value = true

        val initialDateStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            .format(java.util.Date(sessionStartTime))

        db.collection(COL_SESSIONS).document(sessionId)
            .set(mapOf(
                "sessionId" to sessionId,
                "userId"    to userId,
                "userName"  to currentUserName,
                "groupId"   to groupId,
                "startTime" to sessionStartTime,
                "endTime"   to 0L,
                "date"      to initialDateStr,
                "points"    to emptyList<Any>(),
                "distance"  to 0.0,
                "area"      to 0.0,
                "geohash"   to "",
                "xMin"      to 0.0,
                "xMax"      to 0.0,
                "yMin"      to 0.0,
                "yMax"      to 0.0,
                "isLive"    to true
            ))
            .addOnFailureListener { Log.e(TAG, "Session create failed: ${it.message}") }

        // Mark user as having a live session
        // IMPORTANT: only set isSessionLive=true here
        // Never reset sessions/groups/capturedArea — those accumulate across workouts
        val userStateRef = db.collection(COL_USERS).document(userId)
            .collection(COL_USER_STATE).document("data")

        userStateRef.get().addOnSuccessListener { snap ->
            if (snap.exists()) {
                // Doc exists — only flip isSessionLive, never touch sessions/capturedArea
                userStateRef.set(
                    mapOf("isSessionLive" to true),
                    com.google.firebase.firestore.SetOptions.merge()
                ).addOnFailureListener { Log.e(TAG, "User state update failed: ${it.message}") }
            } else {
                // First ever workout — initialise all fields from scratch
                userStateRef.set(
                    mapOf(
                        "isSessionLive" to true,
                        "isGroupLive"   to false,
                        "capturedArea"  to 0.0,
                        "sessions"      to emptyList<String>(),
                        "groups"        to emptyList<String>()
                    )
                ).addOnFailureListener { Log.e(TAG, "User state create failed: ${it.message}") }
            }
        }.addOnFailureListener { Log.e(TAG, "User state read failed: ${it.message}") }

        Log.d(TAG, "── startTracking ── sessionId=$sessionId userId=$userId groupId=$groupId")
        requestLocationUpdates()
        updateNotification("Workout started!")
        Log.d(TAG, "GPS updates requested — interval: ${GPS_INTERVAL_MS}ms")

        // Capture the starting point immediately — don't wait 15s for first GPS interval
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    Log.d(TAG, "Starting point captured immediately: ${location.latitude}, ${location.longitude}")
                    handleNewPoint(location.latitude, location.longitude)
                } else {
                    Log.w(TAG, "Starting point: lastLocation null — will wait for first GPS interval")
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Starting point: permission missing")
        }
    }

    fun stopTracking() {
        if (!_isTracking.value) return
        _isTracking.value = false
        fusedLocationClient.removeLocationUpdates(locationCallback)
        updateNotification("Processing workout...")
        Log.d(TAG, "── stopTracking: collecting final point before finalize ──")

        try {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        Log.d(TAG, "Final point captured: ${location.latitude}, ${location.longitude} (acc=${location.accuracy}m)")
                        handleNewPoint(location.latitude, location.longitude)
                    } else {
                        Log.w(TAG, "Final point: lastLocation was null — skipping")
                    }
                    serviceScope.launch { finalizeSession() }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Final point fetch failed: ${e.message} — finalizing without it")
                    serviceScope.launch { finalizeSession() }
                }
        } catch (e: SecurityException) {
            Log.e(TAG, "Final point: permission missing — finalizing without it")
            serviceScope.launch { finalizeSession() }
        }
    }

    fun fetchNearbyForLocation(lat: Double, lng: Double) {
        val center = TrackPoint(lat = lat, lng = lng)
        fetchNearbyTerritories(center)
        fetchOwnTerritories(lat, lng)
    }

    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                if (location.accuracy > GPS_ACCURACY_THRESHOLD_M) return

                val latLng = LatLng(location.latitude, location.longitude)
                _currentLocation.value = latLng
                broadcastLocationUpdate(latLng)

                if (_isTracking.value) {
                    handleNewPoint(location.latitude, location.longitude)
                }
            }
        }
    }

    private fun requestLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, GPS_INTERVAL_MS)
            .setMinUpdateIntervalMillis(GPS_FASTEST_INTERVAL_MS)
            .setWaitForAccurateLocation(true)
            .build()
        try {
            fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission missing: ${e.message}")
        }
    }

    private fun handleNewPoint(lat: Double, lng: Double) {
        val newPoint = TrackPoint(lat = lat, lng = lng, time = System.currentTimeMillis())
        val lastRaw = lastRawPoint

        if (lastRaw == null) {
            lastRawPoint = newPoint
            Log.d(TAG, "First point — reference only: $lat, $lng")
            return
        }

        val dist = AreaCalculator.haversineDistance(lastRaw.lat, lastRaw.lng, lat, lng)
        if (dist < MIN_POINT_DISTANCE_M) {
            Log.v(TAG, "Point skipped — too close: ${String.format("%.1f", dist)}m < ${MIN_POINT_DISTANCE_M}m")
            return
        }

        totalDistanceM += dist
        lastRawPoint = newPoint

        val secondLast = if (collectedPoints.size >= 2) collectedPoints[collectedPoints.size - 2] else null
        val last = lastKeptPoint

        when {
            last == null -> {
                collectedPoints.add(newPoint)
                lastKeptPoint = newPoint
            }
            else -> when (AreaCalculator.shouldAddPoint(secondLast, last, newPoint)) {
                AreaCalculator.PointDecision.ADD -> {
                    collectedPoints.add(newPoint)
                    lastKeptPoint = newPoint
                    Log.d(TAG, "Point #${collectedPoints.size} added: ($lat, $lng) dist=${String.format("%.1f", dist)}m total=${String.format("%.0f", totalDistanceM)}m")
                }
                AreaCalculator.PointDecision.REPLACE_LAST -> {
                    collectedPoints[collectedPoints.size - 1] = newPoint
                    lastKeptPoint = newPoint
                    Log.v(TAG, "Point #${collectedPoints.size} replaced (collinear): ($lat, $lng)")
                }
            }
        }

        _rawTrackPoints.value = collectedPoints.toList()

        if (isMalpractice()) {
            terminateAsMalpractice()
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastFirebaseSyncTime >= FIREBASE_SYNC_INTERVAL_MS) {
            lastFirebaseSyncTime = now
            syncPointsToFirebase()
        }

        // Problem 3 fix: throttle nearby fetch — not every GPS point
        if (now - lastNearbyFetchTime >= NEARBY_FETCH_INTERVAL_MS) {
            lastNearbyFetchTime = now
            fetchNearbyTerritories(newPoint)
            fetchOwnTerritories(lat, lng)
        }

        updateNotification("Points: ${collectedPoints.size} | ${String.format("%.0f", totalDistanceM)}m")
    }

    // ─── Malpractice Detection ────────────────────────────────────────────────

    private fun isMalpractice(): Boolean {
        if (collectedPoints.size < 2) return false
        val p1 = collectedPoints[collectedPoints.size - 2]
        val p2 = collectedPoints[collectedPoints.size - 1]
        val distM = AreaCalculator.haversineDistance(p1.lat, p1.lng, p2.lat, p2.lng)
        val timeSec = (p2.time - p1.time) / 1000.0
        if (timeSec <= 0) return false
        val speedKmh = (distM / timeSec) * 3.6
        Log.d(TAG, "Speed check: ${String.format("%.1f", speedKmh)} km/h (limit: ${MAL_SPEED_KMH} km/h)")
        if (speedKmh <= MAL_SPEED_KMH) return false
        if (collectedPoints.size < 5) return false
        val last5 = collectedPoints.takeLast(5)
        var totalDist = 0.0
        for (i in 1 until last5.size) {
            totalDist += AreaCalculator.haversineDistance(
                last5[i-1].lat, last5[i-1].lng, last5[i].lat, last5[i].lng
            )
        }
        Log.w(TAG, "⚠️ Speed exceeded! ${String.format("%.1f", speedKmh)} km/h, last-5-dist: ${String.format("%.1f", totalDist)}m (threshold: ${MAL_DIST_5_POINTS_M}m)")
        return totalDist > MAL_DIST_5_POINTS_M
    }

    private fun terminateAsMalpractice() {
        Log.e(TAG, "🚨 MALPRACTICE DETECTED — session terminated. Speed exceeded ${MAL_SPEED_KMH} km/h threshold.")
        _isTracking.value = false
        fusedLocationClient.removeLocationUpdates(locationCallback)
        collectedPoints.clear()
        db.collection(COL_SESSIONS).document(sessionId).delete()
            .addOnSuccessListener { Log.d(TAG, "Malpractice: session doc deleted") }
            .addOnFailureListener { Log.e(TAG, "Malpractice: failed to delete session: ${it.message}") }
        auth.currentUser?.uid?.let { uid ->
            db.collection(COL_USERS).document(uid)
                .collection(COL_USER_STATE).document("data")
                .set(mapOf("isSessionLive" to false), com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener { Log.d(TAG, "Malpractice: user state reset") }
        }
        serviceScope.launch(Dispatchers.Main) {
            Log.d(TAG, "Sending ACTION_SESSION_TERMINATED broadcast — reason=malpractice")
            sendBroadcast(Intent(ACTION_SESSION_TERMINATED).apply {
                setPackage(packageName)
                putExtra(EXTRA_TERMINATION_REASON, "malpractice")
            })
            stopSelf()
        }
    }

    // ─── Mid-session Sync ─────────────────────────────────────────────────────

    private fun syncPointsToFirebase() {
        if (collectedPoints.isEmpty()) return
        Log.d(TAG, "Mid-session sync → ${collectedPoints.size} points to Firestore")
        val pointMaps = collectedPoints.map {
            mapOf("lat" to it.lat, "lng" to it.lng, "time" to it.time)
        }
        db.collection(COL_SESSIONS).document(sessionId)
            .update("points", pointMaps)
            .addOnSuccessListener { Log.d(TAG, "Mid-session sync OK") }
            .addOnFailureListener { Log.e(TAG, "Mid-sync failed: ${it.message}") }
    }

    // ─── Phase 2: Finalize Session ────────────────────────────────────────────

    private suspend fun finalizeSession() {
        val userId = auth.currentUser?.uid ?: return
        Log.d(TAG, "── finalizeSession ── points: ${collectedPoints.size}, distance: ${String.format("%.1f", totalDistanceM)}m")

        if (collectedPoints.size < 4) {
            Log.w(TAG, "Too few points (${collectedPoints.size}) — session discarded")
            db.collection(COL_SESSIONS).document(sessionId).delete().await()
            db.collection(COL_USERS).document(userId)
                .collection(COL_USER_STATE).document("data")
                .set(mapOf("isSessionLive" to false),
                    com.google.firebase.firestore.SetOptions.merge()).await()
            // Notify UI even when session discarded — fixes tvStatus stuck on "Processing"
            withContext(Dispatchers.Main) {
                Log.d(TAG, "Sending ACTION_SESSION_TERMINATED broadcast — reason=too_short")
                sendBroadcast(Intent(ACTION_SESSION_TERMINATED).apply {
                    setPackage(packageName)
                    putExtra(EXTRA_TERMINATION_REASON, "too_short")
                })
                stopSelf()
            }
            return
        }

        val result = withContext(Dispatchers.Default) {
            AreaCalculator.calculate(
                filteredPoints = collectedPoints.toList(),
                totalDistanceM = totalDistanceM
            )
        }

        val centerLat = (result.yMin + result.yMax) / 2
        val centerLng = (result.xMin + result.xMax) / 2
        val geohash = if (result.hasTerritory)
            GeoHashUtil.encode(centerLat, centerLng, GeoHashUtil.STORAGE_PRECISION)
        else ""

        val sessionEndTime = System.currentTimeMillis()
        val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            .format(java.util.Date(sessionEndTime))

        val finalSession = TrackingSession(
            sessionId = sessionId,
            userId = userId,
            userName = currentUserName,
            groupId = groupId,
            startTime = sessionStartTime,
            endTime = sessionEndTime,
            points = result.polygonPoints,
            distance = result.totalDistanceM,
            area = result.areaM2,
            date = dateStr,
            geohash = geohash,
            xMin = result.xMin,
            xMax = result.xMax,
            yMin = result.yMin,
            yMax = result.yMax,
            isLive = false
        )

        if (!result.hasTerritory) {
            Log.w(TAG, "No closed loop detected — area = 0. Session saved without territory.")
            saveSessionToFirestore(finalSession, emptyList(), emptyList())
            return
        }

        if (result.areaM2 < MIN_TERRITORY_AREA_M2) {
            Log.w(TAG, "Area too small: ${result.areaM2}m² < ${MIN_TERRITORY_AREA_M2}m² threshold — territory discarded")
            val noTerritorySession = finalSession.copy(area = 0.0, points = emptyList(), geohash = "")
            saveSessionToFirestore(noTerritorySession, emptyList(), emptyList())
            return
        }

        Log.d(TAG, "Territory valid: ${String.format("%.1f", result.areaM2)}m² — proceeding to intersection check")

        val newPolygon = AreaCalculator.buildPolygonFromStoredPoints(result.polygonPoints)
        if (newPolygon != null) {
            resolveIntersectionsAndSave(finalSession, newPolygon)
        } else {
            saveSessionToFirestore(finalSession, emptyList(), emptyList())
        }
    }

    // ─── Phase 3: Intersection Resolution ────────────────────────────────────

    private suspend fun resolveIntersectionsAndSave(
        newSession: TrackingSession,
        newPolygon: Polygon
    ) {
        val userId = auth.currentUser?.uid ?: return

        // Build geohash neighbor prefixes to avoid missing border territories
        val centerLat = (newSession.yMin + newSession.yMax) / 2
        val centerLng = (newSession.xMin + newSession.xMax) / 2
        val queryPrefix = GeoHashUtil.encode(centerLat, centerLng, GeoHashUtil.QUERY_PRECISION)
        val neighborPrefixes = GeoHashUtil.neighbors(queryPrefix)
            .map { GeoHashUtil.encode(GeoHashUtil.decode(it).first, GeoHashUtil.decode(it).second, GeoHashUtil.QUERY_PRECISION) }
            .distinct()

        // Fetch all candidate sessions (other users + own)
        val seenIds = mutableSetOf<String>()
        val allCandidates = mutableListOf<TrackingSession>()

        for (prefix in neighborPrefixes) {
            val (start, end) = GeoHashUtil.queryRange(prefix)
            try {
                val snap = db.collection(COL_SESSIONS)
                    .whereEqualTo("isLive", false)
                    .whereGreaterThanOrEqualTo("geohash", start)
                    .whereLessThan("geohash", end)
                    .get().await()

                snap.documents.mapNotNull { it.toObject(TrackingSession::class.java) }
                    .filter { s ->
                        s.sessionId != sessionId &&       // exclude current session
                                s.area > 0.0 &&
                                seenIds.add(s.sessionId) &&       // deduplicate
                                s.xMax > newSession.xMin &&
                                s.xMin < newSession.xMax &&
                                s.yMax > newSession.yMin &&
                                s.yMin < newSession.yMax
                    }.forEach { allCandidates.add(it) }
            } catch (e: Exception) {
                Log.e(TAG, "Geohash query failed for prefix $prefix: ${e.message}")
            }
        }

        // Split into own sessions and other users' sessions
        // EC1: Own sessions need MERGE (union), others need SUBTRACT (difference)
        val ownSessions = allCandidates.filter { it.userId == userId }
        val otherSessions = allCandidates.filter { it.userId != userId }

        Log.d(TAG, "Intersections — own: ${ownSessions.size}, others: ${otherSessions.size}")

        // Other users: parallel is fine — each subtract is independent
        val otherResults = withContext(Dispatchers.Default) {
            otherSessions.map { async { computeSubtractResult(it, newPolygon) } }.awaitAll()
        }

        // Own sessions: MUST be sequential — each merge chains into the next.
        // If parallel: Territory A and B both union with newPolygon independently
        // → newPolygon area counted twice (or more). Wrong!
        // Sequential: A ∪ newPolygon → result → result ∪ B → final
        // → newPolygon area counted exactly once. Correct!
        val ownResults = withContext(Dispatchers.Default) {
            var accumulatedPolygon: org.locationtech.jts.geom.Geometry = newPolygon
            val results = mutableListOf<BatchOperation>()

            for (ownSession in ownSessions) {
                val existingPolygon = AreaCalculator.buildPolygonFromStoredPoints(ownSession.points)
                if (existingPolygon == null) {
                    Log.w(TAG, "Own session ${ownSession.sessionId} has no valid polygon — skipping")
                    continue
                }
                if (!existingPolygon.intersects(accumulatedPolygon) && !existingPolygon.touches(accumulatedPolygon)) {
                    Log.d(TAG, "Own session ${ownSession.sessionId} does not overlap accumulated polygon — skipping")
                    continue
                }
                val merged = try {
                    existingPolygon.union(accumulatedPolygon)
                } catch (e: Exception) {
                    Log.e(TAG, "Own territory merge failed for ${ownSession.sessionId}: ${e.message}")
                    continue
                }
                Log.d(TAG, "Own territory merged: ${ownSession.area}m² + accumulated → ${AreaCalculator.calculateAreaM2(merged)}m²")
                results.add(BatchOperation(ownSession, merged, isOwnSession = true))
                accumulatedPolygon = merged  // chain: next merge uses the grown polygon
            }
            results
        }

        saveSessionToFirestore(newSession, otherResults.filterNotNull(), ownResults)
    }

    // ─── EC1: Subtract (other users' territory) ───────────────────────────────

    /**
     * Other user's territory: A = A - (A ∩ B)
     * We take away from them what we walked through.
     */
    private fun computeSubtractResult(
        existingSession: TrackingSession,
        newPolygon: Polygon
    ): BatchOperation? {
        val existingPolygon = AreaCalculator.buildPolygonFromStoredPoints(existingSession.points) ?: return null
        if (!existingPolygon.intersects(newPolygon)) return null

        val intersection = try { existingPolygon.intersection(newPolygon) }
        catch (e: Exception) { return null }
        if (intersection.isEmpty) return null

        val remaining = try { existingPolygon.difference(intersection) }
        catch (e: Exception) { return null }

        return BatchOperation(existingSession, remaining, isOwnSession = false)
    }

    // ─── EC1: Merge (own territory) ───────────────────────────────────────────

    /**
     * Own territory: A = A ∪ B (union — merge both together)
     * When the same user walks again over their own area or extends it,
     * we merge both polygons into one unified territory.
     * The new session itself gets dissolved into the existing one.
     * capturedArea is adjusted by (unionArea - existingArea) — no double counting.
     */
    private fun computeMergeResult(
        existingSession: TrackingSession,
        newPolygon: Polygon
    ): BatchOperation? {
        val existingPolygon = AreaCalculator.buildPolygonFromStoredPoints(existingSession.points) ?: return null
        if (!existingPolygon.intersects(newPolygon) && !existingPolygon.touches(newPolygon)) {
            // Completely separate areas — no merge needed, both remain independent
            return null
        }

        val merged = try { existingPolygon.union(newPolygon) }
        catch (e: Exception) {
            Log.e(TAG, "Own territory merge failed: ${e.message}")
            return null
        }

        return BatchOperation(existingSession, merged, isOwnSession = true)
    }

    // ─── Batch Operation ──────────────────────────────────────────────────────

    data class BatchOperation(
        val existingSession: TrackingSession,
        val resultGeometry: Geometry,
        val isOwnSession: Boolean
    )

    // ─── O4: Single WriteBatch ────────────────────────────────────────────────

    private suspend fun saveSessionToFirestore(
        session: TrackingSession,
        otherOps: List<BatchOperation>,
        ownOps: List<BatchOperation>
    ) {
        val userId = auth.currentUser?.uid ?: return
        val batch = db.batch()
        val userStateRef = db.collection(COL_USERS).document(userId)
            .collection(COL_USER_STATE).document("data")

        // ── Strategy ──────────────────────────────────────────────────────────
        // No overlapping own sessions → just save new session doc as-is.
        //
        // Overlapping own sessions exist → merge everything into ONE doc:
        //   • Survivor = the oldest own session doc (first in ownOps)
        //   • Final geometry = union of all old polygons + new polygon (already
        //     computed sequentially in ownOps.last().resultGeometry)
        //   • Survivor doc updated with merged points/area/bbox/geohash
        //   • Also accumulate distance: survivor.distance + new session.distance
        //   • All other old docs → deleted
        //   • New session doc → NOT created (absorbed into survivor)
        //   • userSessionState.sessions: remove absorbed IDs, keep survivor ID
        //
        // netNewArea = finalMergedArea − sum of all old own areas
        // (the genuinely new ground covered this workout)

        val sumExistingOwnArea = ownOps.sumOf { it.existingSession.area }

        val broadcastSessionId: String

        if (ownOps.isEmpty()) {
            // ── No overlap: plain save ────────────────────────────────────────
            broadcastSessionId = session.sessionId
            val netNew = session.area
            val ref = db.collection(COL_SESSIONS).document(session.sessionId)
            batch.set(ref, mapOf(
                "sessionId" to session.sessionId,
                "userId"    to session.userId,
                "userName"  to session.userName,
                "groupId"   to session.groupId,
                "startTime" to session.startTime,
                "endTime"   to session.endTime,
                "date"      to session.date,
                "points"    to session.points.map { mapOf("lat" to it.lat, "lng" to it.lng, "time" to it.time) },
                "distance"  to session.distance,
                "area"      to session.area,
                "geohash"   to session.geohash,
                "xMin" to session.xMin, "xMax" to session.xMax,
                "yMin" to session.yMin, "yMax" to session.yMax,
                "isLive"    to false
            ))
            batch.set(userStateRef, mapOf(
                "sessions"      to FieldValue.arrayUnion(session.sessionId),
                "isSessionLive" to false,
                "capturedArea"  to FieldValue.increment(netNew)
            ), com.google.firebase.firestore.SetOptions.merge())
            Log.d(TAG, "No overlap — saved new session ${session.sessionId} area=${String.format("%.1f", session.area)}m²")

        } else {
            // ── Overlap: merge into one survivor doc ──────────────────────────
            val finalGeometry  = ownOps.last().resultGeometry
            val finalArea      = AreaCalculator.calculateAreaM2(finalGeometry)
            val finalPoints    = AreaCalculator.extractBoundaryPoints(finalGeometry)
            val env            = finalGeometry.envelopeInternal
            val centerLat      = (env.minY + env.maxY) / 2
            val centerLng      = (env.minX + env.maxX) / 2
            val finalGeohash   = GeoHashUtil.encode(centerLat, centerLng, GeoHashUtil.STORAGE_PRECISION)

            // Accumulate total distance across all merged sessions + new workout
            val totalDistance  = ownOps.sumOf { it.existingSession.distance } + session.distance

            val survivorId     = ownOps.first().existingSession.sessionId
            broadcastSessionId = survivorId

            val absorbedIds    = ownOps.drop(1).map { it.existingSession.sessionId } + session.sessionId
            val netNew         = (finalArea - sumExistingOwnArea).coerceAtLeast(0.0)

            // Update survivor with merged geometry + accumulated distance
            val survivorRef = db.collection(COL_SESSIONS).document(survivorId)
            batch.update(survivorRef, mapOf(
                "points"   to finalPoints.map { mapOf("lat" to it.lat, "lng" to it.lng, "time" to it.time) },
                "area"     to finalArea,
                "distance" to totalDistance,
                "geohash"  to finalGeohash,
                "xMin" to env.minX, "xMax" to env.maxX,
                "yMin" to env.minY, "yMax" to env.maxY,
                "isLive"   to false,
                "userName" to session.userName,
                "date"     to session.date
            ))
            Log.d(TAG, "Survivor: $survivorId area=${String.format("%.1f", finalArea)}m² dist=${String.format("%.1f", totalDistance)}m")

            // Delete all absorbed session docs
            for (id in absorbedIds) {
                batch.delete(db.collection(COL_SESSIONS).document(id))
                Log.d(TAG, "Absorbed+deleted: $id")
            }

            // Remove absorbed IDs from sessions list, keep survivor
            batch.set(userStateRef, mapOf(
                "sessions"      to FieldValue.arrayRemove(*absorbedIds.toTypedArray()),
                "isSessionLive" to false,
                "capturedArea"  to FieldValue.increment(netNew)
            ), com.google.firebase.firestore.SetOptions.merge())

            Log.d(TAG, "capturedArea: finalMerged=${String.format("%.1f", finalArea)}m² sumExisting=${String.format("%.1f", sumExistingOwnArea)}m² netNew=${String.format("%.1f", netNew)}m²")
        }

        // Apply other users' territory subtractions (always)
        for (op in otherOps) {
            applySubtractOperation(batch, op)
        }

        val committed = commitWithRetry(batch)
        if (committed) {
            Log.d(TAG, "── Batch committed ── broadcastId=$broadcastSessionId ownMerges=${ownOps.size} otherSubtracts=${otherOps.size}")

            // ── F: Update SeasonData + UserGameData after successful commit ──
            updateStatsAfterWorkout(session)

            withContext(Dispatchers.Main) {
                _lastCompletedSessionId.value = broadcastSessionId
                Log.d(TAG, "Sending ACTION_WORKOUT_COMPLETE broadcast — sessionId=$broadcastSessionId")
                sendBroadcast(Intent(ACTION_WORKOUT_COMPLETE).apply {
                    putExtra(EXTRA_SESSION_ID, broadcastSessionId)
                    setPackage(packageName)
                })
            }
        } else {
            Log.e(TAG, "Batch commit failed after retries — session data may be lost")
        }

        withContext(Dispatchers.Main) { stopSelf() }
    }

    private fun applySubtractOperation(
        batch: com.google.firebase.firestore.WriteBatch,
        op: BatchOperation
    ) {
        val areaBefore = op.existingSession.area
        val ownerRef = db.collection(COL_USERS).document(op.existingSession.userId)
            .collection(COL_USER_STATE).document("data")
        val sessionRef = db.collection(COL_SESSIONS).document(op.existingSession.sessionId)

        when {

            // ── Case 1: Fully consumed (nothing left) ──────────────────────────
            op.resultGeometry.isEmpty -> {
                batch.update(sessionRef, mapOf("area" to 0.0, "points" to emptyList<Any>()))
                batch.set(ownerRef, mapOf("capturedArea" to FieldValue.increment(-areaBefore)), com.google.firebase.firestore.SetOptions.merge())
                notifyTerritoryFullyConsumed(op.existingSession.userId, areaBefore)
                Log.d(TAG, "Territory fully consumed (${areaBefore}m²)")
            }

            // ── Case 2: Trimmed — one piece remains ────────────────────────────
            op.resultGeometry is Polygon -> {
                val newArea = AreaCalculator.calculateAreaM2(op.resultGeometry)

                if (newArea < MIN_TERRITORY_AREA_M2) {
                    // Remaining sliver too small to keep — treat as fully consumed
                    batch.update(sessionRef, mapOf("area" to 0.0, "points" to emptyList<Any>()))
                    batch.set(ownerRef, mapOf("capturedArea" to FieldValue.increment(-areaBefore)), com.google.firebase.firestore.SetOptions.merge())
                    notifyTerritoryFullyConsumed(op.existingSession.userId, areaBefore)
                    Log.d(TAG, "Trimmed remainder ${newArea}m² < threshold — deleted")
                } else {
                    val newPoints = AreaCalculator.extractBoundaryPoints(op.resultGeometry)
                    val env = op.resultGeometry.envelopeInternal
                    val centerLat = (env.minY + env.maxY) / 2
                    val centerLng = (env.minX + env.maxX) / 2
                    batch.update(sessionRef, mapOf(
                        "points" to newPoints.map { mapOf("lat" to it.lat, "lng" to it.lng, "time" to it.time) },
                        "area" to newArea,
                        "geohash" to GeoHashUtil.encode(centerLat, centerLng, GeoHashUtil.STORAGE_PRECISION),
                        "xMin" to env.minX, "xMax" to env.maxX,
                        "yMin" to env.minY, "yMax" to env.maxY
                    ))
                    batch.set(ownerRef, mapOf("capturedArea" to FieldValue.increment(newArea - areaBefore)), com.google.firebase.firestore.SetOptions.merge())
                    notifyTerritoryTrimmed(op.existingSession.userId, areaBefore, newArea)
                    Log.d(TAG, "Territory trimmed: ${areaBefore}m² → ${newArea}m²")
                }
            }

            // ── Case 3: Split into multiple pieces ────────────────────────────
            op.resultGeometry is GeometryCollection -> {
                // Collect only pieces above threshold
                data class ValidPiece(
                    val points: List<TrackPoint>,
                    val area: Double,
                    val geohash: String,
                    val xMin: Double, val xMax: Double,
                    val yMin: Double, val yMax: Double
                )

                val validPieces = mutableListOf<ValidPiece>()
                var totalDiscardedArea = 0.0

                for (i in 0 until op.resultGeometry.numGeometries) {
                    val piece = op.resultGeometry.getGeometryN(i)
                    if (piece !is Polygon || piece.isEmpty) continue

                    val pieceArea = AreaCalculator.calculateAreaM2(piece)

                    if (pieceArea < MIN_TERRITORY_AREA_M2) {
                        // Too small — discard this fragment
                        totalDiscardedArea += pieceArea
                        Log.d(TAG, "Split piece ${pieceArea}m² < threshold — discarded")
                        continue
                    }

                    val pieceEnv = piece.envelopeInternal
                    val centerLat = (pieceEnv.minY + pieceEnv.maxY) / 2
                    val centerLng = (pieceEnv.minX + pieceEnv.maxX) / 2
                    validPieces.add(ValidPiece(
                        points  = AreaCalculator.extractBoundaryPoints(piece),
                        area    = pieceArea,
                        geohash = GeoHashUtil.encode(centerLat, centerLng, GeoHashUtil.STORAGE_PRECISION),
                        xMin = pieceEnv.minX, xMax = pieceEnv.maxX,
                        yMin = pieceEnv.minY, yMax = pieceEnv.maxY
                    ))
                }

                if (validPieces.isEmpty()) {
                    // All pieces were below threshold — fully consumed
                    batch.update(sessionRef, mapOf("area" to 0.0, "points" to emptyList<Any>()))
                    batch.set(ownerRef, mapOf("capturedArea" to FieldValue.increment(-areaBefore)), com.google.firebase.firestore.SetOptions.merge())
                    notifyTerritoryFullyConsumed(op.existingSession.userId, areaBefore)
                    Log.d(TAG, "All split pieces below threshold — territory fully consumed")
                    return
                }

                // First valid piece → update the original session doc
                val first = validPieces[0]
                batch.update(sessionRef, mapOf(
                    "points" to first.points.map { mapOf("lat" to it.lat, "lng" to it.lng, "time" to it.time) },
                    "area"    to first.area,
                    "geohash" to first.geohash,
                    "xMin" to first.xMin, "xMax" to first.xMax,
                    "yMin" to first.yMin, "yMax" to first.yMax
                ))

                // Remaining valid pieces → new session docs, added to B's sessions list
                for (i in 1 until validPieces.size) {
                    val piece = validPieces[i]
                    val newDocId = db.collection(COL_SESSIONS).document().id
                    val splitSession = op.existingSession.copy(
                        sessionId = newDocId,
                        points    = piece.points,
                        area      = piece.area,
                        geohash   = piece.geohash,
                        xMin = piece.xMin, xMax = piece.xMax,
                        yMin = piece.yMin, yMax = piece.yMax
                    )
                    batch.set(db.collection(COL_SESSIONS).document(newDocId), splitSession)

                    // Fix: add new split doc to B's sessions list
                    batch.set(ownerRef, mapOf("sessions" to FieldValue.arrayUnion(newDocId)), com.google.firebase.firestore.SetOptions.merge())
                }

                // Update B's capturedArea — total valid kept area minus what they had before
                val totalKeptArea = validPieces.sumOf { it.area }
                batch.set(ownerRef, mapOf("capturedArea" to FieldValue.increment(totalKeptArea - areaBefore)), com.google.firebase.firestore.SetOptions.merge())

                notifyTerritorySplit(
                    userId         = op.existingSession.userId,
                    originalArea   = areaBefore,
                    keptPieces     = validPieces.size,
                    discardedArea  = totalDiscardedArea
                )
                Log.d(TAG, "Territory split: ${validPieces.size} kept (${totalKeptArea}m²), discarded ${totalDiscardedArea}m²")
            }
        }
    }



    // ─── A: Batch Commit with Exponential Backoff Retry ─────────────────────────

    private suspend fun commitWithRetry(
        batch: com.google.firebase.firestore.WriteBatch,
        maxRetries: Int = 3
    ): Boolean {
        var attempt = 0
        var delayMs = 1000L
        while (attempt < maxRetries) {
            try {
                batch.commit().await()
                return true
            } catch (e: Exception) {
                attempt++
                if (attempt >= maxRetries) {
                    Log.e(TAG, "Batch commit failed after $maxRetries attempts: ${e.message}")
                    return false
                }
                Log.w(TAG, "Batch commit attempt $attempt failed — retrying in ${delayMs}ms: ${e.message}")
                delay(delayMs)
                delayMs *= 2  // exponential backoff: 1s → 2s → 4s
            }
        }
        return false
    }

    // ─── F: Post-workout Stats Update ───────────────────────────────────────────

    private suspend fun updateStatsAfterWorkout(session: TrackingSession) {
        try {
            val userId = auth.currentUser?.uid ?: return

            // Update SeasonData — cumulative stats for this month
            val durationMs = if (session.startTime > 0L && session.endTime > session.startTime)
                session.endTime - session.startTime
            else 0L

            seasonDataManager.updateWorkoutStats(
                distanceM  = session.distance,
                areaM2     = session.area,
                durationMs = durationMs
            )
            Log.d(TAG, "SeasonData updated — dist=${session.distance}m area=${session.area}m²")

            // Read latest capturedArea from userSessionState — source of truth
            val latestCapturedArea = try {
                val stateSnap = db.collection(COL_USERS).document(userId)
                    .collection(COL_USER_STATE).document("data")
                    .get().await()
                stateSnap.getDouble("capturedArea") ?: 0.0
            } catch (e: Exception) { 0.0 }

            // Update UserGameData — highest-ever records + live capturedArea for leaderboard
            val userData = userGameDataManager.getUserGameData()
            val newDistance = session.distance.toInt()
            val newArea     = session.area.toInt()

            val updatedData = userData.copy(
                highestDistanceCovered = maxOf(userData.highestDistanceCovered, newDistance),
                highestAreaCovered     = maxOf(userData.highestAreaCovered, newArea),
                capturedArea           = latestCapturedArea
            )

            userGameDataManager.updateUserGameData(updatedData)
            Log.d(TAG, "UserGameData updated — capturedArea=${latestCapturedArea}m² highestDist=${updatedData.highestDistanceCovered}m")

            // Update streak — uses session.date already formatted as "yyyy-MM-dd"
            userGameDataManager.updateStreakAfterWorkout(session.date)
            Log.d(TAG, "Streak updated for date=${session.date}")

            // N: Check and unlock achievements with latest stats
            checkAndSaveAchievements()

        } catch (e: Exception) {
            Log.e(TAG, "updateStatsAfterWorkout failed: ${e.message}")
        }
    }

    private suspend fun checkAndSaveAchievements() {
        try {
            val userData      = userGameDataManager.getUserGameData()
            val season        = seasonDataManager.getOrCreateCurrentSeason()
            val base          = achievementManager.getAllAchievements()
            val savedIds      = userData.achievements.toSet()
            val activeMinutes = achievementManager.parseTotalMinutes(season.totalTimePlayed)

            val oldList = base.map { a -> a.copy(isUnlocked = savedIds.contains(a.id)) }

            val updatedList = achievementManager.updateAchievementProgress(
                baseList          = base,
                distance          = userData.highestDistanceCovered,
                area              = userData.highestAreaCovered,
                streak            = userData.currentStreak,
                numberOfWorkouts  = season.numberOfWorkouts,
                activeTimeMinutes = activeMinutes
            ).map { a -> if (savedIds.contains(a.id)) a.copy(isUnlocked = true) else a }

            val newlyUnlocked = achievementManager.getNewlyUnlockedAchievements(oldList, updatedList)

            if (newlyUnlocked.isNotEmpty()) {
                val allUnlockedIds = achievementManager.getUnlockedIds(updatedList)
                userGameDataManager.updateUserAchievements(allUnlockedIds)
                Log.d(TAG, "Achievements unlocked: ${newlyUnlocked.map { it.title }}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "checkAndSaveAchievements failed: ${e.message}")
        }
    }

    // ─── Nearby Territories (other users) ────────────────────────────────────

    fun fetchNearbyTerritories(center: TrackPoint) {
        val userId = auth.currentUser?.uid ?: return
        val pad = NEARBY_RADIUS_DEG
        val queryPrefix = GeoHashUtil.encode(center.lat, center.lng, GeoHashUtil.QUERY_PRECISION)
        val (rangeStart, rangeEnd) = GeoHashUtil.queryRange(queryPrefix)

        db.collection(COL_SESSIONS)
            .whereEqualTo("isLive", false)
            .whereGreaterThanOrEqualTo("geohash", rangeStart)
            .whereLessThan("geohash", rangeEnd)
            .get()
            .addOnSuccessListener { snapshot ->
                val nearby = snapshot.documents.mapNotNull {
                    it.toObject(TrackingSession::class.java)
                }.filter { s ->
                    s.area > 0.0 &&
                            s.userId != userId &&
                            s.xMax > center.lng - pad &&
                            s.xMin < center.lng + pad &&
                            s.yMax > center.lat - pad &&
                            s.yMin < center.lat + pad
                }
                _nearbyTerritories.value = nearby
                sendBroadcast(Intent(ACTION_NEARBY_TERRITORIES_UPDATED).apply { setPackage(packageName) })
                Log.d(TAG, "Nearby territories: ${nearby.size}")
            }
            .addOnFailureListener { Log.e(TAG, "Fetch nearby failed: ${it.message}") }
    }

    // ─── EC3: Own Past Territories ────────────────────────────────────────────

    /**
     * EC3: Fetches current user's OWN past sessions to display on map.
     * Own territories shown in distinct blue — separate from others' colors.
     */
    fun fetchOwnTerritories(lat: Double, lng: Double) {
        val userId = auth.currentUser?.uid ?: return
        val pad = NEARBY_RADIUS_DEG
        val queryPrefix = GeoHashUtil.encode(lat, lng, GeoHashUtil.QUERY_PRECISION)
        val (rangeStart, rangeEnd) = GeoHashUtil.queryRange(queryPrefix)

        db.collection(COL_SESSIONS)
            .whereEqualTo("isLive", false)
            .whereEqualTo("userId", userId)
            .whereGreaterThanOrEqualTo("geohash", rangeStart)
            .whereLessThan("geohash", rangeEnd)
            .get()
            .addOnSuccessListener { snapshot ->
                val own = snapshot.documents.mapNotNull {
                    it.toObject(TrackingSession::class.java)
                }.filter { s ->
                    s.area > 0.0 &&
                            s.xMax > lng - pad &&
                            s.xMin < lng + pad &&
                            s.yMax > lat - pad &&
                            s.yMin < lat + pad
                }
                _ownTerritories.value = own
                sendBroadcast(Intent(ACTION_OWN_TERRITORIES_UPDATED).apply { setPackage(packageName) })
                Log.d(TAG, "Own territories nearby: ${own.size}")
            }
            .addOnFailureListener { Log.e(TAG, "Fetch own territories failed: ${it.message}") }
    }

    // ─── B: Zombie Session Cleanup ───────────────────────────────────────────────

    /**
     * B: On app launch, find sessions stuck as isLive=true from a previous crash.
     *
     * Two cases:
     *   1. Session is < 2 hours old → try to finalize it using the mid-synced points
     *      stored in Firestore (mid-session sync saves points every 60s).
     *      If points form a valid territory, area is calculated and saved properly.
     *      If not enough points, session is discarded cleanly.
     *
     *   2. Session is > 2 hours old → definitely zombie. Mark dead immediately
     *      (isLive=false, area=0) and reset userSessionState.
     */
    private fun cleanupStaleSessions() {
        val userId = auth.currentUser?.uid ?: return
        val twoHoursAgo = System.currentTimeMillis() - 2 * 60 * 60 * 1000L

        db.collection(COL_SESSIONS)
            .whereEqualTo("userId", userId)
            .whereEqualTo("isLive", true)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) return@addOnSuccessListener
                Log.d(TAG, "Found ${snapshot.size()} zombie session(s) — processing")

                serviceScope.launch {
                    val batch = db.batch()
                    val userStateRef = db.collection(COL_USERS).document(userId)
                        .collection(COL_USER_STATE).document("data")

                    val discardedIds = mutableListOf<String>()

                    for (doc in snapshot.documents) {
                        val startTime = doc.getLong("startTime") ?: 0L

                        if (startTime > twoHoursAgo) {
                            // Recent crash — try to salvage using mid-synced points
                            @Suppress("UNCHECKED_CAST")
                            val rawPoints = doc.get("points") as? List<Map<String, Any>> ?: emptyList()
                            val points = rawPoints.mapNotNull { map ->
                                val lat  = (map["lat"]  as? Number)?.toDouble() ?: return@mapNotNull null
                                val lng  = (map["lng"]  as? Number)?.toDouble() ?: return@mapNotNull null
                                val time = (map["time"] as? Number)?.toLong()   ?: 0L
                                TrackPoint(lat, lng, time)
                            }

                            if (points.size >= 4) {
                                try {
                                    val result = withContext(Dispatchers.Default) {
                                        AreaCalculator.calculate(points, 0.0)
                                    }
                                    if (result.hasTerritory && result.areaM2 >= MIN_TERRITORY_AREA_M2) {
                                        // Salvaged — update session with recovered territory
                                        val env = Envelope()
                                        result.polygonPoints.forEach { env.expandToInclude(it.lng, it.lat) }
                                        val centerLat = (env.minY + env.maxY) / 2
                                        val centerLng = (env.minX + env.maxX) / 2
                                        batch.update(doc.reference, mapOf(
                                            "isLive"   to false,
                                            "endTime"  to System.currentTimeMillis(),
                                            "area"     to result.areaM2,
                                            "points"   to result.polygonPoints.map { mapOf("lat" to it.lat, "lng" to it.lng, "time" to it.time) },
                                            "geohash"  to GeoHashUtil.encode(centerLat, centerLng, GeoHashUtil.STORAGE_PRECISION),
                                            "xMin"     to env.minX, "xMax" to env.maxX,
                                            "yMin"     to env.minY, "yMax" to env.maxY
                                        ))
                                        // Keep session in sessions[] — it's valid
                                        batch.set(userStateRef, mapOf(
                                            "sessions" to FieldValue.arrayUnion(doc.id)
                                        ), com.google.firebase.firestore.SetOptions.merge())
                                        Log.d(TAG, "Zombie salvaged: ${doc.id} area=${String.format("%.1f", result.areaM2)}m²")
                                    } else {
                                        // No valid territory — discard
                                        batch.update(doc.reference, mapOf("isLive" to false, "area" to 0.0))
                                        discardedIds.add(doc.id)
                                        Log.d(TAG, "Zombie discarded (no territory): ${doc.id}")
                                    }
                                } catch (e: Exception) {
                                    batch.update(doc.reference, mapOf("isLive" to false, "area" to 0.0))
                                    discardedIds.add(doc.id)
                                    Log.e(TAG, "Zombie salvage failed for ${doc.id}: ${e.message}")
                                }
                            } else {
                                // Too few points — discard
                                batch.update(doc.reference, mapOf("isLive" to false, "area" to 0.0))
                                discardedIds.add(doc.id)
                                Log.d(TAG, "Zombie discarded (too few points: ${points.size}): ${doc.id}")
                            }
                        } else {
                            // Old zombie (> 2 hours) — mark dead immediately
                            batch.update(doc.reference, mapOf("isLive" to false, "area" to 0.0))
                            discardedIds.add(doc.id)
                            Log.d(TAG, "Old zombie killed: ${doc.id} startTime=$startTime")
                        }
                    }

                    // Remove all discarded session IDs from sessions[]
                    // and reset isSessionLive in one write
                    val stateUpdate = mutableMapOf<String, Any>("isSessionLive" to false)
                    if (discardedIds.isNotEmpty()) {
                        batch.set(userStateRef, mapOf(
                            "sessions" to FieldValue.arrayRemove(*discardedIds.toTypedArray())
                        ), com.google.firebase.firestore.SetOptions.merge())
                        Log.d(TAG, "Removed ${discardedIds.size} discarded zombie IDs from sessions[]")
                    }
                    batch.set(userStateRef, stateUpdate,
                        com.google.firebase.firestore.SetOptions.merge())

                    commitWithRetry(batch)
                }
            }
            .addOnFailureListener { Log.e(TAG, "Zombie cleanup query failed: ${it.message}") }
    }

    // ─── Notification Stubs (implement later) ────────────────────────────────

    /**
     * Called when another user fully captures B's territory.
     * TODO: Send push notification to userId — "Your territory was fully captured!"
     */
    private fun notifyTerritoryFullyConsumed(userId: String, lostAreaM2: Double) {
        // TODO: implement push notification
        Log.d(TAG, "NOTIFY [$userId] territory fully consumed (${lostAreaM2}m²)")
    }

    /**
     * Called when another user trims B's territory but leaves a valid remainder.
     * TODO: Send push notification to userId — "Part of your territory was captured!"
     */
    private fun notifyTerritoryTrimmed(userId: String, beforeM2: Double, afterM2: Double) {
        // TODO: implement push notification
        Log.d(TAG, "NOTIFY [$userId] territory trimmed ${beforeM2}m² → ${afterM2}m²")
    }

    /**
     * Called when another user splits B's territory into pieces.
     * Some pieces may have been discarded (below MIN_TERRITORY_AREA_M2).
     * TODO: Send push notification to userId — "Your territory was split!"
     */
    private fun notifyTerritorySplit(
        userId: String,
        originalArea: Double,
        keptPieces: Int,
        discardedArea: Double
    ) {
        // TODO: implement push notification
        Log.d(TAG, "NOTIFY [$userId] territory split → $keptPieces pieces kept, ${discardedArea}m² discarded")
    }

    // ─── Notification ─────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Workout Tracking", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Active while tracking your workout" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MapsActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CapFit — Workout Active")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_sprint)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun broadcastLocationUpdate(latLng: LatLng) {
        sendBroadcast(Intent(ACTION_LOCATION_UPDATE).apply {
            setPackage(packageName)
            putExtra(EXTRA_LAT, latLng.latitude)
            putExtra(EXTRA_LNG, latLng.longitude)
        })
    }
}