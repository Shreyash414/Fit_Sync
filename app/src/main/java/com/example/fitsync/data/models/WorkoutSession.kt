package com.example.fitsync.data.models

import com.google.android.gms.maps.model.LatLng

data class WorkoutSession(
    val id: String,
    val distance: Double,
    val duraration: Long,
    val caloriesBurned: Float,
    val routePoints:List<LatLng>,
    val averagePace: Double,
val timestamp: Long
)