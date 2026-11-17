package com.example.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.auth.AuthService
import com.example.data.auth.AuthUiState
import com.example.data.local.PublishedBusinessEntity
import com.example.data.local.PublishedBusinessRepository
import com.example.data.places.PlacesRepository
import com.example.data.remote.FirestoreBusinessListing
import com.example.data.remote.FirestoreDirectoryRepository
import com.example.domain.model.BusinessCategory
import com.example.domain.model.DirectoryBusiness
import com.example.domain.model.DirectoryCategories
import com.example.domain.model.LocalityRegion
import com.example.domain.model.PatagonianLocalities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BusinessFormState(
  val editingId: Long = 0L,
  val editingFirestoreDocId: String? = null,
  val name: String = "",
  val selectedCategory: BusinessCategory = DirectoryCategories.items.first(),
  val selectedLocality: LocalityRegion = PatagonianLocalities.NeuquenCapital,
  val address: String = "",
  val latitude: Double = PatagonianLocalities.NeuquenCapital.latitude,
  val longitude: Double = PatagonianLocalities.NeuquenCapital.longitude,
  val phone: String = "",
  val whatsapp: String = "",
  val website: String = "",
  val description: String = "",
  val isOpenNow: Boolean = true,
  val hasDelivery: Boolean = false,
  val isSheetOpen: Boolean = false,
  val isSaving: Boolean = false,
  val isGeocodingAddress: Boolean = false,
  val validationError: String? = null,
)

