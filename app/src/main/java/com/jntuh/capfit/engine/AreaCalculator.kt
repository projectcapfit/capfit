package com.jntuh.capfit.engine

import android.util.Log
import com.jntuh.capfit.data.TrackPoint
import org.locationtech.jts.geom.*
import org.locationtech.jts.geom.Polygon
import kotlin.math.*

/**
 * AreaCalculator — Optimized
 *
 * Optimizations applied:
 *  O1. Collinear check during point collection (via shouldAddPoint) — reduces n before O(n²)
 *  O2. Early exit bounding box pre-check in isCollinear — skips cross product for obvious cases
 *  O3. Sweep line algorithm for self-intersection — O(n log n) instead of O(n²)
 *  O4. Background polygon drawing prep (extractBoundaryPoints is pure data, no UI)
 */
object AreaCalculator {

    private const val TAG = "AreaCalculator"
    private const val EARTH_RADIUS_M = 6_371_000.0

    /**
     * Collinearity tolerance in degree².
     * 1e-8 ≈ allows ~1-2m deviation from a straight line.
     */
    private const val COLLINEAR_TOLERANCE = 1e-8

    /**
     * Maximum distance in metres between path start and end to consider the loop closed.
     * 30m = comfortable GPS margin for a user who walks back near their start point.
     */
    private const val CLOSURE_THRESHOLD_M = 20.0

    /**
     * Early-exit threshold for collinear check.
     * If points are more than this far apart in BOTH axes,
     * they can't possibly be collinear — skip the cross product.
     * ~0.001 deg ≈ ~111m
     */
    private const val COLLINEAR_BBOX_THRESHOLD = 0.001

    private val geometryFactory = GeometryFactory(PrecisionModel(PrecisionModel.FLOATING), 4326)

    // ─── Result ───────────────────────────────────────────────────────────────

    data class CalculationResult(
        val totalDistanceM: Double,
        val areaM2: Double,
        val polygonPoints: List<TrackPoint>,
        val xMin: Double,
        val xMax: Double,
        val yMin: Double,
        val yMax: Double,
        val hasTerritory: Boolean
    )

    // ─── O1: Real-time Collinear Check (called from TrackingService) ──────────

    /**
     * Called by TrackingService on every incoming GPS point BEFORE adding to collectedPoints.
     *
     * Returns true if the new point should be added.
     * Returns false if the new point is collinear with the last two kept points
     * — in that case, TrackingService replaces the last point with the new one
     *   so the endpoint of the current straight segment stays fresh.
     *
     * This keeps collectedPoints small throughout the session,
     * making the final O(n²) / sweep line faster.
     *
     * Note: distance is still calculated on raw points BEFORE this filter.
     *
     * @param secondLast  second-to-last kept point (collectedPoints[size-2])
     * @param last        last kept point (collectedPoints[size-1])
     * @param newPoint    incoming candidate point
     */
    fun shouldAddPoint(
        secondLast: TrackPoint?,
        last: TrackPoint,
        newPoint: TrackPoint
    ): PointDecision {
        if (secondLast == null) return PointDecision.ADD  // only 1 point so far, always add

        return if (isCollinear(secondLast, last, newPoint)) {
            PointDecision.REPLACE_LAST  // new point extends the same straight line
        } else {
            PointDecision.ADD
        }
    }

    enum class PointDecision { ADD, REPLACE_LAST }

    // ─── Main Entry Point ─────────────────────────────────────────────────────

