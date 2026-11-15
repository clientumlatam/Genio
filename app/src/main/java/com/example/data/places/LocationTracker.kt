package com.example.data.places

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.example.domain.model.GeoPointCoord
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class LocationTracker(context: Context) {
  private val fusedClient = LocationServices.getFusedLocationProviderClient(context.applicationContext)

  @SuppressLint("MissingPermission")
  suspend fun getCurrentLatLng(): GeoPointCoord? = suspendCancellableCoroutine { cont ->
    val cts = CancellationTokenSource()
    cont.invokeOnCancellation { cts.cancel() }

    fusedClient
      .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
      .addOnSuccessListener { location: Location? ->
        if (location != null) {
          cont.resume(GeoPointCoord(location.latitude, location.longitude))
        } else {
          fusedClient.lastLocation
            .addOnSuccessListener { last ->
              cont.resume(last?.let { GeoPointCoord(it.latitude, it.longitude) })
            }
            .addOnFailureListener { cont.resume(null) }
        }
      }
      .addOnFailureListener {
        fusedClient.lastLocation
          .addOnSuccessListener { last ->
            cont.resume(last?.let { GeoPointCoord(it.latitude, it.longitude) })
          }
          .addOnFailureListener { cont.resume(null) }
      }
  }
}