class MyBusinessesViewModel(
  private val repository: PublishedBusinessRepository,
  private val placesRepository: PlacesRepository? = null,
  private val authService: AuthService? = null,
  private val firestoreRepository: FirestoreDirectoryRepository? = null,
) : ViewModel() {

  val authState: StateFlow<AuthUiState> =
    authService?.authState ?: MutableStateFlow(AuthUiState()).asStateFlow()

  val publishedBusinesses: StateFlow<List<PublishedBusinessEntity>> =
    repository.publishedBusinesses.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000L),
      initialValue = emptyList(),
    )

  private val _formState = MutableStateFlow(BusinessFormState())
  val formState: StateFlow<BusinessFormState> = _formState.asStateFlow()

  private val _feedbackMessage = MutableStateFlow<String?>(null)
  val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

  fun signInWithGoogle(activityContext: Context) {
    val service = authService ?: return
    viewModelScope.launch {
      service.signInWithGoogle(activityContext)
      val signedUser = service.authState.value.currentUser
      if (signedUser != null && firestoreRepository?.isConfigured == true) {
        firestoreRepository.upsertUserProfile(
          userId = signedUser.uid,
          displayName = signedUser.displayName,
          preferredLocalityId = signedUser.preferredLocalityId,
        )
      }
    }
  }

  fun updateOwnerProfile(displayName: String, preferredLocalityId: String) {
    val service = authService ?: return
    viewModelScope.launch {
      val ok = service.updateOwnerProfile(displayName, preferredLocalityId)
      if (ok) {
        val signedUser = service.authState.value.currentUser
        if (signedUser != null && firestoreRepository?.isConfigured == true) {
          firestoreRepository.upsertUserProfile(
            userId = signedUser.uid,
            displayName = signedUser.displayName,
            preferredLocalityId = signedUser.preferredLocalityId,
          )
        }
        _feedbackMessage.value = "Perfil de dueño actualizado"
      }
    }
  }

  fun signOut() {
    val service = authService ?: return
    viewModelScope.launch { service.signOut() }
  }

  fun clearAuthMessages() {
    authService?.clearMessages()
  }

  fun openCreateForm(defaultLocality: LocalityRegion) {
    val targetLocality =
      if (defaultLocality.isUserLocation) PatagonianLocalities.NeuquenCapital
      else defaultLocality
    _formState.value =
      BusinessFormState(
        selectedLocality = targetLocality,
        latitude = targetLocality.latitude,
        longitude = targetLocality.longitude,
        isSheetOpen = true,
      )
  }

  fun openEditForm(entity: PublishedBusinessEntity) {
    _formState.value =
      BusinessFormState(
        editingId = entity.id,
        editingFirestoreDocId = entity.firestoreDocId.ifBlank { null },
        name = entity.name,
        selectedCategory = DirectoryCategories.findById(entity.categoryId),
        selectedLocality = PatagonianLocalities.findById(entity.localityId),
        address = entity.address,
        latitude = entity.latitude,
        longitude = entity.longitude,
        phone = entity.phone,
        whatsapp = entity.whatsapp,
        website = entity.website,
        description = entity.description,
        isOpenNow = entity.isOpenNow,
        hasDelivery = entity.hasDelivery,
        isSheetOpen = true,
      )
  }

  fun openEditFromDirectoryBusiness(business: DirectoryBusiness) {
    viewModelScope.launch {
      val matchedLocality =
        PatagonianLocalities.all.firstOrNull {
          it.name.equals(business.localityName, ignoreCase = true)
        } ?: PatagonianLocalities.NeuquenCapital
      val docId = business.firestoreDocId ?: business.id.takeIf { !it.startsWith("local_") }
      val localMatch =
        if (!docId.isNullOrBlank()) repository.findByFirestoreDocId(docId)
        else {
          val numericId = business.id.removePrefix("local_").toLongOrNull()
          if (numericId != null) publishedBusinesses.value.firstOrNull { it.id == numericId }
          else null
        }

      _formState.value =
        BusinessFormState(
          editingId = localMatch?.id ?: 0L,
          editingFirestoreDocId = docId ?: localMatch?.firestoreDocId?.ifBlank { null },
          name = business.name,
          selectedCategory = DirectoryCategories.findById(business.categoryId),
          selectedLocality = matchedLocality,
          address = business.address,
          latitude = business.latitude,
          longitude = business.longitude,
          phone = business.phoneNumber.orEmpty(),
          whatsapp = business.whatsappNumber.orEmpty(),
          website = business.websiteUri.orEmpty(),
          description = business.description.orEmpty(),
          isOpenNow = business.isOpenNow ?: true,
          hasDelivery = business.hasDelivery,
          isSheetOpen = true,
        )
    }
  }

  fun closeForm() {
    _formState.update { it.copy(isSheetOpen = false, isSaving = false, validationError = null) }
  }

  fun updateName(value: String) {
    _formState.update { it.copy(name = value, validationError = null) }
  }

  fun updateCategory(category: BusinessCategory) {
    _formState.update { it.copy(selectedCategory = category) }
  }

  fun updateLocality(locality: LocalityRegion) {
    _formState.update {
      it.copy(
        selectedLocality = locality,
        latitude = if (it.editingId == 0L && it.editingFirestoreDocId == null) locality.latitude else it.latitude,
        longitude = if (it.editingId == 0L && it.editingFirestoreDocId == null) locality.longitude else it.longitude,
      )
    }
  }

  fun updateAddress(value: String) {
    _formState.update { it.copy(address = value, validationError = null) }
  }

  fun updateCoordinates(lat: Double, lng: Double) {
    _formState.update { it.copy(latitude = lat, longitude = lng) }
  }

  fun updatePhone(value: String) {
    _formState.update { it.copy(phone = value, validationError = null) }
  }

  fun updateWhatsapp(value: String) {
    _formState.update { it.copy(whatsapp = value, validationError = null) }
  }

  fun updateWebsite(value: String) {
    _formState.update { it.copy(website = value) }
  }

  fun updateDescription(value: String) {
    _formState.update { it.copy(description = value) }
  }

  fun updateOpenNow(value: Boolean) {
    _formState.update { it.copy(isOpenNow = value) }
  }

  fun updateDelivery(value: Boolean) {
    _formState.update { it.copy(hasDelivery = value) }
  }

  fun saveBusiness() {
    val state = _formState.value
    val signedInUser = authState.value.currentUser
    if (signedInUser == null) {
      _formState.update {
        it.copy(validationError = "Iniciá sesión con Google para guardar el comercio en tu cuenta.")
      }
      return
    }

    val nameTrim = state.name.trim()
    val addressTrim = state.address.trim().ifBlank { state.selectedLocality.name }
    val phoneTrim = state.phone.trim()
    val waTrim = state.whatsapp.trim()
    val descTrim = state.description.trim()

    if (nameTrim.length < 2) {
      _formState.update { it.copy(validationError = "Ingresá el nombre del comercio.") }
      return
    }
    if (phoneTrim.isBlank() && waTrim.isBlank()) {
      _formState.update {
        it.copy(validationError = "Ingresá al menos un teléfono o número de WhatsApp.")
      }
      return
    }

    viewModelScope.launch {
      _formState.update { it.copy(isSaving = true, validationError = null) }

      val listing =
        FirestoreBusinessListing(
          id = state.editingFirestoreDocId.orEmpty(),
          userId = signedInUser.uid,
          name = nameTrim,
          categoryId = state.selectedCategory.id,
          categoryName = state.selectedCategory.name,
          localityId = state.selectedLocality.id,
          localityName = state.selectedLocality.name,
          address = addressTrim,
          latitude = state.latitude,
          longitude = state.longitude,
          phoneNumber = phoneTrim.ifBlank { null },
          whatsappNumber = waTrim.ifBlank { null },
          websiteUri = state.website.trim().ifBlank { null },
          description = descTrim.ifBlank { null },
          openingHoursSummary =
            listOf(if (state.isOpenNow) "Atendiendo ahora" else "Cerrado temporalmente"),
          isOpenNow = state.isOpenNow,
          hasDelivery = state.hasDelivery,
          source = "user_published",
          markerColorArgb = state.selectedCategory.markerColorArgb.toLong() and 0xFFFFFFFFL,
          isActive = true,
        )

      var savedDocId = state.editingFirestoreDocId.orEmpty()
      var cloudSynced = false
      var cloudError: String? = null

      if (firestoreRepository != null && firestoreRepository.isConfigured) {
        val cloudResult =
          firestoreRepository.saveBusiness(
            listing = listing,
            existingDocId = state.editingFirestoreDocId,
          )
        cloudResult.fold(
          onSuccess = { docId ->
            savedDocId = docId
            cloudSynced = true
          },
          onFailure = { err ->
            cloudError = err.localizedMessage ?: "No se pudo guardar en Firestore."
          },
        )
      }

      if (cloudError != null) {
        _formState.update { it.copy(isSaving = false, validationError = cloudError) }
        return@launch
      }

      repository.saveBusiness(
        PublishedBusinessEntity(
          id = state.editingId,
          name = nameTrim,
          categoryId = state.selectedCategory.id,
          localityId = state.selectedLocality.id,
          address = addressTrim,
          latitude = state.latitude,
          longitude = state.longitude,
          phone = phoneTrim,
          whatsapp = waTrim,
          website = state.website.trim(),
          description = descTrim,
          isOpenNow = state.isOpenNow,
          hasDelivery = state.hasDelivery,
          ownerUserId = signedInUser.uid,
          firestoreDocId = savedDocId,
        )
      )

      _formState.update { it.copy(isSheetOpen = false, isSaving = false, validationError = null) }
      _feedbackMessage.value =
        when {
          cloudSynced && state.editingFirestoreDocId == null ->
            "Comercio publicado en Firestore"
          cloudSynced -> "Comercio actualizado en Firestore"
          state.editingId == 0L -> "Comercio guardado en Guía Genio"
          else -> "Comercio actualizado"
        }
    }
  }

  fun toggleOpenNow(entity: PublishedBusinessEntity) {
    viewModelScope.launch {
      val nextOpen = !entity.isOpenNow
      repository.toggleOpenStatus(entity.id, nextOpen)
      if (entity.firestoreDocId.isNotBlank() && firestoreRepository?.isConfigured == true) {
        firestoreRepository.updateBusinessOperationalFlags(
          docId = entity.firestoreDocId,
          isOpenNow = nextOpen,
          hasDelivery = entity.hasDelivery,
        )
      }
    }
  }

  fun toggleDelivery(entity: PublishedBusinessEntity) {
    viewModelScope.launch {
      val nextDelivery = !entity.hasDelivery
      repository.toggleDeliveryStatus(entity.id, nextDelivery)
      if (entity.firestoreDocId.isNotBlank() && firestoreRepository?.isConfigured == true) {
        firestoreRepository.updateBusinessOperationalFlags(
          docId = entity.firestoreDocId,
          isOpenNow = entity.isOpenNow,
          hasDelivery = nextDelivery,
        )
      }
    }
  }

  fun deleteBusiness(entity: PublishedBusinessEntity) {
    viewModelScope.launch {
      if (entity.firestoreDocId.isNotBlank() && firestoreRepository?.isConfigured == true) {
        firestoreRepository.deleteBusiness(entity.firestoreDocId)
      }
      repository.deleteBusiness(entity.id)
      _feedbackMessage.value = "Comercio eliminado"
    }
  }

  fun geocodeFormAddress() {
    val state = _formState.value
    val repo = placesRepository ?: return
    val addressTrim = state.address.trim()
    if (addressTrim.length < 3) {
      _formState.update { it.copy(validationError = "Ingresá una calle y altura para ubicarla.") }
      return
    }
    viewModelScope.launch {
      _formState.update { it.copy(isGeocodingAddress = true, validationError = null) }
      val result = repo.geocodeAddressWithNominatim(addressTrim, state.selectedLocality)
      result.fold(
        onSuccess = { coord ->
          if (coord != null) {
            _formState.update {
              it.copy(
                latitude = coord.latitude,
                longitude = coord.longitude,
                isGeocodingAddress = false,
              )
            }
            _feedbackMessage.value = "Pin ubicado según dirección"
          } else {
            _formState.update {
              it.copy(
                isGeocodingAddress = false,
                validationError = "No encontramos esa dirección exacta; podés tocar el mapa.",
              )
            }
          }
        },
        onFailure = {
          _formState.update {
            it.copy(
              isGeocodingAddress = false,
              validationError = "Error al geocodificar con Nominatim. Tocá el mapa manualmente.",
            )
          }
        },
      )
    }
  }

  fun clearFeedback() {
    _feedbackMessage.value = null
  }

  class Factory(
    private val repository: PublishedBusinessRepository,
    private val placesRepository: PlacesRepository? = null,
    private val authService: AuthService? = null,
    private val firestoreRepository: FirestoreDirectoryRepository? = null,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return MyBusinessesViewModel(
        repository = repository,
        placesRepository = placesRepository,
        authService = authService,
        firestoreRepository = firestoreRepository,
      ) as T
    }
  }
}
