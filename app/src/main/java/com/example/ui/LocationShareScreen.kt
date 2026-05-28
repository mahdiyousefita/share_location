package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.R
import com.example.data.ShareHistoryEntry
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.cos
import kotlin.math.sqrt

// Custom Theme Color Palette for Tech Obsidian/Immersive UI vibe
private val TechObsidianBg = Color(0xFF0F1115) // Matches bg-[#0F1115]
private val TechCardBg = Color(0xFF1C2026) // Matches bg-[#1C2026]
private val TechMapBg = Color(0xFF16191E) // Matches bg-[#16191E]
private val RadarCyan = Color(0xFF38BDF8) // Matches text-sky-400
private val RadarBlue = Color(0xFF0EA5E9) // Matches bg-sky-500
private val SafetyOrange = Color(0xFFFF9100)
private val SafetyRed = Color(0xFFD50000)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationShareScreen(viewModel: LocationViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ViewModel States
    val gpsState by viewModel.gpsState.collectAsStateWithLifecycle()
    val isLiveSharing by viewModel.isLiveSharing.collectAsStateWithLifecycle()
    val safeZoneRadius by viewModel.safeZoneRadiusMeters.collectAsStateWithLifecycle()
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val radarSweepAngle by viewModel.radarSweepAngle.collectAsStateWithLifecycle()
    val simulationEnabled by viewModel.simulationEnabled.collectAsStateWithLifecycle()

    // Local UI States
    var selectedTab by remember { mutableStateOf(0) } // 0: Radar Map, 1: Circle Status, 2: History
    var mapRangeRadiusMeters by remember { mutableStateOf(2000f) } // Default display range 2km
    var showFriendSpawner by remember { mutableStateOf(false) }

    // Floating spawner variables
    var newFriendName by remember { mutableStateOf("") }
    var newFriendSpeed by remember { mutableStateOf(10f) }
    var newFriendAngleOffset by remember { mutableStateOf(45f) }

    val isPermissionGranted = context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startRealGPS()
        }
    }

    // Handle initial real GPS activation on grant
    LaunchedEffect(isPermissionGranted) {
        if (isPermissionGranted) {
            viewModel.startRealGPS()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = TechObsidianBg
    ) {
        Scaffold(
            topBar = {
                // Immersive UI Header design with matching status animation
                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                val pulseAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.4f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1200, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulseAlpha"
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TechObsidianBg)
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Profile Logo + App Name + Live Indicator
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Letter Ava matching style
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(0xFF2A2D35), CircleShape)
                                .border(1.dp, Color.White.copy(alpha = 0.05f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "W",
                                color = RadarCyan,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = "WayPoint",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.5.sp
                                ),
                                color = Color.White
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(RadarCyan.copy(alpha = pulseAlpha))
                                )
                                Text(
                                    text = "Live Location Active",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = RadarCyan
                                )
                            }
                        }
                    }

                    // Right Side Action Indicators
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Search background circle item
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color(0xFF2A2D35), CircleShape)
                                .clickable {
                                    Toast.makeText(context, "WayPoint active radar pinging...", Toast.LENGTH_SHORT).show()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = "Quick Search Icon",
                                tint = Color.LightGray,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Profile round badge "AR" with light borders
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .border(2.dp, RadarCyan.copy(alpha = 0.3f), CircleShape)
                                .background(Color(0xFF2E333D)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "AR",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }
                    }
                }
            },
            bottomBar = {
                // Highly polished modern dynamic pill-based bottom Navigation bar
                Column {
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    NavigationBar(
                        containerColor = TechObsidianBg,
                        tonalElevation = 0.dp,
                        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                    ) {
                        NavigationBarItem(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            icon = {
                                Icon(
                                    imageVector = if (selectedTab == 0) Icons.Filled.Map else Icons.Outlined.Map,
                                    contentDescription = "Radar Module"
                                )
                            },
                            label = { Text("Radar Hub", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = RadarCyan,
                                selectedTextColor = RadarCyan,
                                indicatorColor = RadarCyan.copy(alpha = 0.15f),
                                unselectedIconColor = Color.LightGray,
                                unselectedTextColor = Color.LightGray
                            ),
                            modifier = Modifier.testTag("tab_radar")
                        )
                        NavigationBarItem(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            icon = {
                                Icon(
                                    imageVector = if (selectedTab == 1) Icons.Filled.Group else Icons.Outlined.Group,
                                    contentDescription = "Safety Circle"
                                )
                            },
                            label = { Text("Circle Status", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = RadarCyan,
                                selectedTextColor = RadarCyan,
                                indicatorColor = RadarCyan.copy(alpha = 0.15f),
                                unselectedIconColor = Color.LightGray,
                                unselectedTextColor = Color.LightGray
                            ),
                            modifier = Modifier.testTag("tab_circle")
                        )
                        NavigationBarItem(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            icon = {
                                Icon(
                                    imageVector = if (selectedTab == 2) Icons.Filled.History else Icons.Outlined.History,
                                    contentDescription = "Share Logs"
                                )
                            },
                            label = { Text("History", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = RadarCyan,
                                selectedTextColor = RadarCyan,
                                indicatorColor = RadarCyan.copy(alpha = 0.15f),
                                unselectedIconColor = Color.LightGray,
                                unselectedTextColor = Color.LightGray
                            ),
                            modifier = Modifier.testTag("tab_history")
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(TechObsidianBg)
            ) {
                when (selectedTab) {
                    0 -> RadarHubModule(
                        gpsState = gpsState,
                        isLiveSharing = isLiveSharing,
                        contacts = contacts,
                        radarSweepAngle = radarSweepAngle,
                        safeZoneRadius = safeZoneRadius,
                        mapRangeRadiusMeters = mapRangeRadiusMeters,
                        onRangeChange = { mapRangeRadiusMeters = it },
                        onToggleSharing = { notes -> viewModel.toggleLiveSharing(notes) },
                        onShiftLocation = { lat, lon -> viewModel.shiftSimulatedLocation(lat, lon) },
                        isPermissionGranted = isPermissionGranted,
                        onRequestPermission = { permissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION) },
                        onStartGps = { viewModel.startRealGPS() },
                        onStopGps = { viewModel.stopRealGPS() }
                    )

                    1 -> CircleStatusModule(
                        contacts = contacts,
                        searchQuery = searchQuery,
                        onQueryChange = { viewModel.updateSearchQuery(it) },
                        safeZoneRadius = safeZoneRadius,
                        onUpdateSafeRadius = { viewModel.setSafeZoneRadius(it) },
                        simulationEnabled = simulationEnabled,
                        onToggleSimulation = { viewModel.toggleSimulation() },
                        showFriendSpawner = showFriendSpawner,
                        onShowFriendSpawnerChange = { showFriendSpawner = it },
                        newFriendName = newFriendName,
                        onFriendNameChange = { newFriendName = it },
                        newFriendSpeed = newFriendSpeed,
                        onFriendSpeedChange = { newFriendSpeed = it },
                        newFriendAngleOffset = newFriendAngleOffset,
                        onFriendAngleOffsetChange = { newFriendAngleOffset = it },
                        onSpawnFriend = { name, speed, angle ->
                            val rad = Math.toRadians(angle.toDouble())
                            // roughly convert offset (distance say 1km) to coordinates
                            val latOffset = (1000.0 / 111320.0) * cos(rad)
                            val lonOffset = (1000.0 / (111320.0 * cos(Math.toRadians(gpsState.latitude)))) * kotlin.math.sin(rad)
                            viewModel.addCustomVirtualFriend(name, speed, latOffset, lonOffset)
                            newFriendName = ""
                            showFriendSpawner = false
                        }
                    )

                    2 -> HistoryModule(
                        history = history,
                        onDeleteEntry = { viewModel.deleteHistoryEntry(it) },
                        onClearAll = { viewModel.clearHistoryLog() }
                    )
                }
            }
        }
    }
}

// ========================
// REUSABLE SUB-COMPONENTS
// ========================

@Composable
fun RadarHubModule(
    gpsState: GPSState,
    isLiveSharing: Boolean,
    contacts: List<Contact>,
    radarSweepAngle: Float,
    safeZoneRadius: Float,
    mapRangeRadiusMeters: Float,
    onRangeChange: (Float) -> Unit,
    onToggleSharing: (String) -> Unit,
    onShiftLocation: (Double, Double) -> Unit,
    isPermissionGranted: Boolean,
    onRequestPermission: () -> Unit,
    onStartGps: () -> Unit,
    onStopGps: () -> Unit
) {
    val context = LocalContext.current
    var showCustomShareSheet by remember { mutableStateOf(false) }
    var shareNotesContext by remember { mutableStateOf("Hiking base point established") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // GPS State and Mode Badge Banner
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                colors = CardDefaults.cardColors(containerColor = TechCardBg),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = Brush.horizontalGradient(listOf(RadarCyan.copy(alpha = 0.4f), Color.Transparent))
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (gpsState.isDeviceGpsActive) Color.Green else SafetyOrange)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (gpsState.isDeviceGpsActive) "HARDWARE HIGH-PRECISION ACTIVATED" else "DEMO VIRTUAL SIMULATOR",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 1.sp
                                ),
                                color = if (gpsState.isDeviceGpsActive) Color.Green else SafetyOrange
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "My Lat: ${String.format("%.5f", gpsState.latitude)} | Lon: ${String.format("%.5f", gpsState.longitude)}",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = Color.White
                        )
                        Text(
                            text = "Provider: ${gpsState.provider} | Accurately bounded to ${gpsState.accuracyMeters}m",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }

                    // Hardware GPS Switch trigger
                    IconButton(
                        onClick = {
                            if (gpsState.isDeviceGpsActive) {
                                onStopGps()
                                Toast.makeText(context, "Switched to Simulated Core Mode", Toast.LENGTH_SHORT).show()
                            } else {
                                if (isPermissionGranted) {
                                    onStartGps()
                                    Toast.makeText(context, "Hardware GPS active", Toast.LENGTH_SHORT).show()
                                } else {
                                    onRequestPermission()
                                }
                            }
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color.White.copy(alpha = 0.05f), CircleShape)
                            .testTag("gps_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (gpsState.isDeviceGpsActive) Icons.Filled.GpsFixed else Icons.Filled.GpsOff,
                            contentDescription = "Toggle hardware GPS",
                            tint = if (gpsState.isDeviceGpsActive) Color.Green else Color.White
                        )
                    }
                }
            }
        }

        // --- HIGH FIDELITY RADAR CANVAS COMPONENT ---
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.2f)
                    .clip(RoundedCornerShape(32.dp))
                    .background(TechMapBg)
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)), RoundedCornerShape(32.dp))
            ) {
                // Background compass texture and drawing elements
                RadarMapCanvas(
                    gpsState = gpsState,
                    contacts = contacts,
                    radarSweepAngle = radarSweepAngle,
                    safeZoneRadius = safeZoneRadius,
                    mapRangeRadiusMeters = mapRangeRadiusMeters
                )

                // Immersive HUD stats overlay
                // Top Left: Bearing/Location Details
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                        .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), RoundedCornerShape(16.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Column {
                        Text(
                            text = "GRID BEARING",
                            color = Color.Gray,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, letterSpacing = 0.5.sp, fontFamily = FontFamily.Monospace)
                        )
                        Text(
                            text = "${String.format("%.0f", gpsState.bearingDegrees)}° CARDINAL",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        )
                    }
                }

                // Top Right: Live metrics
                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), RoundedCornerShape(12.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "SPEED: ${String.format("%.1f", gpsState.speedMps * 3.6f)} km/h",
                            color = RadarCyan,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), RoundedCornerShape(12.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "RANGE: ${String.format("%.0f", mapRangeRadiusMeters)}m",
                            color = RadarCyan,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        )
                    }
                }

                // Center right: Stacked Vertical Zoom controls from custom designs
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF2A2D35).copy(alpha = 0.85f))
                            .clickable {
                                if (mapRangeRadiusMeters > 500f) onRangeChange(mapRangeRadiusMeters - 500f)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("+", color = Color.White, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                    }
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF2A2D35).copy(alpha = 0.85f))
                            .clickable {
                                if (mapRangeRadiusMeters < 8000f) onRangeChange(mapRangeRadiusMeters + 500f)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("−", color = Color.White, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
        }

        // Joystick manual controls (strictly when simulated)
        if (!gpsState.isDeviceGpsActive) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = TechCardBg)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "SIMULATION DRIVER (JOYSTICK)",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 0.5.sp
                            ),
                            color = Color.LightGray
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { onShiftLocation(0.0, -0.0003) }, // Move West
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(Color.White.copy(alpha = 0.05f), CircleShape)
                                    .testTag("joystick_west")
                            ) {
                                Icon(Icons.Filled.ArrowBack, contentDescription = "West", tint = RadarCyan)
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                IconButton(
                                    onClick = { onShiftLocation(0.0003, 0.0) }, // Move North
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(Color.White.copy(alpha = 0.05f), CircleShape)
                                        .testTag("joystick_north")
                                ) {
                                    Icon(Icons.Filled.ArrowUpward, contentDescription = "North", tint = RadarCyan)
                                }

                                IconButton(
                                    onClick = { onShiftLocation(-0.0003, 0.0) }, // Move South
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(Color.White.copy(alpha = 0.05f), CircleShape)
                                        .testTag("joystick_south")
                                ) {
                                    Icon(Icons.Filled.ArrowDownward, contentDescription = "South", tint = RadarCyan)
                                }
                            }

                            IconButton(
                                onClick = { onShiftLocation(0.0, 0.0003) }, // Move East
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(Color.White.copy(alpha = 0.05f), CircleShape)
                                    .testTag("joystick_east")
                            ) {
                                Icon(Icons.Filled.ArrowForward, contentDescription = "East", tint = RadarCyan)
                            }
                        }
                    }
                }
            }
        }

        // --- PRIMARY LIVE CORRESPONDING ACTIONS (SHARE SHEET INTEGRATIONS) ---
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                colors = CardDefaults.cardColors(containerColor = TechCardBg)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "LIVE SHARING SHIELD",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        ),
                        color = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Main Broadcaster Toggle Button
                        Button(
                            onClick = { showCustomShareSheet = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isLiveSharing) SafetyRed else RadarBlue,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .testTag("btn_broadcast_toggle")
                        ) {
                            Icon(
                                imageVector = if (isLiveSharing) Icons.Filled.StopCircle else Icons.Filled.ShareLocation,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isLiveSharing) "STOP ALL SHARES" else "START LIVE BROADCAST",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                        }

                        // Native Copy coords shortcut
                        IconButton(
                            onClick = {
                                val lat = gpsState.latitude
                                val lon = gpsState.longitude
                                val link = "https://www.google.com/maps/search/?api=1&query=$lat,$lon"
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Location Link", link)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Google Maps Link copied!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .size(52.dp)
                                .background(Color.White.copy(alpha = 0.07f), RoundedCornerShape(12.dp))
                        ) {
                            Icon(imageVector = Icons.Filled.ContentCopy, contentDescription = "Copy Coordinates Links", tint = Color.White)
                        }

                        // System Native Share Action Intent
                        IconButton(
                            onClick = {
                                val lat = gpsState.latitude
                                val lon = gpsState.longitude
                                val textPayload = "My Live Location via Live Share App:\nCoordinates: $lat, $lon\nMaps Tracker: https://www.google.com/maps/search/?api=1&query=$lat,$lon"
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, textPayload)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Live GPS coordinates via:"))
                            },
                            modifier = Modifier
                                .size(52.dp)
                                .background(Color.White.copy(alpha = 0.07f), RoundedCornerShape(12.dp))
                        ) {
                            Icon(imageVector = Icons.Filled.Send, contentDescription = "Trigger System Native Share", tint = Color.LightGray)
                        }
                    }

                    if (isLiveSharing) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SafetyRed.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.OnlinePrediction,
                                contentDescription = null,
                                tint = SafetyRed,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Currently broadcasting active ping. Shared links are dynamically logging coordinates securely.",
                                color = Color.White.copy(alpha = 0.85f),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal dialog for customizable live session startup
    if (showCustomShareSheet) {
        AlertDialog(
            onDismissRequest = { showCustomShareSheet = false },
            containerColor = TechCardBg,
            title = {
                Text(
                    text = if (isLiveSharing) "Cease Broadcasting?" else "Configure Sharing Session",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isLiveSharing) {
                        Text(
                            text = "This will immediately secure all streams and stop logging location updates to history.",
                            color = Color.LightGray
                        )
                    } else {
                        Text(
                            text = "Set a tag description for this share history entry:",
                            color = Color.LightGray
                        )
                        OutlinedTextField(
                            value = shareNotesContext,
                            onValueChange = { shareNotesContext = it },
                            placeholder = { Text("e.g. Sailing at lake, Morning cycle") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.LightGray,
                                focusedBorderColor = RadarCyan,
                                unfocusedBorderColor = Color.LightGray.copy(alpha = 0.4f)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().testTag("tag_input")
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onToggleSharing(shareNotesContext)
                        showCustomShareSheet = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isLiveSharing) SafetyRed else RadarCyan,
                        contentColor = if (isLiveSharing) Color.White else Color.Black
                    )
                ) {
                    Text(
                        text = if (isLiveSharing) "STOP SESSION" else "BROADCAST LIVE",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomShareSheet = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }
}

// Draw the Custom High-Fidelity Radar view on canvas
@Composable
fun RadarMapCanvas(
    gpsState: GPSState,
    contacts: List<Contact>,
    radarSweepAngle: Float,
    safeZoneRadius: Float,
    mapRangeRadiusMeters: Float
) {
    Canvas(
        modifier = Modifier.fillMaxSize()
    ) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val maxRadius = size.width.coerceAtMost(size.height) / 2f * 0.9f

        // 1. Draw Background grid & rings
        val gridColor = Color(0xFF222933)
        val outerRadarLineColor = Color(0xFF283543)

        // Draw compass circle background corresponding to TechMapBg
        drawCircle(
            color = Color(0xFF12151A),
            radius = maxRadius
        )

        // High-fidelity dot pattern overlay (matching Tailwind radial-gradient)
        val dotSpacing = 36f
        val dotRadius = 1.2f
        val dotColor = Color(0xFF334155).copy(alpha = 0.35f)
        val columns = (maxRadius * 2 / dotSpacing).toInt()
        for (xIndex in -columns..columns) {
            for (yIndex in -columns..columns) {
                val dx = xIndex * dotSpacing
                val dy = yIndex * dotSpacing
                if (dx * dx + dy * dy <= maxRadius * maxRadius) {
                    drawCircle(
                        color = dotColor,
                        radius = dotRadius,
                        center = Offset(cx + dx, cy + dy)
                    )
                }
            }
        }

        // Draw standard range rings
        val ringsCount = 3
        for (i in 1..ringsCount) {
            val ringRadius = maxRadius * (i.toFloat() / ringsCount.toFloat())
            drawCircle(
                color = gridColor,
                radius = ringRadius,
                style = Stroke(width = 1.5f)
            )
        }

        // 2. Draw Geofence Radius Outer representation
        if (safeZoneRadius <= mapRangeRadiusMeters) {
            val geofenceCanvasRadius = maxRadius * (safeZoneRadius / mapRangeRadiusMeters)
            drawCircle(
                color = SafetyOrange.copy(alpha = 0.5f),
                radius = geofenceCanvasRadius,
                style = Stroke(
                    width = 3f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )
            )
        }

        // Draw cardinal lines (North-South, East-West)
        drawLine(
            color = gridColor,
            start = Offset(cx, cy - maxRadius),
            end = Offset(cx, cy + maxRadius),
            strokeWidth = 1.5f
        )
        drawLine(
            color = gridColor,
            start = Offset(cx - maxRadius, cy),
            end = Offset(cx + maxRadius, cy),
            strokeWidth = 1.5f
        )

        // 3. Draw Radar Sweep Line and trailing glow arc
        withTransform({
            rotate(radarSweepAngle, Offset(cx, cy))
        }) {
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        RadarCyan.copy(alpha = 0.45f),
                        RadarCyan.copy(alpha = 0.12f),
                        Color.Transparent
                    ),
                    center = Offset(cx, cy)
                ),
                startAngle = -45f,
                sweepAngle = 45f,
                useCenter = true
            )
            // Leading beam
            drawLine(
                color = RadarCyan,
                start = Offset(cx, cy),
                end = Offset(cx + maxRadius * cos(0f), cy),
                strokeWidth = 3f
            )
        }

        // 4. Draw Friends/Contacts targets
        contacts.forEach { contact ->
            // Translate degrees coordinate differences to visual cartesian meters
            val latDiff = contact.latitude - gpsState.latitude
            val lonDiff = contact.longitude - gpsState.longitude
            
            val yMeters = latDiff * 111320.0
            val xMeters = lonDiff * 111320.0 * cos(Math.toRadians(gpsState.latitude))

            // Map meters to Canvas coordinates relative to center
            val scaleFactor = maxRadius / mapRangeRadiusMeters
            val cxOffset = (xMeters * scaleFactor).toFloat()
            val cyOffset = (-yMeters * scaleFactor).toFloat() // Invert Y as canvas coordinates increase downwards

            val contactPos = Offset(cx + cxOffset, cy + cyOffset)

            // Draw only if it fits inside the radar circle bounds
            val distanceToCenter = sqrt((cxOffset * cxOffset + cyOffset * cyOffset).toDouble())
            if (distanceToCenter <= maxRadius) {
                // Target highlight color
                val dotColor = if (contact.isGeofenceBreached) SafetyRed else RadarCyan

                // Draw target halo line
                drawCircle(
                    color = dotColor.copy(alpha = 0.25f),
                    radius = 18f,
                    center = contactPos
                )

                // Draw solid inner core
                drawCircle(
                    color = dotColor,
                    radius = 7f,
                    center = contactPos
                )
            }
        }

        // 5. Draw ME (Owner user) at center coordinates with Immersive pulsing design layers
        // Outer pulsing rings
        drawCircle(
            color = RadarCyan.copy(alpha = 0.15f),
            radius = 70f,
            center = Offset(cx, cy),
            style = Stroke(width = 1.5f)
        )
        drawCircle(
            color = RadarCyan.copy(alpha = 0.06f),
            radius = 36f,
            center = Offset(cx, cy)
        )
        drawCircle(
            color = RadarCyan.copy(alpha = 0.25f),
            radius = 36f,
            center = Offset(cx, cy),
            style = Stroke(width = 1.5f)
        )

        // Center dot highlight shadow / outer glow
        drawCircle(
            color = RadarCyan.copy(alpha = 0.35f),
            radius = 18f,
            center = Offset(cx, cy)
        )
        // White boundary border
        drawCircle(
            color = Color.White,
            radius = 12f,
            center = Offset(cx, cy)
        )
        // Accent core
        drawCircle(
            color = RadarCyan,
            radius = 7.5f,
            center = Offset(cx, cy)
        )
    }
}

