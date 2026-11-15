package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.BusinessCategory
import com.example.domain.model.CategorySector
import com.example.domain.model.DirectoryCategories
import com.example.domain.model.LocalityRegion

@Composable
fun CategoriesScreen(
  selectedLocality: LocalityRegion,
  onCategoryClick: (BusinessCategory) -> Unit,
  modifier: Modifier = Modifier,
) {
  var filterText by remember { mutableStateOf("") }
  var selectedSector by remember { mutableStateOf<CategorySector?>(null) }

  val filtered =
    remember(filterText, selectedSector) {
      val query = filterText.trim().lowercase()
      DirectoryCategories.items.filter { item ->
        val matchesSector = selectedSector == null || item.sector == selectedSector
        val matchesQuery =
          query.isEmpty() ||
            item.name.lowercase().contains(query) ||
            item.shortName.lowercase().contains(query) ||
            item.description.lowercase().contains(query)
        matchesSector && matchesQuery
      }
    }

  LazyVerticalGrid(
    columns = GridCells.Adaptive(minSize = 152.dp),
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item(span = { GridItemSpan(maxLineSpan) }, key = "rubros_search_header") {
      Column {
        OutlinedTextField(
          value = filterText,
          onValueChange = { filterText = it },
          placeholder = { Text("Buscar rubro en ${selectedLocality.name}…") },
          leadingIcon = {
            Icon(
              imageVector = Icons.Rounded.Search,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
            )
          },
          singleLine = true,
          shape = RoundedCornerShape(14.dp),
          colors =
            OutlinedTextFieldDefaults.colors(
              focusedContainerColor = MaterialTheme.colorScheme.surface,
              unfocusedContainerColor = MaterialTheme.colorScheme.surface,
              focusedBorderColor = MaterialTheme.colorScheme.primary,
              unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            ),
          modifier = Modifier.fillMaxWidth().testTag("categories_filter_input"),
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          FilterChip(
            selected = selectedSector == null,
            onClick = { selectedSector = null },
            label = { Text("Todos") },
            colors =
              FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primary,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
              ),
          )
          CategorySector.entries.forEach { sector ->
            FilterChip(
              selected = selectedSector == sector,
              onClick = {
                selectedSector = if (selectedSector == sector) null else sector
              },
              label = { Text(sector.title) },
              colors =
                FilterChipDefaults.filterChipColors(
                  selectedContainerColor = MaterialTheme.colorScheme.primary,
                  selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
          }
        }
      }
    }

    items(filtered, key = { it.id }) { category ->
      CategoryGridTile(
        category = category,
        onClick = { onCategoryClick(category) },
      )
    }

    item(span = { GridItemSpan(maxLineSpan) }, key = "rubros_bottom_spacer") {
      Spacer(modifier = Modifier.height(8.dp))
    }
  }
}

@Composable
fun CategoryGridTile(
  category: BusinessCategory,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
    modifier =
      modifier
        .fillMaxWidth()
        .aspectRatio(1.02f)
        .testTag("category_directory_tile_${category.id}")
        .clickable(onClick = onClick),
  ) {
    Column(
      modifier = Modifier.fillMaxSize().padding(14.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      if (category.id == "vaca_muerta_industria") {
        Image(
          painter = painterResource(id = R.drawable.ic_vaca_muerta_seal),
          contentDescription = category.name,
          modifier = Modifier.size(66.dp),
        )
      } else {
        Icon(
          imageVector = category.icon,
          contentDescription = category.name,
          tint = category.accentColor,
          modifier = Modifier.size(56.dp),
        )
      }
      Spacer(modifier = Modifier.height(12.dp))
      Text(
        text = category.name.uppercase(),
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}
