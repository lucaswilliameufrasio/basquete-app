import SwiftUI

private enum Appearance: String, CaseIterable, Identifiable {
    case system, light, dark

    var id: String { rawValue }

    var label: String {
        switch self {
        case .system: "Sistema"
        case .light: "Claro"
        case .dark: "Escuro"
        }
    }

    var colorScheme: ColorScheme? {
        switch self {
        case .system: nil
        case .light: .light
        case .dark: .dark
        }
    }
}

struct ContentView: View {
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @AppStorage("appearance") private var appearance = Appearance.system.rawValue
    @State private var date = Date()
    @State private var league = LeagueFilter.all
    @State private var games = GamesModel()
    @State private var favorites: Favorites = {
        #if DEBUG
        if let suite = ProcessInfo.processInfo.environment["UI_TEST_DEFAULTS_SUITE"],
           let defaults = UserDefaults(suiteName: suite) {
            return Favorites(defaults: defaults)
        }
        #endif
        return Favorites()
    }()

    var body: some View {
        TabView {
            NavigationStack {
                List {
                    DatePicker("Data (UTC)", selection: $date, displayedComponents: .date)
                        .environment(\.timeZone, TimeZone(secondsFromGMT: 0)!)
                    if dynamicTypeSize.isAccessibilitySize {
                        Menu {
                            ForEach(LeagueFilter.allCases) { option in
                                Button {
                                    league = option
                                } label: {
                                    if league == option {
                                        Label(option.label, systemImage: "checkmark")
                                    } else {
                                        Text(option.label)
                                    }
                                }
                            }
                        } label: {
                            VStack(alignment: .leading, spacing: 8) {
                                Text("Liga").foregroundStyle(JirauTheme.textPrimary)
                                Text(league.label).foregroundStyle(JirauTheme.accent)
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                        }
                        .accessibilityLabel("Liga")
                        .accessibilityValue(league.label)
                    } else {
                        Picker("Liga", selection: $league) {
                            ForEach(LeagueFilter.allCases) { option in
                                Text(option.label).tag(option)
                            }
                        }
                    }
                    Section {
                        switch games.state {
                        case .loading:
                            ProgressView("Carregando jogos")
                        case .failed:
                            Text("Não foi possível carregar os jogos. Verifique a conexão e tente novamente.")
                            Button("Tentar novamente") {
                                Task { await games.load(date: date, league: league) }
                            }
                        case .loaded(let items):
                            if items.isEmpty {
                                Text("Nenhum jogo informado para esta data.")
                            }
                            ForEach(items) { game in
                                GameCard(game: game, favorites: favorites)
                                    .listRowBackground(JirauTheme.surface)
                            }
                        }
                    } footer: {
                        Text("Fonte: TheSportsDB. A consulta gratuita mostra até 3 jogos por data; a lista pode estar incompleta. Horários em UTC.")
                            .foregroundStyle(JirauTheme.textSecondary)
                    }
                }
                .scrollContentBackground(.hidden)
                .background(JirauTheme.background)
                .navigationTitle("Jogos")
                .task(id: "\(date.timeIntervalSince1970)-\(league.rawValue)") {
                    await games.load(date: date, league: league)
                }
            }
            .tabItem {
                Label("Jogos", systemImage: "basketball")
            }

            NavigationStack {
                Group {
                    if favorites.teams.isEmpty {
                        ContentUnavailableView(
                            "Nenhum favorito",
                            systemImage: "star",
                            description: Text("Marque um time na lista de jogos para encontrá-lo aqui.")
                        )
                    } else {
                        List {
                            ForEach(favorites.teams.keys.sorted(), id: \.self) { id in
                                if let name = favorites.teams[id] {
                                    FavoriteButton(id: id, name: name, favorites: favorites)
                                        .listRowBackground(JirauTheme.surface)
                                }
                            }
                        }
                        .scrollContentBackground(.hidden)
                    }
                }
                .background(JirauTheme.background)
                .navigationTitle("Favoritos")
            }
            .tabItem {
                Label("Favoritos", systemImage: "star")
            }

            NavigationStack {
                Form {
                    Section("Aparência") {
                        Picker("Tema", selection: $appearance) {
                            ForEach(Appearance.allCases) { option in
                                Text(option.label).tag(option.rawValue)
                            }
                        }
                    }
                    .listRowBackground(JirauTheme.surface)
                    Section("Dados") {
                        Text("Jogos: TheSportsDB (acesso de desenvolvimento limitado). Transmissões ainda não estão conectadas.")
                            .foregroundStyle(JirauTheme.textSecondary)
                    }
                    .listRowBackground(JirauTheme.surface)
                }
                .scrollContentBackground(.hidden)
                .background(JirauTheme.background)
                .navigationTitle("Configurações")
            }
            .tabItem {
                Label("Configurações", systemImage: "gearshape")
            }
        }
        .tint(JirauTheme.accent)
        .preferredColorScheme((Appearance(rawValue: appearance) ?? .system).colorScheme)
    }
}

private struct GameCard: View {
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    let game: Game
    let favorites: Favorites

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            if dynamicTypeSize.isAccessibilitySize {
                VStack(alignment: .leading, spacing: 4) {
                    leagueAndStatus
                }
            } else {
                HStack(alignment: .firstTextBaseline) {
                    Text(game.displayLeague)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(JirauTheme.accent)
                    Spacer(minLength: 8)
                    Text(game.displayStatus)
                        .font(.caption)
                        .foregroundStyle(JirauTheme.textSecondary)
                }
            }
            if dynamicTypeSize.isAccessibilitySize {
                TeamScore(name: game.home, score: game.homeScore, id: game.homeTeamID, favorites: favorites)
                TeamScore(name: game.away, score: game.awayScore, id: game.awayTeamID, favorites: favorites)
            } else {
                HStack(alignment: .center, spacing: 4) {
                    MatchTeam(name: game.home, id: game.homeTeamID, favorites: favorites)
                    HStack(spacing: 4) {
                        if game.homeScore != nil || game.awayScore != nil {
                            MatchScore(name: game.home, score: game.homeScore)
                        }
                        Text("×")
                            .font(.caption)
                            .foregroundStyle(JirauTheme.textSecondary)
                        if game.homeScore != nil || game.awayScore != nil {
                            MatchScore(name: game.away, score: game.awayScore)
                        }
                    }
                    MatchTeam(name: game.away, id: game.awayTeamID, favorites: favorites)
                }
            }
            Divider()
            if game.homeScore == nil || game.awayScore == nil {
                if let time = game.time, !time.isEmpty {
                    Text("\(String(time.prefix(5))) UTC").font(.subheadline)
                } else {
                    Text("Horário não informado").font(.subheadline)
                }
            }
            Text("Transmissão não consultada")
                .font(.caption)
                .foregroundStyle(JirauTheme.textSecondary)
        }
        .foregroundStyle(JirauTheme.textPrimary)
        .padding(.vertical, 8)
    }

    private var leagueAndStatus: some View {
        Group {
            Text(game.displayLeague)
                .font(.caption.weight(.semibold))
                .foregroundStyle(JirauTheme.accent)
            Text(game.displayStatus)
                .font(.caption)
                .foregroundStyle(JirauTheme.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
        }
    }
}