// Custom Contact Avatar helper with matching gradient backgrounds
@Composable
fun ContactAvatar(name: String) {
    val initials = remember(name) {
        val parts = name.trim().split(" ")
        if (parts.size >= 2) {
            "${parts[0].firstOrNull() ?: ""}${parts[1].firstOrNull() ?: ""}"
        } else {
            "${name.firstOrNull() ?: ""}"
        }.uppercase()
    }
    val gradient = remember(name) {
        val hash = name.hashCode()
        val posHash = if (hash < 0) -hash else hash
        val startColor = when (posHash % 4) {
            0 -> Color(0xFF6366F1) // Indigo-500
            1 -> Color(0xFFF97316) // Orange-500
            2 -> Color(0xFF0EA5E9) // Sky Blue
            else -> Color(0xFFEC4899) // Pink-500
        }
        val endColor = when (posHash % 4) {
            0 -> Color(0xFFA855F7) // Purple-500
            1 -> Color(0xFFEC4899) // Pink-500
            2 -> Color(0xFF6366F1) // Indigo-500
            else -> Color(0xFFE11D48) // Rose-500
        }
        Brush.linearGradient(listOf(startColor, endColor))
    }
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(gradient),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
    }
}

// Circle Status Tab Module Component
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CircleStatusModule(
    contacts: List<Contact>,
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    safeZoneRadius: Float,
    onUpdateSafeRadius: (Float) -> Unit,
    simulationEnabled: Boolean,
    onToggleSimulation: () -> Unit,
    showFriendSpawner: Boolean,
    onShowFriendSpawnerChange: (Boolean) -> Unit,
    newFriendName: String,
    onFriendNameChange: (String) -> Unit,
    newFriendSpeed: Float,
    onFriendSpeedChange: (Float) -> Unit,
    newFriendAngleOffset: Float,
    onFriendAngleOffsetChange: (Float) -> Unit,
    onSpawnFriend: (String, Float, Float) -> Unit
) {
    val filteredContacts = contacts.filter {
        it.name.contains(searchQuery, ignoreCase = true)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Safe Zone Perimeter Guard Slider Setting
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                colors = CardDefaults.cardColors(containerColor = TechCardBg)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Security, contentDescription = null, tint = SafetyOrange, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "GEOFENCE SAFE GUARD",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .background(SafetyOrange.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${String.format("%.0f", safeZoneRadius)}m",
                                color = SafetyOrange,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Slider(
                        value = safeZoneRadius,
                        onValueChange = { onUpdateSafeRadius(it) },
                        valueRange = 200f..5000f,
                        colors = SliderDefaults.colors(
                            thumbColor = SafetyOrange,
                            activeTrackColor = SafetyOrange,
                            inactiveTrackColor = Color.LightGray.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.testTag("geofence_slider")
                    )

                    Text(
                        text = "Triggers an warning status if any contact in your radar circle breaches selected safe diameter limits.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
        }

        // Search Bar or Simulator Controller header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { onQueryChange(it) },
                    placeholder = { Text("Filter circle names...", color = Color.Gray) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = Color.Gray) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("search_bar"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.LightGray,
                        focusedBorderColor = RadarCyan,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.1f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                // Simulation Toggle Button
                IconButton(
                    onClick = onToggleSimulation,
                    modifier = Modifier
                        .size(54.dp)
                        .background(if (simulationEnabled) RadarCyan.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                        .testTag("sim_toggle_btn")
                ) {
                    Icon(
                        imageVector = if (simulationEnabled) Icons.Filled.DirectionsWalk else Icons.Filled.Pause,
                        tint = if (simulationEnabled) RadarCyan else Color.White,
                        contentDescription = "Toggle Drift Movement"
                    )
                }
            }
        }

        // Floating Spawn Button to custom fabricate a mock track contact!
        item {
            Button(
                onClick = { onShowFriendSpawnerChange(true) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("btn_add_friend"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RadarBlue, contentColor = Color.White)
            ) {
                Icon(Icons.Filled.PersonAdd, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("SPAWN NEW TRACKER SYMBOL", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
        }

        // Spawner configuration panel inside list
        if (showFriendSpawner) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = TechCardBg),
                    border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(RadarBlue, Color.Transparent)))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "FABRICATE VIRTUAL TRANSPONDER",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                            color = RadarCyan
                        )

                        OutlinedTextField(
                            value = newFriendName,
                            onValueChange = onFriendNameChange,
                            label = { Text("Transponder Label Prefix") },
                            modifier = Modifier.fillMaxWidth().testTag("custom_name_input"),
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White)
                        )

                        Text(
                            text = "Walk Speed: ${String.format("%.0f", newFriendSpeed)} Kmh",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.LightGray
                        )
                        Slider(
                            value = newFriendSpeed,
                            onValueChange = onFriendSpeedChange,
                            valueRange = 3f..60f
                        )

                        Text(
                            text = "Heading Bearing: ${String.format("%.0f", newFriendAngleOffset)}°",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.LightGray
                        )
                        Slider(
                            value = newFriendAngleOffset,
                            onValueChange = onFriendAngleOffsetChange,
                            valueRange = 0f..360f
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { onShowFriendSpawnerChange(false) }) {
                                Text("Discard", color = Color.Gray)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (newFriendName.isNotBlank()) {
                                        onSpawnFriend(newFriendName, newFriendSpeed, newFriendAngleOffset)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = RadarCyan, contentColor = Color.Black)
                            ) {
                                Text("ACTIVATE DETECTOR")
                            }
                        }
                    }
                }
            }
        }

        // Active Circle tracker target instances list
        if (filteredContacts.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Map, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color.DarkGray)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No transponder items matches your request.",
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(filteredContacts) { contact ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (contact.isGeofenceBreached) SafetyRed.copy(alpha = 0.12f) else TechCardBg
                    ),
                    border = if (contact.isGeofenceBreached) BorderStroke(
                        1.dp,
                        Brush.horizontalGradient(listOf(SafetyRed, Color.Transparent))
                    ) else BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Custom avatar with gorgeous gradient backgrounds matching design templates
                        ContactAvatar(name = contact.name)
                        
                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = contact.name,
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                // Geofence breach pill
                                if (contact.isGeofenceBreached) {
                                    Box(
                                        modifier = Modifier
                                            .background(SafetyRed.copy(alpha = 0.25f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "GEOFENCE OUT",
                                            color = SafetyRed,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 9.sp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Current Status: ${contact.statusText} • Speed: ${contact.speedKmh} km/h",
                                color = Color.Gray,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "Latitude: ${String.format("%.5f", contact.latitude)} | Longitude: ${String.format("%.5f", contact.longitude)}",
                                color = Color.LightGray.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                            )
                        }

                        // Distance metric column
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = if (contact.calculatedDistanceMeters > 1000f) {
                                    "${String.format("%.2f", contact.calculatedDistanceMeters / 1000f)} km"
                                } else {
                                    "${String.format("%.0f", contact.calculatedDistanceMeters)} m"
                                },
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = if (contact.isGeofenceBreached) SafetyRed else RadarCyan
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (contact.batteryPercent > 50) Icons.Filled.Battery5Bar else Icons.Filled.BatteryAlert,
                                    contentDescription = null,
                                    tint = if (contact.batteryPercent > 20) Color.Green else SafetyRed,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "${contact.batteryPercent}%",
                                    color = Color.LightGray,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }
        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// Location History module persistence tracker view layout
@Composable
fun HistoryModule(
    history: List<ShareHistoryEntry>,
    onDeleteEntry: (Long) -> Unit,
    onClearAll: () -> Unit
) {
    val formatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "PERSISTENT BROADCASTS LOG",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                ),
                color = Color.LightGray
            )

            if (history.isNotEmpty()) {
                TextButton(
                    onClick = onClearAll,
                    colors = ButtonDefaults.textButtonColors(contentColor = SafetyRed),
                    modifier = Modifier.testTag("btn_clear_history")
                ) {
                    Icon(imageVector = Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Clear Log", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (history.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.HistoryToggleOff,
                        contentDescription = "No history entry found",
                        tint = Color.DarkGray,
                        modifier = Modifier2.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Your location shares list is empty.\nStart manual shares or live broadcasts to generate historical entries.",
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(history, key = { it.id }) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = TechCardBg),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.notes.ifBlank { "Location Broadcast Entry" },
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Lat: ${item.latitude} | Lon: ${item.longitude}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = RadarCyan
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${formatter.format(Date(item.timestamp))} • Method: ${item.method}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Maps click copy link
                                IconButton(
                                    onClick = {
                                        val link = "https://www.google.com/maps/search/?api=1&query=${item.latitude},${item.longitude}"
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("Location Link", link)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "Link copied!", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Icon(imageVector = Icons.Filled.ContentCopy, contentDescription = "Copy History Entry Link", tint = Color.LightGray)
                                }

                                // Delete single record button
                                IconButton(
                                    onClick = { onDeleteEntry(item.id) },
                                    modifier = Modifier.testTag("delete_${item.id}")
                                ) {
                                    Icon(imageVector = Icons.Filled.Delete, contentDescription = "Remove single entry", tint = SafetyRed.copy(alpha = 0.7f))
                                }
                            }
                        }
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

// Workaround for syntax checks
private val Modifier2 = Modifier
