package com.example.ui.screens

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.auth.AuthUiState
import com.example.domain.model.DirectoryBusiness
import com.example.ui.components.BusinessCard

@Composable
fun FavoritesScreen(
  authState: AuthUiState,
  favorites: List<DirectoryBusiness>,
  onGoogleSignIn: (Context) -> Unit,
  onBusinessClick: (DirectoryBusiness) -> Unit,
  onToggleFavorite: (DirectoryBusiness) -> Unit,
  onExploreClick: () -> Unit,
  modifier: Modifier = Modifier,
  isFirestoreConfigured: Boolean = true,
  firestoreDisabledMessage: String? = null,
) {
  val context = LocalContext.current
  val signedInUser = authState.currentUser

  LazyColumn(
    modifier = modifier.fillMaxSize().testTag("favorites_screen"),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item(key = "favorites_header") {
      Column {
        Text(
          text = stringResource(R.string.favorites_title),
          style = MaterialTheme.typography.headlineMedium,
          color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = stringResource(R.string.favorites_subtitle),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    if (!isFirestoreConfigured || !authState.isFirebaseConfigured) {
      item(key = "favorites_firebase_status") {
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = MaterialTheme.colorScheme.secondaryContainer,
          modifier = Modifier.testTag("favorites_firebase_disabled_banner"),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
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
                authState.firebaseConfigMessage
                  ?: firestoreDisabledMessage
                  ?: stringResource(R.string.auth_firebase_pending_notice),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
          }
        }
      }
    }

    if (signedInUser == null) {
      item(key = "favorites_auth_gate") {
        Card(
          shape = RoundedCornerShape(22.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
          modifier = Modifier.fillMaxWidth().testTag("favorites_sign_in_card"),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
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
                imageVector = Icons.Rounded.BookmarkBorder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(30.dp),
              )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
              text = stringResource(R.string.favorites_sign_in_title),
              style = MaterialTheme.typography.titleLarge,
              textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = stringResource(R.string.favorites_sign_in_body),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center,
            )

            authState.errorMessage?.let { error ->
              Spacer(modifier = Modifier.height(12.dp))
              Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer,
              ) {
                Text(
                  text = error,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onErrorContainer,
                  modifier = Modifier.fillMaxWidth().padding(12.dp),
                )
              }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Button(
              onClick = { onGoogleSignIn(context) },
              enabled = !authState.isLoading,
              modifier = Modifier.fillMaxWidth().height(50.dp).testTag("favorites_google_sign_in_button"),
            ) {
              if (authState.isLoading) {
                CircularProgressIndicator(
                  modifier = Modifier.size(22.dp),
                  color = MaterialTheme.colorScheme.onPrimary,
                  strokeWidth = 2.dp,
                )
              } else {
                Icon(
                  imageVector = Icons.Rounded.VerifiedUser,
                  contentDescription = null,
                  modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.auth_sign_in_google))
              }
            }
          }
        }
      }
    } else if (favorites.isEmpty()) {
      item(key = "favorites_empty") {
        Card(
          shape = RoundedCornerShape(22.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
          modifier = Modifier.fillMaxWidth().testTag("favorites_empty_card"),
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
                imageVector = Icons.Rounded.BookmarkBorder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
              )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
              text = stringResource(R.string.favorites_empty_title),
              style = MaterialTheme.typography.titleMedium,
              textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = stringResource(R.string.favorites_empty_body),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(18.dp))
            Button(
              onClick = onExploreClick,
              modifier = Modifier.height(48.dp).testTag("favorites_explore_button"),
            ) {
              Icon(Icons.Rounded.Explore, contentDescription = null)
              Spacer(modifier = Modifier.width(6.dp))
              Text(stringResource(R.string.favorites_explore_cta))
            }
          }
        }
      }
    } else {
      items(favorites, key = { it.id }) { business ->
        BusinessCard(
          business = business,
          isFavorite = true,
          onClick = { onBusinessClick(business) },
          onToggleFavorite = { onToggleFavorite(business) },
        )
      }
    }

    item(key = "favorites_bottom_spacer") { Spacer(modifier = Modifier.height(24.dp)) }
  }
}
