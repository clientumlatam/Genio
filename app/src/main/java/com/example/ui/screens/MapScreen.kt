package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Directions
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.domain.model.DirectoryBusiness
import com.example.domain.model.DirectoryResultsState
import com.example.domain.model.GeoPointCoord
import com.example.domain.model.LocalityRegion
import com.example.domain.model.PatagonianLocalities
import com.example.ui.DirectoryUiSnapshot
import com.example.ui.components.ApprovedBusinessesSearchBar
import com.example.ui.components.BusinessIntents
import com.example.ui.components.StatusPill
import java.io.File
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.ScaleBarOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

@Composable
fun MapScreen(
  uiControl: DirectoryUiSnapshot,
  resultsState: DirectoryResultsState,
  mapSearchQuery: String,
  filteredBusinesses: List<DirectoryBusiness>,
  approvedFirestoreBusinesses: List<DirectoryBusiness> = emptyList(),
  isFirestoreConfigured: Boolean = true,
  firestoreDisabledMessage: String? = null,
  focusedBusiness: DirectoryBusiness? = null,
  userGpsCoordinate: GeoPointCoord? = null,
  gpsCenterRequestNonce: Int = 0,
  onMapSearchQueryChange: (String) -> Unit,
  onFocusedBusinessConsumed: () -> Unit = {},
  onSelectLocality: (LocalityRegion) -> Unit,
  onToggleOpenNow: () -> Unit,
  onToggleDelivery: () -> Unit,
  onToggleWhatsApp: () -> Unit,
  onBusinessClick: (DirectoryBusiness) -> Unit,
  onSearchInArea: (GeoPointCoord) -> Unit,
  onRequestMyLocation: () -> Unit,
  modifier: Modifier = Modifier,
  onRetry: (() -> Unit)? = null,
) {
  Box(modifier = modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize().padding(top = 68.dp)) {
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        PatagonianLocalities.all.take(7).forEach { locality ->
          val selected = locality.id == uiControl.selectedLocality.id
          FilterChip(
            selected = selected,
            onClick = { onSelectLocality(locality) },
            label = { Text(locality.name) },
            colors =
              FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primary,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
              ),
            modifier = Modifier.testTag("map_locality_chip_${locality.id}"),
          )
        }
      }

      Row(
        modifier =
          Modifier.fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        FilterChip(
          selected = uiControl.filters.openNowOnly,
          onClick = onToggleOpenNow,
          label = { Text(stringResource(R.string.filter_open_now)) },
        )
        FilterChip(
          selected = uiControl.filters.deliveryOnly,
          onClick = onToggleDelivery,
          label = { Text(stringResource(R.string.filter_delivery)) },
        )
        FilterChip(
          selected = uiControl.filters.whatsappOnly,
          onClick = onToggleWhatsApp,
          label = { Text(stringResource(R.string.filter_whatsapp)) },
        )
      }

      DirectoryMapCanvas(
        selectedLocality = uiControl.selectedLocality,
        resultsState = resultsState,
        businessesOverride = filteredBusinesses,
        focusedBusiness = focusedBusiness,
        userGpsCoordinate = userGpsCoordinate,
        gpsCenterRequestNonce = gpsCenterRequestNonce,
        isResolvingLocation = uiControl.isResolvingLocation,
        onFocusedBusinessConsumed = onFocusedBusinessConsumed,
        onBusinessClick = onBusinessClick,
        onSearchInArea = onSearchInArea,
        onRequestMyLocation = onRequestMyLocation,
        onRetry = onRetry,
        modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 4.dp),
      )
    }

    // Barra de búsqueda Material 3 (DockedSearchBar) conectada a Firestore sobre el mapa sin comprimir su altura
    ApprovedBusinessesSearchBar(
      query = mapSearchQuery,
      onQueryChange = onMapSearchQueryChange,
      approvedBusinesses = approvedFirestoreBusinesses,
      onBusinessClick = onBusinessClick,
      isFirestoreConfigured = isFirestoreConfigured,
      firestoreDisabledMessage = firestoreDisabledMessage,
      placeholderText = stringResource(R.string.map_search_placeholder),
      modifier =
        Modifier.align(Alignment.TopCenter)
          .padding(horizontal = 16.dp, vertical = 6.dp)
          .zIndex(20f),
    )
  }
}

