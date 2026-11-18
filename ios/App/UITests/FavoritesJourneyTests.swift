import XCTest

final class FavoritesJourneyTests: XCTestCase {
    @MainActor
    func testMatchupRetainsTeamsScoresAndFavoriteActions() {
        let application = XCUIApplication()
        application.launchEnvironment["UI_TEST_FIXTURE"] = "1"
        application.launchEnvironment["UI_TEST_DEFAULTS_SUITE"] = "ui-tests-\(UUID().uuidString)"
        application.launch()

        XCTAssertTrue(application.staticTexts["Time Casa"].waitForExistence(timeout: 10))
        XCTAssertTrue(application.staticTexts["Time Visitante"].exists)
        XCTAssertTrue(application.buttons["Favoritar Time Casa"].exists)
        XCTAssertTrue(application.buttons["Favoritar Time Visitante"].exists)
        XCTAssertTrue(application.staticTexts["80"].exists)
        XCTAssertTrue(application.staticTexts["75"].exists)
        application.buttons["Favoritar Time Casa"].tap()
        XCTAssertTrue(application.buttons["Remover Time Casa dos favoritos"].exists)
        application.buttons["Remover Time Casa dos favoritos"].tap()
    }

    @MainActor
    func testLeagueSelectionReloadsControlledResults() {
        let application = XCUIApplication()
        application.launchEnvironment["UI_TEST_FIXTURE"] = "league"
        application.launchEnvironment["UI_TEST_DEFAULTS_SUITE"] = "ui-tests-\(UUID().uuidString)"
        application.launch()

        let generalTeam = application.staticTexts["Equipe geral fictícia"]
        let filteredTeam = application.staticTexts["Equipe filtrada fictícia"]
        let league = application.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Liga,")).firstMatch
        XCTAssertTrue(generalTeam.waitForExistence(timeout: 10))
        league.tap()
        application.buttons["Liga norte-americana"].tap()
        XCTAssertTrue(filteredTeam.waitForExistence(timeout: 10))
        XCTAssertFalse(generalTeam.exists)
        league.tap()
        application.buttons["Todas as ligas"].tap()
        XCTAssertTrue(generalTeam.waitForExistence(timeout: 10))
        XCTAssertFalse(filteredTeam.exists)
    }

    @MainActor
    func testGameStatusWrapsAtMaximumTextSize() {
        let application = XCUIApplication()
        application.launchArguments += [
            "-UIPreferredContentSizeCategoryName",
            "UICTContentSizeCategoryAccessibilityXXXL"
        ]
        application.launchEnvironment["UI_TEST_FIXTURE"] = "1"
        application.launchEnvironment["UI_TEST_DEFAULTS_SUITE"] = "ui-tests-\(UUID().uuidString)"
        application.launch()

        let status = application.staticTexts["Status não informado"]
        XCTAssertTrue(status.waitForExistence(timeout: 10))
        let league = application.staticTexts["Liga Exemplo"]
        XCTAssertTrue(league.exists)
        XCTAssertGreaterThan(status.frame.height, league.frame.height * 1.4)
    }

    @MainActor
    func testAppearancePersistsAfterRestart() {
        let application = XCUIApplication()
        application.launchEnvironment["UI_TEST_FIXTURE"] = "empty"
        application.launchEnvironment["UI_TEST_DEFAULTS_SUITE"] = "ui-tests-\(UUID().uuidString)"
        application.launch()
        application.tabBars.buttons["Configurações"].tap()

        let theme = application.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Tema")).firstMatch
        XCTAssertTrue(theme.waitForExistence(timeout: 10))
        let originalTheme = ["Sistema", "Claro", "Escuro"].first { theme.label.contains($0) } ?? "Sistema"
        theme.tap()
        application.buttons["Escuro"].tap()
        XCTAssertTrue(theme.label.contains("Escuro"))

        application.terminate()
        application.launch()
        application.tabBars.buttons["Configurações"].tap()
        XCTAssertTrue(theme.waitForExistence(timeout: 10))
        XCTAssertTrue(theme.label.contains("Escuro"))
        theme.tap()
        application.buttons[originalTheme].tap()
        XCTAssertTrue(theme.label.contains(originalTheme))
    }

