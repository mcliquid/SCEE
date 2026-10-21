package de.westnordost.streetcomplete.screens.main

import de.westnordost.streetcomplete.data.osm.geometry.ElementPointGeometry
import de.westnordost.streetcomplete.data.osm.geometry.ElementPolylinesGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.data.osm.edits.create.CreateNodeAction
import de.westnordost.streetcomplete.data.osm.edits.addNodeEdit
import de.westnordost.osmfeatures.FeatureDictionary
import de.westnordost.streetcomplete.data.search.*
import de.westnordost.streetcomplete.ui.LocalAppLocale
import de.westnordost.streetcomplete.ui.common.NonPredictiveBackHandler
import de.westnordost.streetcomplete.screens.main.map.layers.SearchTargetLayer
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import de.westnordost.streetcomplete.screens.main.map.layers.pedestrianRouteColor
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.westnordost.streetcomplete.ApplicationConstants
import de.westnordost.streetcomplete.screens.main.isMainUndoEnabled
import de.westnordost.streetcomplete.screens.settings.customGeometryFile
import de.westnordost.streetcomplete.screens.settings.loadGpxTrackPoints
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.readString
import androidx.compose.material.TextButton
import de.westnordost.streetcomplete.ui.common.feature.FeatureSearchDialog
import de.westnordost.streetcomplete.ui.common.dialogs.AlertDialog
import de.westnordost.streetcomplete.data.quest.VisibleQuestsSource
import de.westnordost.streetcomplete.data.routing.RoutingError
import de.westnordost.streetcomplete.data.routing.isRoutingCoordinateValid
import de.westnordost.streetcomplete.quests.tree.AddTreeGenus
import de.westnordost.streetcomplete.quests.custom.CustomQuestList
import de.westnordost.streetcomplete.data.preferences.Preferences
import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.download.ContinuousMapDownloadController
import de.westnordost.streetcomplete.data.messages.Message
import de.westnordost.streetcomplete.data.osmtracks.Trackpoint
import de.westnordost.streetcomplete.resources.*
import de.westnordost.streetcomplete.screens.MainNavViewModel
import de.westnordost.streetcomplete.screens.main.bottom_sheet.AddPoiForm
import de.westnordost.streetcomplete.screens.main.bottom_sheet.MainBottomSheet
import de.westnordost.streetcomplete.screens.main.controls.LocationState
import de.westnordost.streetcomplete.screens.main.controls.MainScreenControls
import de.westnordost.streetcomplete.screens.main.controls.PointerPinButton
import de.westnordost.streetcomplete.screens.main.edithistory.EditHistorySidebar
import de.westnordost.streetcomplete.screens.main.edithistory.EditHistoryViewModel
import de.westnordost.streetcomplete.screens.main.edithistory.icon
import de.westnordost.streetcomplete.screens.main.errors.LastCrashEffect
import de.westnordost.streetcomplete.screens.main.errors.LastDownloadErrorEffect
import de.westnordost.streetcomplete.screens.main.errors.LastUploadErrorEffect
import de.westnordost.streetcomplete.screens.main.map.BASE_STYLE
import de.westnordost.streetcomplete.screens.main.map.CameraInspectionEffect
import de.westnordost.streetcomplete.screens.main.map.MainMap
import de.westnordost.streetcomplete.screens.main.map.MainMapContent
import de.westnordost.streetcomplete.screens.main.map.MainMapTrackState
import de.westnordost.streetcomplete.screens.main.map.MainMapViewModel
import de.westnordost.streetcomplete.screens.main.map.PinsMode
import de.westnordost.streetcomplete.screens.main.map.getTrackBearing
import de.westnordost.streetcomplete.screens.main.map.offsetInWindow
import de.westnordost.streetcomplete.screens.main.map.positionAtCenter
import de.westnordost.streetcomplete.screens.main.map.rememberMainMapCameraState
import de.westnordost.streetcomplete.screens.main.map.toDpPadding
import de.westnordost.streetcomplete.screens.main.map.toStreetCompleteBoundingBox
import de.westnordost.streetcomplete.screens.main.messages.MessageDialog
import de.westnordost.streetcomplete.screens.main.urlconfig.ApplyUrlConfigDialog
import de.westnordost.streetcomplete.ui.common.ToastPopup
import de.westnordost.streetcomplete.ui.common.dialogs.ConfirmationDialog
import de.westnordost.streetcomplete.ui.common.quest.LocalGetOffsetCallback
import de.westnordost.streetcomplete.ui.common.quest.MapClick
import de.westnordost.streetcomplete.ui.common.quest.Marker
import de.westnordost.streetcomplete.ui.ktx.dir
import de.westnordost.streetcomplete.ui.theme.Dimensions
import de.westnordost.streetcomplete.ui.util.rememberSerializable
import de.westnordost.streetcomplete.util.ktx.toLatLon
import de.westnordost.streetcomplete.util.ktx.toPosition
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import de.westnordost.osmfeatures.GeometryType
import de.westnordost.streetcomplete.data.meta.CountryInfos
import de.westnordost.streetcomplete.data.meta.get
import de.westnordost.streetcomplete.osm.places.POPULAR_PLACE_FEATURE_IDS
import de.westnordost.streetcomplete.util.countryboundaries.CountryBoundaries
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.maplibre.compose.interaction.ClickEvent
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.location.HeadingRequest
import org.maplibre.compose.location.LocationEvent
import org.maplibre.compose.location.LocationMeasurement
import org.maplibre.compose.location.LocationPermission
import org.maplibre.compose.location.LocationUnavailableReason
import org.maplibre.compose.location.rememberDefaultHeadingProvider
import org.maplibre.compose.location.rememberDefaultLocationProvider
import org.maplibre.compose.location.rememberSystemSettingsLauncher
import org.maplibre.compose.map.MapRuntime
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.overlay.GeographicLayout
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.util.DpPadding
import org.maplibre.spatialk.units.Bearing
import org.maplibre.spatialk.units.extensions.inDegrees
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark

