package com.example.ui

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.PublishedBusinessEntity
import com.example.data.local.PublishedBusinessRepository
import com.example.data.places.LocationTracker
import com.example.data.places.PlaceAutocompleteSuggestion
import com.example.data.places.PlacesRepository
import com.example.domain.model.BusinessCategory
import com.example.domain.model.BusinessSource
import com.example.domain.model.DirectoryBusiness
import com.example.domain.model.DirectoryCategories
import com.example.domain.model.DirectoryFilters
import com.example.domain.model.DirectoryResultsState
import com.example.domain.model.DirectoryViewMode
import com.example.domain.model.GeoPointCoord
import com.example.domain.model.LocalityRegion
import com.example.domain.model.PatagonianLocalities
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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

class DirectoryViewModel(
  private val placesRepository: PlacesRepository,
  private val publishedRepository: PublishedBusinessRepository,
  private val locationTracker: LocationTracker,
) : ViewModel() {

  private val _uiControl = MutableStateFlow(DirectoryUiSnapshot())
  val uiControl: StateFlow<DirectoryUiSnapshot> = _uiControl.asStateFlow()

  private val _autocompleteState = MutableStateFlow(AutocompleteUiState())
  val autocompleteState: StateFlow<AutocompleteUiState> = _autocompleteState.asStateFlow()

  private val _placesState = MutableStateFlow(PlacesFetchState())

  private val _selectedBusiness = MutableStateFlow<DirectoryBusiness?>(null)
  val selectedBusiness: StateFlow<DirectoryBusiness?> = _selectedBusiness.asStateFlow()

  private val _focusedMapBusiness = MutableStateFlow<DirectoryBusiness?>(null)
  val focusedMapBusiness: StateFlow<DirectoryBusiness?> = _focusedMapBusiness.asStateFlow()

  val favoriteIds: StateFlow<Set<String>> =
    publishedRepository.favoriteIds
      .combine(MutableStateFlow(Unit)) { ids, _ -> ids.toSet() }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptySet())

  private var searchJob: Job? = null
  private var autocompleteJob: Job? = null
  private var customMapCenter: GeoPointCoord? = null

  val resultsState: StateFlow<DirectoryResultsState> =
    combine(
        _uiControl,
        _placesState,
        publishedRepository.publishedBusinesses,
      ) { control, placesState, publishedEntities ->
        buildResultsState(control, placesState, publishedEntities)
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = DirectoryResultsState.Loading,
      )

  init {
    triggerPlacesSearch(immediate = true)
  }

  fun onSearchQueryChange(query: String) {
    _uiControl.update { it.copy(searchQuery = query) }
    triggerAutocomplete(query)
    triggerPlacesSearch(immediate = false)
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
            locationMessage = "No pudimos obtener tu ubicación actual. Usamos la localidad elegida.",
          )
        }
      }
    }
  }

  fun refreshSearch() {
    triggerPlacesSearch(immediate = true)
  }

  fun openBusinessDetail(business: DirectoryBusiness) {
    _selectedBusiness.value = business
  }

  fun closeBusinessDetail() {
    _selectedBusiness.value = null
  }

  fun toggleFavorite(business: DirectoryBusiness) {
    viewModelScope.launch {
      val currentlyFav = favoriteIds.value.contains(business.id)
      publishedRepository.toggleFavorite(business, currentlyFav)
    }
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
  ): DirectoryResultsState {
    val refCenter = customMapCenter ?: control.selectedLocality.latLng
    val queryLower = control.searchQuery.trim().lowercase()

    val mappedPublished =
      publishedEntities
        .map { entity ->
          val distance = FloatArray(1)
          Location.distanceBetween(
            refCenter.latitude,
            refCenter.longitude,
            entity.latitude,
            entity.longitude,
            distance,
          )
          entity.toDirectoryBusiness(distanceMeters = distance[0])
        }
        .filter { business ->
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
        mappedPublished
      } else {
        mappedPublished + placesState.places
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

  class Factory(
    private val placesRepository: PlacesRepository,
    private val publishedRepository: PublishedBusinessRepository,
    private val locationTracker: LocationTracker,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return DirectoryViewModel(placesRepository, publishedRepository, locationTracker) as T
    }
  }
}
