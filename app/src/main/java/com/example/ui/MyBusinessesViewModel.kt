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
import com.example.domain.model.BusinessCategory
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
  val isGeocodingAddress: Boolean = false,
  val validationError: String? = null,
)

class MyBusinessesViewModel(
  private val repository: PublishedBusinessRepository,
  private val placesRepository: PlacesRepository? = null,
  private val authService: AuthService? = null,
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
    viewModelScope.launch { service.signInWithGoogle(activityContext) }
  }

  fun updateOwnerProfile(displayName: String, preferredLocalityId: String) {
    val service = authService ?: return
    viewModelScope.launch {
      val ok = service.updateOwnerProfile(displayName, preferredLocalityId)
      if (ok) {
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
    _formState.value =
      BusinessFormState(
        selectedLocality =
          if (defaultLocality.isUserLocation) PatagonianLocalities.NeuquenCapital
          else defaultLocality,
        latitude =
          if (defaultLocality.isUserLocation) PatagonianLocalities.NeuquenCapital.latitude
          else defaultLocality.latitude,
        longitude =
          if (defaultLocality.isUserLocation) PatagonianLocalities.NeuquenCapital.longitude
          else defaultLocality.longitude,
        isSheetOpen = true,
      )
  }

  fun openEditForm(entity: PublishedBusinessEntity) {
    _formState.value =
      BusinessFormState(
        editingId = entity.id,
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

  fun closeForm() {
    _formState.update { it.copy(isSheetOpen = false, validationError = null) }
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
        latitude = if (it.editingId == 0L) locality.latitude else it.latitude,
        longitude = if (it.editingId == 0L) locality.longitude else it.longitude,
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
    val nameTrim = state.name.trim()
    val addressTrim = state.address.trim()
    val phoneTrim = state.phone.trim()
    val waTrim = state.whatsapp.trim()

    if (nameTrim.length < 2) {
      _formState.update { it.copy(validationError = "Ingresá el nombre del comercio.") }
      return
    }
    if (addressTrim.length < 4) {
      _formState.update { it.copy(validationError = "Ingresá la dirección del local.") }
      return
    }
    if (phoneTrim.isBlank() && waTrim.isBlank()) {
      _formState.update {
        it.copy(validationError = "Ingresá al menos un teléfono o número de WhatsApp.")
      }
      return
    }

    viewModelScope.launch {
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
          description = state.description.trim(),
          isOpenNow = state.isOpenNow,
          hasDelivery = state.hasDelivery,
        )
      )
      _formState.update { it.copy(isSheetOpen = false, validationError = null) }
      _feedbackMessage.value =
        if (state.editingId == 0L) "Comercio publicado en Guía Genio"
        else "Comercio actualizado"
    }
  }

  fun toggleOpenNow(entity: PublishedBusinessEntity) {
    viewModelScope.launch { repository.toggleOpenStatus(entity.id, !entity.isOpenNow) }
  }

  fun toggleDelivery(entity: PublishedBusinessEntity) {
    viewModelScope.launch { repository.toggleDeliveryStatus(entity.id, !entity.hasDelivery) }
  }

  fun deleteBusiness(entity: PublishedBusinessEntity) {
    viewModelScope.launch {
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
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return MyBusinessesViewModel(repository, placesRepository, authService) as T
    }
  }
}
