package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.remote.FirestoreDirectoryRepository
import com.example.domain.model.BusinessStatus
import com.example.domain.model.DirectoryBusiness
import com.example.domain.model.DirectoryCategories
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Barra de búsqueda Material 3 (`DockedSearchBar`) conectada directamente al repositorio
 * [FirestoreDirectoryRepository] para filtrar en tiempo real comercios `APPROVED` por nombre o rubro.
 */
@Composable
fun FirestoreApprovedBusinessesSearchBar(
  firestoreRepository: FirestoreDirectoryRepository,
  onBusinessClick: (DirectoryBusiness) -> Unit,
  modifier: Modifier = Modifier,
  onQueryChanged: ((String) -> Unit)? = null,
  placeholderText: String = stringResource(R.string.search_approved_placeholder),
) {
  val queryFlow = remember(firestoreRepository) { MutableStateFlow("") }
  val query by queryFlow.collectAsStateWithLifecycle()
  val approvedBusinesses by
    remember(firestoreRepository, queryFlow) {
      firestoreRepository.observeApprovedBusinesses(queryFlow)
    }.collectAsStateWithLifecycle(initialValue = emptyList())
  val configSnapshot by firestoreRepository.configurationState.collectAsStateWithLifecycle()

  ApprovedBusinessesSearchBar(
    query = query,
    onQueryChange = { newQuery ->
      queryFlow.value = newQuery
      onQueryChanged?.invoke(newQuery)
    },
    approvedBusinesses = approvedBusinesses,
    onBusinessClick = onBusinessClick,
    isFirestoreConfigured =
      configSnapshot.isFirestoreInitialized && configSnapshot.configStatus.isConfigured,
    firestoreDisabledMessage = firestoreRepository.disabledReasonMessage(),
    placeholderText = placeholderText,
    modifier = modifier,
  )
}

/**
 * Componente Material 3 `DockedSearchBar` que filtra comercios aprobados en tiempo real
 * por nombre o categoría/rubro.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApprovedBusinessesSearchBar(
  query: String,
  onQueryChange: (String) -> Unit,
  approvedBusinesses: List<DirectoryBusiness>,
  onBusinessClick: (DirectoryBusiness) -> Unit,
  modifier: Modifier = Modifier,
  isFirestoreConfigured: Boolean = true,
  firestoreDisabledMessage: String? = null,
  placeholderText: String = stringResource(R.string.search_approved_placeholder),
) {
  val focusManager = LocalFocusManager.current
  var expanded by rememberSaveable { mutableStateOf(false) }

  // Defensa adicional en UI para garantizar que solo negocios APPROVED aparezcan en resultados
  val filteredApproved =
    remember(approvedBusinesses, query) {
      FirestoreDirectoryRepository.filterApprovedBusinessesByNameOrCategory(
        businesses = approvedBusinesses,
        query = query,
      )
    }

  DockedSearchBar(
    inputField = {
      SearchBarDefaults.InputField(
        query = query,
        onQueryChange = { newQuery ->
          onQueryChange(newQuery)
          if (!expanded && newQuery.isNotEmpty()) {
            expanded = true
          }
        },
        onSearch = {
          focusManager.clearFocus()
        },
        expanded = expanded,
        onExpandedChange = { isOpen -> expanded = isOpen },
        placeholder = {
          Text(
            text = placeholderText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
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
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
          ) {
            if (query.isNotBlank() || expanded) {
              Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer,
              ) {
                Text(
                  text = "${filteredApproved.size}",
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onPrimaryContainer,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
              }
            }
            if (query.isNotEmpty() || expanded) {
              IconButton(
                onClick = {
                  if (query.isNotEmpty()) {
                    onQueryChange("")
                  } else {
                    focusManager.clearFocus()
                    expanded = false
                  }
                },
                modifier = Modifier.size(48.dp).testTag("approved_search_clear"),
              ) {
                Icon(
                  imageVector = Icons.Rounded.Close,
                  contentDescription = stringResource(R.string.cd_clear_search),
                )
              }
            }
          }
        },
        modifier = Modifier.fillMaxWidth().testTag("approved_search_input"),
      )
    },
    expanded = expanded,
    onExpandedChange = { isOpen -> expanded = isOpen },
    shape = RoundedCornerShape(28.dp),
    colors =
      SearchBarDefaults.colors(
        containerColor = MaterialTheme.colorScheme.surface,
        dividerColor = MaterialTheme.colorScheme.outlineVariant,
      ),
    tonalElevation = 2.dp,
    shadowElevation = 4.dp,
    modifier = modifier.fillMaxWidth().testTag("approved_businesses_search_bar"),
  ) {
    if (!isFirestoreConfigured) {
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
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
          text =
            firestoreDisabledMessage
              ?: stringResource(R.string.auth_firebase_pending_notice),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSecondaryContainer,
          modifier = Modifier.weight(1f),
        )
      }
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }

    if (filteredApproved.isEmpty()) {
      Column(
        modifier =
          Modifier.fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 22.dp)
            .testTag("approved_search_empty"),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Icon(
          imageVector = Icons.Rounded.SearchOff,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(34.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = stringResource(R.string.search_approved_empty_title),
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onSurface,
          textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text =
            if (query.isNotBlank()) {
              stringResource(R.string.search_approved_empty_query, query.trim())
            } else {
              stringResource(R.string.search_approved_empty_catalog)
            },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )
        if (query.isNotBlank()) {
          Spacer(modifier = Modifier.height(8.dp))
          TextButton(
            onClick = { onQueryChange("") },
            modifier = Modifier.testTag("approved_search_reset_button"),
          ) {
            Text(stringResource(R.string.clear_filters))
          }
        }
      }
    } else {
      LazyColumn(
        modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
        contentPadding = PaddingValues(vertical = 6.dp),
      ) {
        items(filteredApproved, key = { it.id }) { business ->
          ApprovedBusinessSearchResultRow(
            business = business,
            onClick = {
              focusManager.clearFocus()
              expanded = false
              onBusinessClick(business)
            },
          )
        }
      }
    }
  }
}

@Composable
private fun ApprovedBusinessSearchResultRow(
  business: DirectoryBusiness,
  onClick: () -> Unit,
) {
  val category = DirectoryCategories.findById(business.categoryId)
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .heightIn(min = 56.dp)
        .clickable(onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 10.dp)
        .testTag("approved_search_result_${business.id}"),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Box(
      modifier =
        Modifier.size(40.dp)
          .clip(CircleShape)
          .background(category.accentColor.copy(alpha = 0.14f)),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = category.icon,
        contentDescription = category.name,
        tint = category.accentColor,
        modifier = Modifier.size(20.dp),
      )
    }

    Column(modifier = Modifier.weight(1f)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Text(
          text = business.name,
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f, fill = false),
        )
        if (business.status == BusinessStatus.APPROVED) {
          Icon(
            imageVector = Icons.Rounded.Verified,
            contentDescription = stringResource(R.string.status_approved),
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(15.dp),
          )
        }
      }
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = "${business.categoryName.ifBlank { category.shortName }} • ${business.localityName}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }

    Spacer(modifier = Modifier.width(4.dp))

    when (business.isOpenNow) {
      true ->
        StatusPill(
          text = stringResource(R.string.badge_open),
          containerColor = MaterialTheme.colorScheme.tertiaryContainer,
          contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        )
      false ->
        StatusPill(
          text = stringResource(R.string.badge_closed),
          containerColor = MaterialTheme.colorScheme.errorContainer,
          contentColor = MaterialTheme.colorScheme.onErrorContainer,
        )
      null -> Unit
    }
  }
}
