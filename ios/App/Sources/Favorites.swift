import Foundation
import Observation

@MainActor
@Observable
final class Favorites {
    private let defaults: UserDefaults
    private let key: String
    private(set) var teams: [String: String]

    init(defaults: UserDefaults = .standard, key: String = "favoriteTeams") {
        self.defaults = defaults
        self.key = key
        self.teams = defaults.dictionary(forKey: key) as? [String: String] ?? [:]
    }

    func toggle(id: String?, name: String) {
        guard let id, !id.isEmpty, id != "0" else { return }
        if teams[id] == nil {
            teams[id] = name
        } else {
            teams.removeValue(forKey: id)
        }
        defaults.set(teams, forKey: key)
    }
}
