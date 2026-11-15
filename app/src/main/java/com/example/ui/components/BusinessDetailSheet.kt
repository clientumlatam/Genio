package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.DeliveryDining
import androidx.compose.material.icons.rounded.Directions
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.BusinessSource
import com.example.domain.model.DirectoryBusiness
import com.example.domain.model.DirectoryCategories

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BusinessDetailSheet(
  business: DirectoryBusiness,
  isFavorite: Boolean,
  onToggleFavorite: () -> Unit,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val category = DirectoryCategories.findById(business.categoryId)

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
    ) {
      // Category Identity Header (No photos — always rubro emblem/icon)
      Box(
        modifier =
          Modifier.fillMaxWidth()
            .height(152.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
              Brush.linearGradient(
                listOf(category.accentColor, MaterialTheme.colorScheme.primary)
              )
            )
      ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          if (category.id == "vaca_muerta_industria") {
            Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.94f)) {
              Image(
                painter = painterResource(id = R.drawable.ic_vaca_muerta_seal),
                contentDescription = category.name,
                modifier = Modifier.size(84.dp).padding(10.dp),
              )
            }
          } else {
            Icon(
              imageVector = category.icon,
              contentDescription = category.name,
              tint = Color.White.copy(alpha = 0.92f),
              modifier = Modifier.size(64.dp),
            )
          }
        }

        // Top right favorite & share
        Row(
          modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Surface(shape = RoundedCornerShape(50), color = Color.Black.copy(alpha = 0.45f)) {
            IconButton(
              onClick = onToggleFavorite,
              modifier = Modifier.size(48.dp).testTag("detail_favorite_button"),
            ) {
              Icon(
                imageVector =
                  if (isFavorite) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                contentDescription = stringResource(R.string.cd_favorite_toggle),
                tint = Color.White,
              )
            }
          }
          Surface(shape = RoundedCornerShape(50), color = Color.Black.copy(alpha = 0.45f)) {
            IconButton(
              onClick = { BusinessIntents.shareBusiness(context, business) },
              modifier = Modifier.size(48.dp).testTag("detail_share_button"),
            ) {
              Icon(
                imageVector = Icons.Rounded.Share,
                contentDescription = stringResource(R.string.action_share),
                tint = Color.White,
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      Text(
        text = business.categoryName.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = business.name,
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )

      Spacer(modifier = Modifier.height(10.dp))

      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
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
          null ->
            StatusPill(
              text = "Horario: no informado",
              containerColor = MaterialTheme.colorScheme.surfaceVariant,
              contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (business.hasDelivery) {
          Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.secondaryContainer,
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.DeliveryDining,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(14.dp),
              )
              Text(
                text = stringResource(R.string.badge_delivery),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
              )
            }
          }
        }

        if (business.source == BusinessSource.USER_PUBLISHED) {
          Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.primaryContainer,
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.Verified,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp),
              )
              Text(
                text = stringResource(R.string.badge_published),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      // Primary Direct Action Buttons
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        if (business.hasWhatsApp) {
          Button(
            onClick = {
              BusinessIntents.openWhatsApp(context, business.whatsappNumber!!, business.name)
            },
            modifier = Modifier.weight(1f).height(50.dp).testTag("detail_whatsapp_button"),
            colors =
              ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary,
              ),
          ) {
            Icon(Icons.AutoMirrored.Rounded.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.action_whatsapp))
          }
        }

        Button(
          onClick = { BusinessIntents.openDirections(context, business) },
          modifier = Modifier.weight(1f).height(50.dp).testTag("detail_directions_button"),
        ) {
          Icon(Icons.Rounded.Directions, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(stringResource(R.string.action_directions))
        }
      }

      if (business.hasPhone || !business.websiteUri.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(10.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          if (business.hasPhone) {
            OutlinedButton(
              onClick = { BusinessIntents.dialPhone(context, business.phoneNumber!!) },
              modifier = Modifier.weight(1f).height(48.dp).testTag("detail_call_button"),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
              Icon(Icons.Rounded.Call, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text(business.phoneNumber ?: stringResource(R.string.action_call), maxLines = 1)
            }
          }
          if (!business.websiteUri.isNullOrBlank()) {
            FilledTonalButton(
              onClick = { BusinessIntents.openWebsite(context, business.websiteUri) },
              modifier = Modifier.weight(1f).height(48.dp).testTag("detail_website_button"),
            ) {
              Icon(Icons.Rounded.Language, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text(stringResource(R.string.action_website), maxLines = 1)
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(18.dp))
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
      Spacer(modifier = Modifier.height(16.dp))

      // Address row
      Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Icon(
          imageVector = Icons.Rounded.LocationOn,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(20.dp),
        )
        Column {
          Text(
            text = "${business.address.ifBlank { "no informado" }}, ${business.localityName}",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
          )
          BusinessIntents.formatDistance(business.distanceMeters)?.let { dist ->
            Text(
              text = "A $dist de tu punto de referencia",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      if (!business.description.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
          text = business.description,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      Spacer(modifier = Modifier.height(16.dp))
      Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.Schedule,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(18.dp),
            )
            Text(
              text = "Información del comercio",
              style = MaterialTheme.typography.titleSmall,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
          DetailInfoLine(label = "Teléfono", value = business.phoneNumber ?: "no informado")
          DetailInfoLine(label = "WhatsApp", value = business.whatsappNumber ?: "no informado")
          DetailInfoLine(label = "Sitio web", value = business.websiteUri ?: "no informado")
          DetailInfoLine(
            label = "Horarios",
            value =
              business.openingHoursSummary.joinToString(" · ").ifBlank { "no informado" },
          )
          DetailInfoLine(
            label = "Delivery",
            value = if (business.hasDelivery) "Disponible" else "no informado",
          )
        }
      }
    }
  }
}

@Composable
private fun DetailInfoLine(label: String, value: String) {
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(
      text = "$label:",
      style = MaterialTheme.typography.bodySmall,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.width(12.dp))
    Text(
      text = value,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurface,
    )
  }
}
