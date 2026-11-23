//! Offline experimental page processing. No networking or UI integration.

use std::collections::HashSet;
use std::sync::OnceLock;

use jiff::tz::{TimeZone, TimeZoneDatabase};
use serde::Deserialize;
use thiserror::Error;
use time::{OffsetDateTime, PrimitiveDateTime, UtcOffset, format_description::well_known::Rfc3339};

fn parse_utc(value: &str) -> Option<OffsetDateTime> {
    let instant = OffsetDateTime::parse(value, &Rfc3339).ok()?;
    (instant.offset() == UtcOffset::UTC).then_some(instant)
}

fn parse_local(value: &str) -> Option<jiff::civil::DateTime> {
    let whole = time::macros::format_description!("[year]-[month]-[day]T[hour]:[minute]:[second]");
    let fractional = time::macros::format_description!(
        "[year]-[month]-[day]T[hour]:[minute]:[second].[subsecond]"
    );
    if PrimitiveDateTime::parse(value, whole).is_err()
        && PrimitiveDateTime::parse(value, fractional).is_err()
    {
        return None;
    }
    value.parse().ok()
}

fn parse_zone(value: &str) -> Option<TimeZone> {
    static DATABASE: OnceLock<TimeZoneDatabase> = OnceLock::new();
    let zone = DATABASE
        .get_or_init(TimeZoneDatabase::bundled)
        .get(value)
        .ok()?;
    (zone.iana_name() == Some(value)).then_some(zone)
}

#[cfg(feature = "experimental-local-http")]
pub mod local_http;

#[derive(Debug, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct Event {
    pub id: u64,
    pub tournament: String,
    pub team_home: String,
    pub team_away: String,
    pub starts_at: String,
    pub local_starts_at: Option<String>,
    pub time_zone: Option<String>,
    pub time_zone_confidence: Option<String>,
}

#[derive(Debug, PartialEq, Eq)]
pub enum TimeConsistency {
    Consistent,
    Inconsistent,
    Unverifiable,
    Invalid,
}

impl Event {
    /// Consistency does not establish that the source's time is accurate.
    pub fn time_consistency(&self) -> TimeConsistency {
        if !self.has_valid_times() {
            return TimeConsistency::Invalid;
        }
        let (Some(local), Some(zone)) = (&self.local_starts_at, &self.time_zone) else {
            return TimeConsistency::Unverifiable;
        };
        let Some(instant) = parse_utc(&self.starts_at) else {
            return TimeConsistency::Invalid;
        };
        let Some(local) = parse_local(local) else {
            return TimeConsistency::Invalid;
        };
        let Some(zone) = parse_zone(zone) else {
            return TimeConsistency::Invalid;
        };
        let Ok(timestamp) = jiff::Timestamp::from_nanosecond(instant.unix_timestamp_nanos()) else {
            return TimeConsistency::Invalid;
        };
        if timestamp.to_zoned_datetime(zone).datetime() == local {
            TimeConsistency::Consistent
        } else {
            TimeConsistency::Inconsistent
        }
    }