    /**
     * Called after workout ends with all collected points.
     * Points are already collinear-filtered by TrackingService via shouldAddPoint().
     * Distance is passed in separately since it was calculated on raw points.
     *
     * FIX (ordering bug): Previously tryCloseLoop ran FIRST, stripping the head/tail
     * before intersection detection. This was wrong for paths like figure-8s where the
     * crossing segments live in the trimmed head — after trimming, 0 intersections are
     * found and a concave star polygon is saved instead of the true enclosed territory.
     *
     * Correct order:
     *   1. Run intersection detection on the FULL path first.
     *   2. If crossings found → extract loops → build union. Done.
     *   3. Only if 0 crossings → fall back to tryCloseLoop for clean oval/rectangle walks.
     *   4. If still nothing → near-miss check on working points.
     */
    fun calculate(
        filteredPoints: List<TrackPoint>,
        totalDistanceM: Double
    ): CalculationResult {


        if (filteredPoints.size < 4) {
            return emptyResult(totalDistanceM)
        }

        // ── Step 1: intersection detection on the FULL original path ──────────
        // Must run before any head/tail trimming so crossing segments are not lost.
        val fullPathIntersections = findSelfIntersectionsSweep(filteredPoints)

        if (fullPathIntersections.isNotEmpty()) {
            // Path crosses itself — extract every enclosed loop and union them.
            // tryCloseLoop is NOT needed here: the crossing points already define closure.
            val loops = extractLoops(filteredPoints, fullPathIntersections)
            if (loops.isNotEmpty()) {
                val unionGeometry = buildUnion(loops)
                if (unionGeometry != null && !unionGeometry.isEmpty) {
                    val area = calculateAreaM2(unionGeometry)
                    val envelope = unionGeometry.envelopeInternal
                    val boundaryPoints = extractBoundaryPoints(unionGeometry)
                    return CalculationResult(
                        totalDistanceM = totalDistanceM,
                        areaM2 = area,
                        polygonPoints = boundaryPoints,
                        xMin = envelope.minX,
                        xMax = envelope.maxX,
                        yMin = envelope.minY,
                        yMax = envelope.maxY,
                        hasTerritory = area > 0.0
                    )
                }
            }
        }

        // ── Step 2: no crossings on full path → try clean loop closure ────────
        // EC7: handles oval/rectangle walks where path simply closes near start.
        // Head/tail trimming is safe here because we already confirmed no crossings exist.
        val workingPoints = tryCloseLoop(filteredPoints) ?: filteredPoints

        // ── Step 3: near-miss on working points ───────────────────────────────
        // EC8: collinear filter can collapse a physical crossing into two segments
        // that miss each other by a few metres. Check working points only (post-trim).
        val intersections = if (workingPoints !== filteredPoints) {
            // Working points are trimmed — re-run sweep on them too, then near-miss
            val trimmedIntersections = findSelfIntersectionsSweep(workingPoints)
            if (trimmedIntersections.isNotEmpty()) {
                trimmedIntersections
            } else {
                val nearMiss = findNearMissIntersections(workingPoints)
                nearMiss
            }
        } else {
            // Path never closed — near-miss is our last hope
            val nearMiss = findNearMissIntersections(workingPoints)
            nearMiss
        }

        val closedPoints = if (workingPoints !== filteredPoints) workingPoints else null

        val unionGeometry = if (intersections.isNotEmpty()) {
            // Trimmed path crosses itself (or near-miss) — extract loops
            val loops = extractLoops(workingPoints, intersections)
            if (loops.isEmpty()) return emptyResult(totalDistanceM)
            buildUnion(loops)
        } else if (closedPoints != null) {
            buildPolygonFromPoints(closedPoints)
        } else {
            return emptyResult(totalDistanceM)
        }

        if (unionGeometry == null || unionGeometry.isEmpty) return emptyResult(totalDistanceM)

        val area = calculateAreaM2(unionGeometry)
        val envelope = unionGeometry.envelopeInternal
        val boundaryPoints = extractBoundaryPoints(unionGeometry)

        return CalculationResult(
            totalDistanceM = totalDistanceM,
            areaM2 = area,
            polygonPoints = boundaryPoints,
            xMin = envelope.minX,
            xMax = envelope.maxX,
            yMin = envelope.minY,
            yMax = envelope.maxY,
            hasTerritory = area > 0.0
        )
    }

