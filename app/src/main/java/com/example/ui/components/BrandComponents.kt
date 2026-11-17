package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Directions
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.HomeRepairService
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.OilBarrel
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.model.BusinessSource
import com.example.domain.model.BusinessStatus
import com.example.domain.model.DirectoryBusiness
import com.example.domain.model.DirectoryCategories
import com.example.domain.model.LocalityRegion

@Composable
fun GuiaGenioBrandHeader(
  selectedLocality: LocalityRegion,
  isResolvingLocation: Boolean,
  onLocalityClick: () -> Unit,
  onUseMyLocationClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier.fillMaxWidth(),
    color = Color.Transparent,
    shadowElevation = 4.dp,
  ) {
    Box(
      modifier =
        Modifier.fillMaxWidth()
          .background(
            Brush.horizontalGradient(
              listOf(Color(0xFF0E3B8C), Color(0xFF1546A6), Color(0xFF1A52BF))
            )
          )
          .statusBarsPadding()
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
          Surface(
            shape = CircleShape,
            color = Color.White,
            modifier = Modifier.size(36.dp),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Image(
                painter = painterResource(id = R.drawable.ic_guia_genio_logo),
                contentDescription = stringResource(R.string.cd_app_logo),
                modifier = Modifier.size(30.dp),
              )
            }
          }
          Spacer(modifier = Modifier.width(10.dp))
          Column {
            Text(
              text = stringResource(R.string.app_name),
              style = MaterialTheme.typography.titleLarge,
              color = Color.White,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
            )
            Text(
              text = "Alto Valle & Vaca Muerta",
              style = MaterialTheme.typography.labelSmall,
              color = Color.White.copy(alpha = 0.8f),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Surface(
            onClick = onLocalityClick,
            shape = RoundedCornerShape(10.dp),
            color = Color.White.copy(alpha = 0.16f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
            modifier = Modifier.height(40.dp).testTag("locality_picker_button"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = selectedLocality.name,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Spacer(modifier = Modifier.width(4.dp))
              Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = stringResource(R.string.select_locality),
                tint = Color.White,
                modifier = Modifier.size(18.dp),
              )
            }
          }

          Surface(
            onClick = onUseMyLocationClick,
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.16f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
            modifier = Modifier.size(40.dp).testTag("use_my_location_button"),
          ) {
            Box(contentAlignment = Alignment.Center) {
              if (isResolvingLocation) {
                CircularProgressIndicator(
                  modifier = Modifier.size(18.dp),
                  strokeWidth = 2.dp,
                  color = Color.White,
                )
              } else {
                Icon(
                  imageVector = Icons.Rounded.MyLocation,
                  contentDescription = stringResource(R.string.use_my_location),
                  tint = Color.White,
                  modifier = Modifier.size(19.dp),
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun GuiaGenioSplashOverlay(
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val watermarkIcons =
    listOf(
      listOf(Icons.Rounded.OilBarrel, Icons.Rounded.HomeRepairService, Icons.Rounded.ShoppingCart),
      listOf(Icons.Rounded.Restaurant, Icons.Rounded.LocalHospital, Icons.Rounded.DirectionsCar),
      listOf(Icons.Rounded.Build, Icons.Rounded.LocalShipping, Icons.Rounded.Verified),
    )

  Box(
    modifier =
      modifier
        .fillMaxSize()
        .background(
          Brush.radialGradient(
            colors = listOf(Color(0xFFFFFFFF), Color(0xFFF3F6FB), Color(0xFFE2E9F5))
          )
        )
        .clickable(onClick = onDismiss)
        .testTag("guia_genio_splash"),
  ) {
    // Subtle watermark category icon grid matching splash branding
    Column(
      modifier = Modifier.fillMaxSize().padding(28.dp).alpha(0.065f),
      verticalArrangement = Arrangement.SpaceEvenly,
    ) {
      watermarkIcons.forEach { rowIcons ->
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
          rowIcons.forEach { icon ->
            Icon(
              imageVector = icon,
              contentDescription = null,
              tint = Color(0xFF0E3B8C),
              modifier = Modifier.size(56.dp),
            )
          }
        }
      }
    }

    Column(
      modifier = Modifier.align(Alignment.Center).padding(horizontal = 32.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Image(
        painter = painterResource(id = R.drawable.ic_guia_genio_logo),
        contentDescription = stringResource(R.string.cd_app_logo),
        modifier = Modifier.size(148.dp),
      )
      Spacer(modifier = Modifier.height(14.dp))
      Text(
        text = "GUÍA GENIO",
        style = MaterialTheme.typography.displaySmall.copy(
          fontSize = 34.sp,
          letterSpacing = 1.sp,
          fontWeight = FontWeight.Bold,
        ),
        color = Color(0xFF0A1E44),
        textAlign = TextAlign.Center,
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = stringResource(R.string.tagline_slogan),
        style = MaterialTheme.typography.labelLarge.copy(
          letterSpacing = 1.4.sp,
          fontWeight = FontWeight.SemiBold,
        ),
        color = Color(0xFF1E3A6E),
        textAlign = TextAlign.Center,
      )
    }

    LinearProgressIndicator(
      color = Color(0xFF0E3B8C),
      trackColor = Color(0xFFCBD5E1),
      modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp),
    )
  }
}

@Composable
fun BusinessCard(
  business: DirectoryBusiness,
  isFavorite: Boolean,
  onClick: () -> Unit,
  onToggleFavorite: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val category = DirectoryCategories.findById(business.categoryId)

  Card(
    modifier =
      modifier
        .fillMaxWidth()
        .testTag("business_card_${business.id}")
        .clickable(onClick = onClick),
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
      ) {
        Box(
          modifier =
            Modifier.size(72.dp)
              .clip(RoundedCornerShape(14.dp))
              .background(category.accentColor.copy(alpha = 0.12f)),
          contentAlignment = Alignment.Center,
        ) {
          if (category.id == "vaca_muerta_industria") {
            Image(
              painter = painterResource(id = R.drawable.ic_vaca_muerta_seal),
              contentDescription = category.name,
              modifier = Modifier.size(44.dp),
            )
          } else {
            Icon(
              imageVector = category.icon,
              contentDescription = category.name,
              tint = category.accentColor,
              modifier = Modifier.size(32.dp),
            )
          }
        }

        Column(modifier = Modifier.weight(1f)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text = business.categoryName.uppercase(),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f),
            )
            IconButton(
              onClick = onToggleFavorite,
              modifier = Modifier.size(48.dp).testTag("favorite_button_${business.id}"),
            ) {
              Icon(
                imageVector =
                  if (isFavorite) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                contentDescription = stringResource(R.string.cd_favorite_toggle),
                tint =
                  if (isFavorite) MaterialTheme.colorScheme.secondary
                  else MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }

          Text(
            text = business.name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )

          Spacer(modifier = Modifier.height(2.dp))

          Text(
            text = "${business.address.ifBlank { "no informado" }} • ${business.localityName}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )

          Spacer(modifier = Modifier.height(8.dp))

          Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
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
              StatusPill(
                text = stringResource(R.string.badge_delivery),
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
              )
            }

            if (business.source == BusinessSource.USER_PUBLISHED) {
              Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer,
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.Verified,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(12.dp),
                  )
                  Text(
                    text = "Oficial",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                  )
                }
              }
            }

            BusinessIntents.formatDistance(business.distanceMeters)?.let { dist ->
              Text(
                text = dist,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }

          if (!business.hasWhatsApp && !business.hasPhone) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = "Tel / WhatsApp: no informado",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        if (business.hasWhatsApp) {
          FilledTonalButton(
            onClick = {
              BusinessIntents.openWhatsApp(context, business.whatsappNumber!!, business.name)
            },
            modifier = Modifier.weight(1f).height(48.dp).testTag("whatsapp_btn_${business.id}"),
            contentPadding = PaddingValues(horizontal = 10.dp),
            colors =
              ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
              ),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Rounded.Chat,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = stringResource(R.string.action_whatsapp),
              style = MaterialTheme.typography.labelLarge,
              maxLines = 1,
            )
          }
        }

        if (business.hasPhone) {
          OutlinedButton(
            onClick = { BusinessIntents.dialPhone(context, business.phoneNumber!!) },
            modifier = Modifier.weight(1f).height(48.dp).testTag("call_btn_${business.id}"),
            contentPadding = PaddingValues(horizontal = 10.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
          ) {
            Icon(
              imageVector = Icons.Rounded.Call,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = stringResource(R.string.action_call),
              style = MaterialTheme.typography.labelLarge,
              maxLines = 1,
            )
          }
        }

        FilledTonalButton(
          onClick = { BusinessIntents.openDirections(context, business) },
          modifier = Modifier.weight(1f).height(48.dp).testTag("directions_btn_${business.id}"),
          contentPadding = PaddingValues(horizontal = 10.dp),
          colors =
            ButtonDefaults.filledTonalButtonColors(
              containerColor = MaterialTheme.colorScheme.primaryContainer,
              contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        ) {
          Icon(
            imageVector = Icons.Rounded.Directions,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "Ir",
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
          )
        }
      }
    }
  }
}

@Composable
fun StatusPill(
  text: String,
  containerColor: Color,
  contentColor: Color,
  modifier: Modifier = Modifier,
) {
  Surface(modifier = modifier, shape = RoundedCornerShape(50), color = containerColor) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelSmall,
      color = contentColor,
      fontWeight = FontWeight.SemiBold,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
    )
  }
}

