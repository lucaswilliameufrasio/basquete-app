package com.example.basqueteapp

import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal data class Game(
    val id: String,
    val home: String,
    val away: String,
    val homeTeamId: String?,
    val awayTeamId: String?,
    val league: String,
    val time: String?,
    val homeScore: String?,
    val awayScore: String?,
    val status: String?
)

internal fun displayGameStatus(status: String?): String {
    return when (status?.uppercase(java.util.Locale.ROOT)) {
        "FT" -> "Encerrado"
        "NS" -> "Ainda não iniciado"
        else -> "Status não informado"
    }
}

internal fun displayGameTime(time: String?): String {
    if (time.isNullOrBlank()) {
        return "Horário não informado"
    }
    return try {
        "${LocalTime.parse(time.trim()).format(DateTimeFormatter.ofPattern("HH:mm"))} UTC"
    } catch (exception: DateTimeParseException) {
        "Horário não informado"
    }
}

internal enum class LeagueFilter(val label: String, val leagueId: String?) {
    ALL("Todas as ligas", null),
    NORTH_AMERICA("Liga norte-americana", "4387")
}

internal fun parseGames(body: String): List<Game> {
    val root = JSONObject(body)
    if (!root.has("events")) {
        throw IllegalArgumentException("Missing events")
    }
    if (root.isNull("events")) {
        return emptyList()
    }
    val events = root.getJSONArray("events")
    return buildList {
        for (index in 0 until events.length()) {
            val event = events.getJSONObject(index)
            fun optional(key: String): String? {
                if (!event.has(key) || event.isNull(key)) {
                    return null
                }
                return event.getString(key).takeIf { it.isNotBlank() }
            }
            val league = event.getString("strLeague")
            add(
                Game(
                    id = event.getString("idEvent"),
                    home = event.getString("strHomeTeam"),
                    away = event.getString("strAwayTeam"),
                    homeTeamId = optional("idHomeTeam"),
                    awayTeamId = optional("idAwayTeam"),
                    league = if (league.equals("NBA", ignoreCase = true)) "Liga norte-americana" else league,
                    time = optional("strTime"),
                    homeScore = optional("intHomeScore"),
                    awayScore = optional("intAwayScore"),
                    status = optional("strStatus")
                )
            )
        }
    }
}

internal class GameFeed {
    suspend fun games(date: LocalDate, league: LeagueFilter): List<Game> = withContext(Dispatchers.IO) {
        val url = gamesUrl(date, league)
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Games service unavailable")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            parseGames(body)
        } finally {
            connection.disconnect()
        }
    }
}

internal fun gamesUrl(date: LocalDate, league: LeagueFilter): URL {
    val filter = league.leagueId?.let { "l=$it" } ?: "s=Basketball"
    return URL("https://www.thesportsdb.com/api/v1/json/123/eventsday.php?d=$date&$filter")
}
