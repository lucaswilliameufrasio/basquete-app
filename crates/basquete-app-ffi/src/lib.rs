use basquete_app_core as core;
use std::sync::Mutex;

#[cfg(all(feature = "experimental-brazil-fixtures", not(debug_assertions)))]
compile_error!("experimental-brazil-fixtures is restricted to debug builds");

uniffi::setup_scaffolding!();

#[derive(uniffi::Record, Debug, Clone, PartialEq, Eq)]
pub struct NativeAppState {
    pub counter: i32,
    pub title: String,
}

impl From<core::AppState> for NativeAppState {
    fn from(s: core::AppState) -> Self {
        Self {
            counter: s.counter,
            title: s.title,
        }
    }
}

#[derive(uniffi::Error, Debug)]
pub enum NativeError {
    NegativeValue,
    EmptyTitle,
}

impl From<core::CoreError> for NativeError {
    fn from(err: core::CoreError) -> Self {
        match err {
            core::CoreError::NegativeValue => NativeError::NegativeValue,
            core::CoreError::EmptyTitle => NativeError::EmptyTitle,
        }
    }
}

impl std::fmt::Display for NativeError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "{:?}", self)
    }
}

impl std::error::Error for NativeError {}

#[derive(uniffi::Object)]
pub struct AppEngine {
    inner: Mutex<core::StateEngine>,
}

impl Default for AppEngine {
    fn default() -> Self {
        Self::new()
    }
}

#[uniffi::export]
impl AppEngine {
    #[uniffi::constructor]
    pub fn new() -> Self {
        Self {
            inner: Mutex::new(core::StateEngine::new()),
        }
    }

    pub fn get_state(&self) -> NativeAppState {
        let engine = self.inner.lock().unwrap();
        engine.get_state().into()
    }

    pub fn increment(&self) -> NativeAppState {
        let mut engine = self.inner.lock().unwrap();
        engine.increment().into()
    }

    pub fn decrement(&self) -> Result<NativeAppState, NativeError> {
        let mut engine = self.inner.lock().unwrap();
        engine
            .decrement()
            .map(Into::into)
            .map_err(NativeError::from)
    }

    pub fn update_title(&self, new_title: String) -> Result<NativeAppState, NativeError> {
        let mut engine = self.inner.lock().unwrap();
        engine
            .update_title(&new_title)
            .map(Into::into)
            .map_err(NativeError::from)
    }
}

#[uniffi::export]
pub fn native_greet(name: String) -> String {
    core::greet(&name)
}

#[cfg(feature = "experimental-brazil-fixtures")]
#[derive(uniffi::Record, Debug, PartialEq, Eq)]
pub struct FixtureGame {
    pub id: String,
    pub league: String,
    pub home: String,
    pub away: String,
    pub starts_at_utc: String,
}

#[cfg(feature = "experimental-brazil-fixtures")]
struct FixtureTransport;

#[cfg(feature = "experimental-brazil-fixtures")]
impl core::experimental_brazil::Transport for FixtureTransport {
    fn get_page(
        &mut self,
        cursor: Option<&str>,
    ) -> Result<core::experimental_brazil::Response, ()> {
        if cursor.is_some() {
            return Err(());
        }
        Ok(core::experimental_brazil::Response {
            status: 200,
            retry_after: None,
            body: br#"{"data":[{"id":1,"tournament":"Liga brasileira (teste)","teamHome":"Equipe Brasil A","teamAway":"Equipe Brasil B","startsAt":"2026-11-16T22:30:00Z"}],"nextCursor":null}"#.to_vec(),
        })
    }
}

#[cfg(feature = "experimental-brazil-fixtures")]
#[uniffi::export]
pub fn brazil_fixture_games() -> Vec<FixtureGame> {
    let result = core::experimental_brazil::fetch(&mut FixtureTransport, 1)
        .expect("the embedded test fixture must be valid");
    result
        .events
        .into_iter()
        .map(|event| FixtureGame {
            id: format!("fixture-br-{}", event.id),
            league: event.tournament,
            home: event.team_home,
            away: event.team_away,
            starts_at_utc: event.starts_at,
        })
        .collect()
}

#[cfg(all(test, feature = "experimental-brazil-fixtures"))]
mod fixture_tests {
    use super::*;

    #[test]
    fn fixture_passes_through_adapter_to_ffi_record() {
        let games = brazil_fixture_games();
        assert_eq!(games.len(), 1);
        assert_eq!(games[0].id, "fixture-br-1");
        assert_eq!(games[0].league, "Liga brasileira (teste)");
        assert_eq!(games[0].home, "Equipe Brasil A");
        assert_eq!(games[0].away, "Equipe Brasil B");
        assert_eq!(games[0].starts_at_utc, "2026-11-16T22:30:00Z");
    }
}
