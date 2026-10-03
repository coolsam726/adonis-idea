package com.adonis.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdonisCallSiteSearcherTest {
    @Test
    fun findsRouteUsages() {
        val text = """
            Route.get("/").name("home")
            return redirect().route("home")
            if route_is("home"):
                pass
            other = route("dashboard")
        """.trimIndent()
        val hits = AdonisCallSiteSearcher.findInText(text, SymbolKind.ROUTE, "home")
        assertEquals(2, hits.size) // route("home") + route_is("home"); name() not detected
        assertTrue(hits.all { it.name == "home" })
    }

    @Test
    fun findsViewAndInclude() {
        val text = """
            return view("auth.login", {})
            @include('auth.login')
            @extends("layouts.app")
        """.trimIndent()
        val login = AdonisCallSiteSearcher.findInText(text, SymbolKind.VIEW, "auth.login")
        assertEquals(2, login.size)
        val layout = AdonisCallSiteSearcher.findInText(text, SymbolKind.VIEW, "layouts.app")
        assertEquals(1, layout.size)
    }

    @Test
    fun findsConfigKeys() {
        val text = """
            env = config("app.env")
            name = config('app.name')
            other = config("app.debug")
        """.trimIndent()
        val hits = AdonisCallSiteSearcher.findInText(text, SymbolKind.CONFIG, "app.env")
        assertEquals(1, hits.size)
    }

    @Test
    fun findsComponentTags() {
        val text = """
            <x-alert type="error"/>
            <x-alert.banner/>
            @component('alert')
        """.trimIndent()
        val hits = AdonisCallSiteSearcher.findInText(text, SymbolKind.COMPONENT, "alert")
        assertTrue(hits.size >= 2) // <x-alert + @component('alert')
    }

    @Test
    fun findsEnvKeys() {
        val py = """
            key = env("APP_KEY")
            url = env('APP_URL', 'http://localhost')
        """.trimIndent()
        assertEquals(1, AdonisCallSiteSearcher.findInText(py, SymbolKind.ENV, "APP_KEY").size)

        val dotenv = """
            APP_NAME=Progress
            APP_KEY=base64:secret
            TITLE=${'$'}{APP_KEY}
        """.trimIndent()
        val hits = AdonisCallSiteSearcher.findInText(
            dotenv,
            SymbolKind.ENV,
            "APP_KEY",
            dotenvFile = true,
        )
        assertTrue(hits.size >= 2) // assignment + ${APP_KEY}
    }
}
