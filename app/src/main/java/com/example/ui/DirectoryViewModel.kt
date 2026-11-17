package com.example.ui

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.auth.AuthService
import com.example.data.auth.BusinessOwnerUser
import com.example.data.local.FavoriteBusinessEntity
import com.example.data.local.PublishedBusinessEntity
import com.example.data.local.PublishedBusinessRepository
import com.example.data.places.LocationTracker
import com.example.data.places.PlaceAutocompleteSuggestion
import com.example.data.places.PlacesRepository
import com.example.data.remote.FirebaseConfigurationSnapshot
import com.example.data.remote.FirestoreConfigStatus
import com.example.data.remote.FirestoreDirectoryRepository
import com.example.domain.model.BusinessCategory
import com.example.domain.model.BusinessReview
import com.example.domain.model.BusinessSource
import com.example.domain.model.BusinessStatus
import com.example.domain.model.DirectoryBusiness
import com.example.domain.model.DirectoryCategories
import com.example.domain.model.DirectoryFilters
import com.example.domain.model.DirectoryResultsState
import com.example.domain.model.DirectoryViewMode
import com.example.domain.model.GeoPointCoord
import com.example.domain.model.LocalityRegion
import com.example.domain.model.PatagonianLocalities
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DirectoryUiSnapshot(
  val searchQuery: String = "",
  val selectedLocality: LocalityRegion = PatagonianLocalities.NeuquenCapital,
  val selectedCategory: BusinessCategory = DirectoryCategories.AllCategory,
  val filters: DirectoryFilters = DirectoryFilters(),
  val viewMode: DirectoryViewMode = DirectoryViewMode.LIST,
  val isResolvingLocation: Boolean = false,
  val locationMessage: String? = null,
)

data class AutocompleteUiState(
  val isExpanded: Boolean = false,
  val isLoading: Boolean = false,
  val matchingCategories: List<BusinessCategory> = emptyList(),
  val placePredictions: List<PlaceAutocompleteSuggestion> = emptyList(),
)

