package com.example.basqueteapp

import android.os.Bundle
import android.app.DatePickerDialog
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import com.example.basqueteapp.ui.theme.JirauTheme
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.CancellationException

internal enum class Appearance(val label: String) {
    SYSTEM("Sistema"), LIGHT("Claro"), DARK("Escuro")
}

private enum class Destination(val label: String) {
    GAMES("Jogos"), FAVORITES("Favoritos"), SETTINGS("Configurações")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val preferences = getSharedPreferences("appearance", MODE_PRIVATE)
        setContent {
            var appearance by remember {
                mutableStateOf(
                    Appearance.entries.find { it.name == preferences.getString("theme", null) }
                        ?: Appearance.SYSTEM
                )
            }
            val darkTheme = when (appearance) {
                Appearance.SYSTEM -> isSystemInDarkTheme()
                Appearance.LIGHT -> false
                Appearance.DARK -> true
            }
            SideEffect {
                val style = if (darkTheme) {
                    SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            JirauTheme(darkTheme = darkTheme, dynamicColor = false) {
                AppScreen(appearance) { selected ->
                    appearance = selected
                    preferences.edit().putString("theme", selected.name).apply()
                }
            }
        }
    }
}

@Composable
internal fun AppScreen(
    appearance: Appearance,
    loadGames: suspend (LocalDate, LeagueFilter) -> List<Game> = { date, league ->
        GameFeed().games(date, league)
    },
    onAppearanceChange: (Appearance) -> Unit
) {
    var destination by rememberSaveable { mutableStateOf(Destination.GAMES) }
    val context = LocalContext.current
    val compactNavigationLabels = LocalConfiguration.current.fontScale >= 1.1f
    val favorites = remember { Favorites(context.getSharedPreferences("favoriteTeams", Context.MODE_PRIVATE)) }
    var favoriteTeams by remember { mutableStateOf(favorites.load()) }
    val toggleFavorite: (String?, String) -> Unit = { id, name ->
        favoriteTeams = favorites.toggle(id, name)
    }
    Scaffold(
        bottomBar = {
            val navigationInset = with(LocalDensity.current) {
                WindowInsets.navigationBars.getBottom(this).toDp()
            }
            val navigationModifier = if (LocalConfiguration.current.fontScale < 1.1f) {
                Modifier.height(64.dp + navigationInset)
            } else {
                Modifier
            }
            NavigationBar(
                modifier = navigationModifier,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                for (item in Destination.entries) {
                    NavigationBarItem(
                        selected = destination == item,
                        onClick = { destination = item },
                        icon = {
                            val icon = when (item) {
                                Destination.GAMES -> R.drawable.nav_games
                                Destination.FAVORITES -> R.drawable.nav_favorites
                                Destination.SETTINGS -> R.drawable.nav_settings
                            }
                            Icon(painter = painterResource(icon), contentDescription = null)
                        },
                        label = {
                            if (compactNavigationLabels && item == Destination.SETTINGS) {
                                Text("Ajustes", modifier = Modifier.semantics { contentDescription = item.label })
                            } else if (compactNavigationLabels && item == Destination.FAVORITES) {
                                Text("Salvos", modifier = Modifier.semantics { contentDescription = item.label })
                            } else {
                                Text(item.label)
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primary,
                            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (destination != Destination.GAMES) {
                Text(destination.label, style = MaterialTheme.typography.headlineMedium)
            }
            when (destination) {
                Destination.GAMES -> {
                    val darkTheme = when (appearance) {
                        Appearance.SYSTEM -> isSystemInDarkTheme()
                        Appearance.LIGHT -> false
                        Appearance.DARK -> true
                    }
                    GamesScreen(favoriteTeams, toggleFavorite, loadGames, darkTheme, showHeader = true)
                }
                Destination.FAVORITES -> {
                    if (favoriteTeams.isEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Nenhum favorito", style = MaterialTheme.typography.titleLarge)
                                Text("Marque um time na lista de jogos para encontrá-lo aqui.")
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "Times salvos",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            for ((id, name) in favoriteTeams.toSortedMap()) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                            .compositeOver(MaterialTheme.colorScheme.surfaceContainer)
                                    )
                                ) {
                                    FavoriteButton(id, name, favoriteTeams, toggleFavorite)
                                }
                            }
                        }
                    }
                }
                Destination.SETTINGS -> {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("Aparência", style = MaterialTheme.typography.titleMedium)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                        ) {
                            Column(modifier = Modifier.selectableGroup().padding(horizontal = 16.dp, vertical = 4.dp)) {
                                for (option in Appearance.entries) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(
                                            selected = appearance == option,
                                            role = Role.RadioButton,
                                            onClick = { onAppearanceChange(option) }
                                        ),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(option.label, style = MaterialTheme.typography.titleSmall)
                                            if (option == Appearance.SYSTEM) {
                                                Text(
                                                    "Acompanha o dispositivo",
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                        }
                                        RadioButton(selected = appearance == option, onClick = null)
                                    }
                                }
                            }
                        }
                        Text("Dados", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Jogos: TheSportsDB (acesso de desenvolvimento limitado). Transmissões ainda não estão conectadas.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

private sealed interface GamesState {
    data object Loading : GamesState
    data class Loaded(val games: List<Game>) : GamesState
    data object Failed : GamesState
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun GamesScreen(
    favoriteTeams: Map<String, String>,
    onToggleFavorite: (String?, String) -> Unit,
    loadGames: suspend (LocalDate, LeagueFilter) -> List<Game> = { date, league ->
        GameFeed().games(date, league)
    },
    darkTheme: Boolean = isSystemInDarkTheme(),
    showHeader: Boolean = false
) {
    val context = LocalContext.current
    var dateText by rememberSaveable { mutableStateOf(LocalDate.now(ZoneOffset.UTC).toString()) }
    var retry by remember { mutableStateOf(0) }
    var league by rememberSaveable { mutableStateOf(LeagueFilter.ALL) }
    var state by remember { mutableStateOf<GamesState>(GamesState.Loading) }
    val date = LocalDate.parse(dateText)
    LaunchedEffect(dateText, league, retry) {
        state = GamesState.Loading
        try {
            state = GamesState.Loaded(loadGames(date, league))
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            state = GamesState.Failed
        }
    }
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val dateButton: @Composable () -> Unit = {
        OutlinedButton(onClick = {
            DatePickerDialog(
                context,
                if (darkTheme) R.style.DatePickerDark else R.style.DatePickerLight,
                { _, year, month, day -> dateText = LocalDate.of(year, month + 1, day).toString() },
                date.year,
                date.monthValue - 1,
                date.dayOfMonth
            ).show()
        }) {
            Text("Data (UTC): $dateText")
        }
        }
        if (showHeader) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                if (maxWidth >= 280.dp && LocalConfiguration.current.fontScale < 1.1f) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Jogos", style = MaterialTheme.typography.headlineMedium)
                        dateButton()
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Jogos", style = MaterialTheme.typography.headlineMedium)
                        dateButton()
                    }
                }
            }
        } else {
            dateButton()
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (option in LeagueFilter.entries) {
                LeagueChip(option, league == option) {
                    league = option
                }
            }
        }
        when (val current = state) {
            GamesState.Loading -> {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Text("Carregando jogos", style = MaterialTheme.typography.bodyMedium)
                }
            }
            GamesState.Failed -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Não foi possível carregar os jogos. Verifique a conexão e tente novamente.",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Button(onClick = { retry += 1 }) { Text("Tentar novamente") }
                    }
                }
            }
            is GamesState.Loaded -> {
                if (current.games.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Nenhum jogo informado para esta data.", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Tente outra data ou liga.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                for (game in current.games) {
                    GameCard(game, favoriteTeams, onToggleFavorite)
                }
            }
        }
        Text(
            "Fonte: TheSportsDB. A consulta gratuita mostra até 3 jogos por data; a lista pode estar incompleta. Horários em UTC.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun LeagueChip(
    option: LeagueFilter,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        label = {
            Text(
                option.label,
                style = MaterialTheme.typography.labelMedium,
                textDecoration = if (selected) TextDecoration.Underline else TextDecoration.None
            )
        }
    )
}

