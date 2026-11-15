package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assistant
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.rounded.Assistant
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.auth.AuthService
import com.example.data.local.GuiaGenioDatabase
import com.example.data.local.PublishedBusinessRepository
import com.example.data.places.LocationTracker
import com.example.data.places.PlacesRepository
import com.example.domain.model.DirectoryViewMode
import com.example.ui.DirectoryViewModel
import com.example.ui.AiAssistantViewModel
import com.example.ui.MyBusinessesViewModel
import com.example.ui.components.BusinessDetailSheet
import com.example.ui.components.GuiaGenioBrandHeader
import com.example.ui.components.GuiaGenioSplashOverlay
import com.example.ui.components.LocalityPickerSheet
import com.example.ui.screens.AiAssistantScreen
import com.example.ui.screens.CategoriesScreen
import com.example.ui.screens.ExploreScreen
import com.example.ui.screens.MapScreen
import com.example.ui.screens.MyBusinessesScreen
import com.example.ui.theme.GuiaGenioTheme
import kotlinx.coroutines.launch

enum class GuiaGenioTab(
  val route: String,
  val labelRes: Int,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
) {
  EXPLORE("explore", R.string.nav_explore, Icons.Rounded.Explore, Icons.Outlined.Explore),
  MAP("map", R.string.nav_map, Icons.Rounded.Map, Icons.Outlined.Map),
  CATEGORIES("categories", R.string.nav_categories, Icons.Rounded.Category, Icons.Outlined.Category),
  MY_BUSINESSES(
    "my_businesses",
    R.string.nav_my_businesses,
    Icons.Rounded.Storefront,
    Icons.Outlined.Storefront,
  ),
  AI_ASSISTANT(
    "ai_assistant",
    R.string.nav_ai_assistant,
    Icons.Rounded.Assistant,
    Icons.Outlined.Assistant,
  ),
}

class MainActivity : ComponentActivity() {
  private lateinit var authService: AuthService

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    val appContext = applicationContext
    val database = GuiaGenioDatabase.getInstance(appContext)
    val publishedRepository = PublishedBusinessRepository(database.businessDao())
    val placesRepository = PlacesRepository(appContext)
    val locationTracker = LocationTracker(appContext)
    authService = AuthService(appContext)

    lifecycleScope.launch {
      authService.attemptAutoSignIn(this@MainActivity)
    }

    setContent {
      GuiaGenioTheme {
        val directoryViewModel: DirectoryViewModel =
          viewModel(
            factory =
              DirectoryViewModel.Factory(placesRepository, publishedRepository, locationTracker)
          )
        val myBusinessesViewModel: MyBusinessesViewModel =
          viewModel(
            factory =
              MyBusinessesViewModel.Factory(publishedRepository, placesRepository, authService)
          )
        val aiAssistantViewModel: AiAssistantViewModel = viewModel()

        GuiaGenioApp(
          directoryViewModel = directoryViewModel,
          myBusinessesViewModel = myBusinessesViewModel,
          aiAssistantViewModel = aiAssistantViewModel,
        )
      }
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    if (::authService.isInitialized) {
      authService.release()
    }
  }
}

