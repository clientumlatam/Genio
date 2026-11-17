package com.example.domain.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Engineering
import androidx.compose.material.icons.rounded.HomeRepairService
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.OilBarrel
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector

enum class CategorySector(val title: String) {
  INDUSTRIA("Vaca Muerta e Industria"),
  COMERCIOS("Comercios y Consumo"),
  GASTRONOMIA("Gastronomía y Delivery"),
  SERVICIOS("Profesionales y Servicios"),
}

data class BusinessCategory(
  val id: String,
  val name: String,
  val shortName: String,
  val sector: CategorySector,
  val placesQueryTerm: String,
  val description: String,
  val icon: ImageVector,
  val accentColor: Color,
  val osmFilterClauses: List<String> = emptyList(),
) {
  val markerColorArgb: Int
    get() = accentColor.toArgb()
}

object DirectoryCategories {
  val AllCategory =
    BusinessCategory(
      id = "all",
      name = "Todos los rubros",
      shortName = "Todos",
      sector = CategorySector.COMERCIOS,
      placesQueryTerm = "comercios servicios empresas",
      description = "Búsqueda general en toda la guía regional",
      icon = Icons.Rounded.Storefront,
      accentColor = Color(0xFF1546A6),
      osmFilterClauses =
        listOf(
          """["amenity"~"restaurant|cafe|fast_food|bar|pharmacy|clinic|hospital|doctors|bank|fuel"]""",
          """["shop"~"supermarket|convenience|hardware|doityourself|bakery|car_repair|car_parts|clothes|electronics|chemist"]""",
          """["industrial"]""",
        ),
    )

