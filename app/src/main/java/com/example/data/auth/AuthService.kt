package com.example.data.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.example.data.remote.FirebaseConfigurationService
import com.example.domain.model.PatagonianLocalities
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class BusinessOwnerUser(
  val uid: String,
  val displayName: String,
  val email: String,
  val photoUrl: String? = null,
  val preferredLocalityId: String = PatagonianLocalities.NeuquenCapital.id,
)

data class AuthUiState(
  val currentUser: BusinessOwnerUser? = null,
  val isLoading: Boolean = false,
  val isFirebaseConfigured: Boolean = false,
  val firebaseConfigMessage: String? = null,
  val errorMessage: String? = null,
  val infoMessage: String? = null,
)

sealed interface AuthSignInOutcome {
  data class Success(val user: BusinessOwnerUser) : AuthSignInOutcome
  data object Cancelled : AuthSignInOutcome
  data object NoCredentials : AuthSignInOutcome
  data class Error(val message: String) : AuthSignInOutcome
}

/**
 * Servicio básico de autenticación con Firebase Authentication y Google Sign-In
 * mediante Jetpack Credential Manager para dueños de comercios.
 */
class AuthService(
  context: Context,
  private val firebaseConfigService: FirebaseConfigurationService =
    FirebaseConfigurationService(context.applicationContext ?: context),
) {

  private val appContext = context.applicationContext ?: context
  private val credentialManager = CredentialManager.create(appContext)
  private val profilePrefs =
    appContext.getSharedPreferences("guia_genio_owner_profile", Context.MODE_PRIVATE)

  private var firebaseAuth: FirebaseAuth? = null
  private var authListener: FirebaseAuth.AuthStateListener? = null

  private val _authState = MutableStateFlow(AuthUiState())
  val authState: StateFlow<AuthUiState> = _authState.asStateFlow()

  init {
    val diagnostic = firebaseConfigService.inspectAuthConfiguration()
    val auth = if (diagnostic == null) firebaseConfigService.getFirebaseAuth() else null
    if (diagnostic == null && auth != null) {
      firebaseAuth = auth
      val listener = FirebaseAuth.AuthStateListener { instance ->
        val user = instance.currentUser
        firebaseConfigService.onAuthenticatedUserChanged(user?.uid)
        _authState.update { state ->
          state.copy(
            currentUser = user?.toOwnerUser(),
            isFirebaseConfigured = true,
            firebaseConfigMessage = null,
            isLoading = false,
          )
        }
      }
      authListener = listener
      auth.addAuthStateListener(listener)
      val initialUser = auth.currentUser
      firebaseConfigService.onAuthenticatedUserChanged(initialUser?.uid)
      _authState.update {
        it.copy(
          currentUser = initialUser?.toOwnerUser(),
          isFirebaseConfigured = true,
          firebaseConfigMessage = null,
        )
      }
    } else {
      firebaseConfigService.onAuthenticatedUserChanged(null)
      _authState.update {
        it.copy(
          currentUser = null,
          isFirebaseConfigured = false,
          firebaseConfigMessage =
            diagnostic ?: "No se pudo obtener la instancia de FirebaseAuth.",
        )
      }
    }
  }

  /**
   * Intento silencioso de inicio de sesión al abrir la app para cuentas Google ya autorizadas.
   * Utiliza una única opción `GetGoogleIdOption` por solicitud.
   */
  suspend fun attemptAutoSignIn(activityContext: Context): AuthSignInOutcome {
    val auth = firebaseAuth ?: return AuthSignInOutcome.NoCredentials
    auth.currentUser?.let { existing ->
      firebaseConfigService.onAuthenticatedUserChanged(existing.uid)
      val mapped = existing.toOwnerUser()
      _authState.update { it.copy(currentUser = mapped, isLoading = false) }
      return AuthSignInOutcome.Success(mapped)
    }

    val clientId = firebaseConfigService.resolveWebClientId() ?: return AuthSignInOutcome.NoCredentials
    val googleIdOption =
      GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(true)
        .setServerClientId(clientId)
        .setAutoSelectEnabled(true)
        .build()

    val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()

    return try {
      val result = credentialManager.getCredential(activityContext, request)
      val credential = result.credential
      if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        val firebaseCred = GoogleAuthProvider.getCredential(idToken, null)
        val authResult = auth.signInWithCredential(firebaseCred).await()
        val owner =
          authResult.user?.toOwnerUser()
            ?: return AuthSignInOutcome.Error("No se pudo recuperar el perfil de Firebase.")
        firebaseConfigService.onAuthenticatedUserChanged(owner.uid)
        _authState.update { it.copy(currentUser = owner, isLoading = false, errorMessage = null) }
        AuthSignInOutcome.Success(owner)
      } else {
        AuthSignInOutcome.NoCredentials
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: NoCredentialException) {
      AuthSignInOutcome.NoCredentials
    } catch (e: Exception) {
      Log.d(TAG, "Auto sign-in silencioso omitido: ${e.message}")
      AuthSignInOutcome.NoCredentials
    }
  }

  /**
   * Flujo interactivo de Google Sign-In mediante `GetSignInWithGoogleOption` y Credential Manager.
   */
  suspend fun signInWithGoogle(activityContext: Context): AuthSignInOutcome {
    val auth = firebaseAuth
    val clientId = firebaseConfigService.resolveWebClientId()
    if (auth == null || clientId == null) {
      val msg =
        firebaseConfigService.inspectAuthConfiguration()
          ?: "Falta configurar Firebase (google-services.json / default_web_client_id) para iniciar sesión con Google."
      _authState.update { it.copy(isLoading = false, errorMessage = msg) }
      return AuthSignInOutcome.Error(msg)
    }

    _authState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }

    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()

    return try {
      val contextForPrompt = (activityContext as? Activity) ?: activityContext
      val result = credentialManager.getCredential(contextForPrompt, request)
      val credential = result.credential
      if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        val firebaseCred = GoogleAuthProvider.getCredential(idToken, null)
        val authResult = auth.signInWithCredential(firebaseCred).await()
        val owner =
          authResult.user?.toOwnerUser()
            ?: return AuthSignInOutcome.Error("No se obtuvo usuario tras autenticar con Google.")
        firebaseConfigService.onAuthenticatedUserChanged(owner.uid)
        _authState.update {
          it.copy(
            currentUser = owner,
            isLoading = false,
            errorMessage = null,
            infoMessage = "Sesión iniciada como ${owner.displayName}",
          )
        }
        AuthSignInOutcome.Success(owner)
      } else {
        val msg = "Tipo de credencial inesperado al iniciar sesión con Google."
        _authState.update { it.copy(isLoading = false, errorMessage = msg) }
        AuthSignInOutcome.Error(msg)
      }
    } catch (e: GetCredentialCancellationException) {
      Log.w(TAG, "Google Sign-In cancelado o cerrado por el usuario: ${e.message}", e)
      _authState.update { it.copy(isLoading = false) }
      AuthSignInOutcome.Cancelled
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Error en Google Sign-In con Credential Manager", e)
      val msg = e.localizedMessage ?: "No se pudo completar el inicio de sesión con Google."
      _authState.update { it.copy(isLoading = false, errorMessage = msg) }
      AuthSignInOutcome.Error(msg)
    }
  }

  /**
   * Actualiza el perfil del dueño de comercio autenticado en Firebase Auth y preferencias locales.
   */
  suspend fun updateOwnerProfile(displayName: String, preferredLocalityId: String): Boolean {
    val auth = firebaseAuth ?: return false
    val user = auth.currentUser ?: return false
    val cleanName = displayName.trim()
    if (cleanName.length < 2) {
      _authState.update { it.copy(errorMessage = "Ingresá un nombre válido de al menos 2 caracteres.") }
      return false
    }

    _authState.update { it.copy(isLoading = true, errorMessage = null) }
    return try {
      val profileUpdates =
        UserProfileChangeRequest.Builder().setDisplayName(cleanName).build()
      user.updateProfile(profileUpdates).await()
      profilePrefs.edit().putString(KEY_PREFERRED_LOCALITY_PREFIX + user.uid, preferredLocalityId).apply()
      val updatedUser = user.toOwnerUser(overrideDisplayName = cleanName, overrideLocalityId = preferredLocalityId)
      _authState.update {
        it.copy(
          currentUser = updatedUser,
          isLoading = false,
          infoMessage = "Perfil comercial actualizado",
        )
      }
      true
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Error al actualizar perfil del dueño", e)
      _authState.update {
        it.copy(
          isLoading = false,
          errorMessage = e.localizedMessage ?: "No se pudo actualizar el perfil.",
        )
      }
      false
    }
  }

  /**
   * Cierra sesión limpiando el estado de Credential Manager antes de cerrar sesión en Firebase.
   */
  suspend fun signOut() {
    withContext(NonCancellable) {
      try {
        credentialManager.clearCredentialState(ClearCredentialStateRequest())
      } catch (e: Exception) {
        Log.w(TAG, "No se pudo limpiar el estado de Credential Manager: ${e.message}")
      } finally {
        firebaseAuth?.signOut()
        firebaseConfigService.onAuthenticatedUserChanged(null)
        _authState.update {
          it.copy(
            currentUser = null,
            isLoading = false,
            errorMessage = null,
            infoMessage = "Sesión cerrada",
          )
        }
      }
    }
  }

  fun clearMessages() {
    _authState.update { it.copy(errorMessage = null, infoMessage = null) }
  }

  fun release() {
    authListener?.let { firebaseAuth?.removeAuthStateListener(it) }
    authListener = null
  }

  private fun FirebaseUser.toOwnerUser(
    overrideDisplayName: String? = null,
    overrideLocalityId: String? = null,
  ): BusinessOwnerUser {
    val savedLocality =
      overrideLocalityId
        ?: profilePrefs.getString(
          KEY_PREFERRED_LOCALITY_PREFIX + uid,
          PatagonianLocalities.NeuquenCapital.id,
        )
        ?: PatagonianLocalities.NeuquenCapital.id
    return BusinessOwnerUser(
      uid = uid,
      displayName = (overrideDisplayName ?: displayName)?.takeIf { it.isNotBlank() } ?: "Comerciante",
      email = email.orEmpty(),
      photoUrl = photoUrl?.toString(),
      preferredLocalityId = savedLocality,
    )
  }

  companion object {
    private const val TAG = "AuthService"
    private const val KEY_PREFERRED_LOCALITY_PREFIX = "preferred_locality_"
  }
}
