package dev.munky.roguelike.common

import org.joml.Vector3d
import kotlin.math.sqrt

class IcoSphere(subdivisions: Int = 0) {
    val points = ArrayList<Vector3d>()
    private var index = 0
    private var faces = ArrayList<Face>()
    private val midpointCache = HashMap<Long, Int>()
    init {
        val t = GOLDEN_RATIO
        addVertex(-1.0, t,  0.0)
        addVertex(1.0,  t,  0.0)
        addVertex(-1.0, -t, 0.0)
        addVertex(1.0,  -t, 0.0)

        addVertex(0.0, -1.0, t)
        addVertex(0.0, 1.0,  t)
        addVertex(0.0, -1.0, -t)
        addVertex(0.0, 1.0,  -t)

        addVertex(t,  0.0, -1.0)
        addVertex(t,  0.0, 1.0)
        addVertex(-t, 0.0, -1.0)
        addVertex(-t, 0.0, 1.0)

        faces.add(Face(0, 11, 5))
        faces.add(Face(0, 5, 1))
        faces.add(Face(0, 1, 7))
        faces.add(Face(0, 7, 10))
        faces.add(Face(0, 10, 11))

        faces.add(Face(1, 5, 9))
        faces.add(Face(5, 11, 4))
        faces.add(Face(11, 10, 2))
        faces.add(Face(10, 7, 6))
        faces.add(Face(7, 1, 8))

        faces.add(Face(3, 9, 4))
        faces.add(Face(3, 4, 2))
        faces.add(Face(3, 2, 6))
        faces.add(Face(3, 6, 8))
        faces.add(Face(3, 8, 9))

        faces.add(Face(4, 9, 5))
        faces.add(Face(2, 4, 11))
        faces.add(Face(6, 2, 10))
        faces.add(Face(8, 6, 7))
        faces.add(Face(9, 8, 1))

        subdivide(subdivisions)
    }
    fun mul(r: Double) {
        for (p in points) {
            p.x *= r
            p.y *= r
            p.z *= r
        }
    }
    fun div(r: Double) {
        for (p in points) {
            p.x /= r
            p.y /= r
            p.z /= r
        }
    }
    private fun subdivide(level: Int) {
        repeat(level) {
            val new = ArrayList<Face>()
            for (face in faces) {
                // replace triangle by 4 triangles
                val a = getMiddlePoint(face.a, face.b)
                val b = getMiddlePoint(face.b, face.c)
                val c = getMiddlePoint(face.c, face.a)

                new.add(Face(face.a, a, c))
                new.add(Face(face.b, b, a))
                new.add(Face(face.c, c, b))
                new.add(Face(a, b, c))
            }
            faces = new
        }
    }
    private fun getMiddlePoint(p1: Int, p2: Int) : Int {
        // first check if we have it already
        val firstIsSmaller = p1 < p2
        val smallerIndex = (if (firstIsSmaller) p1 else p2).toLong()
        val greaterIndex = (if (firstIsSmaller) p2 else p1).toLong()
        val key = smallerIndex.shl(32) + greaterIndex

        midpointCache.getOrDefault(key, null)?.let { return it }

        // not in cache, calculate it
        val point1 = points[p1]
        val point2 = points[p2]
        val i = addVertex((point1.x + point2.x) / 2.0, (point1.y + point2.y) / 2.0, (point1.z + point2.z) / 2.0)

        // store it, return index
        midpointCache[key] = i
        return i
    }
    private fun addVertex(x: Double, y: Double, z: Double) : Int {
        val length = sqrt(x * x + y * y + z * z)
        points.add(Vector3d(x / length, y / length, z / length))
        return index++
    }
    data class Face(val a: Int, val b: Int, val c: Int)
    companion object {
        val GOLDEN_RATIO = (1 + sqrt(5.0)) / 2
    }
}