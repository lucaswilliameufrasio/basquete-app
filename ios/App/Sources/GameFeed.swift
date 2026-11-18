import Foundation
import Observation

struct Game: Decodable, Identifiable {
    let id: String
    let home: String
    let away: String
    let homeTeamID: String?
    let awayTeamID: String?
    let league: String
    let date: String
    let time: String?
    let homeScore: String?
    let awayScore: String?
    let status: String?

    enum CodingKeys: String, CodingKey {
        case id = "idEvent"
        case home = "strHomeTeam"
        case away = "strAwayTeam"
        case homeTeamID = "idHomeTeam"
        case awayTeamID = "idAwayTeam"
        case league = "strLeague"
        case date = "dateEvent"
        case time = "strTime"
        case homeScore = "intHomeScore"
        case awayScore = "intAwayScore"
        case status = "strStatus"
    }

    var displayLeague: String {
        if league.uppercased() == "NBA" {
            return "Liga norte-americana"
        }
        return league
    }

    var displayStatus: String {
        switch status?.uppercased() {
        case "FT": "Encerrado"
        case "NS": "Ainda não iniciado"
        default: "Status não informado"
        }
    }
}

struct GameResponse: Decodable {
    let events: [Game]?

    enum CodingKeys: String, CodingKey {
        case events
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        guard container.contains(.events) else {
            throw DecodingError.keyNotFound(
                CodingKeys.events,
                DecodingError.Context(codingPath: decoder.codingPath, debugDescription: "Missing events")
            )
        }
        events = try container.decodeIfPresent([Game].self, forKey: .events)
    }
}

enum FeedError: Error {
    case invalidResponse
}

enum LeagueFilter: String, CaseIterable, Identifiable {
    case all, northAmerica
    var id: String { rawValue }
    var label: String {
        switch self {
        case .all: "Todas as ligas"
        case .northAmerica: "Liga norte-americana"
        }
    }
    var leagueID: String? {
        switch self {
        case .all: nil
        case .northAmerica: "4387"
        }
    }
}

struct GameFeed {
    let session: URLSession
    let baseURL: URL

    init(session: URLSession = .shared, baseURL: URL = URL(string: "https://www.thesportsdb.com/api/v1/json/123/eventsday.php")!) {
        self.session = session
        self.baseURL = baseURL
    }

    func games(on date: Date, league: LeagueFilter = .all) async throws -> [Game] {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        let components = calendar.dateComponents([.year, .month, .day], from: date)
        guard let year = components.year, let month = components.month, let day = components.day,
              var url = URLComponents(url: baseURL, resolvingAgainstBaseURL: false) else {
            throw FeedError.invalidResponse
        }
        url.queryItems = [
            URLQueryItem(name: "d", value: String(format: "%04d-%02d-%02d", year, month, day))
        ]
        if let leagueID = league.leagueID {
            url.queryItems?.append(URLQueryItem(name: "l", value: leagueID))
        } else {
            url.queryItems?.append(URLQueryItem(name: "s", value: "Basketball"))
        }
        guard let requestURL = url.url else {
            throw FeedError.invalidResponse
        }
        var request = URLRequest(url: requestURL)
        request.timeoutInterval = 15
        let (data, response) = try await session.data(for: request)
        guard let response = response as? HTTPURLResponse, (200...299).contains(response.statusCode) else {
            throw FeedError.invalidResponse
        }
        return try JSONDecoder().decode(GameResponse.self, from: data).events ?? []
    }
}

@MainActor
@Observable
final class GamesModel {
    enum State {
        case loading
        case loaded([Game])
        case failed
    }

    var state: State = .loading
    private let feed = GameFeed()
    #if DEBUG
    private var stateFixtureAttempts = 0
    #endif

    func load(date: Date, league: LeagueFilter = .all) async {
        state = .loading
        do {
            #if DEBUG
            if ProcessInfo.processInfo.environment["UI_TEST_FIXTURE"] == "empty" {
                state = .loaded([])
                return
            }
            if ProcessInfo.processInfo.environment["UI_TEST_FIXTURE"] == "league" {
                let home = league == .all ? "Equipe geral fictícia" : "Equipe filtrada fictícia"
                let game = Game(
                    id: "fixture-league", home: home, away: "Equipe visitante fictícia",
                    homeTeamID: nil, awayTeamID: nil, league: "Liga fictícia",
                    date: "2026-11-16", time: nil, homeScore: nil, awayScore: nil, status: nil
                )
                state = .loaded([game])
                return
            }
            if ProcessInfo.processInfo.environment["UI_TEST_FIXTURE"] == "states" {
                stateFixtureAttempts += 1
                try await Task.sleep(for: .seconds(8))
                if stateFixtureAttempts == 1 {
                    throw FeedError.invalidResponse
                }
                state = .loaded([])
                return
            }
            if ProcessInfo.processInfo.environment["UI_TEST_FIXTURE"] == "brazil" {
                #if BRAZIL_FIXTURE_TEST
                let games = brazilFixtureGames().map { fixture in
                    Game(
                        id: fixture.id, home: fixture.home, away: fixture.away,
                        homeTeamID: nil, awayTeamID: nil, league: fixture.league,
                        date: String(fixture.startsAtUtc.prefix(10)),
                        time: String(fixture.startsAtUtc.dropFirst(11).prefix(8)),
                        homeScore: nil, awayScore: nil, status: nil
                    )
                }
                state = .loaded(games)
                #else
                let fixture = Data("""
                {"events":[{"idEvent":"fixture-br-1","strHomeTeam":"Equipe Brasil A",
                "strAwayTeam":"Equipe Brasil B","strLeague":"Liga brasileira (teste)",
                "dateEvent":"2026-11-16","strTime":"22:30:00"}]}
                """.utf8)
                state = .loaded(try JSONDecoder().decode(GameResponse.self, from: fixture).events ?? [])
                #endif
                return
            }
            if ProcessInfo.processInfo.environment["UI_TEST_FIXTURE"] == "1" {
                let fixture = Data("""
                {"events":[{"idEvent":"1","strHomeTeam":"Time Casa","strAwayTeam":"Time Visitante",
                "idHomeTeam":"100","idAwayTeam":"200","strLeague":"Liga Exemplo",
                "dateEvent":"2026-11-16"},
                {"idEvent":"2","strHomeTeam":"Equipe A","strAwayTeam":"Equipe B",
                "strLeague":"Liga Exemplo","dateEvent":"2026-11-16",
                "intHomeScore":"80","intAwayScore":"75","strStatus":"FT"}]}
                """.utf8)
                state = .loaded(try JSONDecoder().decode(GameResponse.self, from: fixture).events ?? [])
                return
            }
            #endif
            let games = try await feed.games(on: date, league: league)
            guard !Task.isCancelled else { return }
            state = .loaded(games)
        } catch {
            guard !Task.isCancelled else { return }
            state = .failed
        }
    }
}
