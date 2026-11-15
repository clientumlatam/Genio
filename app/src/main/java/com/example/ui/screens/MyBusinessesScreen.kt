package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeliveryDining
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.R
import com.example.data.auth.AuthUiState
import com.example.data.auth.BusinessOwnerUser
import com.example.data.local.PublishedBusinessEntity
import com.example.domain.model.BusinessCategory
import com.example.domain.model.DirectoryBusiness
import com.example.domain.model.DirectoryCategories
import com.example.domain.model.LocalityRegion
import com.example.domain.model.PatagonianLocalities
import com.example.ui.BusinessFormState
import com.example.ui.components.StatusPill
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker

@Composable
fun MyBusinessesScreen(
  authState: AuthUiState,
  businesses: List<PublishedBusinessEntity>,
  formState: BusinessFormState,
  feedbackMessage: String?,
  defaultLocality: LocalityRegion,
  onGoogleSignIn: (android.content.Context) -> Unit,
  onUpdateOwnerProfile: (String, String) -> Unit,
  onSignOut: () -> Unit,
  onClearAuthMessages: () -> Unit,
  onClearFeedback: () -> Unit,
  onOpenCreate: (LocalityRegion) -> Unit,
  onOpenEdit: (PublishedBusinessEntity) -> Unit,
  onCloseForm: () -> Unit,
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
  onSaveBusiness: () -> Unit,
  onToggleOpenNow: (PublishedBusinessEntity) -> Unit,
  onToggleDelivery: (PublishedBusinessEntity) -> Unit,
  onDeleteBusiness: (PublishedBusinessEntity) -> Unit,
  onInspectBusiness: (DirectoryBusiness) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val snackbarHostState = remember { SnackbarHostState() }
  var showEditProfileDialog by remember { mutableStateOf(false) }

  LaunchedEffect(feedbackMessage, authState.infoMessage) {
    val message = feedbackMessage ?: authState.infoMessage
    if (message != null) {
      snackbarHostState.showSnackbar(message)
      if (feedbackMessage != null) onClearFeedback()
      if (authState.infoMessage != null) onClearAuthMessages()
    }
  }

  val signedInUser = authState.currentUser

  Scaffold(
    modifier = modifier.fillMaxSize(),
    containerColor = MaterialTheme.colorScheme.background,
    snackbarHost = { SnackbarHost(snackbarHostState) },
    floatingActionButton = {
      if (signedInUser != null) {
        FloatingActionButton(
          onClick = { onOpenCreate(defaultLocality) },
          containerColor = MaterialTheme.colorScheme.secondary,
          contentColor = MaterialTheme.colorScheme.onSecondary,
          modifier = Modifier.testTag("add_business_fab"),
        ) {
          Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = stringResource(R.string.action_publish_business),
          )
        }
      }
    },
  ) { innerPadding ->
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(innerPadding),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item(key = "my_businesses_header") {
        Column {
          Text(
            text = stringResource(R.string.my_businesses_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = stringResource(R.string.my_businesses_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      if (signedInUser == null) {
        item(key = "owner_auth_gate") {
          BusinessOwnerSignInCard(
            authState = authState,
            onSignInClick = { onGoogleSignIn(context) },
          )
        }
      } else {
        item(key = "owner_profile_card") {
          OwnerProfileHeaderCard(
            user = signedInUser,
            onEditProfile = { showEditProfileDialog = true },
            onSignOut = onSignOut,
          )
        }

        item(key = "storage_notice") {
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
              )
              Text(
                text = stringResource(R.string.local_storage_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
              )
            }
          }
        }

        if (businesses.isEmpty()) {
          item(key = "my_businesses_empty") {
            Card(
              shape = RoundedCornerShape(22.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
              Column(
                modifier = Modifier.fillMaxWidth().padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
              ) {
                Box(
                  modifier =
                    Modifier.size(64.dp)
                      .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        RoundedCornerShape(20.dp),
                      ),
                  contentAlignment = Alignment.Center,
                ) {
                  Icon(
                    imageVector = Icons.Rounded.Storefront,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp),
                  )
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                  text = stringResource(R.string.my_businesses_empty_title),
                  style = MaterialTheme.typography.titleMedium,
                  textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                  text = stringResource(R.string.my_businesses_empty_body),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(18.dp))
                Button(
                  onClick = { onOpenCreate(defaultLocality) },
                  modifier = Modifier.testTag("empty_publish_button"),
                ) {
                  Icon(Icons.Rounded.Add, contentDescription = null)
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(stringResource(R.string.action_publish_business))
                }
              }
            }
          }
        } else {
          items(businesses, key = { it.id }) { entity ->
            PublishedBusinessManageCard(
              entity = entity,
              onInspect = { onInspectBusiness(entity.toDirectoryBusiness()) },
              onEdit = { onOpenEdit(entity) },
              onDelete = { onDeleteBusiness(entity) },
              onToggleOpen = { onToggleOpenNow(entity) },
              onToggleDelivery = { onToggleDelivery(entity) },
            )
          }
        }
      }

      item(key = "my_businesses_bottom_space") { Spacer(modifier = Modifier.height(72.dp)) }
    }
  }

  if (showEditProfileDialog && signedInUser != null) {
    OwnerProfileEditDialog(
      user = signedInUser,
      onDismiss = { showEditProfileDialog = false },
      onSave = { updatedName, updatedLocalityId ->
        onUpdateOwnerProfile(updatedName, updatedLocalityId)
        showEditProfileDialog = false
      },
    )
  }

  if (formState.isSheetOpen && signedInUser != null) {
    BusinessFormBottomSheet(
      formState = formState,
      onDismiss = onCloseForm,
      onUpdateName = onUpdateName,
      onUpdateCategory = onUpdateCategory,
      onUpdateLocality = onUpdateLocality,
      onUpdateAddress = onUpdateAddress,
      onUpdateCoordinates = onUpdateCoordinates,
      onUpdatePhone = onUpdatePhone,
      onUpdateWhatsapp = onUpdateWhatsapp,
      onUpdateWebsite = onUpdateWebsite,
      onUpdateDescription = onUpdateDescription,
      onUpdateOpenNow = onUpdateOpenNow,
      onUpdateDelivery = onUpdateDelivery,
      onGeocodeAddress = onGeocodeAddress,
      onSave = onSaveBusiness,
    )
  }
}