@Composable
fun DirectoryMapCanvas(
  selectedLocality: LocalityRegion,
  resultsState: DirectoryResultsState,
  onBusinessClick: (DirectoryBusiness) -> Unit,
  onSearchInArea: (GeoPointCoord) -> Unit,
  modifier: Modifier = Modifier,
  businessesOverride: List<DirectoryBusiness>? = null,
  focusedBusiness: DirectoryBusiness? = null,
  userGpsCoordinate: GeoPointCoord? = null,
  gpsCenterRequestNonce: Int = 0,
  isResolvingLocation: Boolean = false,
  onFocusedBusinessConsumed: () -> Unit = {},
  onRequestMyLocation: (() -> Unit)? = null,
  isCompactPreview: Boolean = false,
  onRetry: (() -> Unit)? = null,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current

  ensureOsmConfigured(context)

  val hasLocationPermission =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
      PackageManager.PERMISSION_GRANTED ||
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
      PackageManager.PERMISSION_GRANTED

  var pinnedBusiness by remember { mutableStateOf<DirectoryBusiness?>(null) }
  var hasMovedMap by remember { mutableStateOf(false) }
  var suppressMoveUntilMs by remember { mutableLongStateOf(System.currentTimeMillis() + 900L) }
  var mapViewRef by remember { mutableStateOf<MapView?>(null) }
  val iconCache = remember { mutableMapOf<Int, Drawable>() }

  val initialZoom = if (selectedLocality.id == "neuquen_capital") 13.5 else 13.8
  val initialCenterMarkerTitle =
    if (selectedLocality.id == "neuquen_capital") {
      stringResource(R.string.map_initial_marker_title)
    } else {
      selectedLocality.name
    }
  val initialCenterMarkerSnippet = stringResource(R.string.map_initial_marker_snippet)

  val baseBusinesses =
    businessesOverride
      ?: when (resultsState) {
        is DirectoryResultsState.Success -> resultsState.businesses
        else -> emptyList()
      }
  val businesses =
    remember(baseBusinesses, focusedBusiness) {
      if (focusedBusiness != null && baseBusinesses.none { it.id == focusedBusiness.id }) {
        listOf(focusedBusiness) + baseBusinesses
      } else {
        baseBusinesses
      }
    }

  LaunchedEffect(selectedLocality.id, selectedLocality.latitude, selectedLocality.longitude) {
    if (focusedBusiness == null) {
      pinnedBusiness = null
      hasMovedMap = false
      suppressMoveUntilMs = System.currentTimeMillis() + 900L
      val targetZoom =
        when {
          selectedLocality.isUserLocation -> 15.8
          selectedLocality.id == "neuquen_capital" -> 13.5
          else -> 13.8
        }
      mapViewRef?.controller?.apply {
        setZoom(targetZoom)
        animateTo(selectedLocality.latLng.toOsmGeoPoint())
      }
    }
  }

  LaunchedEffect(gpsCenterRequestNonce) {
    if (gpsCenterRequestNonce > 0 && userGpsCoordinate != null) {
      hasMovedMap = false
      suppressMoveUntilMs = System.currentTimeMillis() + 900L
      mapViewRef?.controller?.apply {
        setZoom(15.8)
        animateTo(userGpsCoordinate.toOsmGeoPoint())
      }
    }
  }

  LaunchedEffect(focusedBusiness?.id) {
    val target = focusedBusiness ?: return@LaunchedEffect
    pinnedBusiness = target
    hasMovedMap = false
    suppressMoveUntilMs = System.currentTimeMillis() + 900L
    mapViewRef?.controller?.apply {
      setZoom(16.0)
      animateTo(target.latLng.toOsmGeoPoint())
    }
    onFocusedBusinessConsumed()
  }

  DisposableEffect(lifecycleOwner, mapViewRef) {
    val view = mapViewRef
    if (view == null) return@DisposableEffect onDispose {}
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_RESUME -> view.onResume()
        Lifecycle.Event.ON_PAUSE -> view.onPause()
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
      view.onDetach()
    }
  }

  Box(modifier = modifier.fillMaxSize()) {
    AndroidView(
      modifier = Modifier.fillMaxSize().testTag("osm_map_canvas"),
      factory = { ctx ->
        MapView(ctx).apply {
          // Capa base de mapa OpenStreetMap (MAPNIK) centrada en Neuquén
          setTileSource(TileSourceFactory.MAPNIK)
          setMultiTouchControls(true)
          zoomController.setVisibility(
            if (isCompactPreview) CustomZoomButtonsController.Visibility.NEVER
            else CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT
          )
          controller.setZoom(initialZoom)
          controller.setCenter(selectedLocality.latLng.toOsmGeoPoint())

          val scaleBarOverlay = ScaleBarOverlay(this).apply {
            setAlignBottom(true)
            setAlignRight(true)
          }
          overlays.add(scaleBarOverlay)

          if (hasLocationPermission) {
            val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(ctx), this)
            locationOverlay.enableMyLocation()
            overlays.add(locationOverlay)
          }

          overlays.add(
            MapEventsOverlay(
              object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                  pinnedBusiness = null
                  return false
                }

                override fun longPressHelper(p: GeoPoint?): Boolean = false
              }
            )
          )

          addMapListener(
            object : MapListener {
              override fun onScroll(event: ScrollEvent?): Boolean {
                if (System.currentTimeMillis() > suppressMoveUntilMs) {
                  hasMovedMap = true
                }
                return false
              }

              override fun onZoom(event: ZoomEvent?): Boolean {
                if (System.currentTimeMillis() > suppressMoveUntilMs) {
                  hasMovedMap = true
                }
                return false
              }
            }
          )

          mapViewRef = this
        }
      },
      update = { mapView ->
        // Preservar capas base y actualizar radio, marcador inicial/GPS y comercios filtrados
        mapView.overlays.removeAll { it is Marker || it is Polygon }

        val centerPoint = (userGpsCoordinate?.takeIf { selectedLocality.isUserLocation } ?: selectedLocality.latLng).toOsmGeoPoint()
        val nearbyZoneOverlay =
          Polygon(mapView).apply {
            points = Polygon.pointsAsCircle(centerPoint, selectedLocality.radiusMeters.coerceAtMost(5000.0))
            fillPaint.color = android.graphics.Color.argb(22, 21, 70, 166)
            outlinePaint.color = android.graphics.Color.argb(110, 21, 70, 166)
            outlinePaint.strokeWidth = 2.5f
          }
        mapView.overlays.add(nearbyZoneOverlay)

        val initialCenterMarker =
          Marker(mapView).apply {
            id = "initial_neuquen_marker"
            position = centerPoint
            title = initialCenterMarkerTitle
            snippet = initialCenterMarkerSnippet
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            icon = createAnchorCenterPinDrawable(mapView.context)
          }
        mapView.overlays.add(initialCenterMarker)

        businesses.forEach { business ->
          val marker =
            Marker(mapView).apply {
              position = business.latLng.toOsmGeoPoint()
              title = business.name
              snippet = "${business.categoryName} • ${business.address}"
              setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
              icon =
                iconCache.getOrPut(business.markerColorArgb) {
                  createCategoryPinDrawable(mapView.context, business.markerColorArgb)
                }
              setOnMarkerClickListener { _, _ ->
                if (isCompactPreview) {
                  onBusinessClick(business)
                } else {
                  pinnedBusiness = business
                  suppressMoveUntilMs = System.currentTimeMillis() + 600L
                  mapView.controller.animateTo(position)
                }
                true
              }
            }
          mapView.overlays.add(marker)
        }
        mapView.invalidate()
      },
    )

    if (!isCompactPreview) {
      Column(
        modifier =
          Modifier.align(Alignment.TopStart)
            .padding(start = 12.dp, top = 12.dp, end = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Card(
          shape = RoundedCornerShape(50),
          colors =
            CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
            ),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
          modifier = Modifier.testTag("map_base_layer_badge"),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
          ) {
            if (resultsState is DirectoryResultsState.Loading) {
              CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
              )
            }
            Text(
              text =
                if (resultsState is DirectoryResultsState.Loading) {
                  "Buscando comercios en ${selectedLocality.name}…"
                } else {
                  "${stringResource(R.string.map_base_layer_label)} • ${businesses.size} pines"
                },
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
        }

        if (resultsState is DirectoryResultsState.Error && businesses.isEmpty()) {
          Card(
            shape = RoundedCornerShape(14.dp),
            colors =
              CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.95f)
              ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
            modifier = Modifier.testTag("map_error_banner"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Text(
                text = resultsState.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
              )
              if (onRetry != null) {
                OutlinedButton(
                  onClick = onRetry,
                  contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                  modifier = Modifier.height(36.dp).testTag("map_retry_button"),
                ) {
                  Text(
                    text = stringResource(R.string.action_retry),
                    style = MaterialTheme.typography.labelSmall,
                  )
                }
              }
            }
          }
        }
      }
    }

    AnimatedVisibility(
      visible = hasMovedMap,
      enter = fadeIn(),
      exit = fadeOut(),
      modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
    ) {
      ElevatedButton(
        onClick = {
          hasMovedMap = false
          val center = mapViewRef?.mapCenter
          if (center != null) {
            onSearchInArea(GeoPointCoord(center.latitude, center.longitude))
          }
        },
        shape = RoundedCornerShape(50),
        modifier = Modifier.height(42.dp).testTag("search_this_area_button"),
      ) {
        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(stringResource(R.string.search_this_area))
      }
    }

    // Botón flotante para centrar el mapa en la posición GPS actual usando Google Location API
    if (!isCompactPreview && onRequestMyLocation != null) {
      val fabBottomPadding by
        animateDpAsState(
          targetValue = if (pinnedBusiness != null) 216.dp else 20.dp,
          label = "gps_fab_bottom_padding",
        )
      FloatingActionButton(
        onClick = onRequestMyLocation,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier =
          Modifier.align(Alignment.BottomEnd)
            .padding(end = 16.dp, bottom = fabBottomPadding)
            .testTag("map_gps_fab"),
      ) {
        if (isResolvingLocation) {
          CircularProgressIndicator(
            modifier = Modifier.size(22.dp),
            color = MaterialTheme.colorScheme.onPrimary,
            strokeWidth = 2.dp,
          )
        } else {
          Icon(
            imageVector = Icons.Rounded.MyLocation,
            contentDescription = stringResource(R.string.map_gps_center_cd),
          )
        }
      }
    }

    AnimatedVisibility(
      visible = pinnedBusiness != null && !isCompactPreview,
      enter = slideInVertically { it } + fadeIn(),
      exit = slideOutVertically { it } + fadeOut(),
      modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
    ) {
      pinnedBusiness?.let { item ->
        Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
          modifier = Modifier.fillMaxWidth().testTag("map_preview_card"),
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = item.categoryName.uppercase(),
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.primary,
                  fontWeight = FontWeight.Bold,
                )
                Text(
                  text = item.name,
                  style = MaterialTheme.typography.titleMedium,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  text = "${item.address} • ${item.localityName}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
              IconButton(onClick = { pinnedBusiness = null }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_cancel))
              }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              if (item.isOpenNow == true) {
                StatusPill(
                  text = stringResource(R.string.badge_open),
                  containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                  contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                )
              } else if (item.isOpenNow == false) {
                StatusPill(
                  text = stringResource(R.string.badge_closed),
                  containerColor = MaterialTheme.colorScheme.errorContainer,
                  contentColor = MaterialTheme.colorScheme.onErrorContainer,
                )
              } else {
                StatusPill(
                  text = "Horario: no informado",
                  containerColor = MaterialTheme.colorScheme.surfaceVariant,
                  contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              if (item.hasDelivery) {
                StatusPill(
                  text = stringResource(R.string.badge_delivery),
                  containerColor = MaterialTheme.colorScheme.secondaryContainer,
                  contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
              }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              if (item.hasWhatsApp) {
                Button(
                  onClick = {
                    BusinessIntents.openWhatsApp(context, item.whatsappNumber!!, item.name)
                  },
                  modifier = Modifier.weight(1f).height(48.dp),
                  contentPadding = PaddingValues(horizontal = 8.dp),
                  colors =
                    ButtonDefaults.buttonColors(
                      containerColor = MaterialTheme.colorScheme.tertiary,
                      contentColor = MaterialTheme.colorScheme.onTertiary,
                    ),
                ) {
                  Icon(
                    Icons.AutoMirrored.Rounded.Chat,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("WhatsApp", maxLines = 1)
                }
              } else if (item.hasPhone) {
                OutlinedButton(
                  onClick = { BusinessIntents.dialPhone(context, item.phoneNumber!!) },
                  modifier = Modifier.weight(1f).height(48.dp),
                  contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                  Icon(Icons.Rounded.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("Llamar", maxLines = 1)
                }
              }

              OutlinedButton(
                onClick = { BusinessIntents.openDirections(context, item) },
                modifier = Modifier.weight(1f).height(48.dp),
                contentPadding = PaddingValues(horizontal = 8.dp),
              ) {
                Icon(
                  Icons.Rounded.Directions,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Llegar", maxLines = 1)
              }

              Button(
                onClick = { onBusinessClick(item) },
                modifier = Modifier.weight(1f).height(48.dp),
                contentPadding = PaddingValues(horizontal = 8.dp),
              ) {
                Text("Ver ficha", maxLines = 1)
              }
            }
          }
        }
      }
    }
  }
}

