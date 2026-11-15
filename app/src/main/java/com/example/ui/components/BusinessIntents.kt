package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.example.domain.model.DirectoryBusiness

object BusinessIntents {
  fun dialPhone(context: Context, phone: String) {
    val sanitized = phone.trim().replace(" ", "")
    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$sanitized"))
    launchSafe(context, intent, "No se encontró una app para realizar llamadas.")
  }

  fun openWhatsApp(context: Context, whatsappRaw: String, businessName: String) {
    val digits = normalizeArgentineWhatsApp(whatsappRaw)
    if (digits.isBlank()) {
      Toast.makeText(context, "Número de WhatsApp inválido", Toast.LENGTH_SHORT).show()
      return
    }
    val message = Uri.encode("Hola $businessName, te contacto desde Guía Genio.")
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits?text=$message"))
    launchSafe(context, intent, "No se pudo abrir WhatsApp.")
  }

  fun openDirections(context: Context, business: DirectoryBusiness) {
    val label = Uri.encode(business.name)
    val geoUri =
      Uri.parse(
        "geo:${business.latitude},${business.longitude}?q=${business.latitude},${business.longitude}($label)"
      )
    val geoIntent = Intent(Intent.ACTION_VIEW, geoUri)
    if (geoIntent.resolveActivity(context.packageManager) != null) {
      context.startActivity(geoIntent)
      return
    }
    val webUri =
      Uri.parse(
        "https://www.openstreetmap.org/directions?to=${business.latitude}%2C${business.longitude}#map=16/${business.latitude}/${business.longitude}"
      )
    launchSafe(context, Intent(Intent.ACTION_VIEW, webUri), "No se pudo abrir el mapa de rutas.")
  }

  fun openWebsite(context: Context, urlRaw: String) {
    val url =
      if (urlRaw.startsWith("http://") || urlRaw.startsWith("https://")) urlRaw
      else "https://$urlRaw"
    launchSafe(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)), "No se pudo abrir el sitio web.")
  }

  fun shareBusiness(context: Context, business: DirectoryBusiness) {
    val text = buildString {
      append("${business.name} (${business.categoryName})\n")
      append("${business.address}, ${business.localityName}\n")
      business.phoneNumber?.let { append("Tel: $it\n") }
      append("https://www.openstreetmap.org/?mlat=${business.latitude}&mlon=${business.longitude}#map=17/${business.latitude}/${business.longitude}")
    }
    val shareIntent =
      Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, business.name)
        putExtra(Intent.EXTRA_TEXT, text)
      }
    context.startActivity(Intent.createChooser(shareIntent, "Compartir comercio"))
  }

  fun formatDistance(meters: Float?): String? {
    if (meters == null) return null
    return if (meters < 1000f) "${meters.toInt()} m"
    else String.format("%.1f km", meters / 1000f)
  }

  private fun normalizeArgentineWhatsApp(raw: String): String {
    val digits = raw.filter { it.isDigit() }
    if (digits.isEmpty()) return ""
    return when {
      digits.startsWith("549") -> digits
      digits.startsWith("54") -> "549${digits.removePrefix("54")}"
      digits.startsWith("0") -> "549${digits.drop(1)}"
      digits.length == 10 -> "549$digits"
      else -> "549$digits"
    }
  }

  private fun launchSafe(context: Context, intent: Intent, errorMsg: String) {
    try {
      context.startActivity(intent)
    } catch (_: Exception) {
      Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
    }
  }
}
