package com.example.fitsync.data.repository

import android.Manifest.*
import android.Manifest.permission.*
import android.app.Activity
import android.content.Context
import android.location.Location
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.location.LocationManagerCompat.requestLocationUpdates
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng

private var FitnessRepository.trackingStartTime: Any
private var FitnessRepository.totalDuration: Any

class FitnessRepository(private val context: Context) {
    private val locationClient= LocationServices.getFusedLocationProviderClient(context)
    private var lastValidLocation: Location? = null
    private var isActuallyMoving: Boolean = false
    private var duration:Long=0
    private var MOVEMENT_THRESHOLD=1.0f
    private val _routePoints= MutableLiveData<List<LatLng>>(emptyList())
    private val routePoints:LiveData<List<LatLng>> =_routePoints
    private val _totalDistance=MutableLiveData<Float>(0f)
    private val totalDistance:LiveData<Float> =_totalDistance
    private val _isTracking=MutableLiveData<Boolean>(false)
    private val isTracking:LiveData<Boolean> =_isTracking
    private val _currentLocation=MutableLiveData<LatLng?>(null)
    private val currentLocation:LiveData<LatLng?> =_currentLocation
    private var startTime:Long=0
    private var activeMovementTime:Long=0
    private var lastMovementTime:Long=0
    private var lastLocationTime:Long=0
    private var lastLocationUpdateTime:Long=0

    private val locationCallback=object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { newLocation ->
                Log.d(
                    "FitnessRepository",
                    "New Location Recieved: ${newLocation.latitude},${newLocation.longitude}"
                )
                val currentTime: Long = System.currentTimeMillis()
                _currentLocation.postValue(LatLng(newLocation.latitude, newLocation.longitude))
                if (newLocation.accuracy > 20f) {
                    Log.d("FitnessRepository", "Location Accuracy too low")
                    isActuallyMoving = false
                }
                lastValidLocation?.let { lastLocation ->
                    val distance: Float = newLocation.distanceTo(lastLocation)
                    val timeGap: Long = currentTime - lastLocationUpdateTime
                    val speed: Float = if (timeGap > 0) (distance * 1000) / timeGap else 0f
                    isActuallyMoving = distance > MOVEMENT_THRESHOLD &&
                            speed < 8f
                    speed > 0.3f
                    if (isActuallyMoving) {
                        updateMovementTime(currentTime)
                        updateLocationData(newLocation)
                        Log.d(
                            "FitnessRepository",
                            "Valid Movement:$distance meters ,Speed:$speed m/s"
                        )
                    }

                } ?: run {
                    lastValidLocation = newLocation
                    isActuallyMoving = false
                }
                lastLocationUpdateTime = currentTime
            }
        }


    }
    private fun updateMovementTime(currentTime: Long) {
if(lastMovementTime>0){
    activeMovementTime+=currentTime-lastMovementTime
}
            lastMovementTime=currentTime

        }
        fun getCurrentDuration():Long{
            return if(_isTracking.value==true){
                System.currentTimeMillis()-startTime
            } else {
                duration
            }
        }


        private fun updateLocationData(newLocation: Location) {
                if (!isActuallyMoving){
                    Log.d("Fitness Ripository","Not Actually Moving")
                    return
                }
            val latling= LatLng(newLocation.latitude,newLocation.longitude)
            val currentPoints:MutableList<LatLng> =_routePoints.value?.toMutableList()?:mutableListOf()

            if (currentPoints.isEmpty()){
                currentPoints.add(latling)
                _routePoints.postValue(currentPoints)
                Log.d("FitnessRepository","Route Point Added")
                return
            }
            val distanceFromLastPoint=calculateDistance(currentPoints.last(),latling)
            if (distanceFromLastPoint>MOVEMENT_THRESHOLD/1000f){
                currentPoints.add(latling)
                _routePoints.postValue(currentPoints)
                val currentTotal:Float=_totalDistance.value?:0f
                val newTotal=currentTotal+distanceFromLastPoint
                _totalDistance.postValue(newTotal)
            }

            lastValidLocation=newLocation

        }
        private fun initializeTracking(){
            startTime=System.currentTimeMillis()
            val trackingStartTime= System.currentTimeMillis()
            _isTracking.postValue(true)
    }
        private fun requestLocationUpdates() {
            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, // priority
                1000L // interval in milliseconds
            ).apply {
                setMinUpdateIntervalMillis(500L)      // fastest interval
                setMinUpdateDistanceMeters(1f)        // smallest displacement
            }.build()
            if (checkLocationPermission()) {
                try {
                    locationClient.requestLocationUpdates(
                        locationRequest,
                        locationCallback,
                        context.mainLooper
                    )
                }catch (e: SecurityException){
                    Toast.makeText(context,"Location Permission not granted",Toast.LENGTH_SHORT).show()
                }
            }
        }

        private fun checkLocationPermission(): Boolean {
            return (ActivityCompat.checkSelfPermission(
                context,
                ACCESS_FINE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED) &&
                    (ActivityCompat.checkSelfPermission(
                        context,
                        ACCESS_COARSE_LOCATION
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED)


        }


private fun calculateDistance(last: LatLng, latling: LatLng, ): Float {
    val results=FloatArray(1)
    Location.distanceBetween(last.latitude,last.longitude,latling.latitude,latling.longitude,results)
    return results[0]

}
    private fun calculateCalories(weight: Float,distance: Float,duration: Float): Float{
        if(duration<1000){
            return 0f
        }
        val hours= duration/3600000
        if(hours<=0){
            return 0f
        }
        val speed: Float =if(hours>0)distance/hours else 0f
        val met:Int= when{
            speed <=4.0 ->2
            speed <=8.0 ->7
            speed <=11.0 ->8.5
            else ->10
        } as Int
        val calories=(met*weight*hours).toFloat()
        return calories
    }
private fun calculateAveragePace(distance: Float,duration: Long):Double{
    if(duration<1000||distance<=0){
        return 0.0
    }
    val hours:Double=duration/3600000.0
    return distance/hours
}
    private fun startTracking(){
        if(checkLocationPermission()){
            initializeTracking()
            requestLocationUpdates()
        }
    }
    fun stopTracking() {
        _isTracking.postValue( false)
        locationClient.removeLocationUpdates(locationCallback)
        totalDuration = System.currentTimeMillis() - trackingStartTime
        resetTimers()
    }

    private fun resetTimers() {
        lastMovementTime = 0
        activeMovementTime = 0
        trackingStartTime = 0
        totalDuration = 0
    }

    fun clearTracking() {
        _routePoints.postValue(emptyList())
        _totalDistance.postValue( 0f)
        _isTracking.postValue( false)
        startTime = 0
        resetTimers()
        lastValidLocation = null
        isActuallyMoving = false
    }

}