internal val CARTO_VOYAGER_TILE_SOURCE =
  XYTileSource(
    "CartoVoyager",
    0,
    19,
    256,
    ".png",
    arrayOf(
      "https://a.basemaps.cartocdn.com/rastertiles/voyager/",
      "https://b.basemaps.cartocdn.com/rastertiles/voyager/",
      "https://c.basemaps.cartocdn.com/rastertiles/voyager/",
      "https://d.basemaps.cartocdn.com/rastertiles/voyager/",
    ),
    "© OpenStreetMap contributors, © CARTO",
  )

internal fun ensureOsmConfigured(context: Context) {
  val appCtx = context.applicationContext
  val config = Configuration.getInstance()
  config.userAgentValue = com.example.data.places.PlacesRepository.USER_AGENT
  config.osmdroidBasePath = File(appCtx.filesDir, "osmdroid")
  config.osmdroidTileCache = File(appCtx.cacheDir, "osmdroid/tiles")
}

internal fun createCategoryPinDrawable(context: Context, colorArgb: Int): Drawable {
  val density = context.resources.displayMetrics.density
  val width = (32f * density).toInt().coerceAtLeast(32)
  val height = (42f * density).toInt().coerceAtLeast(42)
  val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
  val canvas = Canvas(bitmap)

  val cx = width / 2f
  val radius = width * 0.40f
  val cy = radius + 2f * density

  val fillPaint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.FILL
      color = colorArgb
    }
  val strokePaint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      strokeWidth = 2f * density
      color = android.graphics.Color.WHITE
    }

  val pinPath =
    Path().apply {
      addCircle(cx, cy, radius, Path.Direction.CW)
      moveTo(cx - radius * 0.72f, cy + radius * 0.55f)
      lineTo(cx, height - 2f * density)
      lineTo(cx + radius * 0.72f, cy + radius * 0.55f)
      close()
    }
  canvas.drawPath(pinPath, fillPaint)
  canvas.drawCircle(cx, cy, radius, strokePaint)

  val centerDotPaint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.FILL
      color = android.graphics.Color.WHITE
    }
  canvas.drawCircle(cx, cy, radius * 0.36f, centerDotPaint)

  return BitmapDrawable(context.resources, bitmap)
}