@Composable
fun GuiaGenioApp(
  directoryViewModel: DirectoryViewModel,
  myBusinessesViewModel: MyBusinessesViewModel,
  aiAssistantViewModel: AiAssistantViewModel,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val snackbarHostState = remember { SnackbarHostState() }

  var currentTab by rememberSaveable { mutableStateOf(GuiaGenioTab.EXPLORE) }
  var showLocalityPicker by remember { mutableStateOf(false) }

  val uiControl by directoryViewModel.uiControl.collectAsStateWithLifecycle()
  val resultsState by directoryViewModel.resultsState.collectAsStateWithLifecycle()
  val autocompleteState by directoryViewModel.autocompleteState.collectAsStateWithLifecycle()
  val selectedBusiness by directoryViewModel.selectedBusiness.collectAsStateWithLifecycle()
  val focusedMapBusiness by directoryViewModel.focusedMapBusiness.collectAsStateWithLifecycle()
  val favoriteIds by directoryViewModel.favoriteIds.collectAsStateWithLifecycle()

  val authState by myBusinessesViewModel.authState.collectAsStateWithLifecycle()
  val myBusinesses by myBusinessesViewModel.publishedBusinesses.collectAsStateWithLifecycle()
  val formState by myBusinessesViewModel.formState.collectAsStateWithLifecycle()
  val myBusinessFeedback by myBusinessesViewModel.feedbackMessage.collectAsStateWithLifecycle()

  val locationPermissionLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
      val granted =
        permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
          permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
      if (granted) {
        directoryViewModel.requestCurrentLocation()
      } else {
        scope.launch {
          snackbarHostState.showSnackbar(
            "Podés elegir manualmente cualquier localidad de Neuquén o el Alto Valle."
          )
        }
      }
    }

  val onRequestMyLocation: () -> Unit = {
    val hasFine =
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    val hasCoarse =
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    if (hasFine || hasCoarse) {
      directoryViewModel.requestCurrentLocation()
    } else {
      locationPermissionLauncher.launch(
        arrayOf(
          Manifest.permission.ACCESS_COARSE_LOCATION,
          Manifest.permission.ACCESS_FINE_LOCATION,
        )
      )
    }
  }

  if (currentTab != GuiaGenioTab.EXPLORE) {
    BackHandler { currentTab = GuiaGenioTab.EXPLORE }
  }

  var showSplash by rememberSaveable { mutableStateOf(true) }

  LaunchedEffect(Unit) {
    if (showSplash) {
      kotlinx.coroutines.delay(1300L)
      showSplash = false
    }
  }

  Box(modifier = Modifier.fillMaxSize()) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
      val useNavRail = maxWidth >= 720.dp

    Scaffold(
      modifier = Modifier.fillMaxSize(),
      containerColor = MaterialTheme.colorScheme.background,
      contentWindowInsets = WindowInsets(0, 0, 0, 0),
      snackbarHost = { SnackbarHost(snackbarHostState) },
      bottomBar = {
        if (!useNavRail) {
          NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            windowInsets = WindowInsets.navigationBars,
          ) {
            GuiaGenioTab.entries.forEach { tab ->
              val selected = currentTab == tab
              val label = stringResource(tab.labelRes)
              NavigationBarItem(
                selected = selected,
                onClick = { currentTab = tab },
                icon = {
                  Icon(
                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                    contentDescription = label,
                  )
                },
                label = { Text(text = label, maxLines = 1) },
                colors =
                  NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                  ),
                modifier = Modifier.testTag("nav_tab_${tab.route}"),
              )
            }
          }
        }
      },
    ) { innerPadding ->
      Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        GuiaGenioBrandHeader(
          selectedLocality = uiControl.selectedLocality,
          isResolvingLocation = uiControl.isResolvingLocation,
          onLocalityClick = { showLocalityPicker = true },
          onUseMyLocationClick = onRequestMyLocation,
        )

        Row(modifier = Modifier.weight(1f).fillMaxSize()) {
          if (useNavRail) {
            NavigationRail(
              containerColor = MaterialTheme.colorScheme.surface,
              modifier = Modifier.fillMaxHeight(),
            ) {
              GuiaGenioTab.entries.forEach { tab ->
                val selected = currentTab == tab
                val label = stringResource(tab.labelRes)
                NavigationRailItem(
                  selected = selected,
                  onClick = { currentTab = tab },
                  icon = {
                    Icon(
                      imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                      contentDescription = label,
                    )
                  },
                  label = { Text(text = label, maxLines = 1) },
                  modifier = Modifier.testTag("nav_rail_${tab.route}"),
                )
              }
            }
          }

          Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            when (currentTab) {
              GuiaGenioTab.EXPLORE ->
                ExploreScreen(
                  uiControl = uiControl,
                  resultsState = resultsState,
                  autocompleteState = autocompleteState,
                  focusedMapBusiness = focusedMapBusiness,
                  favoriteIds = favoriteIds,
                  onSearchQueryChange = directoryViewModel::onSearchQueryChange,
                  onSelectAutocompleteCategory = directoryViewModel::selectAutocompleteCategory,
                  onSelectAutocompletePrediction = directoryViewModel::selectAutocompletePrediction,
                  onDismissAutocomplete = directoryViewModel::dismissAutocomplete,
                  onClearFocusedMapBusiness = directoryViewModel::clearFocusedMapBusiness,
                  onSelectCategory = directoryViewModel::selectCategory,
                  onToggleOpenNow = directoryViewModel::toggleOpenNowFilter,
                  onToggleDelivery = directoryViewModel::toggleDeliveryFilter,
                  onToggleWhatsApp = directoryViewModel::toggleWhatsAppFilter,
                  onToggleUserPublished = directoryViewModel::toggleUserPublishedFilter,
                  onClearFilters = directoryViewModel::clearAllFilters,
                  onViewModeChange = directoryViewModel::setViewMode,
                  onRetry = directoryViewModel::refreshSearch,
                  onBusinessClick = directoryViewModel::openBusinessDetail,
                  onToggleFavorite = directoryViewModel::toggleFavorite,
                  onSearchInArea = directoryViewModel::searchInMapArea,
                )

              GuiaGenioTab.MAP ->
                MapScreen(
                  uiControl = uiControl,
                  resultsState = resultsState,
                  focusedBusiness = focusedMapBusiness,
                  onFocusedBusinessConsumed = directoryViewModel::clearFocusedMapBusiness,
                  onSelectLocality = directoryViewModel::selectLocality,
                  onToggleOpenNow = directoryViewModel::toggleOpenNowFilter,
                  onToggleDelivery = directoryViewModel::toggleDeliveryFilter,
                  onToggleWhatsApp = directoryViewModel::toggleWhatsAppFilter,
                  onBusinessClick = directoryViewModel::openBusinessDetail,
                  onSearchInArea = directoryViewModel::searchInMapArea,
                )

              GuiaGenioTab.CATEGORIES ->
                CategoriesScreen(
                  selectedLocality = uiControl.selectedLocality,
                  onCategoryClick = { category ->
                    directoryViewModel.selectCategory(category)
                    directoryViewModel.setViewMode(DirectoryViewMode.LIST)
                    currentTab = GuiaGenioTab.EXPLORE
                  },
                )

              GuiaGenioTab.MY_BUSINESSES ->
                MyBusinessesScreen(
                  authState = authState,
                  businesses = myBusinesses,
                  formState = formState,
                  feedbackMessage = myBusinessFeedback,
                  defaultLocality = uiControl.selectedLocality,
                  onGoogleSignIn = myBusinessesViewModel::signInWithGoogle,
                  onUpdateOwnerProfile = myBusinessesViewModel::updateOwnerProfile,
                  onSignOut = myBusinessesViewModel::signOut,
                  onClearAuthMessages = myBusinessesViewModel::clearAuthMessages,
                  onClearFeedback = myBusinessesViewModel::clearFeedback,
                  onOpenCreate = myBusinessesViewModel::openCreateForm,
                  onOpenEdit = myBusinessesViewModel::openEditForm,
                  onCloseForm = myBusinessesViewModel::closeForm,
                  onUpdateName = myBusinessesViewModel::updateName,
                  onUpdateCategory = myBusinessesViewModel::updateCategory,
                  onUpdateLocality = myBusinessesViewModel::updateLocality,
                  onUpdateAddress = myBusinessesViewModel::updateAddress,
                  onUpdateCoordinates = myBusinessesViewModel::updateCoordinates,
                  onUpdatePhone = myBusinessesViewModel::updatePhone,
                  onUpdateWhatsapp = myBusinessesViewModel::updateWhatsapp,
                  onUpdateWebsite = myBusinessesViewModel::updateWebsite,
                  onUpdateDescription = myBusinessesViewModel::updateDescription,
                  onUpdateOpenNow = myBusinessesViewModel::updateOpenNow,
                  onUpdateDelivery = myBusinessesViewModel::updateDelivery,
                  onGeocodeAddress = myBusinessesViewModel::geocodeFormAddress,
                  onSaveBusiness = myBusinessesViewModel::saveBusiness,
                  onToggleOpenNow = myBusinessesViewModel::toggleOpenNow,
                  onToggleDelivery = myBusinessesViewModel::toggleDelivery,
                  onDeleteBusiness = myBusinessesViewModel::deleteBusiness,
                  onInspectBusiness = directoryViewModel::openBusinessDetail,
                )

              GuiaGenioTab.AI_ASSISTANT ->
                AiAssistantScreen(aiAssistantViewModel = aiAssistantViewModel)
            }
          }
        }
      }
      }
    }

    AnimatedVisibility(
      visible = showSplash,
      exit = fadeOut(),
    ) {
      GuiaGenioSplashOverlay(onDismiss = { showSplash = false })
    }
  }

  if (showLocalityPicker) {
    LocalityPickerSheet(
      selectedLocality = uiControl.selectedLocality,
      onSelectLocality = directoryViewModel::selectLocality,
      onUseMyLocation = onRequestMyLocation,
      onDismiss = { showLocalityPicker = false },
    )
  }

  selectedBusiness?.let { business ->
    BusinessDetailSheet(
      business = business,
      isFavorite = favoriteIds.contains(business.id),
      onToggleFavorite = { directoryViewModel.toggleFavorite(business) },
      onDismiss = directoryViewModel::closeBusinessDetail,
    )
  }
}