@Composable
private fun BusinessOwnerSignInCard(
  authState: AuthUiState,
  onSignInClick: () -> Unit,
) {
  Card(
    shape = RoundedCornerShape(22.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    modifier = Modifier.fillMaxWidth().testTag("owner_sign_in_card"),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Box(
        modifier =
          Modifier.size(64.dp)
            .background(
              MaterialTheme.colorScheme.primaryContainer,
              RoundedCornerShape(20.dp),
            ),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = Icons.Rounded.Lock,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(30.dp),
        )
      }
      Spacer(modifier = Modifier.height(14.dp))
      Text(
        text = stringResource(R.string.auth_owner_gate_title),
        style = MaterialTheme.typography.titleLarge,
        textAlign = TextAlign.Center,
      )
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = stringResource(R.string.auth_owner_gate_subtitle),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )

      if (!authState.isFirebaseConfigured) {
        Spacer(modifier = Modifier.height(12.dp))
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.Info,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSecondaryContainer,
              modifier = Modifier.size(18.dp),
            )
            Text(
              text = stringResource(R.string.auth_firebase_pending_notice),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
          }
        }
      }

      authState.errorMessage?.let { error ->
        Spacer(modifier = Modifier.height(12.dp))
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

      Spacer(modifier = Modifier.height(18.dp))

      Button(
        onClick = onSignInClick,
        enabled = !authState.isLoading,
        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("google_sign_in_button"),
      ) {
        if (authState.isLoading) {
          CircularProgressIndicator(
            modifier = Modifier.size(22.dp),
            color = MaterialTheme.colorScheme.onPrimary,
            strokeWidth = 2.dp,
          )
        } else {
          Icon(
            imageVector = Icons.Rounded.VerifiedUser,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(stringResource(R.string.auth_sign_in_google))
        }
      }
    }
  }
}

@Composable
private fun OwnerProfileHeaderCard(
  user: BusinessOwnerUser,
  onEditProfile: () -> Unit,
  onSignOut: () -> Unit,
) {
  val preferredLocality = PatagonianLocalities.findById(user.preferredLocalityId)
  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    modifier = Modifier.fillMaxWidth().testTag("owner_profile_card"),
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        if (!user.photoUrl.isNullOrBlank()) {
          AsyncImage(
            model = user.photoUrl,
            contentDescription = stringResource(R.string.cd_owner_avatar),
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(52.dp).clip(CircleShape),
          )
        } else {
          Box(
            modifier =
              Modifier.size(52.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Rounded.Person,
              contentDescription = stringResource(R.string.cd_owner_avatar),
              tint = MaterialTheme.colorScheme.primary,
            )
          }
        }

        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = stringResource(R.string.auth_profile_title).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = user.displayName,
            style = MaterialTheme.typography.titleMedium,
          )
          if (user.email.isNotBlank()) {
            Text(
              text = user.email,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Spacer(modifier = Modifier.height(4.dp))
          StatusPill(
            text = "Sede: ${preferredLocality.name}",
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
          )
        }
      }

      Spacer(modifier = Modifier.height(12.dp))
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
      Spacer(modifier = Modifier.height(10.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OutlinedButton(
          onClick = onEditProfile,
          modifier = Modifier.weight(1f).height(48.dp).testTag("edit_profile_button"),
        ) {
          Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(stringResource(R.string.auth_edit_profile), maxLines = 1)
        }
        OutlinedButton(
          onClick = onSignOut,
          modifier = Modifier.weight(1f).height(48.dp).testTag("sign_out_button"),
        ) {
          Icon(
            Icons.AutoMirrored.Rounded.Logout,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(stringResource(R.string.auth_sign_out), maxLines = 1)
        }
      }
    }
  }
}

