package com.example.aicamalert.ui.components

import org.osmdroid.tileprovider.tilesource.TileSourcePolicy
import org.osmdroid.tileprovider.tilesource.XYTileSource

/**
 * OSMdroid's MAPNIK source forces a normalized package-name User-Agent.
 * Our application ID is still com.example.*, so use a source that sends the
 * identifiable app User-Agent configured in MainActivity instead.
 * The new source name also separates old 403 images from valid cached tiles.
 */
internal val aiCamTileSource = XYTileSource(
    "AiCamOSM",
    0,
    19,
    256,
    ".png",
    arrayOf("https://tile.openstreetmap.org/"),
    "© OpenStreetMap contributors",
    TileSourcePolicy(
        2,
        TileSourcePolicy.FLAG_NO_BULK or
            TileSourcePolicy.FLAG_NO_PREVENTIVE or
            TileSourcePolicy.FLAG_USER_AGENT_MEANINGFUL,
    ),
)