  val items: List<BusinessCategory> =
    listOf(
      BusinessCategory(
        id = "vaca_muerta_industria",
        name = "Industria y Vaca Muerta",
        shortName = "Industria",
        sector = CategorySector.INDUSTRIA,
        placesQueryTerm = "servicios petroleros industria metalurgica ingenieria",
        description = "Servicios petroleros, metalúrgica, seguridad industrial y equipamiento",
        icon = Icons.Rounded.OilBarrel,
        accentColor = Color(0xFF1546A6),
        osmFilterClauses =
          listOf(
            """["industrial"]""",
            """["landuse"="industrial"]["name"]""",
            """["craft"~"metal_construction|welder|machinist|turner"]""",
            """["office"~"engineer|company|energy_supplier"]""",
            """["shop"~"trade|gas"]""",
          ),
      ),
      BusinessCategory(
        id = "ferreterias_construccion",
        name = "Ferreterías y Construcción",
        shortName = "Ferreterías",
        sector = CategorySector.INDUSTRIA,
        placesQueryTerm = "ferreteria industrial corralon materiales construccion",
        description = "Ferreterías industriales, corralones, aberturas y bulonería",
        icon = Icons.Rounded.Build,
        accentColor = Color(0xFFD97706),
        osmFilterClauses =
          listOf(
            """["shop"~"hardware|doityourself|trade|paint|tiles|glaziery|locksmith"]""",
            """["craft"~"carpenter|glaziery|locksmith|roofer|scaffolder"]""",
          ),
      ),
      BusinessCategory(
        id = "transporte_logistica",
        name = "Transporte y Logística",
        shortName = "Logística",
        sector = CategorySector.INDUSTRIA,
        placesQueryTerm = "transporte carga logistica expreso encomiendas",
        description = "Expresos, fletes, cargas generales y logística regional",
        icon = Icons.Rounded.LocalShipping,
        accentColor = Color(0xFF1D4ED8),
        osmFilterClauses =
          listOf(
            """["office"~"logistics|transport|courier|moving_company"]""",
            """["amenity"~"courier|post_office"]""",
            """["craft"="movers"]""",
          ),
      ),
      BusinessCategory(
        id = "automotores_talleres",
        name = "Automotores y Talleres",
        shortName = "Automotores",
        sector = CategorySector.INDUSTRIA,
        placesQueryTerm = "taller mecanico repuestos automotor gomeria",
        description = "Talleres mecánicos, repuestos, lubricentros y gomerías 4x4",
        icon = Icons.Rounded.DirectionsCar,
        accentColor = Color(0xFFEA580C),
        osmFilterClauses =
          listOf(
            """["shop"~"car_repair|car_parts|tyres|car|motorcycle|motorcycle_repair"]""",
            """["amenity"~"car_wash|fuel|charging_station"]""",
          ),
      ),
      BusinessCategory(
        id = "gastronomia",
        name = "Gastronomía y Delivery",
        shortName = "Gastronomía",
        sector = CategorySector.GASTRONOMIA,
        placesQueryTerm = "restaurante rotiseria parrilla pizzeria cafe",
        description = "Restaurantes, parrillas, rotiserías, bares y cafeterías",
        icon = Icons.Rounded.Restaurant,
        accentColor = Color(0xFFF59E0B),
        osmFilterClauses =
          listOf(
            """["amenity"~"restaurant|cafe|fast_food|bar|pub|ice_cream|biergarten"]""",
            """["shop"~"bakery|pastry|confectionery|deli"]""",
          ),
      ),
      BusinessCategory(
        id = "supermercados_almacenes",
        name = "Supermercados y Almacenes",
        shortName = "Almacenes",
        sector = CategorySector.COMERCIOS,
        placesQueryTerm = "supermercado almacen despensa verduleria carniceria",
        description = "Autoservicios, mayoristas, carnicerías y mercados de barrio",
        icon = Icons.Rounded.ShoppingCart,
        accentColor = Color(0xFF0284C7),
        osmFilterClauses =
          listOf(
            """["shop"~"supermarket|convenience|greengrocer|butcher|seafood|beverages|wholesale|department_store"]"""
          ),
      ),
      BusinessCategory(
        id = "salud_farmacias",
        name = "Salud y Farmacias",
        shortName = "Salud",
        sector = CategorySector.SERVICIOS,
        placesQueryTerm = "farmacia clinica consultorio odontologia laboratorio",
        description = "Farmacias de turno, clínicas, odontología y kinesiología",
        icon = Icons.Rounded.LocalHospital,
        accentColor = Color(0xFF059669),
        osmFilterClauses =
          listOf(
            """["amenity"~"pharmacy|clinic|hospital|doctors|dentist|veterinary"]""",
            """["healthcare"]""",
            """["shop"~"optician|medical_supply|herbalist"]""",
          ),
      ),
      BusinessCategory(
        id = "servicios_profesionales",
        name = "Estudios y Profesionales",
        shortName = "Profesionales",
        sector = CategorySector.SERVICIOS,
        placesQueryTerm = "estudio contable abogado escribano inmobiliaria arquitecto",
        description = "Contadores, abogados, escribanías, arquitectura e inmobiliarias",
        icon = Icons.Rounded.AccountBalance,
        accentColor = Color(0xFF334155),
        osmFilterClauses =
          listOf(
            """["office"~"lawyer|accountant|estate_agent|architect|notary|insurance|tax_advisor|consulting"]""",
            """["amenity"~"bank|courthouse"]""",
          ),
      ),
      BusinessCategory(
        id = "hogar_oficios",
        name = "Hogar, Electricidad y Gas",
        shortName = "Hogar",
        sector = CategorySector.SERVICIOS,
        placesQueryTerm = "electricista plomero gasista refrigeracion pintureria",
        description = "Gasistas matriculados, plomería, climatización y pinturerías",
        icon = Icons.Rounded.HomeRepairService,
        accentColor = Color(0xFF0D9488),
        osmFilterClauses =
          listOf(
            """["craft"~"electrician|plumber|hvac|painter|tiler"]""",
            """["shop"~"appliance|furniture|lighting|houseware|bathroom_furnishing|kitchen"]""",
          ),
      ),
      BusinessCategory(
        id = "tecnologia",
        name = "Tecnología e Informática",
        shortName = "Tecnología",
        sector = CategorySector.COMERCIOS,
        placesQueryTerm = "computacion servicio tecnico celulares electronica",
        description = "Insumos informáticos, reparación de celulares, redes y CCTV",
        icon = Icons.Rounded.Computer,
        accentColor = Color(0xFF2563EB),
        osmFilterClauses =
          listOf(
            """["shop"~"computer|mobile_phone|electronics|telecommunication|hifi"]""",
            """["craft"="electronics_repair"]""",
            """["office"="it"]""",
          ),
      ),
      BusinessCategory(
        id = "indumentaria",
        name = "Indumentaria y Ropa de Trabajo",
        shortName = "Indumentaria",
        sector = CategorySector.COMERCIOS,
        placesQueryTerm = "ropa de trabajo indumentaria calzado tienda",
        description = "Indumentaria general, calzado de seguridad y ropa térmica",
        icon = Icons.Rounded.Checkroom,
        accentColor = Color(0xFFBE185D),
        osmFilterClauses =
          listOf(
            """["shop"~"clothes|shoes|sports|fashion_accessories|bag|tailor"]"""
          ),
      ),
      BusinessCategory(
        id = "capacitacion",
        name = "Educación y Capacitación",
        shortName = "Capacitación",
        sector = CategorySector.SERVICIOS,
        placesQueryTerm = "instituto capacitacion academia escuela tecnica",
        description = "Institutos técnicos, idiomas, seguridad e higiene y cursos",
        icon = Icons.Rounded.School,
        accentColor = Color(0xFF4F46E5),
        osmFilterClauses =
          listOf(
            """["amenity"~"school|college|university|language_school|training|kindergarten"]"""
          ),
      ),
    )