    @MainActor
    func testLeagueSelectionAtMaximumTextSize() {
        let application = XCUIApplication()
        application.launchArguments += [
            "-UIPreferredContentSizeCategoryName",
            "UICTContentSizeCategoryAccessibilityXXXL"
        ]
        application.launchEnvironment["UI_TEST_FIXTURE"] = "empty"
        application.launchEnvironment["UI_TEST_DEFAULTS_SUITE"] = "ui-tests-\(UUID().uuidString)"
        application.launch()

        let league = application.buttons["Liga"]
        XCTAssertTrue(league.waitForExistence(timeout: 10))
        XCTAssertEqual(league.value as? String, "Todas as ligas")
        league.tap()
        application.buttons["Liga norte-americana"].tap()
        XCTAssertEqual(league.value as? String, "Liga norte-americana")
        XCTAssertTrue(application.staticTexts["Nenhum jogo informado para esta data."].exists)
        league.tap()
        application.buttons["Todas as ligas"].tap()
        XCTAssertEqual(league.value as? String, "Todas as ligas")
        XCTAssertTrue(application.staticTexts["Nenhum jogo informado para esta data."].waitForExistence(timeout: 10))
    }

    @MainActor
    func testLoadingFailureRetryAndEmptyState() {
        let application = XCUIApplication()
        application.launchEnvironment["UI_TEST_FIXTURE"] = "states"
        application.launchEnvironment["UI_TEST_DEFAULTS_SUITE"] = "ui-tests-\(UUID().uuidString)"
        application.launch()

        XCTAssertTrue(application.staticTexts["Carregando jogos"].waitForExistence(timeout: 5))
        let failure = application.staticTexts["Não foi possível carregar os jogos. Verifique a conexão e tente novamente."]
        XCTAssertTrue(failure.waitForExistence(timeout: 15))
        application.buttons["Tentar novamente"].tap()
        XCTAssertTrue(application.staticTexts["Carregando jogos"].waitForExistence(timeout: 5))
        XCTAssertTrue(application.staticTexts["Nenhum jogo informado para esta data."].waitForExistence(timeout: 15))
    }

    func testBrazilFixtureDoesNotInventBroadcastOrFavorites() {
        let application = XCUIApplication()
        application.launchEnvironment["UI_TEST_FIXTURE"] = "brazil"
        application.launchEnvironment["UI_TEST_DEFAULTS_SUITE"] = "ui-tests-\(UUID().uuidString)"
        application.launch()
        XCTAssertTrue(application.staticTexts["Equipe Brasil A"].waitForExistence(timeout: 10))
        XCTAssertTrue(application.staticTexts["Liga brasileira (teste)"].exists)
        XCTAssertTrue(application.staticTexts["Transmissão não consultada"].exists)
        XCTAssertFalse(application.buttons["Favoritar Equipe Brasil A"].exists)
    }

    func testControlledGameStatusesAreDisplayed() {
        let application = XCUIApplication()
        application.launchEnvironment["UI_TEST_FIXTURE"] = "1"
        application.launchEnvironment["UI_TEST_DEFAULTS_SUITE"] = "ui-tests-\(UUID().uuidString)"
        application.launch()

        XCTAssertTrue(application.staticTexts["Status não informado"].waitForExistence(timeout: 10))
        XCTAssertTrue(application.staticTexts["Encerrado"].waitForExistence(timeout: 10))
    }

    func testFavoritePersistsAndCanBeRemoved() {
        let application = XCUIApplication()
        application.launchEnvironment["UI_TEST_FIXTURE"] = "1"
        application.launchEnvironment["UI_TEST_DEFAULTS_SUITE"] = "ui-tests-\(UUID().uuidString)"
        application.launch()

        let favorite = application.buttons["Favoritar Time Casa"]
        XCTAssertTrue(favorite.waitForExistence(timeout: 10))
        favorite.tap()
        application.tabBars.buttons["Favoritos"].tap()
        let remove = application.buttons["Remover Time Casa dos favoritos"]
        XCTAssertTrue(remove.waitForExistence(timeout: 5))

        application.terminate()
        application.launch()
        application.tabBars.buttons["Favoritos"].tap()
        XCTAssertTrue(remove.waitForExistence(timeout: 5))
        remove.tap()
        XCTAssertTrue(application.staticTexts["Nenhum favorito"].waitForExistence(timeout: 5))
    }
}
