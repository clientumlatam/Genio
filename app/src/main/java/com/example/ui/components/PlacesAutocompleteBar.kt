package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.places.PlaceAutocompleteSuggestion
import com.example.domain.model.BusinessCategory
import com.example.ui.AutocompleteUiState

@Composable
fun PlacesAutocompleteField(
  query: String,
  isLoadingPredictions: Boolean,
  onQueryChange: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val focusManager = LocalFocusManager.current
  OutlinedTextField(
    value = query,
    onValueChange = onQueryChange,
    placeholder = {
      Text(
        text = stringResource(R.string.search_placeholder),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    },
    leadingIcon = {
      Icon(
        imageVector = Icons.Rounded.Search,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
      )
    },
    trailingIcon = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        if (isLoadingPredictions) {
          CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary,
          )
          Spacer(modifier = Modifier.width(6.dp))
        }
        if (query.isNotEmpty()) {
          IconButton(
            onClick = { onQueryChange("") },
            modifier = Modifier.testTag("clear_search_button"),
          ) {
            Icon(
              imageVector = Icons.Rounded.Close,
              contentDescription = stringResource(R.string.cd_clear_search),
            )
          }
        }
      }
    },
    singleLine = true,
    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
    shape = RoundedCornerShape(16.dp),
    colors =
      OutlinedTextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surface,
        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
      ),
    modifier = modifier.testTag("explore_search_input"),
  )
}

@Composable
fun PlacesAutocompleteDropdown(
  autocompleteState: AutocompleteUiState,
  onSelectCategory: (BusinessCategory) -> Unit,
  onSelectPrediction: (PlaceAutocompleteSuggestion) -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val focusManager = LocalFocusManager.current
  val hasContent =
    autocompleteState.matchingCategories.isNotEmpty() ||
      autocompleteState.placePredictions.isNotEmpty() ||
      autocompleteState.isLoading

  AnimatedVisibility(
    visible = autocompleteState.isExpanded && hasContent,
    enter = fadeIn() + expandVertically(),
    exit = fadeOut() + shrinkVertically(),
    modifier = modifier,
  ) {
    Card(
      shape = RoundedCornerShape(18.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).testTag("autocomplete_dropdown"),
    ) {
      Column(modifier = Modifier.fillMaxWidth()) {
        if (autocompleteState.matchingCategories.isNotEmpty()) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text = stringResource(R.string.autocomplete_categories_title),
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.primary,
              fontWeight = FontWeight.Bold,
            )
            TextButton(
              onClick = {
                focusManager.clearFocus()
                onDismiss()
              },
              modifier = Modifier.testTag("dismiss_autocomplete_button"),
            ) {
              Text("Cerrar", style = MaterialTheme.typography.labelMedium)
            }
          }

          autocompleteState.matchingCategories.forEach { category ->
            Row(
              modifier =
                Modifier.fillMaxWidth()
                  .heightIn(min = 48.dp)
                  .clickable {
                    focusManager.clearFocus()
                    onSelectCategory(category)
                  }
                  .padding(horizontal = 14.dp, vertical = 8.dp)
                  .testTag("autocomplete_category_${category.id}"),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Box(
                modifier =
                  Modifier.size(36.dp)
                    .clip(CircleShape)
                    .background(category.accentColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
              ) {
                if (category.id == "vaca_muerta_industria") {
                  Image(
                    painter = painterResource(id = R.drawable.ic_vaca_muerta_seal),
                    contentDescription = null,
                    modifier = Modifier.size(26.dp),
                  )
                } else {
                  Icon(
                    imageVector = category.icon,
                    contentDescription = null,
                    tint = category.accentColor,
                    modifier = Modifier.size(20.dp),
                  )
                }
              }
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = category.name,
                  style = MaterialTheme.typography.bodyMedium,
                  fontWeight = FontWeight.SemiBold,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  text = category.sector.title,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
              Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer,
              ) {
                Text(
                  text = stringResource(R.string.autocomplete_filter_category),
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onPrimaryContainer,
                  fontWeight = FontWeight.SemiBold,
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
              }
            }
          }
        }

        if (autocompleteState.matchingCategories.isNotEmpty() &&
          (autocompleteState.placePredictions.isNotEmpty() || autocompleteState.isLoading)
        ) {
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        }

        if (autocompleteState.placePredictions.isNotEmpty() || autocompleteState.isLoading) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text = stringResource(R.string.autocomplete_places_title),
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.secondary,
              fontWeight = FontWeight.Bold,
            )
            if (autocompleteState.matchingCategories.isEmpty()) {
              TextButton(
                onClick = {
                  focusManager.clearFocus()
                  onDismiss()
                },
                modifier = Modifier.testTag("dismiss_autocomplete_button"),
              ) {
                Text("Cerrar", style = MaterialTheme.typography.labelMedium)
              }
            }
          }

          if (autocompleteState.isLoading && autocompleteState.placePredictions.isEmpty()) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
              Text(
                text = "Buscando lugares en Neuquén y Alto Valle…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }

          autocompleteState.placePredictions.forEach { prediction ->
            Row(
              modifier =
                Modifier.fillMaxWidth()
                  .heightIn(min = 52.dp)
                  .clickable {
                    focusManager.clearFocus()
                    onSelectPrediction(prediction)
                  }
                  .padding(horizontal = 14.dp, vertical = 8.dp)
                  .testTag("autocomplete_place_${prediction.placeId}"),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Box(
                modifier =
                  Modifier.size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = Icons.Rounded.LocationOn,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onSecondaryContainer,
                  modifier = Modifier.size(20.dp),
                )
              }
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = prediction.primaryText,
                  style = MaterialTheme.typography.bodyMedium,
                  fontWeight = FontWeight.SemiBold,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                if (prediction.secondaryText.isNotBlank()) {
                  Text(
                    text = prediction.secondaryText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
              }
            }
          }
          Spacer(modifier = Modifier.height(6.dp))
        }
      }
    }
  }
}