    // ─── O2: Collinear Check with Early Exit ──────────────────────────────────

    /**
     * Returns true if P2 lies nearly on the line P1→P3.
     *
     * O2 optimization: bounding box pre-check before cross product.
     * If P1 and P3 are far apart in BOTH lat AND lng,
     * P2 can't be near the line unless it's also in that region.
     * This skips the cross product for most non-collinear cases.
     */
    private fun isCollinear(p1: TrackPoint, p2: TrackPoint, p3: TrackPoint): Boolean {
        // O2: Early exit — if the span is large and p2 is clearly off-line, skip cross product
        val spanLat = abs(p3.lat - p1.lat)
        val spanLng = abs(p3.lng - p1.lng)

        if (spanLat > COLLINEAR_BBOX_THRESHOLD && spanLng > COLLINEAR_BBOX_THRESHOLD) {
            // Check if p2 is within the bounding box of p1-p3 (with tolerance)
            val minLat = min(p1.lat, p3.lat) - COLLINEAR_TOLERANCE
            val maxLat = max(p1.lat, p3.lat) + COLLINEAR_TOLERANCE
            val minLng = min(p1.lng, p3.lng) - COLLINEAR_TOLERANCE
            val maxLng = max(p1.lng, p3.lng) + COLLINEAR_TOLERANCE

            if (p2.lat < minLat || p2.lat > maxLat || p2.lng < minLng || p2.lng > maxLng) {
                return false  // p2 is outside bounding box → definitely not collinear
            }
        }

        // Full cross product check
        val cross = (p2.lng - p1.lng) * (p3.lat - p1.lat) -
                (p2.lat - p1.lat) * (p3.lng - p1.lng)
        return abs(cross) < COLLINEAR_TOLERANCE
    }

    // ─── O3: Sweep Line Self-Intersection ────────────────────────────────────

    data class Intersection(
        val point: Coordinate,
        val segI: Int,
        val segJ: Int
    )

    /**
     * Sweep Line Algorithm for self-intersection detection.
     * O(n log n) average case vs O(n²) brute force.
     *
     * Strategy:
     * 1. Create events for each segment's left (xMin) and right (xMax) endpoints
     * 2. Sort events by X coordinate
     * 3. Sweep left→right:
     *    - On segment START: add to active set, check against all active segments
     *    - On segment END: remove from active set
     * 4. Only segments that are "active" at the same X can possibly intersect
     *
     * This avoids comparing segments that are far apart in X — the main source
     * of wasted work in brute force for typical GPS paths (mostly left-to-right or
     * localized paths).
     */
    private fun findSelfIntersectionsSweep(points: List<TrackPoint>): List<Intersection> {
        val intersections = mutableListOf<Intersection>()
        val segments = points.size - 1

        // Build segment bounding boxes for sweep
        data class SegmentBounds(
            val index: Int,
            val xMin: Double, val xMax: Double,
            val yMin: Double, val yMax: Double
        )

        val bounds = (0 until segments).map { i ->
            SegmentBounds(
                index = i,
                xMin = min(points[i].lng, points[i + 1].lng),
                xMax = max(points[i].lng, points[i + 1].lng),
                yMin = min(points[i].lat, points[i + 1].lat),
                yMax = max(points[i].lat, points[i + 1].lat)
            )
        }

        // Sweep events: (xCoord, isStart, segmentIndex)
        data class Event(val x: Double, val isStart: Boolean, val segIndex: Int)

        val events = mutableListOf<Event>()
        for (b in bounds) {
            events.add(Event(b.xMin, true, b.index))
            events.add(Event(b.xMax, false, b.index))
        }
        events.sortWith(compareBy({ it.x }, { !it.isStart })) // starts before ends at same x

        val activeSegments = mutableSetOf<Int>()

        for (event in events) {
            val i = event.segIndex

            if (event.isStart) {
                // New segment enters sweep — check against all currently active segments
                for (j in activeSegments) {
                    // Skip adjacent segments (share a point)
                    if (abs(i - j) <= 1) continue
                    // Only skip first↔last if the path is actually closed (first point == last point)
                    // For open GPS paths, first and last segments can genuinely intersect
                    val pathIsClosed = points.first().lat == points.last().lat &&
                            points.first().lng == points.last().lng
                    if (pathIsClosed && i == 0 && j == segments - 1) continue
                    if (pathIsClosed && j == 0 && i == segments - 1) continue

                    // Quick Y bounding box overlap check before full intersection test
                    if (bounds[i].yMax < bounds[j].yMin || bounds[i].yMin > bounds[j].yMax) continue

                    val p1 = Coordinate(points[i].lng, points[i].lat)
                    val p2 = Coordinate(points[i + 1].lng, points[i + 1].lat)
                    val p3 = Coordinate(points[j].lng, points[j].lat)
                    val p4 = Coordinate(points[j + 1].lng, points[j + 1].lat)

                    segmentIntersection(p1, p2, p3, p4)?.let { intersectPt ->
                        // Store with smaller index first for consistent ordering
                        val (segI, segJ) = if (i < j) Pair(i, j) else Pair(j, i)
                        intersections.add(Intersection(intersectPt, segI, segJ))
                    }
                }
                activeSegments.add(i)

            } else {
                activeSegments.remove(i)
            }
        }

        // Remove duplicates (same pair found from both directions) and sort
        return intersections
            .distinctBy { "${min(it.segI, it.segJ)}_${max(it.segI, it.segJ)}" }
            .sortedBy { it.segI }
    }