/** The map and its controls, forms, and sidebars. */
@Composable
fun MainScreen(
    tracks: MainMapTrackState,
    routePlanner: RoutePlannerViewModel,
    onClickSettings: () -> Unit,
    onClickQuestSettings: () -> Unit,
    onClickAbout: () -> Unit,
    onClickProfile: () -> Unit,
    onClickLogin: () -> Unit,
    onClickEnterTeamMode: () -> Unit,
    onShowOverlaysTutorial: () -> Unit,
    navViewModel: MainNavViewModel,
    hardwareKeys: Flow<HardwareKey> = emptyFlow(),
    textIntentUri: String? = null,
    onConsumedTextIntent: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = koinViewModel(),
    editHistoryViewModel: EditHistoryViewModel = koinViewModel(),
    mainBottomSheetViewModel: MainBottomSheetViewModel = koinViewModel(),
    mapViewModel: MainMapViewModel = koinViewModel(),
    runtime: MapRuntime = koinInject(),
) {
    //region state
    val scope = rememberCoroutineScope()
    val photon: PhotonSearchClient = koinInject()
    val placeSearch = remember(photon, scope) { PlaceSearchState(scope, photon::search) }
    val searchState by placeSearch.state.collectAsState()
    val appLocale = LocalAppLocale.current
    val searchLanguage = photonLanguage((appLocale.locale ?: appLocale.locales.localeList.firstOrNull())?.language ?: "default")
    var showSearchTargetActions by remember { mutableStateOf(false) }
    NonPredictiveBackHandler(isBackEnabled = searchState.open, onBackCompleted = placeSearch::close)
    val routing = routePlanner.routing
    val routingState by routing.state.collectAsState()
    val routeQuests = routePlanner.quests
    val routeDownloadConfirmation by routeQuests.confirmation.collectAsState()
    val routeQuestStates by routeQuests.alternatives.collectAsState()

    val headingProvider = rememberDefaultHeadingProvider()
    val locationProvider = rememberDefaultLocationProvider()

    val emailAppLauncher = rememberEmailAppLauncher()
    val mapAppLauncher = rememberMapAppLauncher()
    val systemSettingsLauncher = rememberSystemSettingsLauncher()

    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    // The upper controls are 48dp high with 4dp outer padding; leave a clear gap below.
    val routeInfoTop = 76.dp
    val routeInfoMaxHeight = minOf(
        with(density) { LocalWindowInfo.current.containerSize.height.toDp() } * 0.35f,
        if (routingState.routes.size > 1) 160.dp else 80.dp,
    )
    val routeCameraTop = routeInfoTop + routeInfoMaxHeight + 12.dp +
        with(density) { WindowInsets.safeDrawing.getTop(density).toDp() }

    val starsCount by viewModel.starsCount.collectAsState()
    val isShowingStarsCurrentWeek by viewModel.isShowingStarsCurrentWeek.collectAsState()

    val overlays by viewModel.overlays.collectAsState()
    val selectedOverlay by viewModel.selectedOverlay.collectAsState()
    val nearbyQuests by viewModel.nearbyQuests.collectAsState()
    val showQuickSettings by viewModel.showQuickSettings.collectAsState()
    val showOverlaySelector by viewModel.showOverlaySelector.collectAsState()
    val prefs: Preferences = koinInject()
    val continuousMapDownload: ContinuousMapDownloadController = koinInject()
    var gpxTrack by remember { mutableStateOf<List<LatLon>>(emptyList()) }
    var customGeoJson by remember { mutableStateOf<String?>(null) }
    val reversedQuestOrder by viewModel.reverseQuestOrder.collectAsState()
    val customQuestList: CustomQuestList = koinInject()
    val visibleQuestsSource: VisibleQuestsSource = koinInject()
    var showAddPoiDialog by remember { mutableStateOf(false) }
    var addPoiSelection by remember { mutableStateOf<Pair<de.westnordost.osmfeatures.Feature, LatLon>?>(null) }
    val isCreateNodeEnabled by remember { derivedStateOf { selectedOverlay?.isCreateNodeEnabled == true } }

    val isAutoSync by viewModel.isAutoSync.collectAsState()
    val unsyncedEditsCount by viewModel.unsyncedEditsCount.collectAsState()

    val isTeamMode by viewModel.isTeamMode.collectAsState()
    val indexInTeam by viewModel.indexInTeam.collectAsState()

    val messagesCount by viewModel.messagesCount.collectAsState()

    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val isUploading by viewModel.isUploading.collectAsState()
    val isUploadingOrDownloading by viewModel.isUploadingOrDownloading.collectAsState()

    val urlConfig by navViewModel.urlConfig.collectAsState()

    val lastCrashReport by viewModel.lastCrashReport.collectAsState()
    val lastDownloadError by viewModel.lastDownloadError.collectAsState()
    val lastUploadError by viewModel.lastUploadError.collectAsState()

    val showZoomButtons by viewModel.showZoomButtons.collectAsState()

    val isRequestingLogin by viewModel.isRequestingLogin.collectAsState()

    val geoUri by navViewModel.geoUri.collectAsState()

    var confirmReplaceDownload by remember { mutableStateOf(false) }
    var showMainMenuDialog by remember { mutableStateOf(false) }
    var showLocationPermissionRationaleDialog by remember { mutableStateOf(false) }
    var showApplicationSettingsDialog by remember { mutableStateOf(false) }
    var showLocationSettingsDialog by remember { mutableStateOf(false) }
    var shownMessage by remember { mutableStateOf<Message?>(null) }
    var showToast by remember { mutableStateOf<Toast?>(null) }
    var showMapContextMenu by remember { mutableStateOf(false) }
    // retained while the context menu is dismissed so that it does not move during its exit animation
    var lastMapLongClick by remember { mutableStateOf<MapClick?>(null) }
    var lastQuestSolved by remember { mutableStateOf<QuestSolvedEvent?>(null) }

    var location by rememberSerializable { mutableStateOf<LocationMeasurement?>(null) }
    var routingLocationMark by remember { mutableStateOf<TimeMark?>(null) }
    var heading by remember { mutableStateOf<Float?>(null) }
    var locationState by remember { mutableStateOf<LocationState?>(null) }

    val sheet = rememberMainSheetState(mainBottomSheetViewModel, editHistoryViewModel)
    LaunchedEffect(sheet.isOpen) {
        if (sheet.isOpen) { placeSearch.close(); showSearchTargetActions = false }
    }

    var pendingSheetSelection by remember { mutableStateOf<MainSheetSelection?>(null) }

    val sheetSelection = sheet.selection
    val shownBottomSheet = sheet.shownBottomSheet
    //endregion

    //region map state

    val downloadedTiles by mapViewModel.downloadedTiles.collectAsStateWithLifecycle()

    var mapPositionInWindow by remember { mutableStateOf(Offset.Zero) }

    val initialCamera = remember(viewModel) { viewModel.initialCamera }
    val highlightedMarkers by produceState<List<Marker>>(emptyList(), shownBottomSheet) {
        value = shownBottomSheet?.let { mainBottomSheetViewModel.getHighlightedMarkers(it) }.orEmpty()
    }

    // SCEE: other quests near the opened quest. Reset first, so markers of a previous quest never linger
    val nearby by produceState(NearbyQuests.EMPTY, shownBottomSheet) {
        value = NearbyQuests.EMPTY
        value = shownBottomSheet?.let { mainBottomSheetViewModel.getNearbyQuests(it) } ?: NearbyQuests.EMPTY
    }

    // form markers take precedence over highlighted and nearby quest markers
    val markers = sheet.formMarkers ?: remember(highlightedMarkers, nearby) {
        mergeMarkersAtSamePosition(highlightedMarkers, nearby.markers)
    }


    val pinsMode = when (sheetSelection) {
        is MainSheetSelection.EditHistory -> PinsMode.EditHistory
        null -> PinsMode.Quests
        else -> if (shownBottomSheet != null) PinsMode.None else PinsMode.Quests
    }

    var formOverlayVisible by remember(sheet.id) { mutableStateOf(true) }
    val showOverlay =
        formOverlayVisible &&
        selectedOverlay != null
        && (sheetSelection is MainSheetSelection.Overlay || sheetSelection == null)

    val baseStyle = remember { BaseStyle.Json(BASE_STYLE) }
    val mapState = rememberMapState(
        runtime = runtime,
        baseStyle = baseStyle,
        initialCameraPosition = initialCamera
    ) {
        MainMapContent(
            viewModel = mapViewModel,
            location = location,
            heading = heading,
            isRecording = tracks.isRecording,
            trackpoints = tracks.recentTrackPositions,
            oldTrackpointsLists = tracks.olderTrackPositions,
            gpxTrack = gpxTrack,
            pedestrianRoutes = routingState.routes,
            selectedPedestrianRoute = routingState.selectedIndex,
            customGeoJson = customGeoJson,
            shownBottomSheet = shownBottomSheet,
            selectedEdit = sheet.shownEdit,
            shownMarkers = markers,
            hiddenLabels = selectedOverlay?.hiddenLabels.orEmpty(),
            showOverlay = showOverlay,
            downloadedTiles = downloadedTiles,
            pinsMode = pinsMode,
            consumeClick = !sheet.isOpen,
            onClickQuest = { pendingSheetSelection = MainSheetSelection.Quest(it) },
            onClickEdit = { pendingSheetSelection = MainSheetSelection.EditHistory(it) },
            onClickElement = { key ->
                pendingSheetSelection = selectedOverlay?.let { MainSheetSelection.Overlay(it.name, key) }
            },
        )
        searchState.selected?.let { place ->
            SearchTargetLayer(place.position) {
                if (!sheet.isOpen && !searchState.open) {
                    showSearchTargetActions = true
                    ClickResult.Consume
                } else ClickResult.Pass
            }
        }
    }

    val cameraState = rememberMainMapCameraState(mapState, viewModel.initiallyFollowing, viewModel.initiallyNavigating)
    LaunchedEffect(routingState.routes) {
        routingState.routes.firstOrNull()?.let { route ->
            cameraState.showRoute(
                ElementPolylinesGeometry(routingState.routes.map { it.points }, route.points.first()),
                DpPadding(left = 48.dp, right = 48.dp, top = routeCameraTop, bottom = 100.dp),
            )
        }
    }

    val sheetPadding = Dimensions.getOpenQuestFormMapPadding(
        LocalWindowInfo.current,
        WindowInsets.safeDrawing.asPaddingValues()
    ).toDpPadding(layoutDirection)

    //endregion

    //region actions

    fun routeHere(destination: LatLon?) {
        val start = runCatching { location?.position?.toLatLon() }.getOrNull()
        if (location == null) showToast = Toast.RouteNoLocation
        else if (start == null || !start.isRoutingCoordinateValid() || routingLocationMark?.elapsedNow()?.let { it < 0.milliseconds || it > 2.minutes } != false) {
            showToast = Toast.RouteStaleLocation
        } else if (!viewModel.isConnected) showToast = Toast.Offline
        else if (destination != null && destination.isRoutingCoordinateValid()) routing.start(start, destination)
    }

    fun getOffset(position: LatLon): Offset? =
        mapState.offsetInWindow(position, mapPositionInWindow, density)

    // the crosshair is at the map center, see Crosshair()
    fun getCrosshairPosition(): LatLon? =
        mapState.positionAtCenter(DpPadding.Zero)

    fun getMetersPerDp(): Double =
        mapState.metersPerDpAtLatitude(mapState.cameraPosition.center.latitude) ?: 0.0

    fun ClickEvent.toMapClick(): MapClick? =
        position?.let { MapClick(it.toLatLon(), screenOffset, clickAreaSizeInMeters = getMetersPerDp() * 20) }

    fun followPosition() {
        scope.launch {
            cameraState.followPosition(location?.position?.toLatLon(), getTrackBearing(tracks.currentTrack))
        }
    }

    fun jumpToEdit() {
        scope.launch {
            sheet.shownEdit?.geometry?.let { cameraState.focus(it, DpPadding.Zero) }
        }
    }

    fun jumpToBottomSheetFocus() {
        scope.launch {
            shownBottomSheet?.geometry?.let { cameraState.focus(it, sheetPadding) }
        }
    }

    fun toggleNavigationMode() {
        if (!canToggleNavigationMode(prefs, cameraState.isNavigationMode)) return
        scope.launch {
            cameraState.setNavigationMode(
                value = !cameraState.isNavigationMode,
                position = location?.position?.toLatLon(),
                bearing = getTrackBearing(tracks.currentTrack)
            )
        }
    }

    fun zoomBy(amount: Double) {
        scope.launch {
            cameraState.zoomBy(amount)
        }
    }

    fun composeNote(position: LatLon, trackpoints: List<Trackpoint>? = null) {
        sheet.show(MainSheetSelection.CreateNote(position, trackpoints))
    }

    fun download(enqueue: Boolean = false) {
        val displayedArea = mapState.viewport?.visibleBounds?.toStreetCompleteBoundingBox()
        if (displayedArea == null) {
            showToast = Toast.CannotFindBounds
        } else if (!viewModel.download(displayedArea, mapState.cameraPosition.center.toLatLon(), enqueue)) {
            showToast = Toast.DownloadAreaTooBig
        }
    }

    fun onClickLocation() {
        val permission = locationProvider.permission.value
        if (permission is LocationPermission.NotGranted) {
            when {
                permission.canRequest == false -> {
                    if (systemSettingsLauncher.canOpenApplicationSettings) showApplicationSettingsDialog = true
                    else showToast = Toast.NoLocation
                }
                permission.shouldShowRationale -> showLocationPermissionRationaleDialog = true
                else -> locationProvider.requestPermission()
            }
        } else if (locationState == LocationState.ALLOWED) {
            if (systemSettingsLauncher.canOpenLocationServicesSettings) showLocationSettingsDialog = true
            else showToast = Toast.NoLocation
        // while the placement crosshair is shown, the GPS button also recenters an open form while following
        } else if (!cameraState.isFollowingPosition || (isCreateNodeEnabled && cameraState.isSheetOpen)) {
            followPosition()
        } else {
            toggleNavigationMode()
        }
    }

    fun onClickDownload() {
        if (viewModel.isConnected) {
            if (viewModel.isUserInitiatedDownloadInProgress) {
                confirmReplaceDownload = true
            } else {
                download()
            }
        } else {
            showToast = Toast.Offline
        }
    }

    fun onClickUpload() {
        if (viewModel.isConnected) {
            viewModel.upload()
        } else {
            showToast = Toast.Offline
        }
    }

    fun sendErrorReport(errorReport: String) {
        if (!emailAppLauncher.isAvailable()) {
            showToast = Toast.NoEmailClient
        } else {
            emailAppLauncher.compose(
                email = ApplicationConstants.ERROR_REPORTS_EMAIL,
                subject = ApplicationConstants.USER_AGENT + " " + "Error Report",
                body = "Describe how to reproduce it here:\n\n\n\n$errorReport"
            )
        }
    }

    fun sendErrorReport(error: Exception) {
        scope.launch {
            val report = viewModel.createErrorReport(error)
            sendErrorReport(report)
        }
    }
    //endregion

    //region effects

    LaunchedEffect(pendingSheetSelection, sheet.isOpen) {
        val selection = pendingSheetSelection
        if (selection != null) {
            if (!sheet.isOpen) {
                pendingSheetSelection = null
                sheet.show(selection)
            }
        }
    }

    LaunchedEffect(geoUri) {
        geoUri?.let {
            cameraState.moveTo(it)
            navViewModel.consumeGeoUri()
        }
    }

    // Apply the MapLibre viewport to StreetComplete's quest and overlay data sources.
    LaunchedEffect(mapState, mapViewModel) {
        snapshotFlow { mapState.cameraPosition.zoom to mapState.viewport?.visibleBounds }
            .collect { (zoom, bounds) ->
                val scBounds = bounds?.toStreetCompleteBoundingBox()
                mapViewModel.onViewportChanged(zoom, scBounds)
                continuousMapDownload.onViewportChanged(zoom, scBounds)
            }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        continuousMapDownload.setForeground(true)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        continuousMapDownload.setForeground(false)
        viewModel.saveCamera(
            camera = mapState.cameraPosition,
            following = cameraState.isFollowingPosition,
            navigating = cameraState.isNavigationMode
        )
    }

    LaunchedEffect(headingProvider) {
        val request = HeadingRequest { minimumInterval = 33.milliseconds }
        headingProvider.updates(request).collect { headingMeasurement ->
            heading = (headingMeasurement.bearing - Bearing.North).inDegrees.toFloat()
        }
    }

    LaunchedEffect(locationProvider) {
        val request = mainLocationRequest(prefs)
        val updates = if (request != null) locationProvider.updates(request) else locationProvider.updates()
        val throttle = mainLocationUpdateThrottle(prefs)
        updates.collect { event ->
            when (event) {
                is LocationEvent.Update -> {
                    // Ignore stale last-known positions that would jump the camera on startup.
                    if (event.measurementMark.elapsedNow() > 10.minutes) return@collect
                    if (!throttle.accept(event.measurementMark)) return@collect
                    val measurement = event.measurement
                    location = measurement
                    routingLocationMark = event.measurementMark
                    locationState = LocationState.UPDATING
                    tracks.addLocation(measurement)
                    launch {
                        cameraState.animateToPositionIfFollowing(
                            position = measurement.position.toLatLon(),
                            bearing = getTrackBearing(tracks.currentTrack)
                        )
                    }
                }
                is LocationEvent.Unavailable -> {
                    throttle.reset()
                    location = null
                    routingLocationMark = null
                    locationState = when (event.reason) {
                        LocationUnavailableReason.ServicesDisabled -> LocationState.ALLOWED
                        LocationUnavailableReason.TemporarilyUnavailable -> LocationState.SEARCHING
                        LocationUnavailableReason.PermissionDenied -> LocationState.DENIED
                        else -> null // unsupported or unclassified
                    }

                    tracks.clear()
                    launch { cameraState.setNavigationMode(false, null, null) }
                }
                else -> Unit
            }
        }
    }

    CameraInspectionEffect(cameraState, sheet, location?.position, tracks, sheetPadding)

    LaunchedEffect(selectedOverlay) {
        val selection = sheet.selection as? MainSheetSelection.Overlay
        if (selection != null && selection.name != selectedOverlay?.name) sheet.close()
    }

    // Clear nearby-quest highlight list when the bottom sheet closes (SCEE).
    LaunchedEffect(sheet.isFormOpen, nearby) {
        viewModel.nearbyQuests.value = if (sheet.isFormOpen) nearby.groups else emptyList()
    }

    // updated on every composition: the collector below lives longer than a single camera state
    val onHardwareKeyAction = rememberUpdatedState<(HardwareKeyAction) -> Unit> { action ->
        val zoom = action.zoomAmount()
        if (zoom != null) zoomBy(zoom) else showMainMenuDialog = true
    }
    LaunchedEffect(hardwareKeys) {
        hardwareKeys.collectHardwareKeyActions(onHardwareKeyAction)
    }

    LaunchedEffect(mainBottomSheetViewModel) {
        mainBottomSheetViewModel.chainedQuestSelection.collect { next ->
            sheet.show(next)
        }
    }

    LaunchedEffect(Unit) { reloadMapExtras(prefs) { gpx, geoJson -> gpxTrack = gpx; customGeoJson = geoJson } }
    LaunchedEffect(reversedQuestOrder) {
        mapViewModel.setReversedQuestOrder(reversedQuestOrder)
    }

    LaunchedEffect(isTeamMode) {
        // always show this toast on start to remind user that it is still on
        if (isTeamMode) {
            showToast = Toast.TeamModeActive
        }
        // show this only once when turning it off
        else if (viewModel.teamModeChanged) {
            showToast = Toast.TeamModeDeactivated
            viewModel.teamModeChanged = false
        }
    }

    lastDownloadError?.let { error ->
        LastDownloadErrorEffect(lastError = error, onReportError = ::sendErrorReport)
    }
    lastUploadError?.let { error ->
        LastUploadErrorEffect(lastError = error, onReportError = ::sendErrorReport)
    }
    lastCrashReport?.let { report ->
        LastCrashEffect(lastReport = report, onReport = ::sendErrorReport)
    }

    //endregion

    //region content

    Box(modifier) {
        MainMap(
            state = mapState,
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { mapPositionInWindow = it.positionInWindow() },
            onPan = { cameraState.onPan(location != null) },
            onRotate = { cameraState.onRotate(location != null) },
            onMapClick = { event ->
                when (sheet.selection) {
                    null -> {}
                    is MainSheetSelection.EditHistory -> sheet.close()
                    // forms react to clicks near the click position, e.g. to suggest a name
                    else -> event.toMapClick()?.let { sheet.lastMapClick = it }
                }
                ClickResult.Consume
            },
            onMapLongClick = { event ->
                if (!sheet.isOpen) {
                    lastMapLongClick = event.toMapClick()
                    showMapContextMenu = lastMapLongClick != null
                }
                ClickResult.Consume
            },
            overlay = {
                sheet.formMapOverlay?.invoke(this)

                GeographicLayout(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
                    val shownEdit = sheet.shownEdit?.edit
                    shownEdit?.position?.toPosition()?.let { position ->
                        val icon = shownEdit.icon ?: Res.drawable.empty_128
                        PointerPinButton(
                            targetPosition = position,
                            onClick = ::jumpToEdit,
                            contentPadding = PaddingValues(6.dp),
                        ) {
                            Image(painterResource(icon), null, modifier = Modifier.size(36.dp))
                        }
                    }

                    shownBottomSheet?.position?.toPosition()?.let { position ->
                        PointerPinButton(
                            targetPosition = position,
                            onClick = ::jumpToBottomSheetFocus,
                            contentPadding = PaddingValues(6.dp),
                        ) {
                            Image(painterResource(shownBottomSheet.icon), null, modifier = Modifier.size(36.dp))
                        }
                    }

                    val position = location?.position
                    if (position != null) {
                        PointerPinButton(targetPosition = position, onClick = ::followPosition) {
                            Image(painterResource(Res.drawable.location_dot_small), null)
                        }
                    }
                }

                MainScreenControls(
                    starsCount = starsCount,
                    isShowingStarsCurrentWeek = isShowingStarsCurrentWeek,
                    isUploadingOrDownloading = isUploadingOrDownloading,
                    onToggleShowStarsCurrentWeek = { viewModel.toggleShowingCurrentWeek() },

                    messagesCount = messagesCount,
                    onClickMessages = { scope.launch { shownMessage = viewModel.popMessage() } },

                    overlays = overlays,
                    selectedOverlay = selectedOverlay,
                    onSelectOverlay = viewModel::selectOverlay,
                    onShowOverlaysTutorial = onShowOverlaysTutorial.takeUnless {
                        viewModel.hasShownOverlaysTutorial
                    },

                    shownUnsyncedEdits = if (!isAutoSync) unsyncedEditsCount else 0,
                    shownIndexInTeam = if (isTeamMode) indexInTeam else null,
                    onClickMainMenu = { showMainMenuDialog = true },

                    showZoomButtons = showZoomButtons,
                    onClickZoomIn = { zoomBy(1.0) },
                    onClickZoomOut = { zoomBy(-1.0) },
                    onZoomDrag = { zoomBy(it / 20.0) },

                    mapRotation = { mapState.cameraPosition.bearing.toFloat() },
                    mapTilt = { mapState.cameraPosition.pitch.toFloat() },
                    onClickCompass = { scope.launch { cameraState.resetCompass() } },

                    locationState = locationState,
                    isNavigationMode = cameraState.isNavigationMode,
                    isFollowingPosition = cameraState.isFollowingPosition,
                    onClickLocation = ::onClickLocation,

                    isRecordingTracks = tracks.isRecording,
                    onClickStopTrackRecording = {
                        val recorded = tracks.stopRecording()
                        val latLon = location?.position?.toLatLon()
                        if (latLon != null) {
                            composeNote(latLon, recorded.takeIf { it.isNotEmpty() })
                        }
                    },

                    isCreateNodeEnabled = isCreateNodeEnabled,
                    onClickCreate = {
                        if (mapState.cameraPosition.zoom >= 17.0) {
                            selectedOverlay?.let { sheet.show(MainSheetSelection.Overlay(it.name)) }
                        } else {
                            showToast = Toast.DownloadAreaTooBig
                        }
                    },

                    hasEdits = sheet.hasEdits,
                    isUndoEnabled = isMainUndoEnabled(isUploading),
                    onClickUndo = sheet::showEditHistory,

                    metersPerDp = { getMetersPerDp() },

                    showOverlaySelector = showOverlaySelector,
                    nearbyQuests = nearbyQuests,
                    showQuestDetails = { quest -> sheet.show(MainSheetSelection.Quest(quest.key)) },
                    showQuickSettings = showQuickSettings,
                    viewModel = viewModel,
                    camera = mapState.cameraPosition,
                    onClickSearch = if (!sheet.isOpen) ({ showSearchTargetActions = false; placeSearch.open() }) else null,
                )
            },
        )

        val dir = LocalLayoutDirection.current.dir
        AnimatedVisibility(
            visible = sheetSelection is MainSheetSelection.EditHistory && sheet.hasEdits,
            enter = fadeIn() + slideInHorizontally(initialOffsetX = { -it * dir }),
            exit = fadeOut() + slideOutHorizontally(targetOffsetX = { -it * dir }),
        ) {
            EditHistorySidebar(
                editItems = sheet.editItems.orEmpty(),
                selectedEdit = sheet.shownEdit?.edit,
                onSelectEdit = { sheet.show(MainSheetSelection.EditHistory(it.key)) },
                isUploading = isUploading,
                onUndoEdit = { editHistoryViewModel.undo(it.key) },
                onDismissRequest = sheet::close,
                getEditElement = editHistoryViewModel::getEditElement,
            )
        }

        if (!sheet.isOpen && !searchState.open && !showSearchTargetActions && (routingState.loading || routingState.route != null)) {
            Surface(
                modifier = Modifier.align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing).padding(top = routeInfoTop),
                elevation = 4.dp,
                shape = MaterialTheme.shapes.small,
            ) {
                Column(Modifier.heightIn(max = routeInfoMaxHeight)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (routingState.loading) {
                        LinearProgressIndicator()
                        Text(stringResource(Res.string.route_loading))
                    }
                    for ((index, route) in routingState.routes.withIndex()) {
                        val questState = routeQuestStates.getOrNull(index)?.takeIf { it.points == route.points }
                        val multiple = routingState.routes.size > 1
                        val selected = index == routingState.selectedIndex
                        Column(if (multiple) Modifier.selectable(selected, role = Role.RadioButton,
                            onClick = { routing.select(index) })
                            .background(if (selected) MaterialTheme.colors.primary.copy(alpha = 0.12f) else androidx.compose.ui.graphics.Color.Transparent)
                            .padding(horizontal = 6.dp, vertical = 4.dp) else Modifier) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (multiple) {
                                    Box(Modifier.size(10.dp).background(pedestrianRouteColor(index)))
                                    Spacer(Modifier.size(6.dp))
                                    Text(stringResource(Res.string.route_alternative, index + 1),
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                                    Spacer(Modifier.size(6.dp))
                                }
                                Text(stringResource(Res.string.route_summary, routeDistance(route.distanceMeters), routeMinutes(route.durationSeconds)),
                                    fontWeight = if (multiple && selected) FontWeight.Bold else FontWeight.Normal)
                            }
                            if (questState != null) {
                                val message = when (questState.status) {
                                    RouteQuestStatus.Loading -> Res.string.route_quests_loading
                                    RouteQuestStatus.Counting -> Res.string.route_quests_counting
                                    RouteQuestStatus.Complete -> Res.string.route_quests_count
                                    RouteQuestStatus.Incomplete -> Res.string.route_quests_incomplete
                                    RouteQuestStatus.Error -> Res.string.route_quests_error
                                    RouteQuestStatus.AwaitingConfirmation -> Res.string.route_quests_confirmation_pending
                                    RouteQuestStatus.TooLarge -> Res.string.route_quests_too_large
                                }
                                Text(stringResource(message, questState.keys.size), style = MaterialTheme.typography.caption)
                                if (selected && questState.status == RouteQuestStatus.Loading) {
                                    TextButton(onClick = routeQuests::cancelDownload) { Text(stringResource(Res.string.cancel)) }
                                }
                                if (selected && (questState.status == RouteQuestStatus.Incomplete || questState.status == RouteQuestStatus.Error)) {
                                    TextButton(onClick = { routeQuests.retry() }) { Text(stringResource(Res.string.route_quests_retry)) }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (searchState.open && !sheet.isOpen) {
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding().padding(horizontal = 8.dp, vertical = 4.dp), contentAlignment = Alignment.TopCenter) {
                PlaceSearchPanel(searchState,
                    onQuery = { text ->
                        val camera = mapState.cameraPosition
                        placeSearch.update(PhotonQuery(text, camera.center.toLatLon(), camera.zoom, searchLanguage))
                    },
                    onClose = placeSearch::close,
                    onRetry = placeSearch::retry,
                    onSelect = { place ->
                        placeSearch.select(place)
                        showSearchTargetActions = true
                        val camera = mapState.cameraPosition
                        cameraState.moveTo(camera.copy(center = place.position.toPosition(), zoom = place.zoom,
                            pitch = 0.0, padding = DpPadding.Zero))
                    })
            }
        }
        val target = searchState.selected
        if (target != null && showSearchTargetActions && !searchState.open && !sheet.isOpen) {
            Surface(Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(top = routeInfoTop, start = 8.dp, end = 8.dp), elevation = 4.dp, shape = MaterialTheme.shapes.small) {
                Column(Modifier.heightIn(max = routeInfoMaxHeight + 60.dp).verticalScroll(rememberScrollState()).padding(8.dp)) {
                    Text(target.name, style = MaterialTheme.typography.subtitle1)
                    if (target.context.isNotEmpty()) Text(target.context, style = MaterialTheme.typography.caption)
                    FlowRow {
                        TextButton(onClick = { showSearchTargetActions = false; routeHere(target.position) }) { Text(stringResource(Res.string.route_here)) }
                        TextButton(onClick = { placeSearch.removeMarker(); showSearchTargetActions = false }) { Text(stringResource(Res.string.place_search_remove_marker)) }
                        IconButton(onClick = { showSearchTargetActions = false }) {
                            Icon(painterResource(Res.drawable.ic_clear_24), stringResource(Res.string.cancel))
                        }
                    }
                }
            }
        }

        AnimatedContent(
            targetState = shownBottomSheet?.let { sheet.id to it },
            contentKey = { it?.first },
            transitionSpec = {
                if (initialState != null && targetState != null) {
                    fadeIn() + slideInVertically { it / 16 } togetherWith fadeOut()
                } else {
                    // Size transform with snap is necessary so that it doesn't animate the bounds
                    // from zero (=no form) which looks weird
                    (fadeIn() + slideInVertically { it } togetherWith
                    fadeOut() + slideOutVertically { it / 2 }) using SizeTransform(clip = false)
                }
            },
        ) { content ->
            if (content != null) {
                val (id, shownBottomSheet) = content
                val mapCamera = mapState.cameraPosition
                sheet.formStateHolder.SaveableStateProvider(id) {
                    CompositionLocalProvider(
                        LocalGetOffsetCallback provides { getOffset(it) ?: Offset.Zero },
                    ) {
                    MainBottomSheet(
                        onDismiss = sheet::close,
                        onSolved = { icon, position ->
                            if (shouldShowSolvedAnimation(prefs)) {
                                getOffset(position)?.let { lastQuestSolved = QuestSolvedEvent(icon, it) }
                            }
                        },
                        onHideQuest = { key, tempHide -> mainBottomSheetViewModel.hideQuest(key, tempHide) },
                        isSurvey = mainBottomSheetViewModel::isSurvey,
                        onSubmitEdit = { type, geometry, action, hasExtra, key -> mainBottomSheetViewModel.submitEdit(type, geometry, action, hasExtra, key) },
                        onCommentNote = { note, text, images, close -> mainBottomSheetViewModel.commentNote(note, text, images, close) },
                        onCreateNote = { pos, text, images, tracks, isGpx -> mainBottomSheetViewModel.createNote(pos, text, images, tracks, isGpx) },
                        shownBottomSheet = shownBottomSheet,
                        mapRotation = mapCamera.bearing.toFloat(),
                        mapTilt = mapCamera.pitch.toFloat(),
                        mapPosition = getCrosshairPosition() ?: mapCamera.center.toLatLon(),
                        mapMetersPerDp = getMetersPerDp(),
                        onSetMapMarkers = { if (id == sheet.id) sheet.formMarkers = it?.toList() },
                        onSetMapOverlay = { if (id == sheet.id) sheet.formMapOverlay = it },
                        onSetOverlayVisible = { if (id == sheet.id) formOverlayVisible = it },
                        lastMapClick = sheet.lastMapClick,
                    )
                    }
                }
            }
        }
    }

    lastQuestSolved?.let { LastQuestSolvedEffect(it) }

    MapContextMenu(
        expanded = showMapContextMenu,
        onDismissRequest = { showMapContextMenu = false },
        onClickCreateNote = {
            if (mapState.cameraPosition.zoom < ApplicationConstants.NOTE_MIN_ZOOM) showToast = Toast.ImpreciseNote
            else lastMapLongClick?.let { composeNote(it.position) }
        },
        onClickCreateTrack = { tracks.startRecording() },
        onClickRouteHere = { routeHere(lastMapLongClick?.position) },
        onClickRemoveRoute = { routeQuests.setRoute(emptyList()); routing.clear() },
        canRemoveRoute = routingState.route != null || routingState.loading,
        isOpenLocationAvailable = mapAppLauncher.isAvailable(),
        onClickOpenLocation = {
            lastMapLongClick?.let { mapAppLauncher.openAt(it.position, mapState.cameraPosition.zoom) }
        },
        onClickAddNode = { showAddPoiDialog = true },
        onClickInsertNode = {
            lastMapLongClick?.let { sheet.show(MainSheetSelection.InsertNode(it.position)) }
        },
        isExpertMode = prefs.expertMode,
        offset = lastMapLongClick?.screenOffset ?: DpOffset.Zero,
    )

    if (showAddPoiDialog) {
        val featureDictionary: FeatureDictionary = koinInject()
        val countryBoundaries: CountryBoundaries = koinInject()
        val countryInfos: CountryInfos = koinInject()
        val countryInfo = remember(lastMapLongClick) {
            lastMapLongClick?.position?.let { countryInfos.get(countryBoundaries, it) }
        }
        val defaultFeatureIds = recentFeatureIds(
            prefs, Prefs.CREATE_POI_RECENT_FEATURE_IDS, POPULAR_PLACE_FEATURE_IDS, exclude = setOf("shop")
        )
        FeatureSearchDialog(
            onDismissRequest = { showAddPoiDialog = false },
            onSelectedFeature = { feature ->
                showAddPoiDialog = false
                val position = lastMapLongClick?.position ?: return@FeatureSearchDialog
                addRecentFeatureId(prefs, Prefs.CREATE_POI_RECENT_FEATURE_IDS, feature.id)
                addPoiSelection = feature to position
            },
            featureDictionary = featureDictionary,
            geometryType = GeometryType.POINT,
            countryCode = countryInfo?.countryOrSubdivisionCode,
            officialLanguages = countryInfo?.officialLanguages.orEmpty(),
            codesOfDefaultFeatures = defaultFeatureIds.reversed(),
            showKeyboardInitially = prefs.getBoolean(Prefs.CREATE_NODE_SHOW_KEYBOARD, true),
        )
    }

    addPoiSelection?.let { (feature, position) ->
        AddPoiForm(
            feature = feature,
            position = position,
            onAdd = {
                mainBottomSheetViewModel.submitEdit(
                    addNodeEdit,
                    ElementPointGeometry(it.position),
                    CreateNodeAction(it.position, it.tags),
                )
                addPoiSelection = null
            },
            onDismiss = { addPoiSelection = null },
            onSetMapMarkers = { sheet.formMarkers = it.toList() },
        )
    }

    if (textIntentUri != null) {
        AlertDialog(
            onDismissRequest = onConsumedTextIntent,
            buttonRow = {
                TextButton(onConsumedTextIntent) {
                    Text(stringResource(Res.string.cancel))
                }
                TextButton({
                    customQuestList.readFromUri(textIntentUri)
                    visibleQuestsSource.clearCache()
                    onConsumedTextIntent()
                }) { Text(stringResource(Res.string.pref_custom_title)) }
                TextButton({
                    AddTreeGenus.readFromUri(textIntentUri)
                    onConsumedTextIntent()
                }) { Text(stringResource(Res.string.pref_trees_title)) }
            }
        )
    }

    //endregion

    //region dialogs and toast popups

    routeDownloadConfirmation?.let { request ->
        ConfirmationDialog(
            onDismissRequest = { if (routeQuests.confirmation.value == request) routeQuests.cancelDownload() },
            onConfirmed = { routeQuests.confirmDownload(request) },
            title = { Text(stringResource(Res.string.route_quests_download_title)) },
            text = { Text(stringResource(Res.string.route_quests_download_confirmation, request.tiles, request.areaSqKm, request.batches)) },
        )
    }
    if (showLocationPermissionRationaleDialog) {
        ConfirmationDialog(
            onDismissRequest = { showLocationPermissionRationaleDialog = false },
            onConfirmed = { locationProvider.requestPermission() },
            title = { Text(stringResource(Res.string.no_location_permission_warning_title)) },
            text = { Text(stringResource(Res.string.no_location_permission_warning)) },
        )
    }
    if (showApplicationSettingsDialog) {
        ConfirmationDialog(
            onDismissRequest = { showApplicationSettingsDialog = false },
            onConfirmed = { systemSettingsLauncher.openApplicationSettings() },
            text = { Text(stringResource(Res.string.turn_on_location_request)) },
        )
    }
    if (showLocationSettingsDialog) {
        ConfirmationDialog(
            onDismissRequest = { showLocationSettingsDialog = false },
            onConfirmed = { systemSettingsLauncher.openLocationServicesSettings() },
            text = { Text(stringResource(Res.string.turn_on_location_request)) },
        )
    }

    shownMessage?.let { message ->
        val questIcons = remember { viewModel.allQuestTypes.map { it.icon } }
        MessageDialog(
            message = message,
            onDismissRequest = { shownMessage = null },
            allQuestIcons = questIcons,
            onClickOpenQuestSettings = onClickQuestSettings,
            onToggleDontNotifyAgain = { messageType, dontNotifyAgain ->
                viewModel.toggleDisableMessageType(messageType, dontNotifyAgain)
            }
        )
    }

    if (showMainMenuDialog) {
        MainMenuDialog(
            onDismissRequest = { showMainMenuDialog = false },
            onClickProfile = onClickProfile,
            onClickLogin = onClickLogin,
            onClickSettings = onClickSettings,
            onClickAbout = onClickAbout,
            onClickDownload = ::onClickDownload,
            onClickUpload = ::onClickUpload,
            onClickEnterTeamMode = onClickEnterTeamMode,
            onClickExitTeamMode = { viewModel.disableTeamMode() },
            isLoggedIn = isLoggedIn,
            indexInTeam = if (isTeamMode) indexInTeam else null,
            unsyncedEditsCount = if (!isAutoSync) unsyncedEditsCount else null,
            isUploading = isUploading,
            modifier = if (hasHardwareKeyShortcuts) {
                // SCEE: the hardware menu key opens the settings while the menu is open
                val requester = remember { FocusRequester() }
                LaunchedEffect(Unit) { requester.requestFocus() }
                Modifier.focusRequester(requester).focusable().onKeyEvent {
                    if (isMenuKeyReleased(it.key, it.type)) {
                        showMainMenuDialog = false
                        onClickSettings()
                    }
                    it.key == Key.Menu
                }
            } else Modifier,
        )
    }

    urlConfig?.let { config ->
        ApplyUrlConfigDialog(
            presetName = config.urlConfig.presetName,
            presetNameAlreadyExists = config.alreadyExists,
            onDismissRequest = navViewModel::consumeUrlConfig,
            onConfirmed = { navViewModel.applyUrlConfig(config.urlConfig) },
        )
    }

    if (isRequestingLogin) {
        RequestLoginDialog(
            onDismissRequest = { viewModel.finishRequestingLogin() },
            onConfirmed = onClickLogin
        )
    }

    if (confirmReplaceDownload) {
        ConfirmReplaceDownloadDialog(
            onDismissRequest = { confirmReplaceDownload = false },
            onConfirmed = { download() },
            onEnqueue = { download(enqueue = true) },
        )
    }

    showToast?.messageResource?.let { message ->
        ToastPopup(
            onDismissRequest = { showToast = null },
            text = stringResource(message)
        )
    }

    routingState.error?.let { error ->
        ToastPopup(
            onDismissRequest = { routing.dismissError() },
            text = stringResource(when (error) {
                RoutingError.Network -> Res.string.route_error_network
                RoutingError.Timeout -> Res.string.route_error_timeout
                RoutingError.Server -> Res.string.route_error_server
                RoutingError.NoRoute -> Res.string.route_error_no_route
                RoutingError.InvalidResponse -> Res.string.route_error_response
            }),
        )
    }

    //endregion
}

private suspend fun reloadMapExtras(
    prefs: Preferences,
    apply: (List<LatLon>, String?) -> Unit,
) {
    val gpx = if (prefs.getBoolean(Prefs.SHOW_GPX_TRACK, false)) {
        withContext(Dispatchers.IO) { loadGpxTrackPoints({}) ?: emptyList() }
    } else {
        emptyList()
    }
    val geoJson = if (prefs.getBoolean(Prefs.SHOW_CUSTOM_GEOMETRY, false)) {
        runCatching { customGeometryFile.readString() }.getOrNull()
    } else {
        null
    }
    apply(gpx, geoJson)
}

private enum class Toast {
    Offline,
    TeamModeActive,
    TeamModeDeactivated,
    DownloadAreaTooBig,
    NoEmailClient,
    CannotFindBounds,
    NoLocation,
    RouteNoLocation,
    RouteStaleLocation,
    ImpreciseNote
}

private val Toast.messageResource: StringResource get() =  when (this) {
    Toast.CannotFindBounds -> Res.string.cannot_find_bbox_or_reduce_tilt
    Toast.NoLocation -> Res.string.no_gps_no_quests
    Toast.RouteNoLocation -> Res.string.route_no_location
    Toast.RouteStaleLocation -> Res.string.route_stale_location
    Toast.ImpreciseNote -> Res.string.create_new_note_unprecise
    Toast.Offline -> Res.string.offline
    Toast.TeamModeActive -> Res.string.team_mode_active
    Toast.TeamModeDeactivated -> Res.string.team_mode_deactivated
    Toast.DownloadAreaTooBig -> Res.string.download_area_too_big
    Toast.NoEmailClient -> Res.string.no_email_client
}