private data class PlacesFetchState(
  val isLoading: Boolean = true,
  val places: List<DirectoryBusiness> = emptyList(),
  val errorMessage: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class DirectoryViewModel(
  private val placesRepository: PlacesRepository,
  private val publishedRepository: PublishedBusinessRepository,
  private val locationTracker: LocationTracker,
  private val firestoreRepository: FirestoreDirectoryRepository? = null,
  private val authService: AuthService? = null,
) : ViewModel() {

  private val _uiControl = MutableStateFlow(DirectoryUiSnapshot())
  val uiControl: StateFlow<DirectoryUiSnapshot> = _uiControl.asStateFlow()

  private val _mapSearchQuery = MutableStateFlow("")
  val mapSearchQuery: StateFlow<String> = _mapSearchQuery.asStateFlow()

  private val _autocompleteState = MutableStateFlow(AutocompleteUiState())
  val autocompleteState: StateFlow<AutocompleteUiState> = _autocompleteState.asStateFlow()

  private val _placesState = MutableStateFlow(PlacesFetchState())

  private val _selectedBusiness = MutableStateFlow<DirectoryBusiness?>(null)
  val selectedBusiness: StateFlow<DirectoryBusiness?> = _selectedBusiness.asStateFlow()

  private val _focusedMapBusiness = MutableStateFlow<DirectoryBusiness?>(null)
  val focusedMapBusiness: StateFlow<DirectoryBusiness?> = _focusedMapBusiness.asStateFlow()

  private val _userGpsCoordinate = MutableStateFlow<GeoPointCoord?>(null)
  val userGpsCoordinate: StateFlow<GeoPointCoord?> = _userGpsCoordinate.asStateFlow()

  private val _gpsCenterRequestNonce = MutableStateFlow(0)
  val gpsCenterRequestNonce: StateFlow<Int> = _gpsCenterRequestNonce.asStateFlow()

  private val _isSubmittingReview = MutableStateFlow(false)
  val isSubmittingReview: StateFlow<Boolean> = _isSubmittingReview.asStateFlow()

  private val _reviewFeedback = MutableStateFlow<String?>(null)
  val reviewFeedback: StateFlow<String?> = _reviewFeedback.asStateFlow()

  private val _uiFeedbackMessage = MutableStateFlow<String?>(null)
  val uiFeedbackMessage: StateFlow<String?> = _uiFeedbackMessage.asStateFlow()

  val firebaseConfigurationState: StateFlow<FirebaseConfigurationSnapshot> =
    firestoreRepository?.configurationState
      ?: MutableStateFlow(
        FirebaseConfigurationSnapshot(
          configStatus =
            FirestoreConfigStatus.Disabled(
              reason = FirestoreConfigStatus.Disabled.Reason.INIT_FAILED,
              userMessage = "Repositorio Firestore no disponible en esta instancia.",
            )
        )
      ).asStateFlow()

  val isFirestoreConfigured: Boolean
    get() = firestoreRepository?.isConfigured == true

  val firestoreConfigStatus: FirestoreConfigStatus
    get() =
      firestoreRepository?.configStatus
        ?: FirestoreConfigStatus.Disabled(
          reason = FirestoreConfigStatus.Disabled.Reason.INIT_FAILED,
          userMessage = "Repositorio Firestore no disponible en esta instancia.",
        )

  val currentUser: StateFlow<BusinessOwnerUser?> =
    (authService?.authState?.map { it.currentUser } ?: flowOf(null))
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

  private val firestoreBusinesses: Flow<List<DirectoryBusiness>> =
    firestoreRepository?.observeApprovedBusinesses() ?: flowOf(emptyList())

  val favoriteBusinesses: StateFlow<List<DirectoryBusiness>> =
    currentUser
      .flatMapLatest { user ->
        if (user == null) {
          flowOf(emptyList())
        } else if (firestoreRepository != null && firestoreRepository.isConfigured) {
          firestoreRepository.observeFavorites(user.uid)
        } else {
          publishedRepository.favorites.map { list -> list.map { it.toDomainBusiness() } }
        }
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  val favoriteIds: StateFlow<Set<String>> =
    favoriteBusinesses
      .map { list -> list.map { it.id }.toSet() }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptySet())

  val reviewsForSelectedBusiness: StateFlow<List<BusinessReview>> =
    _selectedBusiness
      .flatMapLatest { business ->
        if (business == null || firestoreRepository == null) {
          flowOf(emptyList())
        } else {
          val targetId = business.firestoreDocId ?: business.id
          firestoreRepository.observeReviews(targetId)
        }
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  private var searchJob: Job? = null
  private var autocompleteJob: Job? = null
  private var customMapCenter: GeoPointCoord? = null

  val resultsState: StateFlow<DirectoryResultsState> =
    combine(
        _uiControl,
        _placesState,
        publishedRepository.publishedBusinesses,
        firestoreBusinesses,
      ) { control, placesState, publishedEntities, cloudBusinesses ->
        buildResultsState(control, placesState, publishedEntities, cloudBusinesses)
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = DirectoryResultsState.Loading,
      )

  /**
   * Comercios mostrados en el mapa tras aplicar la barra de búsqueda del mapa
   * sobre datos de Firestore y del directorio local/OSM.
   */
  val mapFilteredBusinesses: StateFlow<List<DirectoryBusiness>> =
    combine(resultsState, firestoreBusinesses, _mapSearchQuery) { state, cloudList, mapQuery ->
      val currentList =
        when (state) {
          is DirectoryResultsState.Success -> state.businesses
          else -> emptyList()
        }
      val trimmed = mapQuery.trim()
      if (trimmed.isEmpty()) {
        currentList
      } else {
        // Unificar comercios de Firestore y resultados actuales evitando duplicados para filtrar por nombre o rubro
        val mergedById = LinkedHashMap<String, DirectoryBusiness>()
        cloudList.forEach { mergedById[it.id] = it }
        currentList.forEach { mergedById.putIfAbsent(it.id, it) }
        filterBusinessesByQuery(mergedById.values.toList(), trimmed)
      }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

  init {
    triggerPlacesSearch(immediate = true)
  }

  fun onSearchQueryChange(query: String) {
    _uiControl.update { it.copy(searchQuery = query) }
    triggerAutocomplete(query)
    triggerPlacesSearch(immediate = false)
  }

  fun onMapSearchQueryChange(query: String) {
    _mapSearchQuery.value = query
  }

  fun dismissAutocomplete() {
    _autocompleteState.update { it.copy(isExpanded = false) }
  }

  fun selectAutocompleteCategory(category: BusinessCategory) {
    _autocompleteState.value = AutocompleteUiState()
    _uiControl.update { it.copy(selectedCategory = category, searchQuery = "") }
    triggerPlacesSearch(immediate = true)
  }

  fun selectAutocompletePrediction(suggestion: PlaceAutocompleteSuggestion) {
    _autocompleteState.value = AutocompleteUiState()
    _uiControl.update { it.copy(searchQuery = suggestion.primaryText) }

    val fetched = suggestion.business
    if (fetched != null) {
      customMapCenter = fetched.latLng
      _focusedMapBusiness.value = fetched
      _placesState.update { state ->
        val merged = listOf(fetched) + state.places.filterNot { it.id == fetched.id }
        state.copy(isLoading = false, places = merged, errorMessage = null)
      }
      openBusinessDetail(fetched)
    } else {
      triggerPlacesSearch(immediate = true)
    }
  }

  fun clearFocusedMapBusiness() {
    _focusedMapBusiness.value = null
  }

  fun selectLocality(locality: LocalityRegion) {
    customMapCenter = null
    _focusedMapBusiness.value = null
    _uiControl.update { it.copy(selectedLocality = locality, locationMessage = null) }
    triggerPlacesSearch(immediate = true)
  }

  fun selectCategory(category: BusinessCategory) {
    _focusedMapBusiness.value = null
    _uiControl.update { it.copy(selectedCategory = category) }
    triggerPlacesSearch(immediate = true)
  }

  fun toggleOpenNowFilter() {
    _uiControl.update {
      it.copy(filters = it.filters.copy(openNowOnly = !it.filters.openNowOnly))
    }
    triggerPlacesSearch(immediate = true)
  }

  fun toggleDeliveryFilter() {
    _uiControl.update {
      it.copy(filters = it.filters.copy(deliveryOnly = !it.filters.deliveryOnly))
    }
    triggerPlacesSearch(immediate = true)
  }

  fun toggleWhatsAppFilter() {
    _uiControl.update {
      it.copy(filters = it.filters.copy(whatsappOnly = !it.filters.whatsappOnly))
    }
  }

  fun toggleUserPublishedFilter() {
    _uiControl.update {
      it.copy(filters = it.filters.copy(userPublishedOnly = !it.filters.userPublishedOnly))
    }
  }

  fun clearAllFilters() {
    _autocompleteState.value = AutocompleteUiState()
    _focusedMapBusiness.value = null
    _mapSearchQuery.value = ""
    _uiControl.update {
      it.copy(
        searchQuery = "",
        selectedCategory = DirectoryCategories.AllCategory,
        filters = DirectoryFilters(),
      )
    }
    triggerPlacesSearch(immediate = true)
  }

  fun setViewMode(mode: DirectoryViewMode) {
    _uiControl.update { it.copy(viewMode = mode) }
  }

  fun searchInMapArea(center: GeoPointCoord) {
    customMapCenter = center
    triggerPlacesSearch(immediate = true)
  }

  fun requestCurrentLocation() {
    viewModelScope.launch {
      _uiControl.update { it.copy(isResolvingLocation = true, locationMessage = null) }
      val coord = locationTracker.getCurrentLatLng()
      if (coord != null) {
        customMapCenter = null
        _userGpsCoordinate.value = coord
        _gpsCenterRequestNonce.update { it + 1 }
        val userLocality =
          LocalityRegion(
            id = "user_location",
            name = "Mi ubicación",
            province = "GPS",
            zoneLabel = "Cerca tuyo",
            latitude = coord.latitude,
            longitude = coord.longitude,
            radiusMeters = 7000.0,
            isUserLocation = true,
          )
        _uiControl.update {
          it.copy(
            selectedLocality = userLocality,
            isResolvingLocation = false,
            locationMessage = null,
          )
        }
        triggerPlacesSearch(immediate = true)
      } else {
        _uiControl.update {
          it.copy(
            isResolvingLocation = false,
            locationMessage = "No pudimos obtener tu ubicación GPS actual. Verificá que la ubicación esté activada.",
          )
        }
      }
    }
  }

  fun refreshSearch() {
    triggerPlacesSearch(immediate = true)
  }

  fun openBusinessDetail(business: DirectoryBusiness) {
    _reviewFeedback.value = null
    _selectedBusiness.value = business
  }

  fun closeBusinessDetail() {
    _selectedBusiness.value = null
    _reviewFeedback.value = null
  }

  fun toggleFavorite(business: DirectoryBusiness) {
    val user = currentUser.value
    if (user == null) {
      _uiFeedbackMessage.value = "Iniciá sesión con Google para guardar favoritos en tu lista."
      return
    }
    viewModelScope.launch {
      val currentlyFav = favoriteIds.value.contains(business.id)
      // Actualizar espejo local y guardar en Firestore únicamente si está configurado
      publishedRepository.toggleFavorite(business, currentlyFav)
      if (firestoreRepository != null && firestoreRepository.isConfigured) {
        val result = firestoreRepository.toggleFavorite(user.uid, business, currentlyFav)
        result.fold(
          onSuccess = {
            _uiFeedbackMessage.value =
              if (currentlyFav) "Quitado de Favoritos en Firestore"
              else "Guardado en Favoritos en Firestore"
          },
          onFailure = { error ->
            _uiFeedbackMessage.value =
              error.localizedMessage ?: "No se pudo sincronizar el favorito en Firestore."
          },
        )
      } else {
        _uiFeedbackMessage.value =
          if (currentlyFav) {
            "Quitado de Favoritos locales (Firebase deshabilitado: no sincroniza en la nube)"
          } else {
            "Guardado en Favoritos locales (Firebase deshabilitado: no sincroniza en la nube)"
          }
      }
    }
  }

  fun submitReview(business: DirectoryBusiness, rating: Int, comment: String) {
    val user = currentUser.value
    if (user == null) {
      _reviewFeedback.value = "Iniciá sesión con Google para publicar una calificación."
      return
    }
    val repo = firestoreRepository
    if (repo == null || !repo.isConfigured) {
      _reviewFeedback.value =
        repo?.disabledReasonMessage()
          ?: "Firebase deshabilitado: no se pueden publicar reseñas en Firestore."
      return
    }
    viewModelScope.launch {
      _isSubmittingReview.value = true
      _reviewFeedback.value = null
      val targetId = business.firestoreDocId ?: business.id
      val result =
        repo.submitReview(
          businessId = targetId,
          rating = rating,
          comment = comment,
          authorName = user.displayName,
        )
      _isSubmittingReview.value = false
      result.fold(
        onSuccess = {
          _reviewFeedback.value = "¡Gracias! Tu reseña de $rating ★ se guardó en Firestore."
        },
        onFailure = { error ->
          _reviewFeedback.value =
            error.localizedMessage ?: "No se pudo guardar la reseña en Firestore."
        },
      )
    }
  }

  fun clearReviewFeedback() {
    _reviewFeedback.value = null
  }

  fun clearUiFeedbackMessage() {
    _uiFeedbackMessage.value = null
  }

  private fun triggerAutocomplete(query: String) {
    autocompleteJob?.cancel()
    val trimmed = query.trim()
    if (trimmed.length < 2) {
      _autocompleteState.value = AutocompleteUiState()
      return
    }

    val matchingCategories =
      DirectoryCategories.items.filter {
        it.name.contains(trimmed, ignoreCase = true) ||
          it.shortName.contains(trimmed, ignoreCase = true) ||
          it.placesQueryTerm.contains(trimmed, ignoreCase = true)
      }.take(3)

    _autocompleteState.update {
      it.copy(
        isExpanded = true,
        isLoading = true,
        matchingCategories = matchingCategories,
      )
    }

    autocompleteJob =
      viewModelScope.launch {
        delay(280L)
        val locality = _uiControl.value.selectedLocality
        val result = placesRepository.autocompletePlaces(trimmed, locality)
        result.fold(
          onSuccess = { predictions ->
            _autocompleteState.update {
              it.copy(
                isExpanded = matchingCategories.isNotEmpty() || predictions.isNotEmpty(),
                isLoading = false,
                placePredictions = predictions,
              )
            }
          },
          onFailure = {
            _autocompleteState.update {
              it.copy(
                isExpanded = matchingCategories.isNotEmpty(),
                isLoading = false,
                placePredictions = emptyList(),
              )
            }
          },
        )
      }
  }

  private fun triggerPlacesSearch(immediate: Boolean) {
    searchJob?.cancel()
    searchJob =
      viewModelScope.launch {
        if (!immediate) delay(400L)

        val snapshot = _uiControl.value
        _placesState.update { it.copy(isLoading = true, errorMessage = null) }

        val result =
          placesRepository.searchBusinesses(
            query = snapshot.searchQuery,
            locality = snapshot.selectedLocality,
            category = snapshot.selectedCategory,
            openNowOnly = snapshot.filters.openNowOnly,
            deliveryHint = snapshot.filters.deliveryOnly,
            searchCenterOverride = customMapCenter,
          )

        result.fold(
          onSuccess = { places ->
            _placesState.value =
              PlacesFetchState(
                isLoading = false,
                places = places,
                errorMessage = null,
              )
          },
          onFailure = { error ->
            _placesState.value =
              PlacesFetchState(
                isLoading = false,
                places = emptyList(),
                errorMessage =
                  error.localizedMessage ?: "No pudimos consultar lugares en OpenStreetMap.",
              )
          },
        )
      }
  }

  private fun buildResultsState(
    control: DirectoryUiSnapshot,
    placesState: PlacesFetchState,
    publishedEntities: List<PublishedBusinessEntity>,
    cloudBusinesses: List<DirectoryBusiness>,
  ): DirectoryResultsState {
    val refCenter = customMapCenter ?: control.selectedLocality.latLng
    val queryLower = control.searchQuery.trim().lowercase()

    // Combinar únicamente comercios aprobados (status == APPROVED) evitando duplicados por ID/firestoreDocId
    val mergedUserBusinesses = LinkedHashMap<String, DirectoryBusiness>()
    cloudBusinesses
      .filter { it.status == BusinessStatus.APPROVED }
      .forEach { biz ->
        val dist = FloatArray(1)
        Location.distanceBetween(
          refCenter.latitude,
          refCenter.longitude,
          biz.latitude,
          biz.longitude,
          dist,
        )
        mergedUserBusinesses[biz.id] = biz.copy(distanceMeters = dist[0])
      }
    publishedEntities
      .filter { it.moderationStatus == BusinessStatus.APPROVED }
      .forEach { entity ->
        val key = entity.firestoreDocId.ifBlank { "local_${entity.id}" }
        if (!mergedUserBusinesses.containsKey(key)) {
          val dist = FloatArray(1)
          Location.distanceBetween(
            refCenter.latitude,
            refCenter.longitude,
            entity.latitude,
            entity.longitude,
            dist,
          )
          mergedUserBusinesses[key] = entity.toDirectoryBusiness(distanceMeters = dist[0])
        }
      }

    val filteredUserPublished =
      mergedUserBusinesses.values.filter { business ->
        val matchesLocality =
          control.selectedLocality.isUserLocation ||
            business.localityName.equals(control.selectedLocality.name, ignoreCase = true) ||
            (business.distanceMeters ?: Float.MAX_VALUE) <=
              control.selectedLocality.radiusMeters.toFloat() * 1.5f
        val matchesCategory =
          control.selectedCategory.id == DirectoryCategories.AllCategory.id ||
            business.categoryId == control.selectedCategory.id
        val matchesQuery =
          queryLower.isEmpty() ||
            business.name.lowercase().contains(queryLower) ||
            business.categoryName.lowercase().contains(queryLower) ||
            business.address.lowercase().contains(queryLower) ||
            (business.description?.lowercase()?.contains(queryLower) == true)
        matchesLocality && matchesCategory && matchesQuery
      }

    val combined =
      if (control.filters.userPublishedOnly) {
        filteredUserPublished
      } else {
        filteredUserPublished + placesState.places
      }

    val filtered =
      combined.filter { item ->
        val openOk = !control.filters.openNowOnly || item.isOpenNow == true
        val deliveryOk =
          !control.filters.deliveryOnly ||
            item.hasDelivery ||
            (item.source == BusinessSource.OPENSTREETMAP && item.categoryId == "gastronomia")
        val whatsappOk = !control.filters.whatsappOnly || item.hasWhatsApp
        openOk && deliveryOk && whatsappOk
      }

    if (placesState.isLoading && filtered.isEmpty()) {
      return DirectoryResultsState.Loading
    }

    if (placesState.errorMessage != null && filtered.isEmpty()) {
      return DirectoryResultsState.Error(message = placesState.errorMessage)
    }

    return DirectoryResultsState.Success(
      businesses = filtered,
      placesWarning = placesState.errorMessage,
    )
  }

  private fun FavoriteBusinessEntity.toDomainBusiness(): DirectoryBusiness {
    val matchedCategory =
      DirectoryCategories.items.firstOrNull {
        it.name.equals(categoryName, ignoreCase = true) ||
          it.shortName.equals(categoryName, ignoreCase = true)
      } ?: DirectoryCategories.AllCategory
    return DirectoryBusiness(
      id = businessId,
      name = name,
      categoryId = matchedCategory.id,
      categoryName = categoryName,
      localityName = localityName,
      address = address,
      latitude = latitude,
      longitude = longitude,
      phoneNumber = phone,
      whatsappNumber = whatsapp,
      hasDelivery = false,
      source = BusinessSource.OPENSTREETMAP,
      status = BusinessStatus.fromString(status, BusinessStatus.APPROVED),
      markerColorArgb = matchedCategory.markerColorArgb,
    )
  }

  companion object {
    fun filterBusinessesByQuery(
      businesses: List<DirectoryBusiness>,
      query: String,
    ): List<DirectoryBusiness> {
      val trimmed = query.trim().lowercase()
      if (trimmed.isEmpty()) return businesses
      return businesses.filter { item ->
        item.name.lowercase().contains(trimmed) ||
          item.categoryName.lowercase().contains(trimmed) ||
          item.address.lowercase().contains(trimmed)
      }
    }
  }

  class Factory(
    private val placesRepository: PlacesRepository,
    private val publishedRepository: PublishedBusinessRepository,
    private val locationTracker: LocationTracker,
    private val firestoreRepository: FirestoreDirectoryRepository? = null,
    private val authService: AuthService? = null,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return DirectoryViewModel(
        placesRepository = placesRepository,
        publishedRepository = publishedRepository,
        locationTracker = locationTracker,
        firestoreRepository = firestoreRepository,
        authService = authService,
      ) as T
    }
  }
}