    // ─── EC8: Near-Miss Intersection Detection ───────────────────────────────

    /**
     * EC8: Detects when two non-adjacent segments pass very close to each other
     * but don't mathematically intersect due to GPS collinear filtering.
     *
     * When the collinear filter collapses a walking turn into two long segments,
     * those segments may miss each other by just a few metres even though the
     * user physically crossed their own path.
     *
     * NEAR_MISS_THRESHOLD_M = 8m:
     * - GPS accuracy ~3-5m, so two segments 8m apart were likely walked through
     * - Large enough to catch filter artifacts, small enough to avoid false positives
     *
     * Returns synthetic Intersection objects snapped to the closest approach point.
     */
    private fun findNearMissIntersections(points: List<TrackPoint>): List<Intersection> {
        val result = mutableListOf<Intersection>()
        val n = points.size - 1  // number of segments

        for (i in 0 until n) {
            for (j in i + 2 until n) {
                // Only skip seg0 vs last segment if they SHARE a point
                // (i.e. path is already closed — first == last point).
                // If first != last, seg0 vs last segment is a valid near-miss candidate
                // e.g. path A→B→C→D→E where E is near segment A→B but doesn't cross it.
                val pathClosed = points.first().lat == points.last().lat &&
                        points.first().lng == points.last().lng
                if (i == 0 && j == n - 1 && pathClosed) continue

                val p1 = points[i];   val p2 = points[i + 1]
                val p3 = points[j];   val p4 = points[j + 1]

                // Quick bounding box check — skip if segments are far apart
                val iMinLat = min(p1.lat, p2.lat); val iMaxLat = max(p1.lat, p2.lat)
                val iMinLng = min(p1.lng, p2.lng); val iMaxLng = max(p1.lng, p2.lng)
                val jMinLat = min(p3.lat, p4.lat); val jMaxLat = max(p3.lat, p4.lat)
                val jMinLng = min(p3.lng, p4.lng); val jMaxLng = max(p3.lng, p4.lng)

                val NEAR_MISS_DEG = NEAR_MISS_THRESHOLD_M / 111_000.0
                if (iMaxLat + NEAR_MISS_DEG < jMinLat || iMinLat - NEAR_MISS_DEG > jMaxLat) continue
                if (iMaxLng + NEAR_MISS_DEG < jMinLng || iMinLng - NEAR_MISS_DEG > jMaxLng) continue

                // Find closest approach between the two segments
                val (closestI, closestJ, distM) = closestApproachBetweenSegments(p1, p2, p3, p4)
                if (distM <= NEAR_MISS_THRESHOLD_M) {
                    // Snap to midpoint of closest approach — this is our synthetic intersection
                    val snapLat = (closestI.lat + closestJ.lat) / 2.0
                    val snapLng = (closestI.lng + closestJ.lng) / 2.0
                    result.add(Intersection(Coordinate(snapLng, snapLat), i, j))
                }
            }
        }
        return result
    }

