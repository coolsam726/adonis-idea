package dev.shamar.adonis.ide

/**
 * Fixed string-enum catalogs for Shamar builder APIs (1.0.8–1.0.10).
 * Receiver keys match [CallSiteDetector] SHAMAR_LITERAL sites.
 */
object ShamarLiterals {
    const val OPEN_IN = "openIn"
    const val PRESENTATION = "presentation"
    const val PAGE_MODE = "pageMode"
    const val STAT_COLOR = "statColor"

    private val OPEN_IN_VALUES = listOf("modal", "embed", "newTab", "download")
    private val PRESENTATION_VALUES = listOf("modal", "sidebar", "fullscreen")
    private val PAGE_MODE_VALUES = listOf("page", "modal", "sidebar", "fullscreen")
    private val STAT_COLOR_VALUES =
        listOf("primary", "success", "warning", "danger", "info", "gray")

    fun valuesFor(receiver: String): List<String> = when (receiver) {
        OPEN_IN -> OPEN_IN_VALUES
        PRESENTATION -> PRESENTATION_VALUES
        PAGE_MODE -> PAGE_MODE_VALUES
        STAT_COLOR -> STAT_COLOR_VALUES
        else -> emptyList()
    }

    fun detailFor(receiver: String): String = when (receiver) {
        OPEN_IN -> "ActionOpenIn"
        PRESENTATION -> "DialogPresentation"
        PAGE_MODE -> "ResourcePageMode"
        STAT_COLOR -> "StatColor"
        else -> "shamar"
    }
}
