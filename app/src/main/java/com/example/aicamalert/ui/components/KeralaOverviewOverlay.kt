package com.example.aicamalert.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

/** Small bundled boundary map for zoomed-out views; never fetches tiles. */
internal class KeralaOverviewOverlay(context: Context) : Overlay() {
    companion object {
        const val MAX_ZOOM = 10.0
    }

    private data class Region(val name: String, val polygons: List<List<List<GeoPoint>>>)

    private val regions: List<Region> = context.assets.open("kerala_overview.geojson").use {
        val features = JSONObject(it.bufferedReader().readText()).getJSONArray("features")
        List(features.length()) { index ->
            val feature = features.getJSONObject(index)
            val polygons = feature.getJSONObject("geometry").getJSONArray("coordinates")
            Region(feature.getJSONObject("properties").getString("name"),
                List(polygons.length()) { polygonIndex ->
                    val rings = polygons.getJSONArray(polygonIndex)
                    List(rings.length()) { ringIndex ->
                        val coordinates = rings.getJSONArray(ringIndex)
                        List(coordinates.length()) { pointIndex ->
                            val coordinate = coordinates.getJSONArray(pointIndex)
                            GeoPoint(coordinate.getDouble(1), coordinate.getDouble(0))
                        }
                    }
                })
        }
    }

    var darkTheme = true
    private val density = context.resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val point = Point()
    // Draw labels after camera markers so dense clusters cannot cover place names.
    val labelsOverlay = object : Overlay() {
        override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
            if (!shadow && mapView.zoomLevelDouble <= MAX_ZOOM) drawLabels(canvas, mapView)
        }
    }
    // Approximate district headquarters, sufficient for an overview.
    private val places = listOf(
        "Thiruvananthapuram" to GeoPoint(8.5241, 76.9366),
        "Kollam" to GeoPoint(8.8932, 76.6141),
        "Pathanamthitta" to GeoPoint(9.2648, 76.7870),
        "Alappuzha" to GeoPoint(9.4981, 76.3388),
        "Kottayam" to GeoPoint(9.5916, 76.5222),
        "Kochi" to GeoPoint(9.9312, 76.2673),
        "Painavu" to GeoPoint(9.8500, 76.9700),
        "Thrissur" to GeoPoint(10.5276, 76.2144),
        "Palakkad" to GeoPoint(10.7867, 76.6548),
        "Malappuram" to GeoPoint(11.0510, 76.0711),
        "Kozhikode" to GeoPoint(11.2588, 75.7804),
        "Kalpetta" to GeoPoint(11.6103, 76.0828),
        "Kannur" to GeoPoint(11.8745, 75.3704),
        "Kasaragod" to GeoPoint(12.4996, 74.9869)
    )

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow || mapView.zoomLevelDouble > MAX_ZOOM) return
        canvas.drawColor(if (darkTheme) Color.rgb(15, 28, 40) else Color.rgb(220, 238, 246))
        val projection = mapView.projection
        regions.forEach { region ->
            path.reset()
            path.fillType = Path.FillType.EVEN_ODD
            region.polygons.forEach { rings ->
                rings.forEach { ring ->
                    ring.forEachIndexed { index, location ->
                        projection.toPixels(location, point)
                        if (index == 0) path.moveTo(point.x.toFloat(), point.y.toFloat())
                        else path.lineTo(point.x.toFloat(), point.y.toFloat())
                    }
                    path.close()
                }
            }
            paint.style = Paint.Style.FILL
            paint.color = if (region.name == "Kerala") {
                if (darkTheme) Color.rgb(40, 78, 64) else Color.rgb(201, 226, 195)
            } else {
                if (darkTheme) Color.rgb(39, 46, 46) else Color.rgb(233, 232, 220)
            }
            canvas.drawPath(path, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = density
            paint.color = if (darkTheme) Color.rgb(116, 147, 127) else Color.rgb(113, 145, 111)
            canvas.drawPath(path, paint)
        }
    }

    private fun drawLabels(canvas: Canvas, mapView: MapView) {
        val projection = mapView.projection
        // Avoid overlapping city labels at statewide zooms.
        val labels = if (mapView.zoomLevelDouble < 8.5) {
            places.filter { it.first in setOf("Thiruvananthapuram", "Kochi", "Kozhikode", "Kasaragod") }
        } else places
        paint.textSize = 11 * density
        labels.forEach { (name, location) ->
            projection.toPixels(location, point)
            paint.style = Paint.Style.FILL
            paint.color = if (darkTheme) Color.WHITE else Color.rgb(30, 55, 40)
            canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), 2 * density, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3 * density
            paint.color = if (darkTheme) Color.rgb(15, 28, 40) else Color.rgb(240, 245, 235)
            canvas.drawText(name, point.x + 5 * density, point.y - 4 * density, paint)
            paint.style = Paint.Style.FILL
            paint.color = if (darkTheme) Color.WHITE else Color.rgb(30, 55, 40)
            canvas.drawText(name, point.x + 5 * density, point.y - 4 * density, paint)
        }
    }
}
