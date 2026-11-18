package com.example.basqueteapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.content.Context
import android.content.res.Configuration
import android.util.TypedValue
import android.widget.DatePicker
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.withId
import com.example.basqueteapp.uniffi.AppEngine
import com.example.basqueteapp.ui.theme.JirauTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeLifecycleTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun rustEngineCanIncrement() {
        val engine = AppEngine()
        try {
            assertEquals(1, engine.increment().counter)
        } finally {
            engine.close()
        }
    }

    @Test
    fun settingsOfferAppearanceChoices() {
        compose.setContent {
            JirauTheme(darkTheme = true, dynamicColor = false) {
                AppScreen(Appearance.SYSTEM, loadGames = { _, _ -> emptyList() }) {}
            }
        }
        compose.onNodeWithText("Configurações").performClick()
        compose.onNodeWithText("Sistema").assertIsDisplayed()
        compose.onNodeWithText("Claro").assertIsDisplayed()
        compose.onNodeWithText("Escuro").assertIsDisplayed()
        compose.onNodeWithText("Acompanha o dispositivo").assertIsDisplayed()
        if (InstrumentationRegistry.getArguments().getString("captureSettings") == "true") {
            val image = compose.onRoot().captureToImage().asAndroidBitmap()
            val directory = InstrumentationRegistry.getInstrumentation().targetContext.filesDir
            java.io.File(directory, "settings-redesign.png").outputStream().use { output ->
                check(image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
            }
        }
    }

    @Test
    fun appearanceOptionsExposeSelectionChanges() {
        val appearance = mutableStateOf(Appearance.SYSTEM)
        compose.setContent {
            AppScreen(appearance.value) { selected -> appearance.value = selected }
        }
        compose.onNodeWithText("Configurações").performClick()
        compose.onNode(hasText("Sistema") and hasClickAction()).assertIsSelected()
        compose.onNode(hasText("Escuro") and hasClickAction()).performClick()
        compose.onNode(hasText("Escuro") and hasClickAction()).assertIsSelected()
        compose.onNode(hasText("Claro") and hasClickAction()).performClick()
        compose.onNode(hasText("Claro") and hasClickAction()).assertIsSelected()
        compose.onNode(hasText("Sistema") and hasClickAction()).performClick()
        compose.onNode(hasText("Sistema") and hasClickAction()).assertIsSelected()
    }

    @Test
    fun gamesHeaderStacksAtIntermediateTextSize() {
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current)
            configuration.fontScale = 1.2f
            val density = Density(LocalDensity.current.density, fontScale = 1.2f)
            CompositionLocalProvider(LocalConfiguration provides configuration, LocalDensity provides density) {
                JirauTheme(darkTheme = true, dynamicColor = false) {
                    AppScreen(Appearance.DARK, loadGames = { _, _ -> emptyList() }) {}
                }
            }
        }
        val title = compose.onNode(hasText("Jogos") and !hasClickAction()).fetchSemanticsNode()
        val date = compose.onNode(hasText("Data (UTC):", substring = true) and hasClickAction()).fetchSemanticsNode()
        assertTrue(title.boundsInRoot.bottom <= date.boundsInRoot.top)
    }

    @Test
    fun changingAppearancePreservesGamesJourney() {
        val games = parseGames(
            """{"events":[{"idEvent":"1","strHomeTeam":"Time Azul","strAwayTeam":"Time Verde","strLeague":"Liga Exemplo","strStatus":"FT","intHomeScore":"80","intAwayScore":"75"}]}"""
        )
        val appearance = mutableStateOf(Appearance.DARK)
        compose.setContent {
            JirauTheme(darkTheme = appearance.value == Appearance.DARK, dynamicColor = false) {
                AppScreen(appearance.value, loadGames = { _, _ -> games }) { selected ->
                    appearance.value = selected
                }
            }
        }
        fun capture(name: String) {
            if (InstrumentationRegistry.getArguments().getString("captureAppearanceJourney") == "true") {
                val image = compose.onRoot().captureToImage().asAndroidBitmap()
                val directory = InstrumentationRegistry.getInstrumentation().targetContext.filesDir
                java.io.File(directory, name).outputStream().use { output ->
                    check(image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
                }
            }
        }
        compose.onNodeWithText("Time Azul").performScrollTo().assertIsDisplayed()
        capture("journey-dark.png")
        compose.onNode(hasText("Configurações") and hasClickAction()).performClick()
        compose.onNode(hasText("Claro") and hasClickAction()).performClick()
        compose.onNode(hasText("Claro") and hasClickAction()).assertIsSelected()
        compose.onNode(hasText("Jogos") and hasClickAction()).performClick()
        compose.onNodeWithText("Time Azul").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Time Verde").assertIsDisplayed()
        compose.onNodeWithText("80").assertIsDisplayed()
        compose.onNodeWithText("75").assertIsDisplayed()
        capture("journey-light.png")
    }

    @Test
    fun intermediateTextNavigationKeepsAccessibleNames() {
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current)
            configuration.fontScale = 1.2f
            val density = Density(LocalDensity.current.density, fontScale = 1.2f)
            CompositionLocalProvider(LocalConfiguration provides configuration, LocalDensity provides density) {
                AppScreen(Appearance.SYSTEM, loadGames = { _, _ -> emptyList() }) {}
            }
        }
        compose.onNodeWithContentDescription("Favoritos").assertIsDisplayed().performClick()
        compose.onNodeWithText("Nenhum favorito").assertIsDisplayed()
        compose.onNodeWithContentDescription("Configurações").assertIsDisplayed().performClick()
        compose.onNodeWithText("Aparência").assertIsDisplayed()
    }

    @Test
    fun navigationExposesSelectedDestination() {
        compose.setContent { AppScreen(Appearance.SYSTEM, loadGames = { _, _ -> emptyList() }) {} }
        val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        for (label in listOf("Jogos", "Favoritos", "Configurações")) {
            val destination = compose.onNode(hasText(label) and hasClickAction())
            destination.assertIsDisplayed()
            assertTrue(destination.fetchSemanticsNode().boundsInRoot.height >= 48f * density)
        }
        compose.onNode(hasText("Jogos") and hasClickAction()).assertIsSelected()
        compose.onNode(hasText("Configurações") and hasClickAction()).performClick()
        compose.onNode(hasText("Configurações") and hasClickAction()).assertIsSelected()
        compose.onNodeWithText("Sistema").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun gamesParserHandlesMissingScoresAndEmptyList() {
        val games = parseGames(
            """{"events":[{"idEvent":"1","strHomeTeam":"Home","strAwayTeam":"Away","strLeague":"Example","intHomeScore":null}]}"""
        )
        assertEquals("Home", games.single().home)
        assertNull(games.single().homeScore)
        assertEquals(emptyList<Game>(), parseGames("""{"events":null}"""))
    }

    @Test
    fun gamesParserRejectsMissingEvents() {
        assertThrows(IllegalArgumentException::class.java) {
            parseGames("{}")
        }
    }

    @Test
    fun leagueFilterIsIncludedInRequest() {
        val date = java.time.LocalDate.of(2026, 11, 16)
        assertTrue(gamesUrl(date, LeagueFilter.NORTH_AMERICA).query.contains("l=4387"))
        assertTrue(!gamesUrl(date, LeagueFilter.NORTH_AMERICA).query.contains("s=Basketball"))
        assertTrue(!gamesUrl(date, LeagueFilter.ALL).query.contains("l=4387"))
        assertTrue(gamesUrl(date, LeagueFilter.ALL).query.contains("s=Basketball"))
    }

    @Test
    fun gameStatusDoesNotGuessUnknownValues() {
        assertEquals("Encerrado", displayGameStatus("FT"))
        assertEquals("Ainda não iniciado", displayGameStatus("NS"))
        assertEquals("Status não informado", displayGameStatus("unknown"))
        assertEquals("Status não informado", displayGameStatus(null))
    }

    @Test
    fun gameTimeRejectsInvalidValues() {
        assertEquals("18:30 UTC", displayGameTime("18:30:00"))
        assertEquals("08:05 UTC", displayGameTime(" 08:05 "))
        assertEquals("Horário não informado", displayGameTime("25:99:00"))
        assertEquals("Horário não informado", displayGameTime("desconhecido"))
        assertEquals("Horário não informado", displayGameTime(null))
    }

    @Test
    fun malformedGameTimeShowsAnHonestFallback() {
        val fixture = parseGames(
            """{"events":[{"idEvent":"1","strHomeTeam":"Equipe Casa","strAwayTeam":"Equipe Visitante","strLeague":"Liga Exemplo","strTime":"25:99:00"}]}"""
        )
        compose.setContent {
            JirauTheme(darkTheme = false, dynamicColor = false) {
                AppScreen(Appearance.LIGHT, loadGames = { _, _ -> fixture }) {}
            }
        }
        compose.onNodeWithText("Horário não informado").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("25:99 UTC").assertDoesNotExist()
        if (InstrumentationRegistry.getArguments().getString("captureInvalidTime") == "true") {
            val image = compose.onRoot().captureToImage().asAndroidBitmap()
            val directory = InstrumentationRegistry.getInstrumentation().targetContext.filesDir
            java.io.File(directory, "invalid-time.png").outputStream().use { output ->
                check(image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
            }
        }
    }

    @Test
    fun favoritesShowTeamNameAndAllowRemoval() {
        val teamId = "fixture-${java.util.UUID.randomUUID()}"
        val name = "Associação de Basquete da Cidade"
        val fixture = parseGames(
            """{"events":[{"idEvent":"1","strHomeTeam":"$name","strAwayTeam":"Equipe Visitante","idHomeTeam":"$teamId","strLeague":"Liga Exemplo"}]}"""
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val lightTheme = InstrumentationRegistry.getArguments().getString("captureFavoritesLight") == "true"
        try {
            compose.setContent {
                JirauTheme(darkTheme = !lightTheme, dynamicColor = false) {
                    AppScreen(if (lightTheme) Appearance.LIGHT else Appearance.DARK, loadGames = { _, _ -> fixture }) {}
                }
            }
            compose.onNodeWithContentDescription("Favoritar $name").performScrollTo().performClick()
            compose.onNode(hasText("Favoritos") and hasClickAction()).performClick()
            compose.onNodeWithText(name).assertIsDisplayed()
            val remove = compose.onNodeWithContentDescription("Remover $name dos favoritos")
            remove.assertHasClickAction()
            if (InstrumentationRegistry.getArguments().getString("captureFavorites") == "true" || lightTheme) {
                val image = compose.onRoot().captureToImage().asAndroidBitmap()
                java.io.File(context.filesDir, "favorites-redesign.png").outputStream().use { output ->
                    check(image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
                }
            }
            remove.performClick()
            compose.onNodeWithText("Nenhum favorito").assertIsDisplayed()
            if (lightTheme) {
                val image = compose.onRoot().captureToImage().asAndroidBitmap()
                java.io.File(context.filesDir, "favorites-empty-light.png").outputStream().use { output ->
                    check(image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
                }
            }
        } finally {
            context.getSharedPreferences("favoriteTeams", Context.MODE_PRIVATE).edit().remove(teamId).commit()
        }
    }

    @Test
    fun darkLargeTextFavoritesJourney() {
        val teamId = "fixture-${java.util.UUID.randomUUID()}"
        val name = "Associação de Basquete da Cidade"
        val fixture = parseGames(
            """{"events":[{"idEvent":"1","strHomeTeam":"$name","strAwayTeam":"Equipe Visitante","idHomeTeam":"$teamId","strLeague":"Liga Exemplo"}]}"""
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        try {
            compose.setContent {
                val configuration = Configuration(LocalConfiguration.current)
                configuration.fontScale = 2f
                val density = Density(LocalDensity.current.density, fontScale = 2f)
                CompositionLocalProvider(LocalConfiguration provides configuration, LocalDensity provides density) {
                    JirauTheme(darkTheme = true, dynamicColor = false) {
                        AppScreen(Appearance.DARK, loadGames = { _, _ -> fixture }) {}
                    }
                }
            }
            compose.onNodeWithContentDescription("Favoritar $name").performScrollTo().performClick()
            compose.onNodeWithContentDescription("Favoritos").performClick()
            compose.onNodeWithText(name).performScrollTo().assertIsDisplayed()
            val remove = compose.onNodeWithContentDescription("Remover $name dos favoritos")
            remove.performScrollTo().assertHasClickAction()
            if (InstrumentationRegistry.getArguments().getString("captureDarkLargeFavorites") == "true") {
                val image = compose.onRoot().captureToImage().asAndroidBitmap()
                java.io.File(context.filesDir, "favorites-dark-large.png").outputStream().use { output ->
                    check(image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
                }
            }
            remove.performClick()
            compose.onNodeWithText("Nenhum favorito").assertIsDisplayed()
        } finally {
            context.getSharedPreferences("favoriteTeams", Context.MODE_PRIVATE).edit().remove(teamId).commit()
        }
    }

    @Test
    fun favoritesRemainAfterScreenIsRecreated() {
        val teamId = "fixture-${java.util.UUID.randomUUID()}"
        val name = "Equipe Persistente"
        val games = parseGames(
            """{"events":[{"idEvent":"1","strHomeTeam":"$name","strAwayTeam":"Equipe Visitante","idHomeTeam":"$teamId","strLeague":"Liga Exemplo","strStatus":"FT","intHomeScore":"80","intAwayScore":"75"}]}"""
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val showApp = mutableStateOf(true)
        try {
            compose.setContent {
                JirauTheme(darkTheme = false, dynamicColor = false) {
                    if (showApp.value) {
                        AppScreen(Appearance.LIGHT, loadGames = { _, _ -> games }) {}
                    }
                }
            }
            compose.onNodeWithContentDescription("Favoritar $name").performScrollTo().performClick()
            compose.onNodeWithContentDescription("Remover $name dos favoritos").assertExists()
            compose.runOnIdle { showApp.value = false }
            compose.waitForIdle()
            compose.runOnIdle { showApp.value = true }
            compose.onNodeWithContentDescription("Remover $name dos favoritos").performScrollTo().assertIsDisplayed()
            compose.onNode(hasText("Favoritos") and hasClickAction()).performClick()
            compose.onNodeWithText(name).assertIsDisplayed()
            if (InstrumentationRegistry.getArguments().getString("captureRecreatedFavorites") == "true") {
                val image = compose.onRoot().captureToImage().asAndroidBitmap()
                java.io.File(context.filesDir, "favorites-recreated.png").outputStream().use { output ->
                    check(image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
                }
            }
            compose.onNodeWithContentDescription("Remover $name dos favoritos").performClick()
            compose.onNodeWithText("Nenhum favorito").assertIsDisplayed()
            compose.onNode(hasText("Jogos") and hasClickAction()).performClick()
            compose.onNodeWithContentDescription("Favoritar $name").performScrollTo().assertIsDisplayed()
        } finally {
            context.getSharedPreferences("favoriteTeams", Context.MODE_PRIVATE).edit().remove(teamId).commit()
        }
    }

    @Test
    fun leagueFilterAndFavoritesJourney() {
        val teamId = "fixture-${java.util.UUID.randomUUID()}"
        val name = "Equipe Filtrada"
        val games = parseGames(
            """{"events":[{"idEvent":"1","strHomeTeam":"$name","strAwayTeam":"Equipe Visitante","idHomeTeam":"$teamId","strLeague":"Liga Exemplo"}]}"""
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val queriedLeague = mutableStateOf(LeagueFilter.ALL)
        try {
            compose.setContent {
                JirauTheme(darkTheme = true, dynamicColor = false) {
                    AppScreen(Appearance.DARK, loadGames = { _, league ->
                        queriedLeague.value = league
                        if (league == LeagueFilter.NORTH_AMERICA) games else emptyList()
                    }) {}
                }
            }
            compose.onNode(hasText("Liga norte-americana") and hasClickAction())
                .performScrollTo().assertIsDisplayed().performClick().assertIsSelected()
            compose.onNodeWithText(name).performScrollTo().assertIsDisplayed()
            compose.runOnIdle { assertEquals(LeagueFilter.NORTH_AMERICA, queriedLeague.value) }
            compose.onNodeWithContentDescription("Favoritar $name").performScrollTo().performClick()
            compose.onNode(hasText("Favoritos") and hasClickAction()).performClick()
            compose.onNodeWithText(name).assertIsDisplayed()
            if (InstrumentationRegistry.getArguments().getString("captureFilteredFavorite") == "true") {
                val image = compose.onRoot().captureToImage().asAndroidBitmap()
                java.io.File(context.filesDir, "filtered-favorite.png").outputStream().use { output ->
                    check(image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
                }
            }
            compose.onNodeWithContentDescription("Remover $name dos favoritos").performClick()
            compose.onNodeWithText("Nenhum favorito").assertIsDisplayed()
        } finally {
            context.getSharedPreferences("favoriteTeams", Context.MODE_PRIVATE).edit().remove(teamId).commit()
        }
    }

    @Test
    fun broadcastNoticeUsesAvailableWidthWithoutFavoriteIds() {
        val games = parseGames(
            """{"events":[{"idEvent":"1","strHomeTeam":"Time A","strAwayTeam":"Time B","strLeague":"Liga Exemplo","strStatus":"FT","intHomeScore":"80","intAwayScore":"75"}]}"""
        )
        compose.setContent {
            JirauTheme(darkTheme = false, dynamicColor = false) {
                Box(modifier = Modifier.width(240.dp)) {
                    GamesScreen(emptyMap(), { _, _ -> }, { _, _ -> games }, darkTheme = false)
                }
            }
        }
        val notice = compose.onNodeWithText("Transmissão não consultada").performScrollTo()
        notice.assertIsDisplayed()
        val width = notice.fetchSemanticsNode().boundsInRoot.width
        val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        assertTrue(width >= 190f * density)
        compose.onNodeWithContentDescription("Favoritar Time A").assertDoesNotExist()
        compose.onNodeWithContentDescription("Favoritar Time B").assertDoesNotExist()
    }

    @Test
    fun multipleGamesKeepFavoriteActionsIndependent() {
        val games = parseGames(
            """{"events":[{"idEvent":"1","strHomeTeam":"Time Azul","strAwayTeam":"Time Verde","idHomeTeam":"101","idAwayTeam":"102","strLeague":"Liga Exemplo","strStatus":"FT","intHomeScore":"80","intAwayScore":"75"},{"idEvent":"2","strHomeTeam":"Time Ouro","strAwayTeam":"Time Prata","idHomeTeam":"201","idAwayTeam":"202","strLeague":"Liga Exemplo","strStatus":"NS","strTime":"18:30:00"}]}"""
        )
        val favorites = mutableStateOf<Map<String, String>>(emptyMap())
        compose.setContent {
            JirauTheme(darkTheme = false, dynamicColor = false) {
                GamesScreen(favorites.value, { id, name ->
                    if (id != null) {
                        favorites.value = favorites.value + (id to name)
                    }
                }, { _, _ -> games }, darkTheme = false)
            }
        }
        compose.onNodeWithText("Time Azul").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Favoritar Time Azul").performClick()
        compose.onNodeWithContentDescription("Remover Time Azul dos favoritos").assertIsDisplayed()
        compose.onNodeWithText("Time Ouro").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Favoritar Time Ouro").assertHasClickAction()
        compose.onNodeWithText("18:30 UTC").performScrollTo().assertIsDisplayed()
        if (InstrumentationRegistry.getArguments().getString("captureMultipleGames") == "true") {
            compose.onNodeWithText("Time Azul").performScrollTo()
            val image = compose.onRoot().captureToImage().asAndroidBitmap()
            val directory = InstrumentationRegistry.getInstrumentation().targetContext.filesDir
            java.io.File(directory, "multiple-games.png").outputStream().use { output ->
                check(image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
            }
        }
    }

    @Test
    fun matchupShowsBothTeamsScoresAndFavoriteActions() {
        val fixture = parseGames(
            """{"events":[{"idEvent":"1","strHomeTeam":"Equipe Casa","strAwayTeam":"Equipe Visitante","idHomeTeam":"100","idAwayTeam":"200","strLeague":"Liga Exemplo","strStatus":"FT","intHomeScore":"80","intAwayScore":"75"}]}"""
        )
        compose.setContent {
            JirauTheme(darkTheme = true, dynamicColor = false) {
                AppScreen(Appearance.DARK, loadGames = { _, _ -> fixture }) {}
            }
        }
        compose.onNodeWithText("Equipe Casa").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Equipe Visitante").assertIsDisplayed()
        compose.onNodeWithText("80").assertIsDisplayed()
        compose.onNodeWithText("75").assertIsDisplayed()
        compose.onNodeWithContentDescription("Favoritar Equipe Casa").assertHasClickAction()
        compose.onNodeWithContentDescription("Favoritar Equipe Visitante").assertHasClickAction()
        if (InstrumentationRegistry.getArguments().getString("captureMatchup") == "true") {
            val image = compose.onRoot().captureToImage().asAndroidBitmap()
            val directory = InstrumentationRegistry.getInstrumentation().targetContext.filesDir
            java.io.File(directory, "matchup-redesign.png").outputStream().use { output ->
                check(image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
            }
        }
    }

    @Test
    fun partialScoreShowsMissingScoreWithoutInventingAResult() {
        val fixture = parseGames(
            """{"events":[{"idEvent":"1","strHomeTeam":"Equipe Casa","strAwayTeam":"Equipe Visitante","strLeague":"Liga Exemplo","intHomeScore":"80"}]}"""
        )
        compose.setContent {
            JirauTheme(darkTheme = false, dynamicColor = false) {
                AppScreen(Appearance.LIGHT, loadGames = { _, _ -> fixture }) {}
            }
        }
        compose.onNodeWithText("Equipe Casa").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Equipe Visitante").assertIsDisplayed()
        compose.onNodeWithText("80").assertIsDisplayed()
        compose.onNodeWithContentDescription("Placar não informado para Equipe Visitante").assertIsDisplayed()
        compose.onNodeWithText("Status não informado").assertIsDisplayed()
        compose.onNodeWithText("0").assertDoesNotExist()
        if (InstrumentationRegistry.getArguments().getString("capturePartialScore") == "true") {
            val image = compose.onRoot().captureToImage().asAndroidBitmap()
            val directory = InstrumentationRegistry.getInstrumentation().targetContext.filesDir
            java.io.File(directory, "partial-score-light.png").outputStream().use { output ->
                check(image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
            }
        }
    }

    @Test
    fun lightLargeTextPartialScoreSupportsFavoriteAction() {
        val fixture = parseGames(
            """{"events":[{"idEvent":"1","strHomeTeam":"Equipe Casa","strAwayTeam":"Equipe Visitante","idAwayTeam":"200","strLeague":"Liga Exemplo","intHomeScore":"80"}]}"""
        )
        val favorites = mutableStateOf<Map<String, String>>(emptyMap())
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current)
            configuration.fontScale = 2f
            val density = Density(LocalDensity.current.density, fontScale = 2f)
            CompositionLocalProvider(LocalConfiguration provides configuration, LocalDensity provides density) {
                JirauTheme(darkTheme = false, dynamicColor = false) {
                    GamesScreen(favorites.value, { id, name ->
                        if (id != null) {
                            favorites.value = favorites.value + (id to name)
                        }
                    }, { _, _ -> fixture }, darkTheme = false)
                }
            }
        }
        compose.onNodeWithText("80").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Placar não informado para Equipe Visitante")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Favoritar Equipe Visitante").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Remover Equipe Visitante dos favoritos").assertIsDisplayed()
        if (InstrumentationRegistry.getArguments().getString("captureLightLargePartial") == "true") {
            val image = compose.onRoot().captureToImage().asAndroidBitmap()
            val directory = InstrumentationRegistry.getInstrumentation().targetContext.filesDir
            java.io.File(directory, "light-large-partial.png").outputStream().use { output ->
                check(image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
            }
        }
    }

    @Test
    fun partialScoreIsIdentifiedAtLargeTextSize() {
        val fixture = parseGames(
            """{"events":[{"idEvent":"1","strHomeTeam":"Equipe Casa","strAwayTeam":"Equipe Visitante","idAwayTeam":"200","strLeague":"Liga Exemplo","intHomeScore":"80"}]}"""
        )
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current)
            configuration.fontScale = 2f
            val density = Density(LocalDensity.current.density, fontScale = 2f)
            CompositionLocalProvider(LocalConfiguration provides configuration, LocalDensity provides density) {
                JirauTheme(darkTheme = true, dynamicColor = false) {
                    AppScreen(Appearance.DARK, loadGames = { _, _ -> fixture }) {}
                }
            }
        }
        compose.onNodeWithText("80").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Placar não informado para Equipe Visitante")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Favoritar Equipe Visitante").assertHasClickAction()
        compose.onNodeWithContentDescription("Favoritos").assertExists()
        compose.onNodeWithText("0").assertDoesNotExist()
        compose.onNodeWithText("Horário não informado").performScrollTo().assertIsDisplayed()
        if (InstrumentationRegistry.getArguments().getString("captureLargePartialScore") == "true") {
            val image = compose.onRoot().captureToImage().asAndroidBitmap()
            val directory = InstrumentationRegistry.getInstrumentation().targetContext.filesDir
            java.io.File(directory, "large-partial-score.png").outputStream().use { output ->
                check(image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
            }
        }
    }

    @Test
    fun gamesScreenDisplaysMissingStatus() {
        val fixture = parseGames(
            """{"events":[{"idEvent":"1","strHomeTeam":"Time Casa","strAwayTeam":"Time Visitante","strLeague":"Liga Exemplo"}]}"""
        )
        compose.setContent {
            GamesScreen(emptyMap(), { _, _ -> }, { _, _ -> fixture })
        }
        compose.onNodeWithText("Status não informado").assertIsDisplayed()
        compose.onNodeWithText("Time Casa").assertIsDisplayed()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        if (InstrumentationRegistry.getArguments().getString("captureStatus") == "true") {
            val screenshot = compose.onRoot().captureToImage().asAndroidBitmap()
            val directory = instrumentation.targetContext.filesDir
            java.io.File(directory, "status-ausente.png").outputStream().use { output ->
                check(screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
            }
        }
    }

    @Test
    fun gameWithoutTimeScoresOrTeamIdsShowsUnknownInformation() {
        val fixture = parseGames(
            """{"events":[{"idEvent":"1","strHomeTeam":"Equipe Casa","strAwayTeam":"Equipe Visitante","strLeague":"Liga Exemplo"}]}"""
        )
        compose.setContent {
            GamesScreen(emptyMap(), { _, _ -> }, { _, _ -> fixture })
        }

        compose.onNodeWithText("Equipe Casa").assertIsDisplayed()
        compose.onNodeWithText("Horário não informado").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Transmissão não consultada").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Favoritar Equipe Casa").assertDoesNotExist()
        compose.onNodeWithContentDescription("Favoritar Equipe Visitante").assertDoesNotExist()
        compose.onNodeWithText("0").assertDoesNotExist()
    }

    @Test
    fun favoriteAccessibleLabelHasClickAction() {
        val fixture = parseGames(
            """{"events":[{"idEvent":"1","strHomeTeam":"Time Casa","strAwayTeam":"Time Visitante","idHomeTeam":"100","strLeague":"Liga Exemplo"}]}"""
        )
        compose.setContent {
            GamesScreen(emptyMap(), { _, _ -> }, { _, _ -> fixture })
        }
        compose.onNodeWithContentDescription("Favoritar Time Casa").assertHasClickAction()
    }

    @Test
    fun favoriteAccessibleActionUpdatesLabel() {
        val fixture = parseGames(
            """{"events":[{"idEvent":"1","strHomeTeam":"Time Casa","strAwayTeam":"Time Visitante","idHomeTeam":"100","strLeague":"Liga Exemplo"}]}"""
        )
        val favorites = mutableStateOf<Map<String, String>>(emptyMap())
        compose.setContent {
            GamesScreen(favorites.value, { id, name ->
                if (id != null) {
                    favorites.value = if (favorites.value.containsKey(id)) {
                        favorites.value - id
                    } else {
                        favorites.value + (id to name)
                    }
                }
            }, { _, _ -> fixture })
        }
        compose.onNodeWithContentDescription("Favoritar Time Casa").performClick()
        compose.onNodeWithContentDescription("Remover Time Casa dos favoritos").assertHasClickAction()
        compose.onNodeWithContentDescription("Remover Time Casa dos favoritos").performClick()
        compose.onNodeWithContentDescription("Favoritar Time Casa").assertHasClickAction()
    }

    @Test
    fun gamesScreenAnnouncesLoading() {
        compose.setContent {
            GamesScreen(emptyMap(), { _, _ -> }, { _, _ -> kotlinx.coroutines.awaitCancellation() })
        }
        compose.onNodeWithText("Carregando jogos").assertIsDisplayed()
    }

    @Test
    fun gamesScreenShowsEmptyResults() {
        compose.setContent {
            GamesScreen(emptyMap(), { _, _ -> }, { _, _ -> emptyList() })
        }
        compose.onNodeWithText("Nenhum jogo informado para esta data.").assertIsDisplayed()
    }

    @Test
    fun largeTextLeagueFilterRemainsSelectable() {
        val selectedLeague = mutableStateOf(LeagueFilter.ALL)
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current)
            configuration.fontScale = 2f
            val density = Density(LocalDensity.current.density, fontScale = 2f)
            CompositionLocalProvider(LocalConfiguration provides configuration, LocalDensity provides density) {
                JirauTheme(darkTheme = true, dynamicColor = false) {
                    AppScreen(Appearance.DARK, loadGames = { _, league ->
                        selectedLeague.value = league
                        emptyList()
                    }) {}
                }
            }
        }
        val all = compose.onNode(hasText("Todas as ligas") and hasClickAction())
        val northAmerica = compose.onNode(hasText("Liga norte-americana") and hasClickAction())
        all.assertIsSelected()
        northAmerica.performScrollTo().assertIsDisplayed().performClick().assertIsSelected()
        compose.runOnIdle { assertEquals(LeagueFilter.NORTH_AMERICA, selectedLeague.value) }
        if (InstrumentationRegistry.getArguments().getString("captureLargeLeagues") == "true") {
            val image = compose.onRoot().captureToImage().asAndroidBitmap()
            val directory = InstrumentationRegistry.getInstrumentation().targetContext.filesDir
            java.io.File(directory, "large-leagues.png").outputStream().use { output ->
                check(image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
            }
        }
    }

    @Test
    fun leagueSelectionReloadsTheControlledGames() {
        val allGames = parseGames(
            """{"events":[{"idEvent":"1","strHomeTeam":"Equipe Geral","strAwayTeam":"Equipe Visitante","strLeague":"Liga Exemplo"}]}"""
        )
        val filteredGames = parseGames(
            """{"events":[{"idEvent":"2","strHomeTeam":"Equipe Filtrada","strAwayTeam":"Equipe Visitante","strLeague":"Liga Exemplo"}]}"""
        )
        compose.setContent {
            GamesScreen(emptyMap(), { _, _ -> }, { _, league ->
                when (league) {
                    LeagueFilter.ALL -> allGames
                    LeagueFilter.NORTH_AMERICA -> filteredGames
                }
            })
        }

        compose.onNodeWithText("Equipe Geral").assertIsDisplayed()
        compose.onNode(hasText("Todas as ligas") and hasClickAction()).assertIsDisplayed()
        compose.onNode(hasText("Liga norte-americana") and hasClickAction()).assertIsDisplayed()
        compose.onNode(hasText("Liga norte-americana") and hasClickAction()).performClick().assertIsSelected()
        compose.onNodeWithText("Equipe Filtrada").assertIsDisplayed()
        compose.onNodeWithText("Equipe Geral").assertDoesNotExist()
        compose.onNode(hasText("Todas as ligas") and hasClickAction()).performClick().assertIsSelected()
        compose.onNodeWithText("Equipe Geral").assertIsDisplayed()
        compose.onNodeWithText("Equipe Filtrada").assertDoesNotExist()
    }

    @Test
    fun changingLeagueCancelsThePreviousLoad() {
        val previousLoadCancelled = mutableStateOf(false)
        val filteredGames = parseGames(
            """{"events":[{"idEvent":"2","strHomeTeam":"Equipe Filtrada","strAwayTeam":"Equipe Visitante","strLeague":"Liga Exemplo"}]}"""
        )
        compose.setContent {
            GamesScreen(emptyMap(), { _, _ -> }, { _, league ->
                when (league) {
                    LeagueFilter.ALL -> {
                        try {
                            kotlinx.coroutines.awaitCancellation()
                        } finally {
                            previousLoadCancelled.value = true
                        }
                    }
                    LeagueFilter.NORTH_AMERICA -> filteredGames
                }
            })
        }

        compose.onNodeWithText("Carregando jogos").assertIsDisplayed()
        compose.onNode(hasText("Liga norte-americana") and hasClickAction()).performClick()
        compose.onNodeWithText("Equipe Filtrada").assertIsDisplayed()
        compose.runOnIdle { assertTrue(previousLoadCancelled.value) }
    }

    @Test
    fun leavingGamesCancelsThePendingLoad() {
        val showGames = mutableStateOf(true)
        val loadCancelled = mutableStateOf(false)
        compose.setContent {
            if (showGames.value) {
                GamesScreen(emptyMap(), { _, _ -> }, { _, _ ->
                    try {
                        kotlinx.coroutines.awaitCancellation()
                    } finally {
                        loadCancelled.value = true
                    }
                })
            }
        }

        compose.onNodeWithText("Carregando jogos").assertIsDisplayed()
        compose.runOnIdle { showGames.value = false }
        compose.waitForIdle()
        compose.runOnIdle { assertTrue(loadCancelled.value) }
        compose.onNodeWithText("Carregando jogos").assertDoesNotExist()
    }

    @Test
    fun navigatingToFavoritesCancelsThePendingGamesLoad() {
        val loadCancelled = mutableStateOf(false)
        compose.setContent {
            AppScreen(Appearance.SYSTEM, loadGames = { _, _ ->
                try {
                    kotlinx.coroutines.awaitCancellation()
                } finally {
                    loadCancelled.value = true
                }
            }) {}
        }

        compose.onNodeWithText("Carregando jogos").assertIsDisplayed()
        compose.onNode(hasText("Favoritos") and hasClickAction()).performClick().assertIsSelected()
        compose.runOnIdle { assertTrue(loadCancelled.value) }
        compose.onNodeWithText("Carregando jogos").assertDoesNotExist()
    }

    @Test
    fun returningToGamesReloadsTheControlledList() {
        var loadCount = 0
        val games = parseGames(
            """{"events":[{"idEvent":"1","strHomeTeam":"Equipe Casa","strAwayTeam":"Equipe Visitante","strLeague":"Liga Exemplo"}]}"""
        )
        compose.setContent {
            AppScreen(Appearance.SYSTEM, loadGames = { _, _ ->
                loadCount += 1
                games
            }) {}
        }

        compose.onNodeWithText("Equipe Casa").assertIsDisplayed()
        compose.onNode(hasText("Favoritos") and hasClickAction()).performClick().assertIsSelected()
        compose.onNodeWithText("Equipe Casa").assertDoesNotExist()
        compose.onNode(hasText("Jogos") and hasClickAction()).performClick().assertIsSelected()
        compose.onNodeWithText("Equipe Casa").assertIsDisplayed()
        compose.runOnIdle { assertEquals(2, loadCount) }
    }

    @Test
    fun favoriteRemainsSelectedWhenChangingLeague() {
        val favorites = mutableStateOf<Map<String, String>>(emptyMap())
        val games = parseGames(
            """{"events":[{"idEvent":"1","strHomeTeam":"Equipe Casa","strAwayTeam":"Equipe Visitante","idHomeTeam":"100","strLeague":"Liga Exemplo"}]}"""
        )
        compose.setContent {
            GamesScreen(favorites.value, { id, name ->
                if (id != null) {
                    favorites.value = if (favorites.value.containsKey(id)) {
                        favorites.value - id
                    } else {
                        favorites.value + (id to name)
                    }
                }
            }, { _, _ -> games })
        }

        compose.onNodeWithContentDescription("Favoritar Equipe Casa").performClick()
        compose.onNodeWithContentDescription("Remover Equipe Casa dos favoritos").assertHasClickAction()
        compose.onNode(hasText("Liga norte-americana") and hasClickAction()).performClick()
        compose.onNodeWithContentDescription("Remover Equipe Casa dos favoritos").assertHasClickAction()
        compose.onNode(hasText("Todas as ligas") and hasClickAction()).performClick()
        compose.onNodeWithContentDescription("Remover Equipe Casa dos favoritos").assertHasClickAction()
        compose.runOnIdle { assertEquals("Equipe Casa", favorites.value["100"]) }
    }

    @Test
    fun datePickerUsesTheCurrentAppearanceAccent() {
        val darkTheme = mutableStateOf(false)
        compose.setContent {
            GamesScreen(emptyMap(), { _, _ -> }, { _, _ -> emptyList() }, darkTheme.value)
        }

        fun assertDialogAccent(expectedColor: Int) {
            compose.onNode(hasText("Data (UTC):", substring = true) and hasClickAction()).performClick()
            onView(isAssignableFrom(DatePicker::class.java)).inRoot(isDialog()).check { view, error ->
                if (error != null) {
                    throw error
                }
                val color = TypedValue()
                assertTrue(view.context.theme.resolveAttribute(android.R.attr.colorAccent, color, true))
                assertEquals(expectedColor, color.data)
            }
            pressBack()
        }

        assertDialogAccent(android.graphics.Color.rgb(169, 63, 0))
        compose.runOnIdle { darkTheme.value = true }
        assertDialogAccent(android.graphics.Color.rgb(255, 122, 26))
    }

    @Test
    fun settingsAppearanceControlsTheDatePicker() {
        val appearance = mutableStateOf(Appearance.LIGHT)
        compose.setContent {
            AppScreen(appearance.value, loadGames = { _, _ -> emptyList() }) { selected ->
                appearance.value = selected
            }
        }

        compose.onNode(hasText("Configurações") and hasClickAction()).performClick()
        compose.onNode(hasText("Escuro") and hasClickAction()).performClick().assertIsSelected()
        compose.onNode(hasText("Jogos") and hasClickAction()).performClick()
        compose.onNode(hasText("Data (UTC):", substring = true) and hasClickAction()).performClick()
        onView(isAssignableFrom(DatePicker::class.java)).inRoot(isDialog()).check { view, error ->
            if (error != null) {
                throw error
            }
            val color = TypedValue()
            assertTrue(view.context.theme.resolveAttribute(android.R.attr.colorAccent, color, true))
            assertEquals(android.graphics.Color.rgb(255, 122, 26), color.data)
        }
        pressBack()
    }

    @Test
    fun lightAppearanceOverridesDarkSystemForTheDatePicker() {
        val appearance = mutableStateOf(Appearance.SYSTEM)
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current)
            configuration.uiMode =
                (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                AppScreen(appearance.value, loadGames = { _, _ -> emptyList() }) { selected ->
                    appearance.value = selected
                }
            }
        }

        compose.onNode(hasText("Configurações") and hasClickAction()).performClick()
        compose.onNode(hasText("Claro") and hasClickAction()).performClick()
        compose.onNode(hasText("Claro") and hasClickAction()).assertIsSelected()
        compose.onNode(hasText("Jogos") and hasClickAction()).performClick()
        compose.onNode(hasText("Data (UTC):", substring = true) and hasClickAction()).performClick()
        onView(isAssignableFrom(DatePicker::class.java)).inRoot(isDialog()).check { view, error ->
            if (error != null) {
                throw error
            }
            val color = TypedValue()
            assertTrue(view.context.theme.resolveAttribute(android.R.attr.colorAccent, color, true))
            assertEquals(android.graphics.Color.rgb(169, 63, 0), color.data)
        }
        pressBack()
    }

    @Test
    fun systemAppearanceUpdatesTheDatePicker() {
        val darkSystem = mutableStateOf(true)
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current)
            val nightMode = if (darkSystem.value) {
                Configuration.UI_MODE_NIGHT_YES
            } else {
                Configuration.UI_MODE_NIGHT_NO
            }
            configuration.uiMode = (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                AppScreen(Appearance.SYSTEM, loadGames = { _, _ -> emptyList() }) {}
            }
        }

        fun assertPickerAccent(expectedColor: Int) {
            compose.onNode(hasText("Data (UTC):", substring = true) and hasClickAction()).performClick()
            onView(isAssignableFrom(DatePicker::class.java)).inRoot(isDialog()).check { view, error ->
                if (error != null) {
                    throw error
                }
                val color = TypedValue()
                assertTrue(view.context.theme.resolveAttribute(android.R.attr.colorAccent, color, true))
                assertEquals(expectedColor, color.data)
            }
            pressBack()
        }

        assertPickerAccent(android.graphics.Color.rgb(255, 122, 26))
        compose.runOnIdle { darkSystem.value = false }
        assertPickerAccent(android.graphics.Color.rgb(169, 63, 0))
    }

    @Test
    fun datePickerConfirmsAndCancelsDateChanges() {
        val loadedDate = mutableStateOf<java.time.LocalDate?>(null)
        compose.setContent {
            GamesScreen(emptyMap(), { _, _ -> }, { date, _ ->
                loadedDate.value = date
                emptyList()
            }, darkTheme = true)
        }

        fun chooseDate(day: Int, buttonId: Int) {
            compose.onNode(hasText("Data (UTC):", substring = true) and hasClickAction()).performClick()
            onView(isAssignableFrom(DatePicker::class.java)).inRoot(isDialog()).check { view, error ->
                if (error != null) {
                    throw error
                }
                check(view is DatePicker)
                view.updateDate(2035, 1, day)
            }
            onView(withId(buttonId)).inRoot(isDialog()).perform(click())
        }

        chooseDate(3, android.R.id.button1)
        compose.onNodeWithText("Data (UTC): 2035-02-03").assertIsDisplayed()
        compose.runOnIdle { assertEquals(java.time.LocalDate.of(2035, 2, 3), loadedDate.value) }

        chooseDate(4, android.R.id.button2)
        compose.onNodeWithText("Data (UTC): 2035-02-03").assertIsDisplayed()
        compose.runOnIdle { assertEquals(java.time.LocalDate.of(2035, 2, 3), loadedDate.value) }
    }

    @Test
    fun changingDateKeepsTheSelectedLeague() {
        val lastQuery = mutableStateOf<Pair<java.time.LocalDate, LeagueFilter>?>(null)
        compose.setContent {
            GamesScreen(emptyMap(), { _, _ -> }, { date, league ->
                lastQuery.value = date to league
                emptyList()
            })
        }

        compose.onNode(hasText("Liga norte-americana") and hasClickAction()).performClick()
        compose.onNode(hasText("Liga norte-americana") and hasClickAction()).assertIsSelected()
        compose.onNode(hasText("Data (UTC):", substring = true) and hasClickAction()).performClick()
        onView(isAssignableFrom(DatePicker::class.java)).inRoot(isDialog()).check { view, error ->
            if (error != null) {
                throw error
            }
            check(view is DatePicker)
            view.updateDate(2035, 1, 3)
        }
        onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())

        compose.onNodeWithText("Data (UTC): 2035-02-03").assertIsDisplayed()
        compose.onNode(hasText("Liga norte-americana") and hasClickAction()).assertIsSelected()
        compose.runOnIdle {
            assertEquals(java.time.LocalDate.of(2035, 2, 3) to LeagueFilter.NORTH_AMERICA, lastQuery.value)
        }
    }

    @Test
    fun gamesScreenRetriesAfterFailure() {
        var attempts = 0
        compose.setContent {
            JirauTheme(darkTheme = false, dynamicColor = false) {
                GamesScreen(emptyMap(), { _, _ -> }, { _, _ ->
                    attempts += 1
                    if (attempts == 1) {
                        throw IllegalStateException("fixture failure")
                    }
                    emptyList()
                })
            }
        }
        compose.onNodeWithText("Não foi possível carregar os jogos. Verifique a conexão e tente novamente.")
            .assertIsDisplayed()
        fun captureState(name: String) {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            if (InstrumentationRegistry.getArguments().getString("captureRetry") == "true") {
                val image = compose.onRoot().captureToImage().asAndroidBitmap()
                java.io.File(instrumentation.targetContext.filesDir, name).outputStream().use { output ->
                    check(image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
                }
            }
        }
        captureState("retry-error.png")
        compose.onNodeWithText("Tentar novamente").performClick()
        compose.onNodeWithText("Nenhum jogo informado para esta data.").assertIsDisplayed()
        captureState("retry-empty.png")
        assertEquals(2, attempts)
    }

    @Test
    fun darkLargeTextErrorCanBeRetried() {
        var attempts = 0
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current)
            configuration.fontScale = 2f
            val density = Density(LocalDensity.current.density, fontScale = 2f)
            CompositionLocalProvider(LocalConfiguration provides configuration, LocalDensity provides density) {
                JirauTheme(darkTheme = true, dynamicColor = false) {
                    AppScreen(Appearance.DARK, loadGames = { _, _ ->
                        attempts += 1
                        if (attempts == 1) {
                            throw IllegalStateException("fixture failure")
                        }
                        emptyList()
                    }) {}
                }
            }
        }

        val retry = compose.onNodeWithText("Tentar novamente")
        retry.performScrollTo().assertIsDisplayed()
        if (InstrumentationRegistry.getArguments().getString("captureLargeError") == "true") {
            val image = compose.onRoot().captureToImage().asAndroidBitmap()
            val directory = InstrumentationRegistry.getInstrumentation().targetContext.filesDir
            java.io.File(directory, "large-dark-error.png").outputStream().use { output ->
                check(image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
            }
        }
        retry.performClick()
        compose.onNodeWithText("Nenhum jogo informado para esta data.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Tente outra data ou liga.").performScrollTo().assertIsDisplayed()
        if (InstrumentationRegistry.getArguments().getString("captureLargeError") == "true") {
            val image = compose.onRoot().captureToImage().asAndroidBitmap()
            val directory = InstrumentationRegistry.getInstrumentation().targetContext.filesDir
            java.io.File(directory, "large-dark-empty.png").outputStream().use { output ->
                check(image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
            }
        }
        assertEquals(2, attempts)
    }

    @Test
    fun darkLargeTextLoadingIsDisplayed() {
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current)
            configuration.fontScale = 2f
            val density = Density(LocalDensity.current.density, fontScale = 2f)
            CompositionLocalProvider(LocalConfiguration provides configuration, LocalDensity provides density) {
                JirauTheme(darkTheme = true, dynamicColor = false) {
                    AppScreen(Appearance.DARK, loadGames = { _, _ ->
                        kotlinx.coroutines.awaitCancellation()
                    }) {}
                }
            }
        }

        compose.onNodeWithText("Carregando jogos").performScrollTo().assertIsDisplayed()
        if (InstrumentationRegistry.getArguments().getString("captureLargeLoading") == "true") {
            val image = compose.onRoot().captureToImage().asAndroidBitmap()
            val directory = InstrumentationRegistry.getInstrumentation().targetContext.filesDir
            java.io.File(directory, "large-dark-loading.png").outputStream().use { output ->
                check(image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
            }
        }
    }

    @Test
    fun darkLargeTextFinishedGameDisplaysScores() {
        val games = parseGames(
            """{"events":[{"idEvent":"1","strHomeTeam":"Equipe Casa","strAwayTeam":"Equipe Visitante","strLeague":"Liga Exemplo","strStatus":"FT","intHomeScore":"80","intAwayScore":"75"}]}"""
        )
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current)
            configuration.fontScale = 2f
            val density = Density(LocalDensity.current.density, fontScale = 2f)
            CompositionLocalProvider(LocalConfiguration provides configuration, LocalDensity provides density) {
                JirauTheme(darkTheme = true, dynamicColor = false) {
                    AppScreen(Appearance.DARK, loadGames = { _, _ -> games }) {}
                }
            }
        }

        compose.onNodeWithText("Encerrado").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("80").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("75").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Transmissão não consultada").performScrollTo().assertIsDisplayed()
        if (InstrumentationRegistry.getArguments().getString("captureFinishedGame") == "true") {
            val image = compose.onRoot().captureToImage().asAndroidBitmap()
            val directory = InstrumentationRegistry.getInstrumentation().targetContext.filesDir
            java.io.File(directory, "large-dark-finished.png").outputStream().use { output ->
                check(image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
            }
        }
    }

    @Test
    fun longScoresRemainReadableAtLargeTextSize() {
        val games = parseGames(
            """{"events":[{"idEvent":"1","strHomeTeam":"Equipe Casa","strAwayTeam":"Equipe Visitante","idHomeTeam":"100","idAwayTeam":"200","strLeague":"Liga Exemplo","intHomeScore":"123456789","intAwayScore":"987654321"}]}"""
        )
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current)
            configuration.fontScale = 2f
            val density = Density(LocalDensity.current.density, fontScale = 2f)
            CompositionLocalProvider(LocalConfiguration provides configuration, LocalDensity provides density) {
                JirauTheme(darkTheme = true, dynamicColor = false) {
                    AppScreen(Appearance.DARK, loadGames = { _, _ -> games }) {}
                }
            }
        }
        compose.onNodeWithText("Equipe Casa").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("123456789").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Favoritar Equipe Casa").performScrollTo().assertHasClickAction()
        compose.onNodeWithText("Equipe Visitante").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("987654321").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Favoritar Equipe Visitante").performScrollTo().assertHasClickAction()
        compose.onNodeWithText("Transmissão não consultada").performScrollTo().assertIsDisplayed()
        if (InstrumentationRegistry.getArguments().getString("captureLargeLongScores") == "true") {
            val image = compose.onRoot().captureToImage().asAndroidBitmap()
            val directory = InstrumentationRegistry.getInstrumentation().targetContext.filesDir
            java.io.File(directory, "large-long-scores.png").outputStream().use { output ->
                check(image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
            }
        }
    }

    @Test
    fun longScoresUseVerticalLayoutWithoutLosingTeams() {
        val games = parseGames(
            """{"events":[{"idEvent":"1","strHomeTeam":"Equipe Casa","strAwayTeam":"Equipe Visitante","idHomeTeam":"100","idAwayTeam":"200","strLeague":"Liga Exemplo","intHomeScore":"123456789","intAwayScore":"987654321"}]}"""
        )
        compose.setContent {
            JirauTheme(darkTheme = true, dynamicColor = false) {
                AppScreen(Appearance.DARK, loadGames = { _, _ -> games }) {}
            }
        }
        compose.onNodeWithText("Equipe Casa").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Equipe Visitante").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("123456789").assertIsDisplayed()
        compose.onNodeWithText("987654321").assertIsDisplayed()
        if (InstrumentationRegistry.getArguments().getString("captureLongScores") == "true") {
            val image = compose.onRoot().captureToImage().asAndroidBitmap()
            val directory = InstrumentationRegistry.getInstrumentation().targetContext.filesDir
            java.io.File(directory, "long-scores.png").outputStream().use { output ->
                check(image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
            }
        }
        compose.onNodeWithText("×").assertDoesNotExist()
        compose.onNodeWithContentDescription("Favoritar Equipe Casa").assertHasClickAction()
        compose.onNodeWithContentDescription("Favoritar Equipe Visitante").assertHasClickAction()
    }

    @Test
    fun longTeamNamesUseFullWidthAtNormalTextSize() {
        val home = "Associação Esportiva de Basquete da Cidade de Exemplo"
        val away = "Clube Atlântico de Basquete da Região Metropolitana"
        val games = parseGames(
            """{"events":[{"idEvent":"1","strHomeTeam":"$home","strAwayTeam":"$away","idHomeTeam":"100","idAwayTeam":"200","strLeague":"Liga Exemplo","strStatus":"FT","intHomeScore":"80","intAwayScore":"75"}]}"""
        )
        compose.setContent {
            JirauTheme(darkTheme = true, dynamicColor = false) {
                AppScreen(Appearance.DARK, loadGames = { _, _ -> games }) {}
            }
        }
        compose.onNodeWithText(home).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(away).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("80").assertIsDisplayed()
        compose.onNodeWithText("75").assertIsDisplayed()
        compose.onNodeWithText("×").assertDoesNotExist()
        compose.onNodeWithContentDescription("Favoritar $home").assertHasClickAction()
        compose.onNodeWithContentDescription("Favoritar $away").assertHasClickAction()
        if (InstrumentationRegistry.getArguments().getString("captureNormalLongNames") == "true") {
            val image = compose.onRoot().captureToImage().asAndroidBitmap()
            val directory = InstrumentationRegistry.getInstrumentation().targetContext.filesDir
            java.io.File(directory, "normal-long-names.png").outputStream().use { output ->
                check(image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
            }
        }
    }

    @Test
    fun longTeamNamesRemainAccessibleAtLargeTextSize() {
        val home = "Associação Esportiva de Basquete da Cidade de Exemplo"
        val away = "Clube Atlântico de Basquete da Região Metropolitana"
        val games = parseGames(
            """{"events":[{"idEvent":"1","strHomeTeam":"$home","strAwayTeam":"$away","idHomeTeam":"100","idAwayTeam":"200","strLeague":"Liga Exemplo","strStatus":"FT","intHomeScore":"80","intAwayScore":"75"}]}"""
        )
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current)
            configuration.fontScale = 2f
            val density = Density(LocalDensity.current.density, fontScale = 2f)
            CompositionLocalProvider(LocalConfiguration provides configuration, LocalDensity provides density) {
                JirauTheme(darkTheme = true, dynamicColor = false) {
                    AppScreen(Appearance.DARK, loadGames = { _, _ -> games }) {}
                }
            }
        }
        compose.onNodeWithText(home).performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Favoritar $home").assertHasClickAction()
        compose.onNodeWithText(away).performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Favoritar $away").assertHasClickAction()
        compose.onNodeWithText("80").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("75").performScrollTo().assertIsDisplayed()
        if (InstrumentationRegistry.getArguments().getString("captureLongNames") == "true") {
            val image = compose.onRoot().captureToImage().asAndroidBitmap()
            val directory = InstrumentationRegistry.getInstrumentation().targetContext.filesDir
            java.io.File(directory, "long-team-names.png").outputStream().use { output ->
                check(image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
            }
        }
    }

    @Test
    fun retryKeepsSelectedDateAndLeagueAfterFailure() {
        val selectedDate = java.time.LocalDate.of(2035, 2, 3)
        val requests = mutableListOf<Pair<java.time.LocalDate, LeagueFilter>>()
        val games = parseGames(
            """{"events":[{"idEvent":"2","strHomeTeam":"Equipe Recuperada","strAwayTeam":"Equipe Visitante","strLeague":"Liga Exemplo"}]}"""
        )
        compose.setContent {
            GamesScreen(emptyMap(), { _, _ -> }, { date, league ->
                requests.add(date to league)
                if (date == selectedDate && league == LeagueFilter.NORTH_AMERICA) {
                    if (requests.count { it == selectedDate to LeagueFilter.NORTH_AMERICA } == 1) {
                        throw IllegalStateException("controlled failure")
                    }
                    games
                } else {
                    emptyList()
                }
            })
        }

        compose.onNodeWithText("Nenhum jogo informado para esta data.").assertIsDisplayed()
        compose.onNode(hasText("Liga norte-americana") and hasClickAction()).performClick()
        compose.onNode(hasText("Data (UTC):", substring = true) and hasClickAction()).performClick()
        onView(isAssignableFrom(DatePicker::class.java)).inRoot(isDialog()).check { view, error ->
            if (error != null) {
                throw error
            }
            check(view is DatePicker)
            view.updateDate(2035, 1, 3)
        }
        onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
        compose.onNodeWithText("Não foi possível carregar os jogos. Verifique a conexão e tente novamente.")
            .assertIsDisplayed()
        compose.onNodeWithText("Tentar novamente").performClick()
        compose.onNodeWithText("Equipe Recuperada").assertIsDisplayed()
        compose.onNodeWithText("Data (UTC): 2035-02-03").assertIsDisplayed()
        compose.onNode(hasText("Liga norte-americana") and hasClickAction()).assertIsSelected()
        compose.runOnIdle {
            assertEquals(2, requests.count { it == selectedDate to LeagueFilter.NORTH_AMERICA })
            assertEquals(selectedDate to LeagueFilter.NORTH_AMERICA, requests.last())
        }
    }

    @Test
    fun retryKeepsTheSelectedLeagueAfterFailure() {
        var filteredAttempts = 0
        val filteredGames = parseGames(
            """{"events":[{"idEvent":"2","strHomeTeam":"Equipe Filtrada","strAwayTeam":"Equipe Visitante","strLeague":"Liga Exemplo"}]}"""
        )
        compose.setContent {
            GamesScreen(emptyMap(), { _, _ -> }, { _, league ->
                when (league) {
                    LeagueFilter.ALL -> emptyList()
                    LeagueFilter.NORTH_AMERICA -> {
                        filteredAttempts += 1
                        if (filteredAttempts == 1) {
                            throw IllegalStateException("controlled failure")
                        }
                        filteredGames
                    }
                }
            })
        }

        compose.onNodeWithText("Nenhum jogo informado para esta data.").assertIsDisplayed()
        val filteredLeague = compose.onNode(hasText("Liga norte-americana") and hasClickAction())
        filteredLeague.performClick().assertIsSelected()
        compose.onNodeWithText("Não foi possível carregar os jogos. Verifique a conexão e tente novamente.")
            .assertIsDisplayed()
        compose.onNodeWithText("Tentar novamente").performClick()
        filteredLeague.assertIsSelected()
        compose.onNodeWithText("Equipe Filtrada").assertIsDisplayed()
        assertEquals(2, filteredAttempts)
    }

    @Test
    fun brazilFixtureDoesNotInventBroadcastOrFavorites() {
        val fixture = parseGames(
            """{"events":[{"idEvent":"fixture-br-1","strHomeTeam":"Equipe Brasil A","strAwayTeam":"Equipe Brasil B","strLeague":"Liga brasileira (teste)","strTime":"22:30:00"}]}"""
        )
        compose.setContent {
            GamesScreen(emptyMap(), { _, _ -> }, { _, _ -> fixture })
        }
        compose.onNodeWithText("Equipe Brasil A").assertIsDisplayed()
        compose.onNodeWithText("Liga brasileira (teste)").assertIsDisplayed()
        compose.onNodeWithText("Transmissão não consultada").assertIsDisplayed()
        compose.onNodeWithContentDescription("Favoritar Equipe Brasil A").assertDoesNotExist()
    }

    @Test
    fun favoritesPersistAndCanBeRemoved() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val key = "test-favorites-${java.util.UUID.randomUUID()}"
        val preferences = context.getSharedPreferences(key, Context.MODE_PRIVATE)
        try {
            val favorites = Favorites(preferences)
            favorites.toggle("123", "Time exemplo")
            assertEquals("Time exemplo", Favorites(preferences).load()["123"])
            favorites.toggle("123", "Time exemplo")
            assertEquals(emptyMap<String, String>(), Favorites(preferences).load())
            favorites.toggle(null, "Sem identificação")
            assertEquals(emptyMap<String, String>(), Favorites(preferences).load())
        } finally {
            preferences.edit().clear().commit()
            context.deleteSharedPreferences(key)
        }
    }
}
