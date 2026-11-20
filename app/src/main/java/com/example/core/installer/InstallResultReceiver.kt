package com.example.core.installer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed interface InstallStatusEvent {
    data class UserConfirmationRequired(val confirmIntent: Intent) : InstallStatusEvent
    data class Success(val packageName: String?) : InstallStatusEvent
    data class Failure(
        val reason: String,
        val statusCode: Int = PackageInstaller.STATUS_FAILURE,
        val isConflictOrSignatureMismatch: Boolean = false
    ) : InstallStatusEvent
}

object InstallSessionBus {
    private val _events = MutableSharedFlow<InstallStatusEvent>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    fun emit(event: InstallStatusEvent) {
        _events.tryEmit(event)
    }
}

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val packageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                if (confirmIntent != null) {
                    confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    InstallSessionBus.emit(InstallStatusEvent.UserConfirmationRequired(confirmIntent))
                } else {
                    InstallSessionBus.emit(InstallStatusEvent.Failure("Missing system confirmation intent"))
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                InstallSessionBus.emit(InstallStatusEvent.Success(packageName))
            }
            else -> {
                val cleanReason = message?.takeIf { it.isNotBlank() } ?: "Install status code $status"
                val isConflict = status == PackageInstaller.STATUS_FAILURE_CONFLICT ||
                    cleanReason.contains("INCOMPATIBLE", ignoreCase = true) ||
                    cleanReason.contains("signature", ignoreCase = true) ||
                    cleanReason.contains("CONFLICT", ignoreCase = true) ||
                    cleanReason.contains("DUPLICATE", ignoreCase = true)
                InstallSessionBus.emit(
                    InstallStatusEvent.Failure(
                        reason = cleanReason,
                        statusCode = status,
                        isConflictOrSignatureMismatch = isConflict
                    )
                )
            }
        }
    }
}
