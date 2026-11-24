package dev.axkit.core

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class GraphPoint(val x: Double, val y: Double)
data class GraphPoint3(val x: Double, val y: Double, val z: Double)

/** Force-directed placement for the document "universe". Deterministic, so the map looks the same every time. Port of GraphLayout.swift. */
object GraphLayout {
    /** FNV-1a; same numbers as the iOS app. */
    fun stableHash(text: String): Long {
        var hash = -0x340d631b7bdddcdbL // 0xcbf29ce484222325
        for (byte in text.toByteArray(Charsets.UTF_8)) {
            hash = (hash xor (byte.toLong() and 0xff)) * 0x100000001b3L
        }
        return java.lang.Long.remainderUnsigned(hash, Int.MAX_VALUE.toLong())
    }

    fun layout(nodes: List<String>, links: List<Pair<String, String>>, groups: Map<String, String> = emptyMap(), size: Double = 1000.0, iterations: Int = 300): Map<String, GraphPoint> {
        if (nodes.isEmpty()) return emptyMap()
        val index = nodes.withIndex().associate { it.value to it.index }
        val edges = links.mapNotNull { (a, b) -> val i = index[a]; val j = index[b]; if (i != null && j != null && i != j) i to j else null }
        val groupNames = groups.values.toSet().sorted()
        val n = nodes.size
        val xs = DoubleArray(n); val ys = DoubleArray(n)
        nodes.forEachIndexed { i, id ->
            val h = stableHash(id)
            var angle = (h % 3600).toDouble() / 3600 * 2 * Math.PI
            val g = groups[id]; val gi = g?.let { groupNames.indexOf(it) } ?: -1
            if (gi >= 0 && groupNames.size > 1) {
                val sector = 2 * Math.PI / groupNames.size
                angle = gi * sector + ((h % 1000).toDouble() / 1000) * sector * 0.8
            }
            val radius = size * (0.15 + ((h / 3600) % 1000).toDouble() / 1000 * 0.3)
            xs[i] = cos(angle) * radius; ys[i] = sin(angle) * radius
        }
        val k = size / sqrt(n.toDouble()) * 0.9
        var temperature = size / 10
        repeat(iterations) {
            val dx = DoubleArray(n); val dy = DoubleArray(n)
            for (i in 0 until n) for (j in i + 1 until n) {
                var ddx = xs[i] - xs[j]; var ddy = ys[i] - ys[j]
                var dist = sqrt(ddx * ddx + ddy * ddy)
                if (dist < 0.01) { ddx = 0.01; ddy = 0.0; dist = 0.01 }
                val force = k * k / dist
                dx[i] += ddx / dist * force; dy[i] += ddy / dist * force
                dx[j] -= ddx / dist * force; dy[j] -= ddy / dist * force
            }
            for ((i, j) in edges) {
                val ddx = xs[i] - xs[j]; val ddy = ys[i] - ys[j]
                val dist = maxOf(sqrt(ddx * ddx + ddy * ddy), 0.01)
                val force = dist * dist / k
                dx[i] -= ddx / dist * force; dy[i] -= ddy / dist * force
                dx[j] += ddx / dist * force; dy[j] += ddy / dist * force
            }
            for (i in 0 until n) {
                dx[i] -= xs[i] * 0.02; dy[i] -= ys[i] * 0.02
                val len = maxOf(sqrt(dx[i] * dx[i] + dy[i] * dy[i]), 0.01)
                xs[i] += dx[i] / len * minOf(len, temperature)
                ys[i] += dy[i] / len * minOf(len, temperature)
            }
            temperature = maxOf(temperature * 0.97, 0.5)
        }
        val scaleX = (size / 2) / maxOf(xs.maxOf { abs(it) }, 1.0)
        val scaleY = (size / 2) / maxOf(ys.maxOf { abs(it) }, 1.0)
        return nodes.withIndex().associate { (i, id) -> id to GraphPoint(xs[i] * scaleX, ys[i] * scaleY) }
    }

