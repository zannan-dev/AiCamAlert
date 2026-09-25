package com.example.aicamalert

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aicamalert.location.CameraGeofenceManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RadarRestoreReceiverTest {
    // Isolate preferences from the installed app and keep radar disabled. A
    // direct receiver call would fail at goAsync() if it tried to restore anyway.
    private val context = object : ContextWrapper(
        InstrumentationRegistry.getInstrumentation().context,
    ) {
        override fun getApplicationContext(): Context = this
    }
    private val preferences = context.getSharedPreferences(
        CameraGeofenceManager.PREFERENCES_NAME, Context.MODE_PRIVATE,
    )

    @Before
    fun prepare() {
        preferences.edit().clear()
            .putBoolean("bg_radar_enabled", false)
            .putLong("snooze_until_timestamp", Long.MAX_VALUE)
            .putStringSet(CameraGeofenceManager.KEY_ACTIVE_CLUSTER_IDS, setOf("old-cluster"))
            .commit()
    }

    @Test
    fun rebootClearsStaleMembershipWithoutEnablingRadarOrClearingSnooze() {
        RadarRestoreReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertFalse(preferences.contains(CameraGeofenceManager.KEY_ACTIVE_CLUSTER_IDS))
        assertFalse(preferences.getBoolean("bg_radar_enabled", true))
        assertEquals(Long.MAX_VALUE, preferences.getLong("snooze_until_timestamp", 0))
    }

    @Test
    fun packageUpdateClearsStaleMembershipWithoutEnablingRadar() {
        RadarRestoreReceiver().onReceive(context, Intent(Intent.ACTION_MY_PACKAGE_REPLACED))
        assertFalse(preferences.contains(CameraGeofenceManager.KEY_ACTIVE_CLUSTER_IDS))
        assertFalse(preferences.getBoolean("bg_radar_enabled", true))
    }

    @Test
    fun unrelatedBroadcastDoesNotChangeMembership() {
        RadarRestoreReceiver().onReceive(context, Intent("unrelated"))
        assertEquals(setOf("old-cluster"), preferences.getStringSet(
            CameraGeofenceManager.KEY_ACTIVE_CLUSTER_IDS, emptySet(),
        ))
    }
}
