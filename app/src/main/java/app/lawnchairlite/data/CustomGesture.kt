package app.lawnchairlite.data

import kotlin.math.atan2
import kotlin.math.hypot

data class CustomGesturePoint(val x: Float, val y: Float)

fun encodeCustomGesture(
    points: List<CustomGesturePoint>,
    minSegmentPx: Float = 24f,
    minTravelPx: Float = 96f,
): String {
    if (points.size < 4) return ""
    var totalTravel = 0f
    val directions = buildString {
        var anchor = points.first()
        var previous = points.first()
        points.drop(1).forEach { point ->
            totalTravel += hypot(point.x - previous.x, point.y - previous.y)
            previous = point
            val dx = point.x - anchor.x
            val dy = point.y - anchor.y
            val distance = hypot(dx, dy)
            if (distance >= minSegmentPx) {
                val direction = gestureDirection(dx, dy)
                if (lastOrNull() != direction) append(direction)
                anchor = point
            }
        }
    }
    if (totalTravel < minTravelPx || directions.length < 2) return ""
    return directions.take(24)
}

fun customGestureMatches(recordedPattern: String, points: List<CustomGesturePoint>): Boolean =
    customGesturePatternMatches(recordedPattern, encodeCustomGesture(points))

fun customGesturePatternMatches(recordedPattern: String, candidatePattern: String): Boolean {
    val expected = recordedPattern.trim()
    val candidate = candidatePattern.trim()
    if (expected.isBlank() || candidate.isBlank()) return false
    if (expected == candidate) return true
    val distance = levenshtein(expected, candidate)
    val tolerance = (maxOf(expected.length, candidate.length) / 3).coerceAtLeast(1)
    return distance <= tolerance
}

fun sanitizeCustomGesturePattern(raw: String): String =
    raw.filter { it in '0'..'7' }.take(24)

private fun gestureDirection(dx: Float, dy: Float): Char {
    val degrees = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble()))
    val normalized = (degrees + 360.0 + 22.5) % 360.0
    val sector = (normalized / 45.0).toInt()
    return "01234567"[sector]
}

private fun levenshtein(left: String, right: String): Int {
    if (left == right) return 0
    if (left.isEmpty()) return right.length
    if (right.isEmpty()) return left.length
    var previous = IntArray(right.length + 1) { it }
    var current = IntArray(right.length + 1)
    for (i in left.indices) {
        current[0] = i + 1
        for (j in right.indices) {
            val cost = if (left[i] == right[j]) 0 else 1
            current[j + 1] = minOf(
                current[j] + 1,
                previous[j + 1] + 1,
                previous[j] + cost,
            )
        }
        val swap = previous
        previous = current
        current = swap
    }
    return previous[right.length]
}