@Composable
fun BusinessModerationStatusPill(
  status: BusinessStatus,
  modifier: Modifier = Modifier,
) {
  val (label, containerColor, contentColor) =
    when (status) {
      BusinessStatus.APPROVED ->
        Triple(
          stringResource(R.string.status_approved),
          MaterialTheme.colorScheme.tertiaryContainer,
          MaterialTheme.colorScheme.onTertiaryContainer,
        )
      BusinessStatus.PENDING ->
        Triple(
          stringResource(R.string.status_pending),
          MaterialTheme.colorScheme.secondaryContainer,
          MaterialTheme.colorScheme.onSecondaryContainer,
        )
      BusinessStatus.REJECTED ->
        Triple(
          stringResource(R.string.status_rejected),
          MaterialTheme.colorScheme.errorContainer,
          MaterialTheme.colorScheme.onErrorContainer,
        )
      BusinessStatus.SUSPENDED ->
        Triple(
          stringResource(R.string.status_suspended),
          MaterialTheme.colorScheme.surfaceVariant,
          MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

  StatusPill(
    text = label,
    containerColor = containerColor,
    contentColor = contentColor,
    modifier = modifier.testTag("moderation_status_pill_${status.name.lowercase()}"),
  )
}

@Composable
fun PatagoniaHeroBanner(
  locality: LocalityRegion,
  totalCount: Int,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier =
      modifier
        .fillMaxWidth()
        .height(140.dp)
        .clip(RoundedCornerShape(22.dp))
  ) {
    Image(
      painter = painterResource(id = R.drawable.img_hero_patagonia),
      contentDescription = stringResource(R.string.cd_hero_banner),
      contentScale = ContentScale.Crop,
      modifier = Modifier.fillMaxSize(),
    )
    Box(
      modifier =
        Modifier.fillMaxSize()
          .background(
            Brush.horizontalGradient(
              colors =
                listOf(
                  Color(0xE60B1E47),
                  Color(0x991546A6),
                  Color(0x331546A6),
                )
            )
          )
    )
    Column(
      modifier = Modifier.fillMaxSize().padding(18.dp),
      verticalArrangement = Arrangement.SpaceBetween,
    ) {
      Surface(
        shape = RoundedCornerShape(50),
        color = Color(0xFFF59E0B),
      ) {
        Text(
          text = "${locality.zoneLabel.uppercase()} • ${locality.name.uppercase()}",
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.Bold,
          color = Color(0xFF1E1B18),
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
      }
      Column {
        Text(
          text = "Tu socio comercial en el Alto Valle",
          style = MaterialTheme.typography.titleLarge,
          color = Color.White,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text =
            if (totalCount > 0) "$totalCount lugares listos para contactar ahora"
            else "Buscá rubros, servicios petroleros y locales abiertos",
          style = MaterialTheme.typography.bodySmall,
          color = Color.White.copy(alpha = 0.88f),
        )
      }
    }
  }
}
