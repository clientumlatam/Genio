package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.DeliveryDining
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.R
import com.example.data.places.PlaceAutocompleteSuggestion
import com.example.domain.model.BusinessCategory
import com.example.domain.model.DirectoryBusiness
import com.example.domain.model.DirectoryCategories
import com.example.domain.model.DirectoryResultsState
import com.example.domain.model.DirectoryViewMode
import com.example.domain.model.GeoPointCoord
import com.example.ui.AutocompleteUiState
import com.example.ui.DirectoryUiSnapshot
import com.example.ui.components.BusinessCard
import com.example.ui.components.PatagoniaHeroBanner
import com.example.ui.components.PlacesAutocompleteDropdown
import com.example.ui.components.PlacesAutocompleteField

@Composable
fun ExploreScreen(
  uiControl: DirectoryUiSnapshot,
  resultsState: DirectoryResultsState,
  autocompleteState: AutocompleteUiState,
  focusedMapBusiness: DirectoryBusiness?,
  favoriteIds: Set<String>,
  onSearchQueryChange: (String) -> Unit,
  onSelectAutocompleteCategory: (BusinessCategory) -> Unit,
  onSelectAutocompletePrediction: (PlaceAutocompleteSuggestion) -> Unit,
  onDismissAutocomplete: () -> Unit,
  onClearFocusedMapBusiness: () -> Unit,
  onSelectCategory: (BusinessCategory) -> Unit,
  onToggleOpenNow: () -> Unit,
  onToggleDelivery: () -> Unit,
  onToggleWhatsApp: () -> Unit,
  onToggleUserPublished: () -> Unit,
  onClearFilters: () -> Unit,
  onViewModeChange: (DirectoryViewMode) -> Unit,
  onRetry: () -> Unit,
  onBusinessClick: (DirectoryBusiness) -> Unit,
  onToggleFavorite: (DirectoryBusiness) -> Unit,
  onSearchInArea: (GeoPointCoord) -> Unit,
  modifier: Modifier = Modifier,
) {
  val allCategories = listOf(DirectoryCategories.AllCategory) + DirectoryCategories.items
  var showInlineMapCard by rememberSaveable { mutableStateOf(true) }

  val nearbyCount =
    when (resultsState) {
      is DirectoryResultsState.Success -> resultsState.businesses.size
      else -> 0
    }

  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val isWideScreen = maxWidth >= 720.dp

    Column(modifier = Modifier.fillMaxSize()) {
      // Top Search Bar with Places Autocomplete + List/Map Switcher
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        PlacesAutocompleteField(
          query = uiControl.searchQuery,
          isLoadingPredictions = autocompleteState.isLoading,
          onQueryChange = onSearchQueryChange,
          modifier = Modifier.weight(1f),
        )

        if (!isWideScreen) {
          SingleChoiceSegmentedButtonRow(modifier = Modifier.height(48.dp)) {
            SegmentedButton(
              selected = uiControl.viewMode == DirectoryViewMode.LIST,
              onClick = { onViewModeChange(DirectoryViewMode.LIST) },
              shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
              modifier = Modifier.testTag("toggle_list_view"),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Rounded.FormatListBulleted,
                contentDescription = stringResource(R.string.view_mode_list),
                modifier = Modifier.size(18.dp),
              )
            }
            SegmentedButton(
              selected = uiControl.viewMode == DirectoryViewMode.MAP,
              onClick = { onViewModeChange(DirectoryViewMode.MAP) },
              shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
              modifier = Modifier.testTag("toggle_map_view"),
            ) {
              Icon(
                imageVector = Icons.Rounded.Map,
                contentDescription = stringResource(R.string.view_mode_map),
                modifier = Modifier.size(18.dp),
              )
            }
          }
        }
      }

      Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxSize()) {
          // Horizontal Rubros Strip
          LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            items(allCategories, key = { it.id }) { category ->
              val selected = category.id == uiControl.selectedCategory.id
              FilterChip(
                selected = selected,
                onClick = { onSelectCategory(category) },
                label = { Text(category.shortName) },
                leadingIcon = {
                  Icon(
                    imageVector = category.icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                  )
                },
                colors =
                  FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                  ),
                modifier = Modifier.testTag("category_chip_${category.id}"),
              )
            }
          }

          // Operational Filters Row (Abierto ahora, Delivery, WhatsApp, Mis publicados)
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
              leadingIcon = {
                Icon(
                  Icons.Rounded.Schedule,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                )
              },
              modifier = Modifier.testTag("filter_open_now"),
            )
            FilterChip(
              selected = uiControl.filters.deliveryOnly,
              onClick = onToggleDelivery,
              label = { Text(stringResource(R.string.filter_delivery)) },
              leadingIcon = {
                Icon(
                  Icons.Rounded.DeliveryDining,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                )
              },
              modifier = Modifier.testTag("filter_delivery"),
            )
            FilterChip(
              selected = uiControl.filters.whatsappOnly,
              onClick = onToggleWhatsApp,
              label = { Text(stringResource(R.string.filter_whatsapp)) },
              modifier = Modifier.testTag("filter_whatsapp"),
            )
            FilterChip(
              selected = uiControl.filters.userPublishedOnly,
              onClick = onToggleUserPublished,
              label = { Text(stringResource(R.string.filter_user_published)) },
              leadingIcon = {
                Icon(
                  Icons.Rounded.Verified,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                )
              },
              modifier = Modifier.testTag("filter_user_published"),
            )
          }

          AnimatedVisibility(visible = uiControl.locationMessage != null) {
            uiControl.locationMessage?.let { msg ->
              Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
              ) {
                Text(
                  text = msg,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSecondaryContainer,
                  modifier = Modifier.padding(10.dp),
                )
              }
            }
          }

          if (isWideScreen) {
            // Canonical List + Live Map Split Layout on Tablets / Foldables
            Row(modifier = Modifier.fillMaxSize().padding(top = 4.dp)) {
              Box(modifier = Modifier.weight(0.52f).fillMaxHeight()) {
                DirectoryListBody(
                  uiControl = uiControl,
                  resultsState = resultsState,
                  favoriteIds = favoriteIds,
                  onClearFilters = onClearFilters,
                  onRetry = onRetry,
                  onBusinessClick = onBusinessClick,
                  onToggleFavorite = onToggleFavorite,
                )
              }
              Box(
                modifier =
                  Modifier.weight(0.48f)
                    .fillMaxHeight()
                    .padding(end = 16.dp, bottom = 12.dp)
              ) {
                Surface(
                  shape = RoundedCornerShape(22.dp),
                  border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                  modifier = Modifier.fillMaxSize(),
                ) {
                  DirectoryMapCanvas(
                    selectedLocality = uiControl.selectedLocality,
                    resultsState = resultsState,
                    focusedBusiness = focusedMapBusiness,
                    onFocusedBusinessConsumed = onClearFocusedMapBusiness,
                    onBusinessClick = onBusinessClick,
                    onSearchInArea = onSearchInArea,
                  )
                }
              }
            }
          } else {
            if (uiControl.viewMode == DirectoryViewMode.MAP) {
              DirectoryMapCanvas(
                selectedLocality = uiControl.selectedLocality,
                resultsState = resultsState,
                focusedBusiness = focusedMapBusiness,
                onFocusedBusinessConsumed = onClearFocusedMapBusiness,
                onBusinessClick = onBusinessClick,
                onSearchInArea = onSearchInArea,
                modifier = Modifier.fillMaxSize().padding(top = 4.dp),
              )
            } else {
              // Embedded Live OpenStreetMap Card centered on Neuquén/Alto Valle + Business Directory
              EmbeddedExploreMapCard(
                localityName = uiControl.selectedLocality.name,
                nearbyCount = nearbyCount,
                isExpanded = showInlineMapCard,
                onToggleExpand = { showInlineMapCard = !showInlineMapCard },
                onExpandFullMap = { onViewModeChange(DirectoryViewMode.MAP) },
                mapContent = {
                  DirectoryMapCanvas(
                    selectedLocality = uiControl.selectedLocality,
                    resultsState = resultsState,
                    focusedBusiness = focusedMapBusiness,
                    onFocusedBusinessConsumed = onClearFocusedMapBusiness,
                    onBusinessClick = onBusinessClick,
                    onSearchInArea = onSearchInArea,
                    isCompactPreview = true,
                  )
                },
              )

              DirectoryListBody(
                uiControl = uiControl,
                resultsState = resultsState,
                favoriteIds = favoriteIds,
                onClearFilters = onClearFilters,
                onRetry = onRetry,
                onBusinessClick = onBusinessClick,
                onToggleFavorite = onToggleFavorite,
                modifier = Modifier.weight(1f),
              )
            }
          }
        }

        // Autocomplete Dropdown overlay anchored right under the Search Bar
        PlacesAutocompleteDropdown(
          autocompleteState = autocompleteState,
          onSelectCategory = onSelectAutocompleteCategory,
          onSelectPrediction = onSelectAutocompletePrediction,
          onDismiss = onDismissAutocomplete,
          modifier = Modifier.align(Alignment.TopCenter).zIndex(20f),
        )
      }
    }
  }
}