    private const val NEAR_MISS_THRESHOLD_M = 8.0

    /**
     * Returns the closest points on two line segments and the distance between them.
     * Uses parametric form: P = A + t*(B-A), Q = C + s*(D-C), t,s ∈ [0,1]
     */
    private fun closestApproachBetweenSegments(
        a: TrackPoint, b: TrackPoint,
        c: TrackPoint, d: TrackPoint
    ): Triple<TrackPoint, TrackPoint, Double> {
        val dx1 = b.lat - a.lat; val dy1 = b.lng - a.lng
        val dx2 = d.lat - c.lat; val dy2 = d.lng - c.lng
        val dx3 = a.lat - c.lat; val dy3 = a.lng - c.lng

        val d11 = dx1*dx1 + dy1*dy1
        val d22 = dx2*dx2 + dy2*dy2
        val d12 = dx1*dx2 + dy1*dy2
        val d13 = dx1*dx3 + dy1*dy3
        val d23 = dx2*dx3 + dy2*dy3

        val denom = d11*d22 - d12*d12
        var t = if (abs(denom) < 1e-12) 0.0 else (d12*d23 - d22*d13) / denom
        t = t.coerceIn(0.0, 1.0)
        var s = if (abs(d22) < 1e-12) 0.0 else (d12*t + d23) / d22
        s = s.coerceIn(0.0, 1.0)
        // Re-clamp t
        t = if (abs(d11) < 1e-12) 0.0 else (-(d13 + d12*s) / d11).coerceIn(0.0, 1.0)

        val closestOnI = TrackPoint(a.lat + t*dx1, a.lng + t*dy1, 0L)
        val closestOnJ = TrackPoint(c.lat + s*dx2, c.lng + s*dy2, 0L)
        val dist = haversineDistance(closestOnI.lat, closestOnI.lng, closestOnJ.lat, closestOnJ.lng)
        return Triple(closestOnI, closestOnJ, dist)
    }

    // ─── EC7: Near-Closure Detection ─────────────────────────────────────────

