import XCTest
@testable import BasqueteApp

final class NativeLifecycleTests: XCTestCase {
    func testRustEngineCanIncrement() {
        let engine = AppEngine()
        XCTAssertEqual(engine.getState().counter, 0)
        XCTAssertEqual(engine.increment().counter, 1)
    }

    func testRustRejectsNegativeCounter() {
        let engine = AppEngine()
        XCTAssertThrowsError(try engine.decrement())
        XCTAssertEqual(engine.getState().counter, 0)
    }
}
