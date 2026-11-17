package com.example.data.remote

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface FirestoreConfigStatus {
  val isConfigured: Boolean

  data class Ready(
    val projectId: String,
    val databaseId: String,
    val hasWebClientId: Boolean,
  ) : FirestoreConfigStatus {
    override val isConfigured: Boolean = true
  }

  data class Disabled(
    val reason: Reason,
    val userMessage: String,
  ) : FirestoreConfigStatus {
    override val isConfigured: Boolean = false

    enum class Reason {
      MISSING_GOOGLE_SERVICES,
      INVALID_PLACEHOLDER_PROJECT,
      MISSING_DATABASE_ID,
      INVALID_DATABASE_ID,
      INIT_FAILED,
    }
  }
}

data class NamedFirestoreResolution(
  val firestore: FirebaseFirestore?,
  val status: FirestoreConfigStatus,
)

/**
 * Snapshot sanitizado del estado de configuración de Firebase.
 * Nunca expone claves API, tokens ni secretos privados.
 */
data class FirebaseConfigurationSnapshot(
  val configStatus: FirestoreConfigStatus,
  val projectId: String? = null,
  val databaseId: String? = null,
  val hasGoogleServicesProject: Boolean = false,
  val hasWebClientId: Boolean = false,
  val authenticatedUserId: String? = null,
  val isFirestoreInitialized: Boolean = false,
  val authConfigDiagnostic: String? = null,
) {
  val isFirebaseProjectConfigured: Boolean
    get() = configStatus.isConfigured && !projectId.isNullOrBlank()

  val isAuthConfigured: Boolean
    get() = hasGoogleServicesProject && hasWebClientId && authConfigDiagnostic == null

  val isAuthenticatedUserPresent: Boolean
    get() = !authenticatedUserId.isNullOrBlank()

  val isAuthenticatedFirestoreReady: Boolean
    get() = isFirestoreInitialized && isAuthenticatedUserPresent
}

internal data class DiscoveredFirebaseProject(
  val app: FirebaseApp,
  val projectId: String,
  val hasWebClientId: Boolean,
)

/**
 * Servicio centralizado de configuración segura de Firebase para Guía Genio:
 * 1. Descubre automáticamente el proyecto desde la configuración generada por Google Services
 *    (`google-services.json` -> `FirebaseApp` + recursos `project_id`, `google_app_id`,
 *    `default_web_client_id`), sin leer ni exponer nunca claves API ni credenciales privadas.
 * 2. Gestiona `firestore_database_id` (desde `firebase_applet_config.xml` o override de pruebas)
 *    rechazando `(default)` o valores de plantilla.
 * 3. Coordina y garantiza la inicialización de Cloud Firestore (`FirebaseFirestore.getInstance(app, databaseId)`)
 *    tanto para lecturas públicas como cuando hay un usuario autenticado presente.
 */
