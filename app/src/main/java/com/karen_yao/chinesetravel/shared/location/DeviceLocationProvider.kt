package com.karen_yao.chinesetravel.shared.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Obtains a recent device location without accepting an indefinitely stale cached fix. */
object DeviceLocationProvider {

    sealed interface Result {
        data class Success(
            val location: Location,
            val isMock: Boolean
        ) : Result

        data class Unavailable(val reason: FailureReason) : Result
    }

    enum class FailureReason {
        PERMISSION_DENIED,
        LOCATION_DISABLED,
        TIMED_OUT,
        INVALID_OR_STALE,
        REQUEST_FAILED
    }

    fun hasLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(context: Context): Result {
        val applicationContext = context.applicationContext
        if (!hasLocationPermission(applicationContext)) {
            return Result.Unavailable(FailureReason.PERMISSION_DENIED)
        }

        val locationManager =
            applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!locationManager.isLocationEnabled) {
            return Result.Unavailable(FailureReason.LOCATION_DISABLED)
        }

        return withTimeoutOrNull(LOCATION_REQUEST_TIMEOUT_MS) {
            requestLocation(applicationContext)
        } ?: Result.Unavailable(FailureReason.TIMED_OUT)
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestLocation(context: Context): Result =
        suspendCancellableCoroutine { continuation ->
            val hasFineLocation = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val request = CurrentLocationRequest.Builder()
                .setPriority(
                    if (hasFineLocation) {
                        Priority.PRIORITY_HIGH_ACCURACY
                    } else {
                        Priority.PRIORITY_BALANCED_POWER_ACCURACY
                    }
                )
                .setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
                .setDurationMillis(LOCATION_REQUEST_TIMEOUT_MS)
                .setMaxUpdateAgeMillis(MAX_LOCATION_AGE_MS)
                .build()
            val cancellationSource = CancellationTokenSource()

            continuation.invokeOnCancellation { cancellationSource.cancel() }
            LocationServices.getFusedLocationProviderClient(context)
                .getCurrentLocation(request, cancellationSource.token)
                .addOnSuccessListener { location ->
                    if (!continuation.isActive) return@addOnSuccessListener
                    continuation.resume(
                        if (location != null && isUsable(location)) {
                            Result.Success(location, isSimulated(location))
                        } else {
                            Result.Unavailable(FailureReason.INVALID_OR_STALE)
                        }
                    )
                }
                .addOnFailureListener {
                    if (continuation.isActive) {
                        continuation.resume(Result.Unavailable(FailureReason.REQUEST_FAILED))
                    }
                }
                .addOnCanceledListener {
                    if (continuation.isActive) {
                        continuation.resume(Result.Unavailable(FailureReason.REQUEST_FAILED))
                    }
                }
        }

    private fun isSimulated(location: Location): Boolean =
        LocationCompat.isMock(location) || isEmulatorEnvironment()

    private fun isEmulatorEnvironment(): Boolean =
        Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.contains("emulator", ignoreCase = true) ||
            Build.MODEL.contains("Emulator", ignoreCase = true) ||
            Build.MODEL.contains("Android SDK built for", ignoreCase = true) ||
            Build.PRODUCT.contains("sdk", ignoreCase = true)

    private fun isUsable(location: Location): Boolean {
        val latitude = location.latitude
        val longitude = location.longitude
        val ageNanos = SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos

        return latitude.isFinite() && longitude.isFinite() &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0 &&
            ageNanos >= 0L && ageNanos <= MAX_LOCATION_AGE_NANOS
    }

    private const val LOCATION_REQUEST_TIMEOUT_MS = 5_000L
    private const val MAX_LOCATION_AGE_MS = 30_000L
    private const val MAX_LOCATION_AGE_NANOS = MAX_LOCATION_AGE_MS * 1_000_000L
}
