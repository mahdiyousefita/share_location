package com.example.ui

import android.annotation.SuppressLint
import android.app.Application
import android.location.Location
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.LocationDatabase
import com.example.data.LocationRepository
import com.example.data.ShareHistoryEntry
import com.google.android.gms.location.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.*

data class Contact(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val speedKmh: Float,
    val bearing: Float,
    val batteryPercent: Int,
    val isSharing: Boolean = true,
    val statusText: String = "Active",
    val calculatedDistanceMeters: Double = 0.0,
    val isGeofenceBreached: Boolean = false
)

data class GPSState(
    val latitude: Double = 51.5074, // Default London
    val longitude: Double = -0.1278,
    val altitude: Double = 35.0,
    val speedMps: Float = 0f,
    val bearingDegrees: Float = 0f,
    val accuracyMeters: Float = 5f,
    val satCount: Int = 12,
    val isDeviceGpsActive: Boolean = false,
    val provider: String = "Simulated Core"
)

class LocationViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: LocationRepository
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(application)

    // Location request setup
    private val locationRequest = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY, 3000L
    ).apply {
        setMinUpdateIntervalMillis(1500L)
        setWaitForAccurateLocation(true)
    }.build()

    private var locationCallback: LocationCallback? = null

    // UI State variables
    private val _gpsState = MutableStateFlow(GPSState())
    val gpsState: StateFlow<GPSState> = _gpsState.asStateFlow()

    private val _isLiveSharing = MutableStateFlow(false)
    val isLiveSharing: StateFlow<Boolean> = _isLiveSharing.asStateFlow()

    private val _safeZoneRadiusMeters = MutableStateFlow(1000f) // 1km default
    val safeZoneRadiusMeters: StateFlow<Float> = _safeZoneRadiusMeters.asStateFlow()

    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()

    private val _history = MutableStateFlow<List<ShareHistoryEntry>>(emptyList())
    val history: StateFlow<List<ShareHistoryEntry>> = _history.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _radarSweepAngle = MutableStateFlow(0f)
    val radarSweepAngle: StateFlow<Float> = _radarSweepAngle.asStateFlow()

    // Simulation settings & triggers
    private val _simulationEnabled = MutableStateFlow(true)
    val simulationEnabled: StateFlow<Boolean> = _simulationEnabled.asStateFlow()

    private var simulationJob: Job? = null
    private var radarJob: Job? = null

    init {
        val db = LocationDatabase.getDatabase(application)
        repository = LocationRepository(db.historyDao())

        // Setup base contacts
        resetContacts()

        // Sync with Room storage
        viewModelScope.launch {
            repository.historyLog.collectLatest { list ->
                _history.value = list
            }
        }

        // Start constant background updates for simulation & radar angle
        startSimulation()
        startRadarSweep()
    }

    private fun resetContacts() {
        _contacts.value = listOf(
            Contact(
                id = "c1",
                name = "Sarah Jenkins",
                latitude = 51.5090, // Drift offset from London default
                longitude = -0.1250,
                speedKmh = 14.5f,
                bearing = 45f,
                batteryPercent = 89,
                statusText = "Heading to Cafe"
            ),
            Contact(
                id = "c2",
                name = "Alex Vance",
                latitude = 51.5035,
                longitude = -0.1340,
                speedKmh = 4.2f,
                bearing = 210f,
                batteryPercent = 64,
                statusText = "Walking dog"
            ),
            Contact(
                id = "c3",
                name = "Rover Tracker (Pet)",
                latitude = 51.5120,
                longitude = -0.1210,
                speedKmh = 8.1f,
                bearing = 120f,
                batteryPercent = 95,
                statusText = "In safe zone"
            )
        )
        refreshDistances()
    }

    private fun startRadarSweep() {
        radarJob?.cancel()
        radarJob = viewModelScope.launch {
            while (true) {
                _radarSweepAngle.value = (_radarSweepAngle.value + 4f) % 360f
                delay(30)
            }
        }
    }

    private fun startSimulation() {
        simulationJob?.cancel()
        simulationJob = viewModelScope.launch {
            while (true) {
                if (_simulationEnabled.value) {
                    // Update our contacts coordinates using mini random walks
                    _contacts.value = _contacts.value.map { contact ->
                        val bearingDiff = (Math.random() * 40 - 20).toFloat()
                        val newBearing = (contact.bearing + bearingDiff + 360f) % 360f
                        
                        // Convert speed km/h to degrees drift over tick
                        val distInTickM = (contact.speedKmh / 3.6f) * 2.0f // 2 second ticks
                        val latDrift = (distInTickM / 111320.0) * cos(Math.toRadians(newBearing.toDouble()))
                        val lonDrift = (distInTickM / (111320.0 * cos(Math.toRadians(contact.latitude)))) * sin(Math.toRadians(newBearing.toDouble()))
                        
                        var newLat = contact.latitude + latDrift
                        var newLon = contact.longitude + lonDrift

                        // Confine drift to within 4km of user to keep on simulated map
                        val distFromUser = calculateDistance(newLat, newLon, _gpsState.value.latitude, _gpsState.value.longitude)
                        if (distFromUser > 3000.0) {
                            // Turn back towards user
                            val angleRad = atan2(newLon - _gpsState.value.longitude, newLat - _gpsState.value.latitude)
                            val reverseBearing = (Math.toDegrees(angleRad) + 180f) % 360
                            val targetBearing = (reverseBearing + (Math.random() * 60 - 30)).toFloat()
                            newLat = _gpsState.value.latitude + (2000 / 111320.0) * cos(Math.toRadians(targetBearing.toDouble()))
                            newLon = _gpsState.value.longitude + (2000 / (111320.0 * cos(Math.toRadians(newLat)))) * sin(Math.toRadians(targetBearing.toDouble()))
                        }

                        // Check battery depletion simulation
                        val newBat = if (Math.random() < 0.05) max(1, contact.batteryPercent - 1) else contact.batteryPercent

                        contact.copy(
                            latitude = newLat,
                            longitude = newLon,
                            bearing = newBearing,
                            batteryPercent = newBat,
                            statusText = when {
                                newBat < 15 -> "Battery critical"
                                contact.speedKmh > 20 -> "Driving"
                                contact.speedKmh > 6 -> "Cycling"
                                contact.speedKmh > 1.5 -> "Walking"
                                else -> "Stationary"
                            }
                        )
                    }
                    refreshDistances()
                }
                delay(2000L)
            }
        }
    }

    // Refresh distance calculation from user to all contacts
    private fun refreshDistances() {
        val user = _gpsState.value
        _contacts.value = _contacts.value.map { contact ->
            val dist = calculateDistance(user.latitude, user.longitude, contact.latitude, contact.longitude)
            val breached = dist > _safeZoneRadiusMeters.value
            contact.copy(
                calculatedDistanceMeters = dist,
                isGeofenceBreached = breached
            )
        }
    }

    // Haversine formula
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    // UI actions
    fun setSafeZoneRadius(radius: Float) {
        _safeZoneRadiusMeters.value = radius
        refreshDistances()
    }

    fun toggleSimulation() {
        _simulationEnabled.value = !_simulationEnabled.value
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    @SuppressLint("MissingPermission")
    fun startRealGPS() {
        try {
            _gpsState.value = _gpsState.value.copy(
                provider = "Hardware GPS",
                isDeviceGpsActive = true
            )
            locationCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val location = result.lastLocation ?: return
                    viewModelScope.launch {
                        _gpsState.value = GPSState(
                            latitude = location.latitude,
                            longitude = location.longitude,
                            altitude = location.altitude,
                            speedMps = location.speed,
                            bearingDegrees = location.bearing,
                            accuracyMeters = location.accuracy,
                            satCount = location.extras?.getInt("satellites", 14) ?: 14,
                            isDeviceGpsActive = true,
                            provider = location.provider ?: "Android Source"
                        )
                        refreshDistances()

                        // Automatically log to Room if currently sharing in live mode
                        if (_isLiveSharing.value) {
                            repository.logShare(
                                ShareHistoryEntry(
                                    latitude = location.latitude,
                                    longitude = location.longitude,
                                    accuracy = location.accuracy,
                                    speed = location.speed * 3.6f, // kmh
                                    notes = "Auto GPS Update",
                                    method = "System Live Tracker"
                                )
                            )
                        }
                    }
                }
            }
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )
        } catch (e: Exception) {
            _gpsState.value = _gpsState.value.copy(
                provider = "Simulated Core (Failover)",
                isDeviceGpsActive = false
            )
        }
    }

    fun stopRealGPS() {
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
        }
        locationCallback = null
        _gpsState.value = _gpsState.value.copy(
            isDeviceGpsActive = false,
            provider = "Simulated Core"
        )
    }

    // Allow user to mock modify latitude/longitude using control keypad
    fun shiftSimulatedLocation(latStep: Double, lonStep: Double) {
        val current = _gpsState.value
        if (!current.isDeviceGpsActive) {
            val randomSpeed = (5 + Math.random() * 15).toFloat() / 3.6f // drift mock speed
            val calculatedBearing = kotlin.math.atan2(lonStep, latStep)
            val bearingDegrees = (Math.toDegrees(calculatedBearing) + 360f).toFloat() % 360f

            _gpsState.value = current.copy(
                latitude = current.latitude + latStep,
                longitude = current.longitude + lonStep,
                speedMps = randomSpeed,
                bearingDegrees = bearingDegrees,
                accuracyMeters = 4f + (Math.random() * 2).toFloat()
            )
            refreshDistances()

            // If active sharing is on, automatically post a record
            if (_isLiveSharing.value) {
                viewModelScope.launch {
                    repository.logShare(
                        ShareHistoryEntry(
                            latitude = _gpsState.value.latitude,
                            longitude = _gpsState.value.longitude,
                            accuracy = _gpsState.value.accuracyMeters,
                            speed = _gpsState.value.speedMps * 3.6f,
                            notes = "Manual Joystick Drift",
                            method = "Joystick"
                        )
                    )
                }
            }
        }
    }

    // Toggle live location sharing
    fun toggleLiveSharing(notes: String = "Broadcasting Coordinates") {
        val newState = !_isLiveSharing.value
        _isLiveSharing.value = newState

        if (newState) {
            // Instantly log initial shared point
            viewModelScope.launch {
                repository.logShare(
                    ShareHistoryEntry(
                        latitude = _gpsState.value.latitude,
                        longitude = _gpsState.value.longitude,
                        accuracy = _gpsState.value.accuracyMeters,
                        speed = _gpsState.value.speedMps * 3.6f,
                        notes = notes,
                        method = "System Sheet"
                    )
                )
            }
        }
    }

    // Delete single element from database
    fun deleteHistoryEntry(id: Long) {
        viewModelScope.launch {
            repository.deleteShare(id)
        }
    }

    // Clear and reset history
    fun clearHistoryLog() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    // Add custom virtual friend to the simulation map
    fun addCustomVirtualFriend(name: String, speedKmh: Float, latOffset: Double, lonOffset: Double) {
        val user = _gpsState.value
        val newFriend = Contact(
            id = "c_custom_" + System.currentTimeMillis(),
            name = name,
            latitude = user.latitude + latOffset,
            longitude = user.longitude + lonOffset,
            speedKmh = speedKmh,
            bearing = (Math.random() * 360).toFloat(),
            batteryPercent = 100,
            statusText = "Custom Tracker Spawned",
            calculatedDistanceMeters = 0.0
        )
        _contacts.value = _contacts.value + newFriend
        refreshDistances()
    }

    override fun onCleared() {
        super.onCleared()
        stopRealGPS()
        simulationJob?.cancel()
        radarJob?.cancel()
    }
}