    /**
     * EC7: Detects when the path forms a clean loop (rectangle, oval, etc.)
     * without self-intersecting. This is the most natural way to walk a territory.
     *
     * Returns the points with the last point replaced/appended by the first point
     * (closing the polygon) if the path end is within CLOSURE_THRESHOLD_M of the start.
     * Returns null if the path doesn't close.
     *
     * CLOSURE_THRESHOLD_M = 20m:
     *  - GPS accuracy is ~2-5m, so 20m gives a comfortable margin
     *  - ~2-3 walking steps — user doesn't need to return to exact start
     *  - Tighter than 30m to avoid false closures on dense paths
     */
    private fun tryCloseLoop(points: List<TrackPoint>): List<TrackPoint>? {
        if (points.size < 4) return null

        // Scan all point pairs (j, i) where j > i + 2 to find any sub-path that closes.
        // This handles:
        //   - Simple loops:          A→B→C→D→A (first==last)
        //   - Loops with head tails: A→B→C→D→E where E is near B
        //   - Loops with both tails: A→B→C→D→E→F where C is near E (mid-path closure)
        //
        // Strategy: scan j from the END backwards, i from 0 up to j-2.
        // Take the LARGEST valid loop (prefer j as late as possible, i as early as possible).
        // Min loop size = 4 points (i, i+1, i+2, j) to form a valid polygon.

        var bestI = -1
        var bestJ = -1
        var bestLen = 0

        for (j in points.indices.reversed()) {
            val pj = points[j]
            for (i in 0..(j - 3)) {
                val pi = points[i]
                val dist = haversineDistance(pi.lat, pi.lng, pj.lat, pj.lng)
                if (dist <= CLOSURE_THRESHOLD_M) {
                    val loopLen = j - i
                    if (loopLen > bestLen) {
                        bestLen = loopLen
                        bestI = i
                        bestJ = j
                    }
                }
            }
            // Once we found a closure for this j, no need to scan further back
            if (bestJ == j) break
        }

        if (bestI == -1) {
            return null
        }

        val loopPoints = points.subList(bestI, bestJ + 1).toMutableList()

        // Snap last point to exact coordinates of first point to close ring cleanly
        loopPoints[loopPoints.size - 1] = TrackPoint(
            lat  = points[bestI].lat,
            lng  = points[bestI].lng,
            time = points[bestJ].time
        )
        return loopPoints
    }

    /**
     * Builds a polygon directly from an ordered list of points that already form a closed loop.
     * Used when tryCloseLoop() succeeds but there are no self-intersections.
     */
    private fun buildPolygonFromPoints(points: List<TrackPoint>): Geometry? {
        if (points.size < 3) return null
        return try {
            val coords = points.map { Coordinate(it.lng, it.lat) }.toMutableList()
            // Ensure ring is closed
            if (coords.first().x != coords.last().x || coords.first().y != coords.last().y) {
                coords.add(coords.first())
            }
            if (coords.size < 4) return null
            val ring = geometryFactory.createLinearRing(coords.toTypedArray())
            val polygon = geometryFactory.createPolygon(ring)
            if (polygon.isValid) polygon else polygon.buffer(0.0)
        } catch (e: Exception) {
            null
        }
    }

    private fun segmentIntersection(
        p1: Coordinate, p2: Coordinate,
        p3: Coordinate, p4: Coordinate
    ): Coordinate? {
        val d1x = p2.x - p1.x; val d1y = p2.y - p1.y
        val d2x = p4.x - p3.x; val d2y = p4.y - p3.y
        val cross = d1x * d2y - d1y * d2x
        if (abs(cross) < 1e-10) return null

        val dx = p3.x - p1.x; val dy = p3.y - p1.y
        val t = (dx * d2y - dy * d2x) / cross
        val u = (dx * d1y - dy * d1x) / cross

        if (t < 0.0 || t > 1.0 || u < 0.0 || u > 1.0) return null
        return Coordinate(p1.x + t * d1x, p1.y + t * d1y)
    }

    // ─── Extract Loops ────────────────────────────────────────────────────────

    private fun extractLoops(
        points: List<TrackPoint>,
        intersections: List<Intersection>
    ): List<Polygon> {
        val loops = mutableListOf<Polygon>()

        for (intersection in intersections) {
            val loopCoords = mutableListOf<Coordinate>()

            // Start at the intersection point (lies on segment segI, between segI and segI+1)
            loopCoords.add(intersection.point)

            // Add all full points between segI+1 and segJ (inclusive)
            // These are the GPS points that form the loop body
            for (k in (intersection.segI + 1)..intersection.segJ) {
                loopCoords.add(Coordinate(points[k].lng, points[k].lat))
            }

            // The intersection point also lies on segment segJ (between segJ and segJ+1).
            // We must explicitly add it here before closing the ring.
            // Without this, the ring jumps from points[segJ] directly back to
            // intersection.point — skipping the actual crossing location on segJ,
            // producing a self-intersecting ring that JTS marks as invalid.
            loopCoords.add(intersection.point)

            // Need at least 4 coords to form a valid ring (3 unique + closing repeat)
            if (loopCoords.size < 4) continue

            try {
                val coordArray = loopCoords.toTypedArray()
                val ring = geometryFactory.createLinearRing(coordArray)
                var polygon = geometryFactory.createPolygon(ring)

                // If JTS reports invalid (e.g. tiny self-touch), attempt repair via buffer(0)
                if (!polygon.isValid) {
                    val repaired = polygon.buffer(0.0)
                    if (repaired is Polygon && repaired.isValid && !repaired.isEmpty) {
                        polygon = repaired
                    } else {
                        continue
                    }
                }

                if (!polygon.isEmpty) {
                    loops.add(polygon)
                }
            } catch (e: Exception) {
            }
        }

        return loops
    }

