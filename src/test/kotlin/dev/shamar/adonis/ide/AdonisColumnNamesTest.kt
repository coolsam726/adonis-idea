package dev.shamar.adonis.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdonisColumnNamesTest {
    @Test
    fun convertsCamelAndSnake() {
        assertEquals("country_id", AdonisColumnNames.toSnake("countryId"))
        assertEquals("country_id", AdonisColumnNames.toSnake("country_id"))
        assertEquals("countryId", AdonisColumnNames.toCamel("country_id"))
        assertEquals("countryId", AdonisColumnNames.toCamel("countryId"))
        assertEquals("createdAt", AdonisColumnNames.toCamel("created_at"))
    }

    @Test
    fun lucidQueryOffersBothFormsSnakeFirst() {
        val names = AdonisColumnNames.forQuery(listOf("countryId", "email"), "lucid")
        assertTrue(names.indexOf("country_id") < names.indexOf("countryId"))
        assertTrue("email" in names)
        assertTrue("countryId" in names)
        assertTrue("country_id" in names)
    }

    @Test
    fun mongooseQueryKeepsExactKeys() {
        val names = AdonisColumnNames.forQuery(listOf("countryId", "email"), "mongoose")
        assertEquals(listOf("countryId", "email"), names)
        assertFalse("country_id" in names)
    }

    @Test
    fun attrPrefersCamel() {
        assertEquals(
            listOf("countryId", "createdAt"),
            AdonisColumnNames.forAttr(listOf("country_id", "createdAt")),
        )
    }

    @Test
    fun completionCatalogSplitsQueryAndAttr() {
        val index = AdonisIndex(
            ok = true,
            framework = AdonisIndex.FrameworkEntry(orm = "lucid"),
            tables = mapOf(
                "companies" to AdonisIndex.TableEntry(
                    columns = mapOf(
                        "country_id" to AdonisIndex.Located(),
                        "name" to AdonisIndex.Located(),
                    ),
                    detail = "lucid",
                    model = "Company",
                ),
            ),
            modelMetadata = mapOf(
                "Company" to AdonisIndex.ModelEntry(
                    fillable = listOf("countryId", "name"),
                    table = "companies",
                    orm = "lucid",
                ),
            ),
        )
        val query = AdonisCompletionCatalog.symbolsFor(
            index,
            CallSiteDetector.Site(SymbolKind.COLUMN, "", "Company"),
        ).map { it.first }
        assertTrue("country_id" in query)
        assertTrue("countryId" in query)

        val attr = AdonisCompletionCatalog.symbolsFor(
            index,
            CallSiteDetector.Site(SymbolKind.ATTR, "", "Company"),
        ).map { it.first }
        assertTrue("countryId" in attr)
        assertFalse("country_id" in attr)

        assertEquals("lucid", AdonisModelResolver.ormFor(index, "Company"))
        assertTrue(AdonisColumnNames.forQuery(emptyList(), "lucid").isEmpty())
        assertEquals("", AdonisColumnNames.toSnake(""))
        assertEquals("", AdonisColumnNames.toCamel(""))
        assertEquals(
            "mongoose",
            AdonisModelResolver.ormFor(
                AdonisIndex(ok = true, framework = AdonisIndex.FrameworkEntry(orm = "mongoose")),
                null,
            ),
        )
    }
}
