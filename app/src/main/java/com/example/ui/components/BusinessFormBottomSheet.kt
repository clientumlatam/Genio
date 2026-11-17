package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.domain.model.BusinessCategory
import com.example.domain.model.DirectoryCategories
import com.example.domain.model.LocalityRegion
import com.example.domain.model.PatagonianLocalities
import com.example.ui.BusinessFormState
import com.example.ui.screens.CARTO_VOYAGER_TILE_SOURCE
import com.example.ui.screens.createCategoryPinDrawable
import com.example.ui.screens.ensureOsmConfigured
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusinessFormBottomSheet(
  formState: BusinessFormState,
  onDismiss: () -> Unit,
  onUpdateName: (String) -> Unit,
  onUpdateCategory: (BusinessCategory) -> Unit,
  onUpdateLocality: (LocalityRegion) -> Unit,
  onUpdateAddress: (String) -> Unit,
  onUpdateCoordinates: (Double, Double) -> Unit,
  onUpdatePhone: (String) -> Unit,
  onUpdateWhatsapp: (String) -> Unit,
  onUpdateWebsite: (String) -> Unit,
  onUpdateDescription: (String) -> Unit,
  onUpdateOpenNow: (Boolean) -> Unit,
  onUpdateDelivery: (Boolean) -> Unit,
  onGeocodeAddress: () -> Unit,
  onSave: () -> Unit,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  ensureOsmConfigured(context)
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val pinGeoPoint = GeoPoint(formState.latitude, formState.longitude)
  val pickerMapRef = remember { mutableStateOf<MapView?>(null) }

  DisposableEffect(lifecycleOwner, pickerMapRef.value) {
    val view = pickerMapRef.value
    if (view == null) return@DisposableEffect onDispose {}
    if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
      view.onResume()
    }
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

  val isEditing = formState.editingId != 0L || !formState.editingFirestoreDocId.isNullOrBlank()

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp)
          .padding(bottom = 32.dp)
          .testTag("business_form_bottom_sheet"),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text =
          if (isEditing) "Editar comercio"
          else "Publicar comercio en Guía Genio",
        style = MaterialTheme.typography.headlineSmall,
      )

      formState.validationError?.let { error ->
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.errorContainer,
        ) {
          Text(
            text = error,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.fillMaxWidth().padding(12.dp),
          )
        }
      }

      OutlinedTextField(
        value = formState.name,
        onValueChange = onUpdateName,
        label = { Text("Nombre del comercio o servicio *") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("form_name_input"),
      )

      Text(
        text = "Rubro *",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
      )
      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(DirectoryCategories.items, key = { it.id }) { cat ->
          FilterChip(
            selected = cat.id == formState.selectedCategory.id,
            onClick = { onUpdateCategory(cat) },
            label = { Text(cat.shortName) },
            leadingIcon = {
              Icon(cat.icon, contentDescription = null, modifier = Modifier.size(16.dp))
            },
            modifier = Modifier.testTag("form_category_chip_${cat.id}"),
          )
        }
      }

      OutlinedTextField(
        value = formState.description,
        onValueChange = onUpdateDescription,
        label = { Text("Descripción de productos o servicios") },
        minLines = 2,
        maxLines = 4,
        modifier = Modifier.fillMaxWidth().testTag("form_description_input"),
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OutlinedTextField(
          value = formState.phone,
          onValueChange = onUpdatePhone,
          label = { Text("Teléfono *") },
          placeholder = { Text("299 442 0000") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
          singleLine = true,
          modifier = Modifier.weight(1f).testTag("form_phone_input"),
        )
        OutlinedTextField(
          value = formState.whatsapp,
          onValueChange = onUpdateWhatsapp,
          label = { Text("WhatsApp") },
          placeholder = { Text("299 512 3456") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
          singleLine = true,
          modifier = Modifier.weight(1f).testTag("form_whatsapp_input"),
        )
      }

      Text(
        text = "Localidad",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
      )
      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(PatagonianLocalities.all, key = { it.id }) { loc ->
          FilterChip(
            selected = loc.id == formState.selectedLocality.id,
            onClick = { onUpdateLocality(loc) },
            label = { Text(loc.name) },
          )
        }
      }

      OutlinedTextField(
        value = formState.address,
        onValueChange = onUpdateAddress,
        label = { Text("Dirección (opcional, ej: Av. Argentina 450)") },
        placeholder = { Text("Av. Argentina 450") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("form_address_input"),
      )

      OutlinedButton(
        onClick = onGeocodeAddress,
        enabled = !formState.isGeocodingAddress,
        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("form_geocode_button"),
      ) {
        Icon(Icons.Rounded.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          if (formState.isGeocodingAddress) "Buscando dirección en Nominatim…"
          else "Ubicar dirección en el mapa",
        )
      }

      // Embedded Interactive Map Pin Picker
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Icon(
            Icons.Rounded.LocationOn,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
          )
          Text(
            text = "Tocá el mapa para ubicar tu pin exacto",
            style = MaterialTheme.typography.labelLarge,
          )
        }
        Box(
          modifier = Modifier.fillMaxWidth().height(175.dp).clip(RoundedCornerShape(16.dp))
        ) {
          AndroidView(
            modifier = Modifier.fillMaxSize().testTag("my_business_map_picker"),
            factory = { ctx ->
              MapView(ctx).apply {
                setTileSource(CARTO_VOYAGER_TILE_SOURCE)
                isTilesScaledToDpi = true
                setUseDataConnection(true)
                setMultiTouchControls(true)
                zoomController.setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
                controller.setZoom(14.5)
                controller.setCenter(pinGeoPoint)

                overlays.add(
                  MapEventsOverlay(
                    object : MapEventsReceiver {
                      override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                        if (p != null) {
                          onUpdateCoordinates(p.latitude, p.longitude)
                        }
                        return true
                      }

                      override fun longPressHelper(p: GeoPoint?): Boolean = false
                    }
                  )
                )
                pickerMapRef.value = this
              }
            },
            update = { mapView ->
              mapView.overlays.removeAll { it is Marker }
              val marker =
                Marker(mapView).apply {
                  position = GeoPoint(formState.latitude, formState.longitude)
                  title = formState.name.ifBlank { "Ubicación de tu comercio" }
                  setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                  icon =
                    createCategoryPinDrawable(
                      mapView.context,
                      formState.selectedCategory.markerColorArgb,
                    )
                }
              mapView.overlays.add(marker)
              mapView.controller.animateTo(marker.position)
              mapView.invalidate()
            },
          )
        }
        Text(
          text =
            "Coordenadas: ${String.format("%.4f", formState.latitude)}, ${String.format("%.4f", formState.longitude)}",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      OutlinedTextField(
        value = formState.website,
        onValueChange = onUpdateWebsite,
        label = { Text("Sitio web o red social (opcional)") },
        placeholder = { Text("https://micomercio.com.ar") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text("Abierto ahora", style = MaterialTheme.typography.bodyLarge)
        Switch(checked = formState.isOpenNow, onCheckedChange = onUpdateOpenNow)
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text("Ofrece servicio de delivery", style = MaterialTheme.typography.bodyLarge)
        Switch(checked = formState.hasDelivery, onCheckedChange = onUpdateDelivery)
      }

      Spacer(modifier = Modifier.height(6.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OutlinedButton(
          onClick = onDismiss,
          enabled = !formState.isSaving,
          modifier = Modifier.weight(1f).height(50.dp),
        ) {
          Text(stringResource(R.string.action_cancel))
        }
        Button(
          onClick = onSave,
          enabled = !formState.isSaving,
          modifier = Modifier.weight(1f).height(50.dp).testTag("form_save_button"),
        ) {
          if (formState.isSaving) {
            CircularProgressIndicator(
              modifier = Modifier.size(20.dp),
              color = MaterialTheme.colorScheme.onPrimary,
              strokeWidth = 2.dp,
            )
          } else {
            Text(stringResource(R.string.action_save))
          }
        }
      }
    }
  }
}
