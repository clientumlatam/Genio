# Auditoría de código: Guía Genio

App Android nativa (Kotlin + Jetpack Compose, MVVM) exportada de Google AI Studio. Auditoría del 17 de noviembre de 2026.

Se revisaron el build, el manifest, `firestore.rules`, Room, `PlacesRepository`, `AuthService`, `FirestoreDirectoryRepository`, `BusinessIntents`, el asistente y el CI. No se leyeron a fondo las pantallas de Compose (`MapScreen`, `ExploreScreen`) ni se compiló nada: los hallazgos salen de leer el código.

El proyecto está bien armado en general. La gestión de config de Firebase es cuidadosa, las reglas ya validan bastante y el manejo de cancelación de corrutinas es correcto. Lo más urgente está en las reglas de Firestore y en la higiene de secretos y firma.

## Prioridad alta

1. **Reseñas ilimitadas por usuario.** `submitReview` usa `document()` con ID aleatorio y las reglas no atan el ID al `uid`. Un usuario puede dejar 100 reseñas y manipular el promedio. Corrección: usar `document(uid)` en el repo y exigir `reviewId == request.auth.uid` en la regla de `create`. El plan del proyecto lo tiene como pendiente.
2. **Reseñas sobre comercios que no existen o no están aprobados.** La regla de `create` en `reviews` no hace `exists()` ni chequea `status == 'APPROVED'` del comercio padre. Además, el dueño puede reseñar su propio comercio. Corrección: agregar `get(/databases/$(database)/documents/businesses/$(businessId)).data.status == 'APPROVED'` y exigir `userId != owner`.
3. **La moderación se saltea al editar.** Las reglas impiden que el dueño cambie `status`, pero un comercio ya `APPROVED` puede cambiar nombre, descripción, teléfono y web sin volver a revisión. Corrección: que cualquier cambio de contenido en un comercio aprobado lo devuelva a `PENDING`, o limitar qué campos puede tocar el dueño sin revisión (por ejemplo solo `isOpenNow` y `hasDelivery`).
4. **Faltan llaves y keystores en `.gitignore`.** Ignora `debug.keystore` y `.env`, pero no `*.jks`. El build de release busca `${rootDir}/my-upload-key.jks`, así que es fácil commitearlo por accidente. Agregar `*.jks` y `*.keystore`.
5. **`GEMINI_API_KEY` sin uso real.** El plugin `secrets` + `buildConfig = true` meten la clave en `BuildConfig` y, por lo tanto, en el APK. Ninguna línea del código la usa (el "asistente" es detección de palabras clave sobre OSM). Si no se necesita, sacar el plugin y el `.env.example`. Si algún día se usa, que sea desde un backend y no desde el cliente.
6. **Sin App Check.** Las reglas permiten lecturas públicas de `businesses` y `reviews`, y creación por cualquier usuario logueado. Sin App Check es fácil scrapear o spamear con scripts. Las dependencias de App Check están comentadas en `app/build.gradle.kts`.

## Prioridad media

