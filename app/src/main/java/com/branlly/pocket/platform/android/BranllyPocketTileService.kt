package com.branlly.pocket.platform.android

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.branlly.pocket.MainActivity

/** Opens Branlly Pocket from Android quick settings when no favorite routine is configured. */
class BranllyPocketTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_ACTIVE
            label = "Branlly Pocket"
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
