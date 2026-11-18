package com.example.basqueteapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.basqueteapp.uniffi.brazilFixtureGames
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class BrazilAdapterJourneyTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun displaysGamesFromExperimentalRustAdapter() {
        val fixture = brazilFixtureGames().map { event ->
            val utcTime = Instant.parse(event.startsAtUtc).atOffset(ZoneOffset.UTC).toLocalTime()
            Game(
                id = event.id,
                home = event.home,
                away = event.away,
                homeTeamId = null,
                awayTeamId = null,
                league = event.league,
                time = utcTime.toString(),
                homeScore = null,
                awayScore = null,
                status = null
            )
        }
        compose.setContent {
            GamesScreen(emptyMap(), { _, _ -> }, { _, _ -> fixture })
        }
        compose.onNodeWithText("Equipe Brasil A").assertIsDisplayed()
        compose.onNodeWithText("Liga brasileira (teste)").assertIsDisplayed()
        compose.onNodeWithText("Transmissão não consultada").assertIsDisplayed()
        compose.onNodeWithContentDescription("Favoritar Equipe Brasil A").assertDoesNotExist()
    }
}