@Composable
private fun OwnerProfileEditDialog(
  user: BusinessOwnerUser,
  onDismiss: () -> Unit,
  onSave: (String, String) -> Unit,
) {
  var nameInput by remember(user.uid) { mutableStateOf(user.displayName) }
  var selectedLocalityId by remember(user.uid) { mutableStateOf(user.preferredLocalityId) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.auth_edit_profile)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
          value = nameInput,
          onValueChange = { nameInput = it },
          label = { Text(stringResource(R.string.auth_profile_name_label)) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("profile_name_input"),
        )
        Text(
          text = "Localidad principal del comercio",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.primary,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          items(PatagonianLocalities.all, key = { it.id }) { loc ->
            FilterChip(
              selected = loc.id == selectedLocalityId,
              onClick = { selectedLocalityId = loc.id },
              label = { Text(loc.name) },
            )
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = { onSave(nameInput, selectedLocalityId) },
        enabled = nameInput.trim().length >= 2,
        modifier = Modifier.testTag("profile_save_button"),
      ) {
        Text(stringResource(R.string.auth_profile_save))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(R.string.action_cancel))
      }
    },
  )
}

@Composable
private fun PublishedBusinessManageCard(
  entity: PublishedBusinessEntity,
  onInspect: () -> Unit,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
  onToggleOpen: () -> Unit,
  onToggleDelivery: () -> Unit,
) {
  val category = DirectoryCategories.findById(entity.categoryId)
  val locality = PatagonianLocalities.findById(entity.localityId)

  Card(
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    modifier =
      Modifier.fillMaxWidth()
        .testTag("published_card_${entity.id}")
        .clickable(onClick = onInspect),
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Box(
          modifier =
            Modifier.size(46.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(category.accentColor.copy(alpha = 0.14f)),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = category.icon,
            contentDescription = null,
            tint = category.accentColor,
          )
        }
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "${category.shortName} • ${locality.name}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
          )
          Text(
            text = entity.name,
            style = MaterialTheme.typography.titleMedium,
          )
          Text(
            text = entity.address,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        IconButton(onClick = onEdit, modifier = Modifier.size(48.dp)) {
          Icon(
            imageVector = Icons.Rounded.Edit,
            contentDescription = stringResource(R.string.action_edit),
            tint = MaterialTheme.colorScheme.primary,
          )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
          Icon(
            imageVector = Icons.Rounded.Delete,
            contentDescription = stringResource(R.string.action_delete),
            tint = MaterialTheme.colorScheme.error,
          )
        }
      }

      Spacer(modifier = Modifier.height(10.dp))
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
      Spacer(modifier = Modifier.height(10.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        FilterChip(
          selected = entity.isOpenNow,
          onClick = onToggleOpen,
          label = {
            Text(
              if (entity.isOpenNow) stringResource(R.string.badge_open)
              else stringResource(R.string.badge_closed)
            )
          },
          leadingIcon = {
            Icon(Icons.Rounded.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
          },
        )
        FilterChip(
          selected = entity.hasDelivery,
          onClick = onToggleDelivery,
          label = { Text(stringResource(R.string.badge_delivery)) },
          leadingIcon = {
            Icon(
              Icons.Rounded.DeliveryDining,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
            )
          },
        )
        Spacer(modifier = Modifier.weight(1f))
        if (entity.whatsapp.isNotBlank()) {
          StatusPill(
            text = "WhatsApp",
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BusinessFormBottomSheet(
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
  ensureOsmConfigured(context)
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val pinGeoPoint = GeoPoint(formState.latitude, formState.longitude)
  val pickerMapRef = remember { androidx.compose.runtime.mutableStateOf<MapView?>(null) }

  androidx.compose.runtime.DisposableEffect(Unit) {
    onDispose { pickerMapRef.value?.onDetach() }
  }

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
          .padding(bottom = 32.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text =
          if (formState.editingId == 0L) "Publicar comercio en Guía Genio"
          else "Editar comercio",
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
        text = "Rubro",
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
          )
        }
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
        label = { Text("Dirección completa *") },
        placeholder = { Text("Ej: Av. Argentina 450") },
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
          modifier =
            Modifier.fillMaxWidth()
              .height(175.dp)
              .clip(RoundedCornerShape(16.dp))
        ) {
          AndroidView(
            modifier = Modifier.fillMaxSize().testTag("my_business_map_picker"),
            factory = { ctx ->
              MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
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

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OutlinedTextField(
          value = formState.phone,
          onValueChange = onUpdatePhone,
          label = { Text("Teléfono") },
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

      OutlinedTextField(
        value = formState.website,
        onValueChange = onUpdateWebsite,
        label = { Text("Sitio web o red social (opcional)") },
        placeholder = { Text("https://micomercio.com.ar") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )

      OutlinedTextField(
        value = formState.description,
        onValueChange = onUpdateDescription,
        label = { Text("Descripción de productos o servicios") },
        minLines = 2,
        maxLines = 4,
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
          modifier = Modifier.weight(1f).height(50.dp),
        ) {
          Text(stringResource(R.string.action_cancel))
        }
        Button(
          onClick = onSave,
          modifier = Modifier.weight(1f).height(50.dp).testTag("form_save_button"),
        ) {
          Text(stringResource(R.string.action_save))
        }
      }
    }
  }
}