@Composable
private fun EmbeddedExploreMapCard(
  localityName: String,
  nearbyCount: Int,
  isExpanded: Boolean,
  onToggleExpand: () -> Unit,
  onExpandFullMap: () -> Unit,
  mapContent: @Composable () -> Unit,
) {
  Card(
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier =
      Modifier.fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 6.dp)
        .testTag("explore_embedded_map_card"),
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.weight(1f),
        ) {
          Icon(
            imageVector = Icons.Rounded.Map,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
          )
          Text(
            text =
              if (nearbyCount > 0) "$localityName • $nearbyCount lugares en mapa"
              else "${stringResource(R.string.map_section_title)} • $localityName",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          TextButton(
            onClick = onExpandFullMap,
            modifier = Modifier.testTag("expand_full_map_button"),
          ) {
            Icon(
              imageVector = Icons.Rounded.OpenInFull,
              contentDescription = null,
              modifier = Modifier.size(15.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(stringResource(R.string.map_expand), style = MaterialTheme.typography.labelMedium)
          }
          TextButton(
            onClick = onToggleExpand,
            modifier = Modifier.testTag("toggle_inline_map_button"),
          ) {
            Icon(
              imageVector =
                if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
              contentDescription =
                if (isExpanded) stringResource(R.string.map_collapse)
                else stringResource(R.string.map_show),
              modifier = Modifier.size(18.dp),
            )
          }
        }
      }

      AnimatedVisibility(visible = isExpanded) {
        Box(modifier = Modifier.fillMaxWidth().height(225.dp)) {
          mapContent()
        }
      }
    }
  }
}