internal fun createAnchorCenterPinDrawable(context: Context): Drawable {
  val density = context.resources.displayMetrics.density
  val width = (36f * density).toInt().coerceAtLeast(36)
  val height = (46f * density).toInt().coerceAtLeast(46)
  val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
  val canvas = Canvas(bitmap)

  val cx = width / 2f
  val radius = width * 0.38f
  val cy = radius + 2f * density

  val fillPaint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.FILL
      color = android.graphics.Color.rgb(21, 70, 166)
    }
  val ringPaint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      strokeWidth = 2.5f * density
      color = android.graphics.Color.WHITE
    }
  val goldPaint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.FILL
      color = android.graphics.Color.rgb(245, 158, 11)
    }

  val pinPath =
    Path().apply {
      addCircle(cx, cy, radius, Path.Direction.CW)
      moveTo(cx - radius * 0.70f, cy + radius * 0.55f)
      lineTo(cx, height - 2f * density)
      lineTo(cx + radius * 0.70f, cy + radius * 0.55f)
      close()
    }
  canvas.drawPath(pinPath, fillPaint)
  canvas.drawCircle(cx, cy, radius, ringPaint)
  canvas.drawCircle(cx, cy, radius * 0.42f, goldPaint)

  return BitmapDrawable(context.resources, bitmap)
}