    /** The same force layout in three dimensions, for the 3D document universe. Fits in a sphere of radius `size / 2`. */
    fun layout3D(nodes: List<String>, links: List<Pair<String, String>>, groups: Map<String, String> = emptyMap(), size: Double = 1000.0, iterations: Int = 260): Map<String, GraphPoint3> {
        if (nodes.isEmpty()) return emptyMap()
        val index = nodes.withIndex().associate { it.value to it.index }
        val edges = links.mapNotNull { (a, b) -> val i = index[a]; val j = index[b]; if (i != null && j != null && i != j) i to j else null }
        val groupNames = groups.values.toSet().sorted()
        val n = nodes.size
        val p = Array(n) { DoubleArray(3) }
        nodes.forEachIndexed { i, id ->
            val h = stableHash(id)
            var theta = (h % 3600).toDouble() / 3600 * 2 * Math.PI
            var phi = acos(1 - 2 * ((h / 3600) % 1000).toDouble() / 1000)
            val gi = groups[id]?.let { groupNames.indexOf(it) } ?: -1
            if (gi >= 0 && groupNames.size > 1) {
                val kk = gi + 0.5
                phi = acos(1 - 2 * kk / groupNames.size) + ((h % 100).toDouble() / 100 - 0.5) * 0.6
                theta = gi * Math.PI * (3 - sqrt(5.0)) + (((h / 100) % 100).toDouble() / 100 - 0.5) * 0.9
            }
            val r = size * (0.25 + ((h / 7) % 100).toDouble() / 100 * 0.2)
            p[i][0] = r * sin(phi) * cos(theta); p[i][1] = r * sin(phi) * sin(theta); p[i][2] = r * cos(phi)
        }
        val k = size / cbrt(n.toDouble()) * 0.55
        var temperature = size / 10
        repeat(iterations) {
            val d = Array(n) { DoubleArray(3) }
            for (i in 0 until n) for (j in i + 1 until n) {
                var v = doubleArrayOf(p[i][0] - p[j][0], p[i][1] - p[j][1], p[i][2] - p[j][2])
                var dist = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
                if (dist < 0.01) { v = doubleArrayOf(0.01, 0.0, 0.0); dist = 0.01 }
                val f = k * k / dist
                for (a in 0..2) { d[i][a] += v[a] / dist * f; d[j][a] -= v[a] / dist * f }
            }
            for ((i, j) in edges) {
                val v = doubleArrayOf(p[i][0] - p[j][0], p[i][1] - p[j][1], p[i][2] - p[j][2])
                val dist = maxOf(sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]), 0.01)
                val f = dist * dist / k
                for (a in 0..2) { d[i][a] -= v[a] / dist * f; d[j][a] += v[a] / dist * f }
            }
            for (i in 0 until n) {
                for (a in 0..2) d[i][a] -= p[i][a] * 0.03
                val len = maxOf(sqrt(d[i][0] * d[i][0] + d[i][1] * d[i][1] + d[i][2] * d[i][2]), 0.01)
                for (a in 0..2) p[i][a] += d[i][a] / len * minOf(len, temperature)
            }
            temperature = maxOf(temperature * 0.97, 0.5)
        }
        val c = DoubleArray(3)
        for (q in p) for (a in 0..2) c[a] += q[a] / n
        val q = Array(n) { i -> DoubleArray(3) { a -> p[i][a] - c[a] } }
        for (a in 0..2) {
            val extent = maxOf(q.maxOf { abs(it[a]) }, 1.0)
            for (i in 0 until n) q[i][a] = q[i][a] / extent * (size / 2)
        }
        val radius = maxOf(q.maxOf { sqrt(it[0] * it[0] + it[1] * it[1] + it[2] * it[2]) }, 1.0)
        val s = (size / 2) / radius
        return nodes.withIndex().associate { (i, id) -> id to GraphPoint3(q[i][0] * s, q[i][1] * s, q[i][2] * s) }
    }
}
