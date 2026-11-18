import XCTest
@testable import BasqueteApp

@MainActor
final class FavoritesTests: XCTestCase {
    func testFavoritesPersistAndCanBeRemoved() throws {
        let suite = "favorites-tests-\(UUID().uuidString)"
        let defaults = try XCTUnwrap(UserDefaults(suiteName: suite))
        defer { defaults.removePersistentDomain(forName: suite) }
        let favorites = Favorites(defaults: defaults)
        favorites.toggle(id: "123", name: "Time exemplo")
        XCTAssertEqual(Favorites(defaults: defaults).teams["123"], "Time exemplo")
        favorites.toggle(id: "123", name: "Time exemplo")
        XCTAssertTrue(Favorites(defaults: defaults).teams.isEmpty)
    }

    func testMissingTeamIDIsIgnored() throws {
        let suite = "favorites-tests-\(UUID().uuidString)"
        let defaults = try XCTUnwrap(UserDefaults(suiteName: suite))
        defer { defaults.removePersistentDomain(forName: suite) }
        let favorites = Favorites(defaults: defaults)
        favorites.toggle(id: nil, name: "Time exemplo")
        favorites.toggle(id: "0", name: "Time exemplo")
        XCTAssertTrue(favorites.teams.isEmpty)
    }
}
