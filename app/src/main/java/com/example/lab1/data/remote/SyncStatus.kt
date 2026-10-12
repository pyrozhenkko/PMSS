package com.example.lab1.data.remote

/** Cloud synchronization state for the on-screen indicator. */
sealed interface SyncStatus {
    data object Idle : SyncStatus
    data object Syncing : SyncStatus
    data class Synced(val at: Long) : SyncStatus
    data class Pending(val count: Int) : SyncStatus
    data class Error(val message: String) : SyncStatus
}
