package com.example.locationspoofer.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.locationspoofer.data.LocationRepository
import com.example.locationspoofer.data.SavedLocation
import com.example.locationspoofer.location.CoordinateParser
import com.example.locationspoofer.location.DeviceLocationHelper
import com.example.locationspoofer.location.LocationSearchManager
import com.example.locationspoofer.location.LocationValidator
import com.example.locationspoofer.model.LocationSearchResult
import com.example.locationspoofer.model.SpoofLocation
import com.example.locationspoofer.route.RoadRoute
import com.example.locationspoofer.route.RouteManager
import com.example.locationspoofer.route.RoutePoint
import com.example.locationspoofer.service.LocationForegroundService
import com.example.locationspoofer.service.ServiceStatus
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()

    val repository = remember { LocationRepository(context) }
    val savedLocations by repository.savedLocations.collectAsStateWithLifecycle()
    val serviceStatus by LocationForegroundService.serviceStatus.collectAsStateWithLifecycle()

    // Mode Selection: 0 = Static Mock, 1 = Road Route Simulation
    var selectedModeTab by remember { mutableIntStateOf(0) }

    // Static Location Inputs
    var latitudeText by remember { mutableStateOf("13.0827") }
    var longitudeText by remember { mutableStateOf("80.2707") }
    var accuracyText by remember { mutableStateOf("5") }
    var localError by remember { mutableStateOf<String?>(null) }

    // Route Simulation Inputs
    var startLatText by remember { mutableStateOf("13.0827") }
    var startLonText by remember { mutableStateOf("80.2707") }
    var endLatText by remember { mutableStateOf("13.0600") }
    var endLonText by remember { mutableStateOf("80.2400") }
    var calculatedRoute by remember { mutableStateOf<RoadRoute?>(null) }
    var isCalculatingRoute by remember { mutableStateOf(false) }
    var routingError by remember { mutableStateOf<String?>(null) }
    var selectedSpeedKmh by remember { mutableFloatStateOf(40f) }
    var followLocation by remember { mutableStateOf(true) }

    // Search state
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<LocationSearchResult>>(emptyList()) }
    var searchMessage by remember { mutableStateOf<String?>(null) }

    // Map selection and device location states
    var mapLatitude by remember { mutableDoubleStateOf(13.0827) }
    var mapLongitude by remember { mutableDoubleStateOf(80.2707) }
    var cameraTarget by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var currentDeviceLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var isLocating by remember { mutableStateOf(false) }

    // Save Location Dialog State
    var showSaveDialog by remember { mutableStateOf(false) }

    val isRunningStatic = serviceStatus is ServiceStatus.Running
    val isSimulatingRoute = serviceStatus is ServiceStatus.SimulatingRoute
    val isAnyServiceRunning = isRunningStatic || isSimulatingRoute
    val staticValidationResult = LocationValidator.validate(latitudeText, longitudeText, accuracyText)
    val isStaticInputValid = staticValidationResult is LocationValidator.ValidationResult.Valid

    // Extract active simulation progress if simulating
    val activeSimulationProgress = (serviceStatus as? ServiceStatus.SimulatingRoute)?.progress
    val isSimulationPaused = (serviceStatus as? ServiceStatus.SimulatingRoute)?.isPaused ?: false

    // Required permissions launcher
    val permissionsToRequest = remember {
        buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (fineLocationGranted) {
            localError = null
            coroutineScope.launch {
                val loc = DeviceLocationHelper.getCurrentLocation(context)
                if (loc != null) {
                    currentDeviceLocation = loc
                }
            }
        } else {
            localError = "Location permission is required for mock location publishing and GPS positioning."
        }
    }

    // Auto-detect current device location on launch if permitted
    LaunchedEffect(Unit) {
        if (DeviceLocationHelper.hasLocationPermission(context)) {
            val loc = DeviceLocationHelper.getCurrentLocation(context)
            if (loc != null) {
                currentDeviceLocation = loc
            }
        }
    }

    fun handleLocateMe() {
        if (!DeviceLocationHelper.hasLocationPermission(context)) {
            permissionLauncher.launch(permissionsToRequest)
            return
        }

        coroutineScope.launch {
            isLocating = true
            val loc = DeviceLocationHelper.getCurrentLocation(context)
            isLocating = false
            if (loc != null) {
                currentDeviceLocation = loc
                cameraTarget = loc
                searchMessage = "📍 Centered on your current GPS location"
            } else {
                localError = "Could not fetch current device location. Ensure device GPS is active."
            }
        }
    }

    fun onStartStaticClicked() {
        when (val res = LocationValidator.validate(latitudeText, longitudeText, accuracyText)) {
            is LocationValidator.ValidationResult.Invalid -> {
                localError = res.reason
            }
            is LocationValidator.ValidationResult.Valid -> {
                localError = null
                val fineLocationGranted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

                if (fineLocationGranted) {
                    val lat = latitudeText.trim().toDoubleOrNull() ?: 0.0
                    val lon = longitudeText.trim().toDoubleOrNull() ?: 0.0
                    val acc = accuracyText.trim().toFloatOrNull() ?: 5.0f
                    LocationForegroundService.startService(context, SpoofLocation(lat, lon, acc))
                } else {
                    permissionLauncher.launch(permissionsToRequest)
                }
            }
        }
    }

    fun onStopClicked() {
        localError = null
        LocationForegroundService.stopService(context)
    }

    fun onCalculateRouteClicked() {
        val sLat = startLatText.trim().toDoubleOrNull()
        val sLon = startLonText.trim().toDoubleOrNull()
        val eLat = endLatText.trim().toDoubleOrNull()
        val eLon = endLonText.trim().toDoubleOrNull()

        if (sLat == null || sLat < -90.0 || sLat > 90.0 || sLon == null || sLon < -180.0 || sLon > 180.0) {
            routingError = "Please enter valid Start coordinates (-90 to 90, -180 to 180)."
            return
        }
        if (eLat == null || eLat < -90.0 || eLat > 90.0 || eLon == null || eLon < -180.0 || eLon > 180.0) {
            routingError = "Please enter valid Destination coordinates (-90 to 90, -180 to 180)."
            return
        }

        routingError = null
        coroutineScope.launch {
            isCalculatingRoute = true
            val result = RouteManager.calculateRoute(sLat, sLon, eLat, eLon)
            isCalculatingRoute = false

            result.fold(
                onSuccess = { route ->
                    calculatedRoute = route
                    routingError = null
                    cameraTarget = Pair(sLat, sLon)
                    searchMessage = String.format(Locale.US, "✓ Road route calculated: %.2f km along actual roads", route.distanceKm)
                },
                onFailure = { error ->
                    calculatedRoute = null
                    routingError = error.message ?: "Unable to find a road route between the selected locations."
                }
            )
        }
    }

    fun onStartSimulationClicked() {
        val route = calculatedRoute ?: return
        val fineLocationGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (fineLocationGranted) {
            localError = null
            routingError = null
            LocationForegroundService.startRouteSimulation(
                context = context,
                route = route,
                speedKmh = selectedSpeedKmh,
                accuracy = accuracyText.toFloatOrNull() ?: 5.0f
            )
        } else {
            permissionLauncher.launch(permissionsToRequest)
        }
    }

    fun applyCoordinates(lat: Double, lon: Double, centerCamera: Boolean = true) {
        latitudeText = String.format(Locale.US, "%.6f", lat)
        longitudeText = String.format(Locale.US, "%.6f", lon)
        mapLatitude = lat
        mapLongitude = lon
        if (centerCamera) {
            cameraTarget = Pair(lat, lon)
        }
        localError = null
    }

    fun performSearch() {
        val trimmed = searchQuery.trim()
        if (trimmed.isEmpty()) return

        focusManager.clearFocus()
        localError = null
        searchMessage = null

        // Check if user pasted/typed raw coordinates
        val directCoords = CoordinateParser.parse(trimmed)
        if (directCoords != null) {
            val (lat, lon) = directCoords
            applyCoordinates(lat, lon, centerCamera = true)
            searchResults = emptyList()
            searchMessage = "✓ Applied coordinates: $lat, $lon"
            return
        }

        // Run geocoding search
        coroutineScope.launch {
            isSearching = true
            val results = LocationSearchManager.search(trimmed)
            isSearching = false
            searchResults = results
            if (results.isEmpty()) {
                searchMessage = "No places found for '$trimmed'. Try coordinates or another query."
            }
        }
    }

    val scrollState = rememberScrollState()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(scrollState)
                .padding(top = 18.dp, start = 20.dp, end = 20.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Header with Icon & Distinguished Branding
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            androidx.compose.ui.graphics.Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.tertiary
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "📍",
                        fontSize = 22.sp
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "GeoMock",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = "PRO",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "OpenStreetMap GPS Mock Provider & Road Simulator",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Status Card (Publishing / Simulation Status)
            StatusCard(serviceStatus = serviceStatus, localError = localError)

            // Mode Selector Tabs (Static Location vs Road Route Simulation)
            TabRow(
                selectedTabIndex = selectedModeTab,
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clip(RoundedCornerShape(14.dp))
            ) {
                Tab(
                    selected = selectedModeTab == 0,
                    onClick = { selectedModeTab = 0 },
                    text = {
                        Text(
                            text = "📍 Static Mock",
                            fontWeight = if (selectedModeTab == 0) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp
                        )
                    }
                )
                Tab(
                    selected = selectedModeTab == 1,
                    onClick = { selectedModeTab = 1 },
                    text = {
                        Text(
                            text = "🚗 Road Simulation",
                            fontWeight = if (selectedModeTab == 1) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp
                        )
                    }
                )
            }

            // Search / Coordinate Quick Paste Bar
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Search or Paste Location",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = {
                                searchQuery = it
                                searchMessage = null
                            },
                            placeholder = { Text("Search city or paste: 13.08, 80.27") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { performSearch() }),
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = {
                                        searchQuery = ""
                                        searchResults = emptyList()
                                        searchMessage = null
                                    }) {
                                        Text("✕", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Button(
                            onClick = { performSearch() },
                            shape = RoundedCornerShape(12.dp),
                            enabled = searchQuery.isNotBlank() && !isSearching
                        ) {
                            if (isSearching) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text("Search")
                            }
                        }
                    }

                    // Direct coordinate quick-apply chip
                    val detectedCoords = CoordinateParser.parse(searchQuery)
                    if (detectedCoords != null) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    applyCoordinates(detectedCoords.first, detectedCoords.second, centerCamera = true)
                                    searchMessage = "✓ Coordinates applied to map!"
                                    searchQuery = ""
                                },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "📍 Paste Coords: ${detectedCoords.first}, ${detectedCoords.second}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "Tap to Apply →",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    // Search Feedback Message
                    if (searchMessage != null) {
                        Text(
                            text = searchMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (searchMessage!!.startsWith("✓") || searchMessage!!.startsWith("📍") || searchMessage!!.startsWith("★")) {
                                Color(0xFF2E7D32)
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Search Results List
                    if (searchResults.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Results (${searchResults.size}):",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            searchResults.forEach { result ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            applyCoordinates(result.latitude, result.longitude, centerCamera = true)
                                            searchMessage = "✓ Selected: ${result.title}"
                                            searchResults = emptyList()
                                        },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = result.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            if (!result.subtitle.isNullOrBlank()) {
                                                Text(
                                                    text = result.subtitle,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1
                                                )
                                            }
                                            Text(
                                                text = String.format(Locale.US, "Lat: %.4f | Lon: %.4f", result.latitude, result.longitude),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                repository.saveLocation(
                                                    SavedLocation(
                                                        name = result.title.take(24),
                                                        latitude = result.latitude,
                                                        longitude = result.longitude,
                                                        accuracy = accuracyText.toFloatOrNull() ?: 5.0f,
                                                        icon = "📍",
                                                        description = result.subtitle ?: ""
                                                    )
                                                )
                                                applyCoordinates(result.latitude, result.longitude, centerCamera = true)
                                                searchMessage = "★ Saved to Bookmarks: ${result.title}"
                                                searchResults = emptyList()
                                            }
                                        ) {
                                            Text("★", color = MaterialTheme.colorScheme.primary, fontSize = 16.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Interactive OSM Map (Supports Static Pin + OSRM Polyline + Directional Rotating Vehicle)
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (selectedModeTab == 1) "OSRM Road Simulation Map" else "Interactive OSM Map",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (currentDeviceLocation != null) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF2563EB).copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = "● GPS Ready",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2563EB),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "osmdroid",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    MapLocationPicker(
                        selectedLatitude = mapLatitude,
                        selectedLongitude = mapLongitude,
                        onLocationSelected = { lat, lon ->
                            mapLatitude = lat
                            mapLongitude = lon
                            latitudeText = String.format(Locale.US, "%.6f", lat)
                            longitudeText = String.format(Locale.US, "%.6f", lon)
                            localError = null
                        },
                        currentDeviceLocation = currentDeviceLocation,
                        isLocating = isLocating,
                        onLocateMeClicked = { handleLocateMe() },
                        cameraTarget = cameraTarget,
                        route = calculatedRoute,
                        startPoint = calculatedRoute?.startPoint,
                        endPoint = calculatedRoute?.endPoint,
                        movingPoint = activeSimulationProgress?.currentPoint,
                        movingBearing = activeSimulationProgress?.bearing,
                        followLocation = followLocation
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { applyCoordinates(mapLatitude, mapLongitude, centerCamera = false) },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            enabled = !isAnyServiceRunning,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Use Map Pin")
                        }

                        if (currentDeviceLocation != null) {
                            OutlinedButton(
                                onClick = {
                                    val (cLat, cLon) = currentDeviceLocation!!
                                    applyCoordinates(cLat, cLon, centerCamera = true)
                                    searchMessage = "📍 Target set to current device GPS location"
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                                enabled = !isAnyServiceRunning,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Use Real GPS")
                            }
                        } else {
                            OutlinedButton(
                                onClick = { handleLocateMe() },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Locate Me")
                            }
                        }
                    }
                }
            }

            // CONTROLS BASED ON SELECTED MODE TAB
            if (selectedModeTab == 0) {
                // ================= MODE 1: STATIC MOCK LOCATION =================
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Target Coordinates",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )

                            TextButton(
                                onClick = { showSaveDialog = true },
                                enabled = isStaticInputValid && !isAnyServiceRunning
                            ) {
                                Text("★ Bookmark Spot")
                            }
                        }

                        OutlinedTextField(
                            value = latitudeText,
                            onValueChange = {
                                latitudeText = it
                                localError = null
                            },
                            label = { Text("Latitude (°)") },
                            placeholder = { Text("e.g. 13.0827 (-90 to 90)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isAnyServiceRunning
                        )

                        OutlinedTextField(
                            value = longitudeText,
                            onValueChange = {
                                longitudeText = it
                                localError = null
                            },
                            label = { Text("Longitude (°)") },
                            placeholder = { Text("e.g. 80.2707 (-180 to 180)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isAnyServiceRunning
                        )

                        OutlinedTextField(
                            value = accuracyText,
                            onValueChange = {
                                accuracyText = it
                                localError = null
                            },
                            label = { Text("Accuracy (meters)") },
                            placeholder = { Text("e.g. 5 (> 0)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isAnyServiceRunning
                        )
                    }
                }

                // Start & Stop Controls for Static Mocking
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { onStartStaticClicked() },
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp),
                        enabled = isStaticInputValid && !isAnyServiceRunning,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            text = "Start Mock Location",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    OutlinedButton(
                        onClick = { onStopClicked() },
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp),
                        enabled = isRunningStatic,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(
                            text = "Stop Mock Location",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            } else {
                // ================= MODE 2: ROAD-FOLLOWING SIMULATION =================
                RouteSimulationCard(
                    startLatText = startLatText,
                    startLonText = startLonText,
                    endLatText = endLatText,
                    endLonText = endLonText,
                    onStartLatChange = {
                        startLatText = it
                        calculatedRoute = null // Invalidate previous route
                        routingError = null
                    },
                    onStartLonChange = {
                        startLonText = it
                        calculatedRoute = null // Invalidate previous route
                        routingError = null
                    },
                    onEndLatChange = {
                        endLatText = it
                        calculatedRoute = null // Invalidate previous route
                        routingError = null
                    },
                    onEndLonChange = {
                        endLonText = it
                        calculatedRoute = null // Invalidate previous route
                        routingError = null
                    },
                    onSetStartToCurrentPin = {
                        startLatText = String.format(Locale.US, "%.6f", mapLatitude)
                        startLonText = String.format(Locale.US, "%.6f", mapLongitude)
                        calculatedRoute = null
                        routingError = null
                    },
                    onSetStartToDeviceGps = {
                        currentDeviceLocation?.let { (cLat, cLon) ->
                            startLatText = String.format(Locale.US, "%.6f", cLat)
                            startLonText = String.format(Locale.US, "%.6f", cLon)
                            calculatedRoute = null
                            routingError = null
                        }
                    },
                    onSetEndToCurrentPin = {
                        endLatText = String.format(Locale.US, "%.6f", mapLatitude)
                        endLonText = String.format(Locale.US, "%.6f", mapLongitude)
                        calculatedRoute = null
                        routingError = null
                    },
                    calculatedRoute = calculatedRoute,
                    isCalculatingRoute = isCalculatingRoute,
                    routingError = routingError,
                    onCalculateRoute = { onCalculateRouteClicked() },
                    onClearRoute = {
                        calculatedRoute = null
                        routingError = null
                    },
                    selectedSpeedKmh = selectedSpeedKmh,
                    onSpeedChange = { selectedSpeedKmh = it },
                    followLocation = followLocation,
                    onFollowLocationChange = { followLocation = it },
                    isSimulating = isSimulatingRoute,
                    isSimulationPaused = isSimulationPaused,
                    simulationProgress = activeSimulationProgress,
                    onStartSimulation = { onStartSimulationClicked() },
                    onPauseSimulation = { LocationForegroundService.pauseRouteSimulation(context) },
                    onResumeSimulation = { LocationForegroundService.resumeRouteSimulation(context) },
                    onStopSimulation = { onStopClicked() }
                )
            }

            // Saved Locations Section (Clean Bookmark Manager)
            SavedLocationsSection(
                savedLocations = savedLocations,
                currentLatitude = latitudeText.toDoubleOrNull() ?: mapLatitude,
                currentLongitude = longitudeText.toDoubleOrNull() ?: mapLongitude,
                onLocationSelected = { lat, lon, acc ->
                    applyCoordinates(lat, lon, centerCamera = true)
                    accuracyText = acc.toInt().toString()
                    searchMessage = "✓ Loaded bookmark"
                },
                onShowOnMap = { lat, lon ->
                    mapLatitude = lat
                    mapLongitude = lon
                    cameraTarget = Pair(lat, lon)
                },
                onDeleteLocation = { id ->
                    repository.deleteLocation(id)
                },
                onRestorePresets = {
                    repository.resetToDefaults()
                },
                onOpenSaveDialog = {
                    showSaveDialog = true
                },
                isMockRunning = isAnyServiceRunning
            )

            // Developer Options Guide Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "⚙️ Developer Setup Guide",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "1. Open Android Settings → Developer options\n" +
                                "2. Tap 'Select mock location app'\n" +
                                "3. Choose 'GeoMock - GPS Spoofer'",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            }

            // Anti-Rubberbanding Stability Guide Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "🛡️ Prevent Location Jumping",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "Anti-Jitter Active",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "If target apps bounce between real & mock location (rubber-banding):\n" +
                                "• Turn OFF 'Wi-Fi scanning' and 'Bluetooth scanning' in Location Services.\n" +
                                "• In 'Google Location Accuracy', turn OFF (or set to Device GPS Only).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                    OutlinedButton(
                        onClick = {
                            try {
                                context.startActivity(android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                            } catch (_: Exception) { }
                        },
                        modifier = Modifier.fillMaxWidth().height(38.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Open Location Settings ⚙️", fontSize = 12.sp)
                    }
                }
            }
        }
    }

    // Rich Save Location Dialog
    if (showSaveDialog) {
        val lat = latitudeText.trim().toDoubleOrNull() ?: mapLatitude
        val lon = longitudeText.trim().toDoubleOrNull() ?: mapLongitude
        val acc = accuracyText.trim().toFloatOrNull() ?: 5.0f

        SaveLocationDialog(
            latitude = lat,
            longitude = lon,
            accuracy = acc,
            onDismiss = { showSaveDialog = false },
            onSave = { savedLoc ->
                repository.saveLocation(savedLoc)
                showSaveDialog = false
                searchMessage = "★ Saved '${savedLoc.name}' to bookmarks"
            }
        )
    }
}