private struct MatchTeam: View {
    let name: String
    let id: String?
    let favorites: Favorites

    var body: some View {
        VStack(spacing: 4) {
            Text(name)
                .font(.subheadline.weight(.semibold))
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
            if let id, !id.isEmpty, id != "0" {
                let selected = favorites.teams[id] != nil
                Button {
                    favorites.toggle(id: id, name: name)
                } label: {
                    Image(systemName: selected ? "star.fill" : "star")
                        .frame(minWidth: 44, minHeight: 44)
                }
                .buttonStyle(.borderless)
                .accessibilityLabel(selected ? "Remover \(name) dos favoritos" : "Favoritar \(name)")
            }
        }
        .frame(maxWidth: .infinity)
    }
}

private struct MatchScore: View {
    let name: String
    let score: String?

    var body: some View {
        if let score {
            Text(score).font(.title3.bold()).monospacedDigit()
        } else {
            Text("—")
                .font(.title3.bold())
                .accessibilityLabel("Placar não informado para \(name)")
        }
    }
}

private struct TeamScore: View {
    let name: String
    let score: String?
    let id: String?
    let favorites: Favorites

    var body: some View {
        HStack(alignment: .firstTextBaseline) {
            Text(name).font(.headline)
            Spacer(minLength: 12)
            if let score {
                Text(score).font(.title3.bold()).monospacedDigit()
            }
            if let id, !id.isEmpty, id != "0" {
                let selected = favorites.teams[id] != nil
                Button {
                    favorites.toggle(id: id, name: name)
                } label: {
                    Image(systemName: selected ? "star.fill" : "star")
                        .frame(minWidth: 44, minHeight: 44)
                }
                .buttonStyle(.borderless)
                .accessibilityLabel(selected ? "Remover \(name) dos favoritos" : "Favoritar \(name)")
            }
        }
    }
}

private struct FavoriteButton: View {
    let id: String?
    let name: String
    let favorites: Favorites

    var body: some View {
        if let id, !id.isEmpty, id != "0" {
            let selected = favorites.teams[id] != nil
            Button {
                favorites.toggle(id: id, name: name)
            } label: {
                Label(
                    selected ? "Remover \(name) dos favoritos" : "Favoritar \(name)",
                    systemImage: selected ? "star.fill" : "star"
                )
            }
            .buttonStyle(.borderless)
        }
    }
}

#Preview {
    ContentView()
}