    // ─── Build Union ──────────────────────────────────────────────────────────

    private fun buildUnion(loops: List<Polygon>): Geometry? {
        if (loops.isEmpty()) return null
        if (loops.size == 1) return loops[0]
        return try {
            var union: Geometry = loops[0]
            for (i in 1 until loops.size) union = union.union(loops[i])
            union
        } catch (e: Exception) {
            loops.maxByOrNull { it.area }
        }
    }

    // ─── Extract Boundary Points ──────────────────────────────────────────────

    /**
     * Converts a JTS Geometry into a flat list of TrackPoints for Firestore storage.
     *
     * Encoding:
     *   - Outer ring points: time = original GPS timestamp (or 0L for synthetic points)
     *   - Hole ring separator: time = -2L  (hole follows)
     *   - Polygon separator:   time = -1L  (next polygon in MultiPolygon)
     *
     * This lets MapsActivity correctly draw holes as cutouts rather than filled shapes.
     * Made public so TrackingService can use it for subtract/merge operations too.
     */
    fun extractBoundaryPoints(geometry: Geometry): List<TrackPoint> {
        return when (geometry) {
            is Polygon -> extractPolygonPoints(geometry)
            is GeometryCollection -> {
                val allPoints = mutableListOf<TrackPoint>()
                var first = true
                for (i in 0 until geometry.numGeometries) {
                    val piece = geometry.getGeometryN(i)
                    if (piece !is Polygon || piece.isEmpty) continue
                    if (!first) allPoints.add(TrackPoint(lat = 0.0, lng = 0.0, time = -1L))
                    allPoints.addAll(extractPolygonPoints(piece))
                    first = false
                }
                allPoints
            }
            else -> geometry.coordinates.map { TrackPoint(lat = it.y, lng = it.x, time = 0L) }
        }
    }

    /**
     * Extracts outer ring + all hole rings from a single Polygon.
     * Holes are prefixed with time=-2L separator so renderer knows to treat them as cutouts.
     */
    private fun extractPolygonPoints(polygon: Polygon): List<TrackPoint> {
        val points = mutableListOf<TrackPoint>()
        // Outer ring
        polygon.exteriorRing.coordinates.forEach {
            points.add(TrackPoint(lat = it.y, lng = it.x, time = 0L))
        }
        // Hole rings — each prefixed with -2L marker
        for (h in 0 until polygon.numInteriorRing) {
            points.add(TrackPoint(lat = 0.0, lng = 0.0, time = -2L))  // hole marker
            polygon.getInteriorRingN(h).coordinates.forEach {
                points.add(TrackPoint(lat = it.y, lng = it.x, time = 0L))
            }
        }
        return points
    }

    // ─── Area Calculation ─────────────────────────────────────────────────────

    fun calculateAreaM2(geometry: Geometry): Double {
        return when (geometry) {
            is Polygon -> polygonAreaM2(geometry)
            is GeometryCollection -> {
                var total = 0.0
                for (i in 0 until geometry.numGeometries) {
                    val g = geometry.getGeometryN(i)
                    if (g is Polygon) total += polygonAreaM2(g)
                }
                total
            }
            else -> 0.0
        }
    }