    fn has_valid_times(&self) -> bool {
        if parse_utc(&self.starts_at).is_none() {
            return false;
        }
        if let Some(local) = &self.local_starts_at
            && parse_local(local).is_none()
        {
            return false;
        }
        if let Some(zone) = &self.time_zone
            && parse_zone(zone).is_none()
        {
            return false;
        }
        true
    }
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Page {
    pub data: Vec<Event>,
    pub next_cursor: Option<String>,
}

#[derive(Debug, PartialEq, Eq)]
pub enum Progress {
    Complete,
    Next(String),
    Truncated,
}

#[derive(Debug, Error, PartialEq, Eq)]
pub enum PageError {
    #[error("page budget must be positive")]
    InvalidBudget,
    #[error("page exceeds the provider's documented maximum")]
    TooManyEvents,
    #[error("cursor is empty or repeated")]
    InvalidCursor,
    #[error("event has invalid required fields")]
    InvalidEvent,
    #[error("event ID is repeated")]
    DuplicateEvent,
    #[error("pagination already ended")]
    AlreadyFinished,
}

/// The caller supplies a transport. No production network implementation is included.
pub trait Transport {
    fn get_page(&mut self, cursor: Option<&str>) -> Result<Response, TransportError>;
}

#[derive(Debug, Error)]
#[error("transport failed")]
pub struct TransportError;

pub struct Response {
    pub status: u16,
    pub retry_after: Option<String>,
    pub body: Vec<u8>,
}

#[derive(Debug, Error, PartialEq, Eq)]
pub enum FetchError {
    #[error("invalid page budget")]
    InvalidBudget,
    #[error("transport failed")]
    Transport,
    #[error("provider rate limit reached")]
    RateLimited { retry_after: Option<String> },
    #[error("provider returned HTTP {0}")]
    Http(u16),
    #[error("response body too large")]
    BodyTooLarge,
    #[error("invalid page")]
    InvalidPage,
    #[error(transparent)]
    Pagination(#[from] PageError),
}

pub struct FetchResult {
    pub events: Vec<Event>,
    pub truncated: bool,
}

/// Bounded per-call pagination; this does not enforce shared per-IP quotas.
pub fn fetch<T: Transport>(transport: &mut T, max_pages: usize) -> Result<FetchResult, FetchError> {
    if !(1..=10).contains(&max_pages) {
        return Err(FetchError::InvalidBudget);
    }
    let mut collector = Collector::new(max_pages)?;
    let mut cursor: Option<String> = None;
    loop {
        let response = transport
            .get_page(cursor.as_deref())
            .map_err(|_| FetchError::Transport)?;
        if response.status == 429 {
            return Err(FetchError::RateLimited {
                retry_after: response.retry_after,
            });
        }
        if !(200..300).contains(&response.status) {
            return Err(FetchError::Http(response.status));
        }
        if response.body.len() > 1_048_576 {
            return Err(FetchError::BodyTooLarge);
        }
        // A missing cursor field is not proof that this was the final page.
        let value: serde_json::Value =
            serde_json::from_slice(&response.body).map_err(|_| FetchError::InvalidPage)?;
        if !value
            .as_object()
            .is_some_and(|object| object.contains_key("nextCursor"))
        {
            return Err(FetchError::InvalidPage);
        }
        let page: Page = serde_json::from_value(value).map_err(|_| FetchError::InvalidPage)?;
        match collector.accept(page)? {
            Progress::Next(next) => cursor = Some(next),
            Progress::Complete => {
                return Ok(FetchResult {
                    events: collector.events,
                    truncated: false,
                });
            }
            Progress::Truncated => {
                return Ok(FetchResult {
                    events: collector.events,
                    truncated: true,
                });
            }
        }
    }
}

pub struct Collector {
    max_pages: usize,
    pages: usize,
    cursors: HashSet<String>,
    event_ids: HashSet<u64>,
    finished: bool,
    pub events: Vec<Event>,
}

impl Collector {
    pub fn new(max_pages: usize) -> Result<Self, PageError> {
        if max_pages == 0 {
            return Err(PageError::InvalidBudget);
        }
        Ok(Self {
            max_pages,
            pages: 0,
            cursors: HashSet::new(),
            event_ids: HashSet::new(),
            finished: false,
            events: Vec::new(),
        })
    }