@Composable
private fun DirectoryListBody(
  uiControl: DirectoryUiSnapshot,
  resultsState: DirectoryResultsState,
  favoriteIds: Set<String>,
  onClearFilters: () -> Unit,
  onRetry: () -> Unit,
  onBusinessClick: (DirectoryBusiness) -> Unit,
  onToggleFavorite: (DirectoryBusiness) -> Unit,
  modifier: Modifier = Modifier,
) {
  when (resultsState) {
    is DirectoryResultsState.Loading -> {
      Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        PatagoniaHeroBanner(locality = uiControl.selectedLocality, totalCount = 0)
        Box(
          modifier = Modifier.fillMaxWidth().weight(1f),
          contentAlignment = Alignment.Center,
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
              text = "Buscando comercios reales en ${uiControl.selectedLocality.name}…",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }

    is DirectoryResultsState.Error -> {
      LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier.fillMaxSize(),
      ) {
        item { PatagoniaHeroBanner(locality = uiControl.selectedLocality, totalCount = 0) }
        item {
          Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
          ) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(20.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(40.dp),
              )
              Spacer(modifier = Modifier.height(10.dp))
              Text(
                text = resultsState.message,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
              )
              Spacer(modifier = Modifier.height(14.dp))
              Button(onClick = onRetry, modifier = Modifier.testTag("retry_search_button")) {
                Text(stringResource(R.string.action_retry))
              }
            }
          }
        }
      }
    }

    is DirectoryResultsState.Success -> {
      LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize(),
      ) {
        item(key = "hero_banner") {
          PatagoniaHeroBanner(
            locality = uiControl.selectedLocality,
            totalCount = resultsState.businesses.size,
          )
        }

        resultsState.placesWarning?.let { warning ->
          item(key = "places_warning") {
            Surface(
              shape = RoundedCornerShape(14.dp),
              color = MaterialTheme.colorScheme.errorContainer,
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Icon(
                  imageVector = Icons.Rounded.ErrorOutline,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                  text = warning,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onErrorContainer,
                  modifier = Modifier.weight(1f),
                )
              }
            }
          }
        }

        if (resultsState.businesses.isEmpty()) {
          item(key = "empty_state") {
            Card(
              shape = RoundedCornerShape(20.dp),
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
                    imageVector = Icons.Rounded.SearchOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp),
                  )
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                  text = stringResource(R.string.empty_results_title),
                  style = MaterialTheme.typography.titleMedium,
                  textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                  text = stringResource(R.string.empty_results_body),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onClearFilters, modifier = Modifier.testTag("clear_filters_button")) {
                  Text(stringResource(R.string.clear_filters))
                }
              }
            }
          }
        } else {
          items(resultsState.businesses, key = { it.id }) { business ->
            BusinessCard(
              business = business,
              isFavorite = favoriteIds.contains(business.id),
              onClick = { onBusinessClick(business) },
              onToggleFavorite = { onToggleFavorite(business) },
            )
          }
        }

        item(key = "bottom_spacer") { Spacer(modifier = Modifier.height(16.dp)) }
      }
    }
  }
}