@Composable
private fun GameCard(
    game: Game,
    favoriteTeams: Map<String, String>,
    onToggleFavorite: (String?, String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                game.league,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val textMeasurer = rememberTextMeasurer()
                val showScores = game.homeScore != null || game.awayScore != null
                val scoreStyle = MaterialTheme.typography.titleLarge
                val separatorWidth = textMeasurer.measure("×", style = MaterialTheme.typography.labelMedium).size.width
                val scoresWidth = if (showScores) {
                    textMeasurer.measure(game.homeScore ?: "—", style = scoreStyle).size.width +
                        textMeasurer.measure(game.awayScore ?: "—", style = scoreStyle).size.width
                } else {
                    0
                }
                val density = LocalDensity.current
                val availableWidth = with(density) { maxWidth.roundToPx() }
                val statusWidth = textMeasurer.measure(
                    displayGameStatus(game.status),
                    style = MaterialTheme.typography.labelMedium,
                    constraints = Constraints(maxWidth = with(density) { 88.dp.roundToPx() })
                ).size.width
                val timeWidth = if (game.homeScore == null || game.awayScore == null) {
                    textMeasurer.measure(
                        displayGameTime(game.time),
                        style = MaterialTheme.typography.labelMedium,
                        constraints = Constraints(maxWidth = with(density) { 88.dp.roundToPx() })
                    ).size.width
                } else {
                    0
                }
                val centerWidth = maxOf(separatorWidth, statusWidth, timeWidth)
                val nameWidth = ((availableWidth - centerWidth - with(density) { 16.dp.roundToPx() }) / 2).coerceAtLeast(1)
                val textStyle = MaterialTheme.typography.titleSmall
                val nameConstraints = Constraints(maxWidth = nameWidth)
                val homeOverflow = textMeasurer.measure(
                    text = game.home, style = textStyle, maxLines = 2, constraints = nameConstraints
                ).hasVisualOverflow
                val awayOverflow = textMeasurer.measure(
                    text = game.away, style = textStyle, maxLines = 2, constraints = nameConstraints
                ).hasVisualOverflow
                val scoreOverflow = showScores && listOf(game.homeScore ?: "—", game.awayScore ?: "—").any {
                    textMeasurer.measure(it, style = scoreStyle).size.width > nameWidth
                }
                if (LocalConfiguration.current.fontScale >= 1.5f || scoresWidth > availableWidth / 2 || scoreOverflow ||
                    nameWidth < with(density) { 48.dp.roundToPx() } || homeOverflow || awayOverflow
                ) {
                    Column {
                        TeamScore(game.home, game.homeScore, game.homeTeamId, favoriteTeams, onToggleFavorite, showScores)
                        TeamScore(game.away, game.awayScore, game.awayTeamId, favoriteTeams, onToggleFavorite, showScores)
                        Text(
                            displayGameStatus(game.status),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (game.homeScore == null || game.awayScore == null) {
                            Text(displayGameTime(game.time), style = MaterialTheme.typography.bodyMedium)
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text(
                            "Transmissão não consultada",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else {
                    Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            MatchTeam(game.home)
                            if (showScores) {
                                MatchScore(game.home, game.homeScore)
                            }
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "×",
                                modifier = Modifier.padding(horizontal = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                          Text(
                              displayGameStatus(game.status),
                              modifier = Modifier.widthIn(max = 88.dp),
                              textAlign = TextAlign.Center,
                              style = MaterialTheme.typography.labelMedium,
                              color = MaterialTheme.colorScheme.onSurfaceVariant
                          )
                          if (game.homeScore == null || game.awayScore == null) {
                              Text(
                                  displayGameTime(game.time),
                                  modifier = Modifier.widthIn(max = 88.dp),
                                  textAlign = TextAlign.Center,
                                  style = MaterialTheme.typography.labelMedium
                              )
                          }
                        }
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            MatchTeam(game.away)
                            if (showScores) {
                                MatchScore(game.away, game.awayScore)
                            }
                        }
                    }
                    if ((game.homeTeamId.isNullOrBlank() || game.homeTeamId == "0") &&
                        (game.awayTeamId.isNullOrBlank() || game.awayTeamId == "0")
                    ) {
                        Text(
                            "Transmissão não consultada",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MatchFavorite(game.home, game.homeTeamId, favoriteTeams, onToggleFavorite)
                            Text(
                                "Transmissão não consultada",
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                            MatchFavorite(game.away, game.awayTeamId, favoriteTeams, onToggleFavorite)
                        }
                    }
                    }
                }
            }
        }
    }
}

@Composable
private fun MatchTeam(
    name: String,
    modifier: Modifier = Modifier
) {
    Text(
        name,
        modifier = modifier,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.titleSmall
    )
}

@Composable
private fun MatchFavorite(
    name: String,
    id: String?,
    favoriteTeams: Map<String, String>,
    onToggleFavorite: (String?, String) -> Unit
) {
        if (!id.isNullOrBlank() && id != "0") {
            val selected = favoriteTeams.containsKey(id)
            val label = if (selected) "Remover $name dos favoritos" else "Favoritar $name"
            IconButton(
                onClick = { onToggleFavorite(id, name) },
                modifier = Modifier.semantics { contentDescription = label }
            ) {
                Text(
                    if (selected) "★" else "☆",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.headlineSmall
                )
            }
        } else {
            Spacer(modifier = Modifier.width(48.dp))
        }
}

@Composable
private fun MatchScore(name: String, score: String?) {
    if (score != null) {
        Text(score, style = MaterialTheme.typography.titleLarge)
    } else {
        Text(
            "—",
            modifier = Modifier.semantics { contentDescription = "Placar não informado para $name" },
            style = MaterialTheme.typography.titleLarge
        )
    }
}

@Composable
private fun TeamScore(
    name: String,
    score: String?,
    id: String?,
    favoriteTeams: Map<String, String>,
    onToggleFavorite: (String?, String) -> Unit,
    showScores: Boolean
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val scoreWidth = if (score != null) {
            rememberTextMeasurer().measure(score, style = MaterialTheme.typography.titleLarge).size.width
        } else {
            0
        }
        val stackScore = scoreWidth > with(LocalDensity.current) { (maxWidth / 4).roundToPx() }
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    name,
                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                    style = MaterialTheme.typography.titleMedium
                )
                if (score != null && !stackScore) {
                    Text(score, style = MaterialTheme.typography.titleLarge)
                } else if (score == null && showScores) {
                    Text(
                        "—",
                        modifier = Modifier.semantics { contentDescription = "Placar não informado para $name" },
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                if (!id.isNullOrBlank() && id != "0") {
                    val selected = favoriteTeams.containsKey(id)
                    val label = if (selected) "Remover $name dos favoritos" else "Favoritar $name"
                    IconButton(
                        onClick = { onToggleFavorite(id, name) },
                        modifier = Modifier.semantics { contentDescription = label }
                    ) {
                        Text(
                            if (selected) "★" else "☆",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.headlineSmall
                        )
                    }
                }
            }
            if (stackScore && score != null) {
                Text(score, style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

@Composable
private fun FavoriteButton(
    id: String?,
    name: String,
    favoriteTeams: Map<String, String>,
    onToggleFavorite: (String?, String) -> Unit
) {
    if (!id.isNullOrBlank() && id != "0") {
        val label = if (favoriteTeams.containsKey(id)) {
            "Remover $name dos favoritos"
        } else {
            "Favoritar $name"
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium
            )
            IconButton(
                onClick = { onToggleFavorite(id, name) },
                modifier = Modifier.semantics { contentDescription = label }
            ) {
                Text(
                    "★",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.headlineSmall
                )
            }
        }
    }
}