@Composable
private fun StatusCard(serviceStatus: ServiceStatus, localError: String?) {
    val displayedError = localError ?: (serviceStatus as? ServiceStatus.Error)?.message

    val (badgeBg, badgeText, statusText) = when {
        displayedError != null -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            "Error"
        )
        serviceStatus is ServiceStatus.SimulatingRoute -> {
            if (serviceStatus.isPaused) {
                Triple(Color(0xFFF59E0B), Color.White, "Simulation Paused")
            } else {
                Triple(Color(0xFF16A34A), Color.White, "Simulating Route")
            }
        }
        serviceStatus is ServiceStatus.RouteCompleted -> Triple(
            Color(0xFF0284C7),
            Color.White,
            "Route Completed"
        )
        serviceStatus is ServiceStatus.Running -> Triple(
            Color(0xFF2E7D32),
            Color.White,
            "Static Running"
        )
        else -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "Stopped"
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                displayedError != null -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                serviceStatus is ServiceStatus.SimulatingRoute -> Color(0xFFEFF6FF)
                serviceStatus is ServiceStatus.Running -> Color(0xFFE8F5E9)
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Publishing Status",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(badgeBg)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = statusText,
                        color = badgeText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
            )

            if (displayedError != null) {
                Text(
                    text = displayedError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            } else if (serviceStatus is ServiceStatus.SimulatingRoute) {
                val progress = serviceStatus.progress
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = String.format(Locale.US, "Speed: %.1f km/h | Bearing: %d° | Progress: %d%%", progress.speedKmh, progress.bearing.toInt(), (progress.progressFraction * 100).toInt()),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF0369A1),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = String.format(Locale.US, "Lat: %.6f | Lon: %.6f (Remaining: %.2f km)", progress.currentPoint.latitude, progress.currentPoint.longitude, progress.remainingDistanceMeters / 1000.0),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF0C4A6E)
                    )
                }
            } else if (serviceStatus is ServiceStatus.RouteCompleted) {
                Text(
                    text = String.format(Locale.US, "Destination reached! Total road distance covered: %.2f km", serviceStatus.route.distanceKm),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF0369A1)
                )
            } else if (serviceStatus is ServiceStatus.Running) {
                val loc = serviceStatus.location
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Broadcasting Static Coordinates (2 Hz):",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF1B5E20),
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Lat: ${loc.latitude} | Lon: ${loc.longitude} (±${loc.accuracy}m)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF1B5E20)
                    )
                }
            } else {
                Text(
                    text = "Mock location is currently inactive.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