    private fun polygonAreaM2(polygon: Polygon): Double {
        // Exterior ring area minus all hole areas for correct net area
        val outerArea = ringAreaM2(polygon.exteriorRing.coordinates)
        var holeArea = 0.0
        for (h in 0 until polygon.numInteriorRing) {
            holeArea += ringAreaM2(polygon.getInteriorRingN(h).coordinates)
        }
        return (outerArea - holeArea).coerceAtLeast(0.0)
    }

    private fun ringAreaM2(coords: Array<Coordinate>): Double {
        if (coords.size < 3) return 0.0
        var area = 0.0
        for (i in coords.indices) {
            val j = (i + 1) % coords.size
            val lat1 = Math.toRadians(coords[i].y)
            val lat2 = Math.toRadians(coords[j].y)
            val dLng = Math.toRadians(coords[j].x - coords[i].x)
            area += dLng * (2 + sin(lat1) + sin(lat2))
        }
        return abs(area * EARTH_RADIUS_M * EARTH_RADIUS_M / 2)
    }

    // ─── Public Helpers ───────────────────────────────────────────────────────

    fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)
        return EARTH_RADIUS_M * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    /**
     * Reconstructs a JTS Polygon (with optional holes) from stored TrackPoints.
     *
     * Encoding (from extractBoundaryPoints):
     *   time >= 0  → outer ring coordinate
     *   time = -2L → start of a hole ring
     *   time = -1L → MultiPolygon separator (we only use first polygon here)
     *
     * If stored points contain holes (-2L markers), they are reconstructed
     * as interior rings so JTS operations (difference, union) work correctly.
     */
    fun buildPolygonFromStoredPoints(points: List<TrackPoint>): Polygon? {
        if (points.size < 3) return null
        return try {
            // Split into outer ring and hole rings by -2L/-1L markers
            val outerCoords = mutableListOf<Coordinate>()
            val holeRings   = mutableListOf<LinearRing>()
            var currentHole = mutableListOf<Coordinate>()
            var inHole = false

            for (pt in points) {
                when (pt.time) {
                    -1L -> break  // stop at MultiPolygon separator — use first polygon only
                    -2L -> {
                        // Save previous hole if any, start new hole
                        if (inHole && currentHole.size >= 3) {
                            if (currentHole.first() != currentHole.last()) currentHole.add(currentHole.first())
                            holeRings.add(geometryFactory.createLinearRing(currentHole.toTypedArray()))
                        }
                        currentHole = mutableListOf()
                        inHole = true
                    }
                    else -> {
                        val coord = Coordinate(pt.lng, pt.lat)
                        if (inHole) currentHole.add(coord) else outerCoords.add(coord)
                    }
                }
            }
            // Save last hole if any
            if (inHole && currentHole.size >= 3) {
                if (currentHole.first() != currentHole.last()) currentHole.add(currentHole.first())
                holeRings.add(geometryFactory.createLinearRing(currentHole.toTypedArray()))
            }

            if (outerCoords.size < 3) return null
            if (outerCoords.first() != outerCoords.last()) outerCoords.add(outerCoords.first())

            val shell = geometryFactory.createLinearRing(outerCoords.toTypedArray())
            val polygon = geometryFactory.createPolygon(shell, holeRings.toTypedArray())
            if (polygon.isValid) polygon else polygon.buffer(0.0) as? Polygon
        } catch (e: Exception) {
            null
        }
    }

    private fun emptyResult(distance: Double) = CalculationResult(
        totalDistanceM = distance,
        areaM2 = 0.0,
        polygonPoints = emptyList(),
        xMin = 0.0, xMax = 0.0, yMin = 0.0, yMax = 0.0,
        hasTerritory = false
    )
}