7. **Datos personales en logcat.** `handleFirestoreError` loguea `uid`, `email`, `providerInfo` y `tenantId` con `Log.e`. En debug es útil, pero en release es un dato personal innecesario. Quitarlo o limitarlo a `BuildConfig.DEBUG`.
8. **Bug en `normalizeArgentineWhatsApp`.** Para un número local como `0299 15-4xx-xxxx` genera `549299154xxxxxxx`, pero WhatsApp necesita `549` + área + número **sin el `15`**. Es el formato más común en Neuquén y Alto Valle, así que los links `wa.me` fallarían. Hay que quitar el `15` después del código de área. Además, las ramas `length == 10` y `else` son idénticas.
9. **Escapado de regex en Overpass.** `escapeOverpassRegex` antepone una sola `\` a los metacaracteres. Dentro de un string de Overpass QL, para que la regex reciba `\.` normalmente hay que escribir `\\.`. No se probó contra la API: verificar con una búsqueda que tenga punto o paréntesis (por ejemplo "Dr. Pérez" o "Ferretería (Roca)").
10. **Mirror de Overpass de terceros.** `maps.mail.ru` está en `OVERPASS_ENDPOINTS`. Cada fallback le manda a ese servidor lo que el usuario busca y la zona. Reemplazarlo por otro mirror europeo o por `overpass-api.de` con reintento.
11. **Reglas sin validar algunos campos.** `source`, `openingHoursRaw`, `openingHoursSummary` y `markerColorArgb` entran sin validación de tipo ni tamaño. `websiteUri` solo valida largo, no esquema. En el cliente `openWebsite` fuerza `https://`, pero cualquier cliente con otro código podría escribir basura. En `users/{id}`, `favoriteBusinessIds` no tiene límite de tamaño.
12. **Room destructivo.** `fallbackToDestructiveMigration(dropAllTables = true)` puede borrar favoritos y comercios locales en una actualización futura, y `exportSchema = false` impide tests de migración. El plan ya lo marca para la Fase 5.
13. **Release sin minificar.** `isMinifyEnabled = false` y `proguard-rules.pro` vacío. Al activar R8 hay que agregar reglas para los modelos de Firestore y Room, así que conviene hacerlo con un build de prueba.

## Prioridad baja

- **CI:** el archivo se llama `andriod.yml` (typo). Solo corre `assembleDebug`, no los tests. Falta `gradlew`/`gradle-wrapper.jar` (hay `gradle-wrapper.properties` pero no el jar), y el workflow depende de `gradle-version: 9.3.1`. Corre en cada `push` y `pull_request`.
- **Nominatim:** sigue en uso en `MyBusinessesViewModel` línea 498 (alta de comercio con dirección), aunque el plan dice eliminarlo. Respeta el límite de 1 req/s y manda User-Agent, que es lo mínimo exigido por su política.
- **Backups:** `allowBackup="true"` con las plantillas vacías de `backup_rules.xml` y `data_extraction_rules.xml`. La base Room con favoritos se respalda por defecto. Decidir qué incluir.
- **Teléfono:** `dialPhone` arma `Uri.parse("tel:$x")`; mejor `Uri.fromParts("tel", x, null)` por si hay `#` o `*`.
- **`shareBusiness`** llama a `startActivity` sin `try/catch`, a diferencia de los demás intents.
- **Assets sueltos:** `public/assets/aistudio/` (\~1,2 MB de imágenes) no se usa en Android, y `.aistudio/` es metadata de AI Studio. Sacarlos del repo.
- **`namespace = "com.example"`** no coincide con el `applicationId`. Funciona, pero conviene alinearlos antes de publicar en Play.

## El plan del proyecto quedó desactualizado

- Las reglas **ya implementan** gran parte de la Fase 3: estados `PENDING/APPROVED/...`, lectura pública solo de `APPROVED`, y el cliente no puede auto-aprobarse. Lo que falta de esa fase son los puntos 1 a 3 de arriba.
- El plan dice 23 tests; el código tiene **32** `@Test` (hay un test nuevo de `ApprovedBusinessesSearchBar` que el plan no cuenta).
- Faltan `firebase.json` y `firestore.test.js` (que el plan menciona). Sin tests de reglas contra el emulador no hay forma de verificar los puntos 1 a 3.

## Lo que está bien

- Las reglas fuerzan `status == 'PENDING'` e `isActive == false` al crear, y `isActive` queda atado a `status == 'APPROVED'`.
- `FirebaseConfigurationService` bloquea la lectura de claves y rechaza IDs plantilla y `(default)`.
- El reconocimiento de voz usa el diálogo del sistema (`RecognizerIntent`), así que no necesita `RECORD_AUDIO`. `tts.shutdown()` está en `onCleared`.
- `osmdroid` define User-Agent propio y el `MapView` libera recursos con `DisposableEffect`/`onDetach`.
- Las queries públicas filtran `status == APPROVED` del lado del servidor, que es lo que exigen las reglas.

## Próximos pasos sugeridos

Parchear los puntos 1 a 3 (reglas + `submitReview`), arreglar la normalización de WhatsApp y el `.gitignore`, y agregar `firebase.json` con tests de reglas contra el emulador de Firestore.