class FirebaseConfigurationService internal constructor(
  context: Context,
  private val databaseIdOverride: String? = null,
  private val authProvider: (FirebaseApp) -> FirebaseAuth? = ::defaultFirebaseAuthProvider,
  private val firestoreFactory: (FirebaseApp, String) -> FirebaseFirestore = ::defaultFirestoreFactory,
  private val externalUidProvider: (() -> String?)? = null,
) {

  constructor(
    context: Context,
    databaseIdOverride: String? = null,
  ) : this(
    context = context,
    databaseIdOverride = databaseIdOverride,
    authProvider = ::defaultFirebaseAuthProvider,
    firestoreFactory = ::defaultFirestoreFactory,
    externalUidProvider = null,
  )

  internal val appContext: Context = context.applicationContext ?: context
  private val initLock = Any()

  @Volatile private var cachedApp: FirebaseApp? = null
  @Volatile private var cachedDatabaseId: String? = null
  @Volatile private var cachedFirestore: FirebaseFirestore? = null
  @Volatile private var observedAuth: FirebaseAuth? = null
  @Volatile private var authStateListener: FirebaseAuth.AuthStateListener? = null
  @Volatile private var explicitlyAuthenticatedUid: String? = null

  private val _configurationState =
    MutableStateFlow(
      FirebaseConfigurationSnapshot(
        configStatus =
          FirestoreConfigStatus.Disabled(
            reason = FirestoreConfigStatus.Disabled.Reason.MISSING_GOOGLE_SERVICES,
            userMessage = "Inicializando verificación de configuración Firebase...",
          )
      )
    )
  val configurationState: StateFlow<FirebaseConfigurationSnapshot> = _configurationState.asStateFlow()

  init {
    refreshConfiguration()
  }

  /**
   * Re-evalúa de forma segura el proyecto de Google Services, el `firestore_database_id`
   * y la presencia del usuario autenticado para inicializar Firestore cuando corresponda.
   */
  fun refreshConfiguration(): FirebaseConfigurationSnapshot {
    synchronized(initLock) {
      val authDiagnostic = inspectAuthConfigurationInternal()
      val projectDiscovery = discoverProjectFromGoogleServices()
      val app = projectDiscovery.first
      if (app != null) {
        attachAuthObserverIfNeeded(app)
      }

      val resolvedUid = resolveCurrentAuthenticatedUidLocked(app)
      val resolution = resolveNamedFirestoreInternalLocked(projectDiscovery)

      if (resolution.firestore != null && resolvedUid != null) {
        Log.d(
          TAG,
          "Firestore nombrado inicializado para proyecto='${projectDiscovery.first?.options?.projectId}' " +
            "db='${cachedDatabaseId}' con usuario autenticado presente.",
        )
      }

      val snapshot = buildSnapshotLocked(resolution, projectDiscovery, resolvedUid, authDiagnostic)
      _configurationState.value = snapshot
      return snapshot
    }
  }

  /**
   * Notifica cambios en el usuario autenticado (por ejemplo tras Google Sign-In o Sign-Out)
   * para inicializar o validar inmediatamente la instancia de Firestore asociada a su sesión.
   */
  fun onAuthenticatedUserChanged(uid: String?): FirebaseConfigurationSnapshot {
    synchronized(initLock) {
      explicitlyAuthenticatedUid = uid?.trim()?.takeIf { it.isNotEmpty() }
      val projectDiscovery = discoverProjectFromGoogleServices()
      val app = projectDiscovery.first
      if (app != null) {
        attachAuthObserverIfNeeded(app)
      }
      val resolvedUid = resolveCurrentAuthenticatedUidLocked(app)
      val resolution = resolveNamedFirestoreInternalLocked(projectDiscovery)
      val authDiagnostic = inspectAuthConfigurationInternal()
      val snapshot = buildSnapshotLocked(resolution, projectDiscovery, resolvedUid, authDiagnostic)
      _configurationState.value = snapshot
      return snapshot
    }
  }

  /**
   * Devuelve la instancia nombrada de Firestore para consultas públicas del directorio
   * (sin requerir sesión iniciada, pero exigiendo Google Services + `firestore_database_id` válidos).
   */
  fun getPublicFirestore(): NamedFirestoreResolution {
    synchronized(initLock) {
      val projectDiscovery = discoverProjectFromGoogleServices()
      val resolution = resolveNamedFirestoreInternalLocked(projectDiscovery)
      val resolvedUid = resolveCurrentAuthenticatedUidLocked(projectDiscovery.first)
      val authDiagnostic = inspectAuthConfigurationInternal()
      _configurationState.value =
        buildSnapshotLocked(resolution, projectDiscovery, resolvedUid, authDiagnostic)
      return resolution
    }
  }

  /**
   * Garantiza que Firestore esté inicializado contra la base nombrada cuando existe un
   * usuario autenticado válido, rechazando operaciones si falta configuración o sesión.
   */
  fun getAuthenticatedFirestore(expectedUid: String? = null): NamedFirestoreResolution {
    synchronized(initLock) {
      val projectDiscovery = discoverProjectFromGoogleServices()
      val app = projectDiscovery.first
      if (app != null) {
        attachAuthObserverIfNeeded(app)
      }

      val resolution = resolveNamedFirestoreInternalLocked(projectDiscovery)
      val resolvedUid = resolveCurrentAuthenticatedUidLocked(app)
      val authDiagnostic = inspectAuthConfigurationInternal()
      _configurationState.value =
        buildSnapshotLocked(resolution, projectDiscovery, resolvedUid, authDiagnostic)

      if (resolution.firestore == null) {
        return resolution
      }

      if (resolvedUid.isNullOrBlank()) {
        return NamedFirestoreResolution(
          firestore = resolution.firestore,
          status = resolution.status,
        )
      }

      if (!expectedUid.isNullOrBlank() && resolvedUid != expectedUid) {
        Log.w(TAG, "La sesión autenticada actual no coincide con el UID solicitado.")
      }

      return resolution
    }
  }

  /**
   * Devuelve el UID autenticado actual si Firebase está configurado y hay sesión activa.
   */
  fun currentAuthenticatedUid(): String? {
    synchronized(initLock) {
      val app = cachedApp ?: discoverProjectFromGoogleServices().first
      return resolveCurrentAuthenticatedUidLocked(app)
    }
  }

  /**
   * Devuelve `FirebaseAuth` asociado al `FirebaseApp` descubierto vía Google Services,
   * o `null` si falta `google-services.json`, es plantilla o falta `default_web_client_id`.
   */
  fun getFirebaseAuth(): FirebaseAuth? {
    synchronized(initLock) {
      if (inspectAuthConfigurationInternal() != null) return null
      val app = discoverProjectFromGoogleServices().first ?: return null
      attachAuthObserverIfNeeded(app)
      return observedAuth ?: authProvider(app)
    }
  }

  /**
   * Resuelve el Client ID web generado automáticamente por Google Services (`default_web_client_id`)
   * sin leer claves privadas.
   */
  fun resolveWebClientId(): String? = readNonSecretStringResource(appContext, RES_WEB_CLIENT_ID)
    ?.takeUnless { isPlaceholderConfigValue(it) }

  /**
   * Valida si la configuración automática de Google Services es apta para Google Sign-In.
   * Devuelve `null` si está lista, o un mensaje descriptivo para el usuario si falta aprovisionar.
   */
  fun inspectAuthConfiguration(): String? = synchronized(initLock) { inspectAuthConfigurationInternal() }

  fun release() {
    synchronized(initLock) {
      val listener = authStateListener
      val auth = observedAuth
      if (listener != null && auth != null) {
        runCatching { auth.removeAuthStateListener(listener) }
      }
      authStateListener = null
      observedAuth = null
    }
  }

  private fun inspectAuthConfigurationInternal(): String? {
    return try {
      val (app, errorStatus) = discoverProjectFromGoogleServices()
      if (app == null) {
        return when (errorStatus?.reason) {
          FirestoreConfigStatus.Disabled.Reason.INVALID_PLACEHOLDER_PROJECT ->
            "google-services.json contiene un projectId de plantilla sin aprovisionar."
          else -> "Falta app/google-services.json para habilitar Google Sign-In."
        }
      }
      if (resolveWebClientId() == null) {
        return "Falta el recurso default_web_client_id generado por google-services.json."
      }
      null
    } catch (e: Exception) {
      Log.w(TAG, "Error verificando configuración de Google Services para Auth: ${e.message}")
      "No se pudo inicializar Firebase Auth (${e.localizedMessage ?: "sin detalle"})."
    }
  }

  /**
   * Descubre el proyecto Firebase de forma segura a partir de la inicialización automática
   * de Google Services (`FirebaseApp` + metadatos públicos generados en recursos Android),
   * omitiendo por completo claves API o secretos privados.
   */
  private fun discoverProjectFromGoogleServices():
    Pair<FirebaseApp?, FirestoreConfigStatus.Disabled?> {
    return try {
      val apps = FirebaseApp.getApps(appContext)
      val app = apps.firstOrNull() ?: FirebaseApp.initializeApp(appContext)
      if (app == null) {
        return null to
          FirestoreConfigStatus.Disabled(
            reason = FirestoreConfigStatus.Disabled.Reason.MISSING_GOOGLE_SERVICES,
            userMessage =
              "Firebase deshabilitado: falta app/google-services.json en el proyecto.",
          )
      }

      val resourceProjectId = readNonSecretStringResource(appContext, RES_GOOGLE_SERVICES_PROJECT_ID)
      val projectId =
        app.options.projectId?.trim().takeUnless { it.isNullOrBlank() } ?: resourceProjectId.orEmpty()
      val resourceAppId = readNonSecretStringResource(appContext, RES_GOOGLE_SERVICES_APP_ID)
      val applicationId =
        app.options.applicationId?.trim().takeUnless { it.isNullOrBlank() } ?: resourceAppId.orEmpty()

      if (isPlaceholderConfigValue(projectId) || isPlaceholderConfigValue(applicationId)) {
        return null to
          FirestoreConfigStatus.Disabled(
            reason = FirestoreConfigStatus.Disabled.Reason.INVALID_PLACEHOLDER_PROJECT,
            userMessage =
              "Firebase deshabilitado: google-services.json tiene configuración de plantilla.",
          )
      }

      if (!resourceProjectId.isNullOrBlank() &&
        !isPlaceholderConfigValue(resourceProjectId) &&
        resourceProjectId != projectId
      ) {
        Log.w(
          TAG,
          "Discrepancia entre FirebaseApp.options.projectId y el recurso generado por Google Services.",
        )
        return null to
          FirestoreConfigStatus.Disabled(
            reason = FirestoreConfigStatus.Disabled.Reason.INIT_FAILED,
            userMessage =
              "Firebase deshabilitado: inconsistencia en la configuración automática de Google Services.",
          )
      }

      app to null
    } catch (e: Exception) {
      Log.w(TAG, "Fallo al descubrir proyecto desde Google Services: ${e.message}")
      null to
        FirestoreConfigStatus.Disabled(
          reason = FirestoreConfigStatus.Disabled.Reason.INIT_FAILED,
          userMessage =
            "Firebase deshabilitado: error al leer la configuración de Google Services (${e.localizedMessage ?: "sin detalle"}).",
        )
    }
  }

  private fun resolveNamedFirestoreInternalLocked(
    projectDiscovery: Pair<FirebaseApp?, FirestoreConfigStatus.Disabled?>,
  ): NamedFirestoreResolution {
    val (app, discoveryError) = projectDiscovery
    if (app == null) {
      clearCachedFirestoreLocked()
      val disabled =
        discoveryError
          ?: FirestoreConfigStatus.Disabled(
            reason = FirestoreConfigStatus.Disabled.Reason.MISSING_GOOGLE_SERVICES,
            userMessage =
              "Firebase deshabilitado: falta app/google-services.json en el proyecto.",
          )
      return NamedFirestoreResolution(firestore = null, status = disabled)
    }

    val projectId =
      app.options.projectId?.trim().takeUnless { it.isNullOrBlank() }
        ?: readNonSecretStringResource(appContext, RES_GOOGLE_SERVICES_PROJECT_ID).orEmpty()

    val resolvedDbId = resolveDatabaseIdSafely()
    if (resolvedDbId.isNullOrBlank()) {
      clearCachedFirestoreLocked()
      Log.w(TAG, "Recurso $RES_DATABASE_ID no encontrado; Firestore permanece deshabilitado.")
      return NamedFirestoreResolution(
        firestore = null,
        status =
          FirestoreConfigStatus.Disabled(
            reason = FirestoreConfigStatus.Disabled.Reason.MISSING_DATABASE_ID,
            userMessage =
              "Firebase deshabilitado: falta el recurso firestore_database_id (firebase_applet_config.xml).",
          ),
      )
    }

    if (isInvalidDatabaseId(resolvedDbId)) {
      clearCachedFirestoreLocked()
      Log.w(TAG, "firestore_database_id inválido; nunca se permite '(default)' ni plantillas.")
      return NamedFirestoreResolution(
        firestore = null,
        status =
          FirestoreConfigStatus.Disabled(
            reason = FirestoreConfigStatus.Disabled.Reason.INVALID_DATABASE_ID,
            userMessage =
              "Firebase deshabilitado: firestore_database_id inválido (no puede ser '(default)' ni plantilla).",
          ),
      )
    }

    return try {
      val existing = cachedFirestore
      val instance =
        if (existing != null && cachedApp === app && cachedDatabaseId == resolvedDbId) {
          existing
        } else {
          val created = firestoreFactory(app, resolvedDbId)
          cachedApp = app
          cachedDatabaseId = resolvedDbId
          cachedFirestore = created
          created
        }

      NamedFirestoreResolution(
        firestore = instance,
        status =
          FirestoreConfigStatus.Ready(
            projectId = projectId,
            databaseId = resolvedDbId,
            hasWebClientId = resolveWebClientId() != null,
          ),
      )
    } catch (e: Exception) {
      clearCachedFirestoreLocked()
      Log.w(TAG, "No se pudo inicializar Firestore nombrado: ${e.message}")
      NamedFirestoreResolution(
        firestore = null,
        status =
          FirestoreConfigStatus.Disabled(
            reason = FirestoreConfigStatus.Disabled.Reason.INIT_FAILED,
            userMessage =
              "Firebase deshabilitado: error al inicializar Firestore (${e.localizedMessage ?: "sin detalle"}).",
          ),
      )
    }
  }

  private fun resolveDatabaseIdSafely(): String? {
    val override = databaseIdOverride?.trim()?.takeIf { it.isNotEmpty() }
    if (override != null) return override
    return readNonSecretStringResource(appContext, RES_DATABASE_ID)
  }

  private fun attachAuthObserverIfNeeded(app: FirebaseApp) {
    if (observedAuth != null && cachedApp === app) return
    val auth = authProvider(app) ?: return
    val prevAuth = observedAuth
    val prevListener = authStateListener
    if (prevAuth != null && prevListener != null && prevAuth !== auth) {
      runCatching { prevAuth.removeAuthStateListener(prevListener) }
    }
    val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
      onAuthenticatedUserChanged(firebaseAuth.currentUser?.uid)
    }
    runCatching {
      auth.addAuthStateListener(listener)
      observedAuth = auth
      authStateListener = listener
    }
  }

  private fun resolveCurrentAuthenticatedUidLocked(app: FirebaseApp?): String? {
    val fromExternal = externalUidProvider?.invoke()?.trim()?.takeIf { it.isNotEmpty() }
    if (fromExternal != null) return fromExternal
    val authUid =
      if (app != null) {
        runCatching { (observedAuth ?: authProvider(app))?.currentUser?.uid }.getOrNull()
      } else {
        null
      }
    return authUid?.trim()?.takeIf { it.isNotEmpty() } ?: explicitlyAuthenticatedUid
  }

  private fun buildSnapshotLocked(
    resolution: NamedFirestoreResolution,
    projectDiscovery: Pair<FirebaseApp?, FirestoreConfigStatus.Disabled?>,
    resolvedUid: String?,
    authDiagnostic: String?,
  ): FirebaseConfigurationSnapshot {
    val app = projectDiscovery.first
    val ready = resolution.status as? FirestoreConfigStatus.Ready
    val projectId =
      ready?.projectId
        ?: app?.options?.projectId?.trim()?.takeUnless { isPlaceholderConfigValue(it) }
    return FirebaseConfigurationSnapshot(
      configStatus = resolution.status,
      projectId = projectId,
      databaseId = ready?.databaseId,
      hasGoogleServicesProject = app != null && projectDiscovery.second == null,
      hasWebClientId = resolveWebClientId() != null,
      authenticatedUserId = resolvedUid,
      isFirestoreInitialized = resolution.firestore != null,
      authConfigDiagnostic = authDiagnostic,
    )
  }

  private fun clearCachedFirestoreLocked() {
    cachedApp = null
    cachedDatabaseId = null
    cachedFirestore = null
  }

  companion object {
    private const val TAG = "FirebaseConfigService"
    internal const val RES_DATABASE_ID = "firestore_database_id"
    internal const val RES_WEB_CLIENT_ID = "default_web_client_id"
    internal const val RES_GOOGLE_SERVICES_PROJECT_ID = "project_id"
    internal const val RES_GOOGLE_SERVICES_APP_ID = "google_app_id"

    internal fun isPlaceholderConfigValue(raw: String?): Boolean {
      val trimmed = raw?.trim().orEmpty()
      if (trimmed.isBlank()) return true
      val lower = trimmed.lowercase()
      return lower == "remixed-project-id" ||
        lower == "remixed-database-id" ||
        lower.startsWith("your_") ||
        lower.startsWith("my_") ||
        lower.contains("placeholder")
    }

    internal fun isInvalidDatabaseId(raw: String?): Boolean {
      val trimmed = raw?.trim().orEmpty()
      if (isPlaceholderConfigValue(trimmed)) return true
      return trimmed.equals("(default)", ignoreCase = true)
    }

    /**
     * Lee exclusivamente recursos de configuración no secretos desde Android Resources
     * (por ejemplo `firestore_database_id`, `project_id`, `google_app_id`, `default_web_client_id`).
     * Bloquea por nombre cualquier clave privada o API key para evitar exposiciones accidentales.
     */
    internal fun readNonSecretStringResource(context: Context, resourceName: String): String? {
      val normalized = resourceName.trim().lowercase()
      if (normalized.contains("api_key") ||
        normalized.contains("secret") ||
        normalized.contains("private") ||
        normalized.contains("password") ||
        normalized.contains("token")
      ) {
        return null
      }
      return try {
        val resId = context.resources.getIdentifier(resourceName, "string", context.packageName)
        if (resId == 0) null else context.getString(resId).trim().takeIf { it.isNotEmpty() }
      } catch (_: Exception) {
        null
      }
    }
  }
}

private fun defaultFirebaseAuthProvider(app: FirebaseApp): FirebaseAuth? =
  runCatching { FirebaseAuth.getInstance(app) }.getOrNull()

private fun defaultFirestoreFactory(app: FirebaseApp, dbId: String): FirebaseFirestore =
  FirebaseFirestore.getInstance(app, dbId)

