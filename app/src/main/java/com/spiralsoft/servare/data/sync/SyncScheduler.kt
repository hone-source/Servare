package com.spiralsoft.servare.data.sync

import android.util.Log
import javax.inject.Inject

interface SyncScheduler { fun requestSync() }

class PlaceholderSyncScheduler @Inject constructor() : SyncScheduler {
    override fun requestSync() { Log.d("Sync", "requestSync() solicitado (placeholder)") }
}
