import XCTest
@testable import BasqueteApp

final class GameFeedTests: XCTestCase {
    func testLocalStartConvertsUtcAndDetectsDateRollover() throws {
        let game = Game(
            id: "1", home: "Casa", away: "Visitante", homeTeamID: nil, awayTeamID: nil,
            league: "NBB", date: "2026-12-02", time: "22:30:00",
            homeScore: nil, awayScore: nil, status: nil
        )
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = try XCTUnwrap(TimeZone(identifier: "America/Sao_Paulo"))
        let brazil = try XCTUnwrap(game.localStart(timeZone: calendar.timeZone))
        XCTAssertEqual(calendar.component(.hour, from: brazil.instant), 19)
        XCTAssertEqual(calendar.component(.minute, from: brazil.instant), 30)
        XCTAssertFalse(brazil.changesDay)

        calendar.timeZone = try XCTUnwrap(TimeZone(identifier: "Asia/Tokyo"))
        let japan = try XCTUnwrap(game.localStart(timeZone: calendar.timeZone))
        XCTAssertEqual(calendar.component(.day, from: japan.instant), 3)
        XCTAssertEqual(calendar.component(.hour, from: japan.instant), 7)
        XCTAssertTrue(japan.changesDay)
    }

    func testRequestIncludesDateAndSport() async throws {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [StubProtocol.self]
        let session = URLSession(configuration: configuration)
        defer { session.invalidateAndCancel() }
        let feed = GameFeed(session: session, baseURL: try XCTUnwrap(URL(string: "https://example.test/events")))
        let date = try XCTUnwrap(ISO8601DateFormatter().date(from: "2026-11-16T12:00:00Z"))
        let games = try await feed.games(on: date)
        XCTAssertEqual(games.first?.home, "Home")
    }

    func testRejectsHttpError() async throws {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [StubProtocol.self]
        let session = URLSession(configuration: configuration)
        defer { session.invalidateAndCancel() }
        let feed = GameFeed(session: session, baseURL: try XCTUnwrap(URL(string: "https://example.test/error")))
        do {
            _ = try await feed.games(on: Date())
            XCTFail("Expected HTTP error")
        } catch {
            XCTAssertTrue(error is FeedError)
        }
    }

    func testLeagueFilterIsSentToProvider() async throws {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [StubProtocol.self]
        let session = URLSession(configuration: configuration)
        defer { session.invalidateAndCancel() }
        let feed = GameFeed(session: session, baseURL: try XCTUnwrap(URL(string: "https://example.test/filtered")))
        let date = try XCTUnwrap(ISO8601DateFormatter().date(from: "2026-11-16T12:00:00Z"))
        let games = try await feed.games(on: date, league: .northAmerica)
        XCTAssertEqual(games.first?.home, "Home")
    }

    func testDecodesGamesAndHandlesMissingScores() throws {
        let data = Data("""
        {"events":[{"idEvent":"1","strHomeTeam":"Home","strAwayTeam":"Away",
        "strLeague":"Example","dateEvent":"2026-11-16","strTime":"18:30:00",
        "intHomeScore":null,"intAwayScore":null}]}
        """.utf8)
        let games = try JSONDecoder().decode(GameResponse.self, from: data).events
        XCTAssertEqual(games?.first?.home, "Home")
        XCTAssertEqual(games?.first?.time, "18:30:00")
        XCTAssertNil(games?.first?.homeScore)
    }

    func testRejectsMalformedGame() {
        let data = Data(#"{"events":[{"idEvent":"1"}]}"#.utf8)
        XCTAssertThrowsError(try JSONDecoder().decode(GameResponse.self, from: data))
    }

    func testRejectsMissingEvents() {
        XCTAssertThrowsError(try JSONDecoder().decode(GameResponse.self, from: Data("{}".utf8)))
    }

    func testNullEventsIsEmpty() throws {
        let data = Data(#"{"events":null}"#.utf8)
        let response = try JSONDecoder().decode(GameResponse.self, from: data)
        XCTAssertTrue((response.events ?? []).isEmpty)
    }

    func testStatusIsNotGuessedFromScores() throws {
        let data = Data("""
        {"events":[{"idEvent":"1","strHomeTeam":"Home","strAwayTeam":"Away",
        "strLeague":"Example","dateEvent":"2026-11-16","intHomeScore":"20","strStatus":"FT"},
        {"idEvent":"2","strHomeTeam":"Home","strAwayTeam":"Away","strLeague":"Example",
        "dateEvent":"2026-11-16","strStatus":"NS"},
        {"idEvent":"3","strHomeTeam":"Home","strAwayTeam":"Away","strLeague":"Example",
        "dateEvent":"2026-11-16","intHomeScore":"20","strStatus":"unknown"}]}
        """.utf8)
        let games = try XCTUnwrap(JSONDecoder().decode(GameResponse.self, from: data).events)
        XCTAssertEqual(games.map(\.displayStatus), ["Encerrado", "Ainda não iniciado", "Status não informado"])
    }
}

private final class StubProtocol: URLProtocol, @unchecked Sendable {
    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        guard let url = request.url else { return }
        let components = URLComponents(url: url, resolvingAgainstBaseURL: false)
        let items = components?.queryItems ?? []
        let hasDate = items.contains(URLQueryItem(name: "d", value: "2026-11-16"))
        let hasSport = items.contains(URLQueryItem(name: "s", value: "Basketball"))
        let hasLeague = items.contains(URLQueryItem(name: "l", value: "4387"))
        let isValid = hasDate && ((url.path == "/events" && hasSport && !hasLeague) || (url.path == "/filtered" && hasLeague && !hasSport))
        let status = isValid ? 200 : 503
        guard let response = HTTPURLResponse(url: url, statusCode: status, httpVersion: nil, headerFields: nil) else { return }
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: Data("""
        {"events":[{"idEvent":"1","strHomeTeam":"Home","strAwayTeam":"Away",
        "strLeague":"Example","dateEvent":"2026-11-16"}]}
        """.utf8))
        client?.urlProtocolDidFinishLoading(self)
    }

    override func stopLoading() {}
}