  fun findById(id: String): BusinessCategory =
    items.firstOrNull { it.id == id } ?: AllCategory

  fun inferFromOsmTags(
    tags: Map<String, String>,
    fallbackCategory: BusinessCategory,
  ): BusinessCategory {
    if (fallbackCategory.id != AllCategory.id) return fallbackCategory
    val shop = tags["shop"].orEmpty().lowercase()
    val amenity = tags["amenity"].orEmpty().lowercase()
    val craft = tags["craft"].orEmpty().lowercase()
    val office = tags["office"].orEmpty().lowercase()
    val industrial = tags["industrial"].orEmpty().lowercase()
    val healthcare = tags["healthcare"].orEmpty().lowercase()

    return when {
      amenity in setOf("restaurant", "cafe", "fast_food", "bar", "pub", "ice_cream") ||
        shop in setOf("bakery", "pastry", "confectionery", "deli") ->
        findById("gastronomia")
      amenity in setOf("pharmacy", "clinic", "hospital", "doctors", "dentist", "veterinary") ||
        healthcare.isNotEmpty() ||
        shop in setOf("optician", "medical_supply") ->
        findById("salud_farmacias")
      shop in setOf("hardware", "doityourself", "trade", "paint", "tiles", "locksmith") ->
        findById("ferreterias_construccion")
      shop in setOf("car_repair", "car_parts", "tyres", "car", "motorcycle", "motorcycle_repair") ||
        amenity in setOf("car_wash", "fuel") ->
        findById("automotores_talleres")
      shop in setOf("supermarket", "convenience", "greengrocer", "butcher", "seafood", "beverages", "wholesale") ->
        findById("supermercados_almacenes")
      shop in setOf("computer", "mobile_phone", "electronics", "telecommunication") ||
        craft == "electronics_repair" ||
        office == "it" ->
        findById("tecnologia")
      shop in setOf("clothes", "shoes", "sports", "fashion_accessories", "bag") ->
        findById("indumentaria")
      office in setOf("logistics", "transport", "courier", "moving_company") ||
        amenity in setOf("courier", "post_office") ->
        findById("transporte_logistica")
      office in setOf("lawyer", "accountant", "estate_agent", "architect", "notary", "insurance") ||
        amenity == "bank" ->
        findById("servicios_profesionales")
      craft in setOf("electrician", "plumber", "hvac", "painter") ||
        shop in setOf("appliance", "furniture", "lighting", "houseware") ->
        findById("hogar_oficios")
      amenity in setOf("school", "college", "university", "language_school", "training") ->
        findById("capacitacion")
      industrial.isNotEmpty() ||
        craft in setOf("metal_construction", "welder", "machinist") ||
        office in setOf("engineer", "company") ->
        findById("vaca_muerta_industria")
      else -> BusinessCategory(
        id = "comercio_general",
        name = "Comercio y Servicios",
        shortName = "Comercio",
        sector = CategorySector.COMERCIOS,
        placesQueryTerm = "comercio",
        description = "Comercio local en OpenStreetMap",
        icon = Icons.Rounded.Engineering,
        accentColor = Color(0xFF1546A6),
      )
    }
  }
}