    pub fn accept(&mut self, page: Page) -> Result<Progress, PageError> {
        if self.finished {
            return Err(PageError::AlreadyFinished);
        }
        if page.data.len() > 100 {
            return Err(PageError::TooManyEvents);
        }
        if let Some(cursor) = &page.next_cursor
            && (cursor.trim().is_empty() || cursor.len() > 4096 || self.cursors.contains(cursor))
        {
            return Err(PageError::InvalidCursor);
        }
        let mut page_ids = HashSet::new();
        for event in &page.data {
            if event.id == 0
                || !event.has_valid_times()
                || [
                    &event.tournament,
                    &event.team_home,
                    &event.team_away,
                    &event.starts_at,
                ]
                .iter()
                .any(|value| value.trim().is_empty() || value.len() > 512)
            {
                return Err(PageError::InvalidEvent);
            }
            if self.event_ids.contains(&event.id) || !page_ids.insert(event.id) {
                return Err(PageError::DuplicateEvent);
            }
        }
        self.pages += 1;
        self.event_ids.extend(page_ids);
        self.events.extend(page.data);
        match page.next_cursor {
            None => {
                self.finished = true;
                Ok(Progress::Complete)
            }
            Some(_) if self.pages == self.max_pages => {
                self.finished = true;
                Ok(Progress::Truncated)
            }
            Some(cursor) => {
                self.cursors.insert(cursor.clone());
                Ok(Progress::Next(cursor))
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    const FIXTURE: &str = r#"{
      "data":[{"id":1,"tournament":"Example","teamHome":"Home","teamAway":"Away",
        "startsAt":"2026-11-16T00:00:00Z","localStartsAt":"2026-11-15T21:00:00",
        "timeZone":"America/Sao_Paulo","timeZoneConfidence":null}],
      "nextCursor":"page-2"
    }"#;

    struct FakeTransport {
        responses: std::collections::VecDeque<Response>,
        cursors: Vec<Option<String>>,
    }

    impl Transport for FakeTransport {
        fn get_page(&mut self, cursor: Option<&str>) -> Result<Response, TransportError> {
            self.cursors.push(cursor.map(str::to_owned));
            self.responses.pop_front().ok_or(TransportError)
        }
    }

    fn response(status: u16, body: &str) -> Response {
        Response {
            status,
            retry_after: None,
            body: body.as_bytes().to_vec(),
        }
    }

    #[test]
    fn fetches_next_cursor_and_stops_at_final_page() {
        let mut transport = FakeTransport {
            responses: [
                response(200, FIXTURE),
                response(200, r#"{"data":[],"nextCursor":null}"#),
            ]
            .into(),
            cursors: Vec::new(),
        };
        let result = fetch(&mut transport, 2).unwrap();
        assert!(!result.truncated);
        assert_eq!(result.events.len(), 1);
        assert_eq!(transport.cursors, vec![None, Some("page-2".into())]);
    }

    #[test]
    fn rate_limit_stops_without_retry_or_partial_success() {
        let mut limited = response(429, "");
        limited.retry_after = Some("60".into());
        let mut transport = FakeTransport {
            responses: [response(200, FIXTURE), limited].into(),
            cursors: Vec::new(),
        };
        assert!(
            matches!(fetch(&mut transport, 3), Err(FetchError::RateLimited { retry_after: Some(value) }) if value == "60")
        );
        assert_eq!(transport.cursors.len(), 2);
    }

    #[test]
    fn budget_truncates_and_invalid_envelopes_fail() {
        let mut transport = FakeTransport {
            responses: [response(200, FIXTURE)].into(),
            cursors: Vec::new(),
        };
        assert!(fetch(&mut transport, 1).unwrap().truncated);
        assert_eq!(transport.cursors.len(), 1);
        let mut invalid = FakeTransport {
            responses: [response(200, r#"{"data":[]}"#)].into(),
            cursors: Vec::new(),
        };
        assert!(matches!(
            fetch(&mut invalid, 1),
            Err(FetchError::InvalidPage)
        ));
        assert!(matches!(
            fetch(&mut invalid, 11),
            Err(FetchError::InvalidBudget)
        ));
    }

    #[test]
    fn http_failures_do_not_retry_or_return_partial_results() {
        for status in [403, 500, 503] {
            let mut transport = FakeTransport {
                responses: [response(200, FIXTURE), response(status, "{}")].into(),
                cursors: Vec::new(),
            };
            assert!(
                matches!(fetch(&mut transport, 3), Err(FetchError::Http(code)) if code == status)
            );
            assert_eq!(transport.cursors.len(), 2);
        }
    }

    #[test]
    fn transport_failure_after_first_page_is_not_success() {
        let mut transport = FakeTransport {
            responses: [response(200, FIXTURE)].into(),
            cursors: Vec::new(),
        };
        assert!(matches!(
            fetch(&mut transport, 3),
            Err(FetchError::Transport)
        ));
        assert_eq!(transport.cursors.len(), 2);
    }

    #[test]
    fn malformed_json_and_invalid_page_shapes_are_rejected() {
        for body in [
            "{",
            "[]",
            r#"{"data":null,"nextCursor":null}"#,
            r#"{"data":[],"nextCursor":42}"#,
            r#"{"data":[{"id":1}],"nextCursor":null}"#,
        ] {
            let mut transport = FakeTransport {
                responses: [response(200, body)].into(),
                cursors: Vec::new(),
            };
            assert!(matches!(
                fetch(&mut transport, 1),
                Err(FetchError::InvalidPage)
            ));
            assert_eq!(transport.cursors.len(), 1);
        }
    }

    #[test]
    fn response_body_limit_is_enforced_after_transport_returns() {
        let mut transport = FakeTransport {
            responses: [Response {
                status: 200,
                retry_after: None,
                body: vec![b' '; 1_048_577],
            }]
            .into(),
            cursors: Vec::new(),
        };
        assert!(matches!(
            fetch(&mut transport, 1),
            Err(FetchError::BodyTooLarge)
        ));
    }

    #[test]
    fn invalid_budget_and_empty_cursor_stop_further_requests() {
        let mut transport = FakeTransport {
            responses: [response(200, r#"{"data":[],"nextCursor":""}"#)].into(),
            cursors: Vec::new(),
        };
        assert!(matches!(
            fetch(&mut transport, 0),
            Err(FetchError::InvalidBudget)
        ));
        assert!(transport.cursors.is_empty());
        assert!(matches!(
            fetch(&mut transport, 2),
            Err(FetchError::Pagination(PageError::InvalidCursor))
        ));
        assert_eq!(transport.cursors.len(), 1);
    }

    #[test]
    fn invalid_event_rejects_entire_page_without_changing_collector() {
        let mut collector = Collector::new(3).unwrap();
        let mut page: Page = serde_json::from_str(FIXTURE).unwrap();
        page.data[0].team_home = "   ".into();
        assert_eq!(collector.accept(page), Err(PageError::InvalidEvent));
        assert!(collector.events.is_empty());
        assert_eq!(
            collector
                .accept(serde_json::from_str(FIXTURE).unwrap())
                .unwrap(),
            Progress::Next("page-2".into())
        );
    }

    #[test]
    fn duplicate_event_across_pages_rejects_page_atomically() {
        let mut collector = Collector::new(3).unwrap();
        collector
            .accept(serde_json::from_str(FIXTURE).unwrap())
            .unwrap();
        let mut duplicate: Page = serde_json::from_str(FIXTURE).unwrap();
        duplicate.next_cursor = Some("page-3".into());
        assert_eq!(collector.accept(duplicate), Err(PageError::DuplicateEvent));
        assert_eq!(collector.events.len(), 1);
        let final_page: Page = serde_json::from_str(r#"{"data":[],"nextCursor":null}"#).unwrap();
        assert_eq!(collector.accept(final_page), Ok(Progress::Complete));
    }

    #[test]
    fn duplicate_events_within_page_are_rejected() {
        let json = r#"{"data":[
          {"id":1,"tournament":"Example","teamHome":"A","teamAway":"B","startsAt":"2026-11-16T00:00:00Z"},
          {"id":1,"tournament":"Example","teamHome":"C","teamAway":"D","startsAt":"2026-11-16T01:00:00Z"}
        ],"nextCursor":null}"#;
        let page: Page = serde_json::from_str(json).unwrap();
        assert_eq!(
            Collector::new(1).unwrap().accept(page),
            Err(PageError::DuplicateEvent)
        );
    }

    #[test]
    fn invalid_identifiers_and_long_fields_are_rejected() {
        for variant in 0..3 {
            let mut page: Page = serde_json::from_str(FIXTURE).unwrap();
            match variant {
                0 => page.data[0].id = 0,
                1 => page.data[0].starts_at = String::new(),
                _ => page.data[0].team_away = "x".repeat(513),
            }
            assert_eq!(
                Collector::new(1).unwrap().accept(page),
                Err(PageError::InvalidEvent)
            );
        }
    }

    #[test]
    fn blank_and_oversized_cursors_are_rejected() {
        for cursor in [" ".to_owned(), "x".repeat(4097)] {
            let mut page: Page = serde_json::from_str(FIXTURE).unwrap();
            page.next_cursor = Some(cursor);
            assert_eq!(
                Collector::new(1).unwrap().accept(page),
                Err(PageError::InvalidCursor)
            );
        }
    }

    #[test]
    fn preserves_timezone_uncertainty_and_utc_date_boundary() {
        let page: Page = serde_json::from_str(FIXTURE).unwrap();
        let event = &page.data[0];
        assert_eq!(event.starts_at, "2026-11-16T00:00:00Z");
        assert_eq!(
            event.local_starts_at.as_deref(),
            Some("2026-11-15T21:00:00")
        );
        assert_eq!(event.time_zone_confidence, None);
    }

    #[test]
    fn rejects_invalid_dates_offsets_local_times_and_unknown_zones() {
        for variant in 0..5 {
            let mut page: Page = serde_json::from_str(FIXTURE).unwrap();
            match variant {
                0 => page.data[0].starts_at = "2026-02-30T00:00:00Z".into(),
                1 => page.data[0].starts_at = "2026-11-16T00:00:00-03:00".into(),
                2 => page.data[0].local_starts_at = Some("2026-11-31T21:00:00".into()),
                3 => page.data[0].time_zone = Some("Invalid/Timezone".into()),
                _ => page.data[0].starts_at = "not-a-date".into(),
            }
            assert_eq!(
                Collector::new(1).unwrap().accept(page),
                Err(PageError::InvalidEvent)
            );
        }
    }

    #[test]
    fn accepts_absent_local_timezone_and_fractional_utc() {
        let mut page: Page = serde_json::from_str(FIXTURE).unwrap();
        page.data[0].starts_at = "2026-11-16T00:00:00.000000Z".into();
        page.data[0].local_starts_at = None;
        page.data[0].time_zone = None;
        assert_eq!(
            Collector::new(1).unwrap().accept(page),
            Ok(Progress::Truncated)
        );
    }

    #[test]
    fn accepts_only_zero_offsets_for_utc_instants() {
        for instant in [
            "2026-11-16T00:00:00Z",
            "2026-11-16T00:00:00+00:00",
            "2026-11-16T00:00:00-00:00",
        ] {
            let mut page: Page = serde_json::from_str(FIXTURE).unwrap();
            page.data[0].starts_at = instant.into();
            assert_eq!(page.data[0].time_consistency(), TimeConsistency::Consistent);
        }
        for instant in [
            "2026-11-15T21:00:00-03:00",
            "2026-11-16T01:00:00+01:00",
            "2026-11-16T00:00:00",
        ] {
            let mut page: Page = serde_json::from_str(FIXTURE).unwrap();
            page.data[0].starts_at = instant.into();
            assert_eq!(page.data[0].time_consistency(), TimeConsistency::Invalid);
        }
    }

    #[test]
    fn rejects_timezone_offsets_in_local_civil_time() {
        for local in [
            "2026-11-15T21:00:00Z",
            "2026-11-15T21:00:00-03:00",
            "2026-11-15T21:00:00+00:00",
        ] {
            let mut page: Page = serde_json::from_str(FIXTURE).unwrap();
            page.data[0].local_starts_at = Some(local.into());
            assert_eq!(page.data[0].time_consistency(), TimeConsistency::Invalid);
        }
    }

    #[test]
    fn compares_utc_to_local_across_date_boundary_without_changing_confidence() {
        let mut page: Page = serde_json::from_str(FIXTURE).unwrap();
        assert_eq!(page.data[0].time_consistency(), TimeConsistency::Consistent);
        assert_eq!(page.data[0].time_zone_confidence, None);
        page.data[0].local_starts_at = Some("2026-11-16T21:00:00".into());
        page.data[0].time_zone_confidence = Some("medium".into());
        assert_eq!(
            page.data[0].time_consistency(),
            TimeConsistency::Inconsistent
        );
        assert_eq!(page.data[0].time_zone_confidence.as_deref(), Some("medium"));
    }

    #[test]
    fn missing_local_or_zone_is_unverifiable() {
        let mut page: Page = serde_json::from_str(FIXTURE).unwrap();
        page.data[0].local_starts_at = None;
        assert_eq!(
            page.data[0].time_consistency(),
            TimeConsistency::Unverifiable
        );
        page.data[0].local_starts_at = Some("2026-11-15T21:00:00".into());
        page.data[0].time_zone = None;
        assert_eq!(
            page.data[0].time_consistency(),
            TimeConsistency::Unverifiable
        );
    }

    #[test]
    fn compares_both_instants_in_repeated_daylight_saving_hour() {
        let mut page: Page = serde_json::from_str(FIXTURE).unwrap();
        page.data[0].time_zone = Some("America/New_York".into());
        page.data[0].local_starts_at = Some("2026-11-01T01:30:00".into());
        for instant in ["2026-11-01T05:30:00Z", "2026-11-01T06:30:00Z"] {
            page.data[0].starts_at = instant.into();
            assert_eq!(page.data[0].time_consistency(), TimeConsistency::Consistent);
        }
        page.data[0].starts_at = "invalid".into();
        assert_eq!(page.data[0].time_consistency(), TimeConsistency::Invalid);
    }

    #[test]
    fn accepts_utc_and_iana_alias_from_bundled_timezone_database() {
        let mut page: Page = serde_json::from_str(FIXTURE).unwrap();
        page.data[0].time_zone = Some("UTC".into());
        page.data[0].local_starts_at = Some("2026-11-16T00:00:00".into());
        assert_eq!(page.data[0].time_consistency(), TimeConsistency::Consistent);

        page.data[0].time_zone = Some("US/Eastern".into());
        page.data[0].starts_at = "2026-11-01T05:30:00Z".into();
        page.data[0].local_starts_at = Some("2026-11-01T01:30:00".into());
        assert_eq!(page.data[0].time_consistency(), TimeConsistency::Consistent);
    }

    #[test]
    fn collects_pages_and_marks_completion() {
        let mut collector = Collector::new(2).unwrap();
        assert_eq!(
            collector
                .accept(serde_json::from_str(FIXTURE).unwrap())
                .unwrap(),
            Progress::Next("page-2".into())
        );
        let final_page: Page = serde_json::from_str(r#"{"data":[],"nextCursor":null}"#).unwrap();
        assert_eq!(collector.accept(final_page).unwrap(), Progress::Complete);
        assert_eq!(collector.events.len(), 1);
    }

    #[test]
    fn bounds_pages_and_reports_truncation() {
        let mut collector = Collector::new(1).unwrap();
        assert_eq!(
            collector
                .accept(serde_json::from_str(FIXTURE).unwrap())
                .unwrap(),
            Progress::Truncated
        );
        assert_eq!(Collector::new(0).err(), Some(PageError::InvalidBudget));
    }

    #[test]
    fn rejects_repeated_cursor_without_appending() {
        let mut collector = Collector::new(3).unwrap();
        collector
            .accept(serde_json::from_str(FIXTURE).unwrap())
            .unwrap();
        assert_eq!(
            collector.accept(serde_json::from_str(FIXTURE).unwrap()),
            Err(PageError::InvalidCursor)
        );
        assert_eq!(collector.events.len(), 1);
    }

    #[test]
    fn rejects_oversized_page() {
        let mut page: Page = serde_json::from_str(FIXTURE).unwrap();
        let event = page.data.pop().unwrap();
        page.data = std::iter::repeat_with(|| Event {
            id: event.id,
            tournament: event.tournament.clone(),
            team_home: event.team_home.clone(),
            team_away: event.team_away.clone(),
            starts_at: event.starts_at.clone(),
            local_starts_at: event.local_starts_at.clone(),
            time_zone: event.time_zone.clone(),
            time_zone_confidence: event.time_zone_confidence.clone(),
        })
        .take(101)
        .collect();
        assert_eq!(
            Collector::new(1).unwrap().accept(page),
            Err(PageError::TooManyEvents)
        );
    }
}
