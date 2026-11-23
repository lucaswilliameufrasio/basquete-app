//! Loopback-only experimental transport; not suitable for production or TLS.

use std::io::Read;
use std::net::SocketAddr;
use std::sync::{Arc, Mutex};
use std::time::{Duration, Instant};

use time::{Date, Duration as DateDuration, OffsetDateTime};

use super::{Response, Transport, TransportError};

pub struct TournamentQuery {
    tournament_id: u64,
    from: Date,
    to: Date,
}

impl TournamentQuery {
    /// Dates and the supplied current day are UTC; the end is exclusive.
    pub fn new(
        tournament_id: u64,
        from: Date,
        to: Date,
        today: Date,
    ) -> Result<Self, &'static str> {
        let query = Self {
            tournament_id,
            from,
            to,
        };
        query.validate(today)?;
        Ok(query)
    }

    fn validate(&self, today: Date) -> Result<(), &'static str> {
        if self.tournament_id == 0 || self.from >= self.to {
            return Err("positive tournament and nonempty UTC date range required");
        }
        let earliest = today
            .checked_sub(DateDuration::days(7))
            .ok_or("invalid current day")?;
        let latest = today
            .checked_add(DateDuration::days(30))
            .ok_or("invalid current day")?;
        if self.from < earliest || self.to > latest {
            return Err("date range outside provider window");
        }
        Ok(())
    }
}

pub struct LocalHttp {
    agent: ureq::Agent,
    endpoint: String,
    query: TournamentQuery,
    utc_day: Box<dyn FnMut() -> Date>,
    quota: SharedQuota,
    monotonic_now: Box<dyn FnMut() -> Instant>,
}

/// Coordinates only transports explicitly sharing this gate in one process.
#[derive(Clone, Default)]
pub struct SharedQuota(Arc<Mutex<QuotaState>>);

#[derive(Default)]
struct QuotaState {
    blocked_until: Option<Instant>,
    blocked_indefinitely: bool,
}

impl LocalHttp {
    pub fn new(
        address: SocketAddr,
        timeout: Duration,
        query: TournamentQuery,
    ) -> Result<Self, &'static str> {
        Self::with_clock(address, timeout, query, || OffsetDateTime::now_utc().date())
    }

    pub fn with_clock(
        address: SocketAddr,
        timeout: Duration,
        query: TournamentQuery,
        utc_day: impl FnMut() -> Date + 'static,
    ) -> Result<Self, &'static str> {
        Self::with_shared_quota(
            address,
            timeout,
            query,
            utc_day,
            SharedQuota::default(),
            Instant::now,
        )
    }

    pub fn with_shared_quota(
        address: SocketAddr,
        timeout: Duration,
        query: TournamentQuery,
        utc_day: impl FnMut() -> Date + 'static,
        quota: SharedQuota,
        monotonic_now: impl FnMut() -> Instant + 'static,
    ) -> Result<Self, &'static str> {
        if !address.ip().is_loopback() || timeout.is_zero() {
            return Err("loopback address and positive timeout required");
        }
        let config = ureq::Agent::config_builder()
            .timeout_global(Some(timeout))
            .http_status_as_error(false)
            .max_redirects(0)
            .proxy(None)
            .build();
        Ok(Self {
            agent: config.into(),
            endpoint: format!("http://{address}/api/v1/events"),
            query,
            utc_day: Box::new(utc_day),
            quota,
            monotonic_now: Box::new(monotonic_now),
        })
    }
}

impl Transport for LocalHttp {
    fn get_page(&mut self, cursor: Option<&str>) -> Result<Response, TransportError> {
        if self.query.validate((self.utc_day)()).is_err() {
            return Err(TransportError);
        }
        let mut quota = self.quota.0.lock().map_err(|_| TransportError)?;
        let now = (self.monotonic_now)();
        if quota.blocked_indefinitely || quota.blocked_until.is_some_and(|until| now < until) {
            return Err(TransportError);
        }
        quota.blocked_until = None;
        let mut request = self
            .agent
            .get(&self.endpoint)
            .query("tournament_id", self.query.tournament_id.to_string())
            .query("from", self.query.from.to_string())
            .query("to", self.query.to.to_string());
        if let Some(cursor) = cursor {
            request = request.query("cursor", cursor);
        }
        let mut response = request.call().map_err(|_| TransportError)?;
        let status = response.status().as_u16();
        let retry_after = response
            .headers()
            .get("retry-after")
            .and_then(|header| header.to_str().ok())
            .map(str::to_owned);
        if status == 429 {
            let seconds = retry_after
                .as_deref()
                .and_then(|value| value.parse::<u64>().ok())
                .filter(|seconds| *seconds > 0);
            let retry_at = seconds.and_then(|seconds| {
                (self.monotonic_now)().checked_add(Duration::from_secs(seconds))
            });
            quota.blocked_until = retry_at;
            quota.blocked_indefinitely = retry_at.is_none();
        }
        let mut body = Vec::new();
        response
            .body_mut()
            .as_reader()
            .take(1_048_577)
            .read_to_end(&mut body)
            .map_err(|_| TransportError)?;
        Ok(Response {
            status,
            retry_after,
            body,
        })
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::experimental_brazil::{FetchError, fetch};
    use std::io::{Read, Write};
    use std::net::{Ipv4Addr, TcpListener};
    use std::thread;
    use time::macros::date;

    fn query() -> TournamentQuery {
        TournamentQuery::new(
            150914,
            date!(2026 - 11 - 16),
            date!(2026 - 11 - 17),
            date!(2026 - 11 - 16),
        )
        .unwrap()
    }

    fn local_transport(address: SocketAddr, timeout: Duration) -> LocalHttp {
        LocalHttp::with_clock(address, timeout, query(), || date!(2026 - 11 - 16)).unwrap()
    }

    fn server(response: Vec<u8>, delay: Duration) -> SocketAddr {
        let listener = TcpListener::bind((Ipv4Addr::LOCALHOST, 0)).unwrap();
        let address = listener.local_addr().unwrap();
        thread::spawn(move || {
            let (mut stream, _) = listener.accept().unwrap();
            let mut request = [0u8; 1024];
            let _ = stream.read(&mut request);
            thread::sleep(delay);
            let _ = stream.write_all(&response);
        });
        address
    }

    #[test]
    fn reads_local_response_and_preserves_rate_limit() {
        let reply = b"HTTP/1.1 429 Too Many Requests\r\nRetry-After: 30\r\nContent-Length: 0\r\nConnection: close\r\n\r\n";
        let address = server(reply.to_vec(), Duration::ZERO);
        let mut transport = local_transport(address, Duration::from_secs(2));
        assert!(matches!(
            fetch(&mut transport, 1),
            Err(FetchError::RateLimited { retry_after: Some(value) }) if value == "30"
        ));
        assert!(transport.get_page(None).is_err());
    }

    #[test]
    fn shared_gate_blocks_until_retry_after_then_allows_another_instance() {
        let listener = TcpListener::bind((Ipv4Addr::LOCALHOST, 0)).unwrap();
        let address = listener.local_addr().unwrap();
        let server = thread::spawn(move || {
            let body = r#"{"data":[],"nextCursor":null}"#;
            let success = format!(
                "HTTP/1.1 200 OK\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",
                body.len(),
                body
            );
            for reply in [
                "HTTP/1.1 429 Too Many Requests\r\nRetry-After: 30\r\nContent-Length: 0\r\nConnection: close\r\n\r\n",
                success.as_str(),
            ] {
                let (mut stream, _) = listener.accept().unwrap();
                let mut request = [0u8; 1024];
                assert!(stream.read(&mut request).unwrap() > 0);
                stream.write_all(reply.as_bytes()).unwrap();
            }
        });
        let gate = SharedQuota::default();
        let instant = Instant::now();
        let clock = std::rc::Rc::new(std::cell::Cell::new(instant));
        let day = || date!(2026 - 11 - 16);
        let first_clock = clock.clone();
        let mut first = LocalHttp::with_shared_quota(
            address,
            Duration::from_secs(2),
            query(),
            day,
            gate.clone(),
            move || first_clock.get(),
        )
        .unwrap();
        let second_clock = clock.clone();
        let mut second = LocalHttp::with_shared_quota(
            address,
            Duration::from_secs(2),
            query(),
            day,
            gate,
            move || second_clock.get(),
        )
        .unwrap();
        assert!(matches!(
            fetch(&mut first, 1),
            Err(FetchError::RateLimited { .. })
        ));
        clock.set(instant + Duration::from_secs(29));
        assert!(second.get_page(None).is_err());
        clock.set(instant + Duration::from_secs(30));
        assert!(!fetch(&mut second, 1).unwrap().truncated);
        server.join().unwrap();
    }

    #[test]
    fn missing_retry_after_blocks_instance_conservatively() {
        let reply =
            b"HTTP/1.1 429 Too Many Requests\r\nContent-Length: 0\r\nConnection: close\r\n\r\n";
        let address = server(reply.to_vec(), Duration::ZERO);
        let mut transport = local_transport(address, Duration::from_secs(2));
        assert!(matches!(
            fetch(&mut transport, 1),
            Err(FetchError::RateLimited { retry_after: None })
        ));
        assert!(transport.get_page(None).is_err());
    }

    #[test]
    fn revalidates_window_before_next_request_after_utc_midnight() {
        let listener = TcpListener::bind((Ipv4Addr::LOCALHOST, 0)).unwrap();
        let address = listener.local_addr().unwrap();
        let (sender, receiver) = std::sync::mpsc::channel();
        let server = thread::spawn(move || {
            let (mut stream, _) = listener.accept().unwrap();
            let mut request = [0u8; 1024];
            assert!(stream.read(&mut request).unwrap() > 0);
            let body = r#"{"data":[],"nextCursor":"next"}"#;
            write!(
                stream,
                "HTTP/1.1 200 OK\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",
                body.len(),
                body
            )
            .unwrap();
            sender.send(listener).unwrap();
        });
        let day = date!(2026 - 11 - 16);
        let from = date!(2026 - 11 - 09);
        let query = TournamentQuery::new(1, from, day, day).unwrap();
        let mut calls = 0;
        let mut transport =
            LocalHttp::with_clock(address, Duration::from_secs(2), query, move || {
                calls += 1;
                if calls == 1 {
                    day
                } else {
                    day.next_day().unwrap()
                }
            })
            .unwrap();
        assert!(matches!(
            fetch(&mut transport, 2),
            Err(FetchError::Transport)
        ));
        let listener = receiver.recv_timeout(Duration::from_secs(2)).unwrap();
        server.join().unwrap();
        listener.set_nonblocking(true).unwrap();
        assert!(
            matches!(listener.accept(), Err(error) if error.kind() == std::io::ErrorKind::WouldBlock)
        );
    }

    #[test]
    fn reads_valid_empty_page_from_local_server() {
        let body = r#"{"data":[],"nextCursor":null}"#;
        let reply = format!(
            "HTTP/1.1 200 OK\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",
            body.len(),
            body
        );
        let address = server(reply.into_bytes(), Duration::ZERO);
        let mut transport = local_transport(address, Duration::from_secs(2));
        let result = fetch(&mut transport, 1).unwrap();
        assert!(result.events.is_empty());
        assert!(!result.truncated);
    }

    #[test]
    fn paginates_and_encodes_cursor_in_http_request() {
        let listener = TcpListener::bind((Ipv4Addr::LOCALHOST, 0)).unwrap();
        let address = listener.local_addr().unwrap();
        let (sender, receiver) = std::sync::mpsc::channel();
        thread::spawn(move || {
            for body in [
                r#"{"data":[],"nextCursor":"a+b/c= d&x"}"#,
                r#"{"data":[],"nextCursor":null}"#,
            ] {
                let (mut stream, _) = listener.accept().unwrap();
                stream
                    .set_read_timeout(Some(Duration::from_secs(2)))
                    .unwrap();
                let mut request = [0u8; 4096];
                let size = stream.read(&mut request).unwrap();
                sender
                    .send(String::from_utf8_lossy(&request[..size]).into_owned())
                    .unwrap();
                let reply = format!(
                    "HTTP/1.1 200 OK\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",
                    body.len(),
                    body
                );
                stream.write_all(reply.as_bytes()).unwrap();
            }
        });
        let mut transport = local_transport(address, Duration::from_secs(2));
        let result = fetch(&mut transport, 2).unwrap();
        assert!(!result.truncated);
        let first = receiver.recv_timeout(Duration::from_secs(2)).unwrap();
        let second = receiver.recv_timeout(Duration::from_secs(2)).unwrap();
        assert!(first.starts_with(
            "GET /api/v1/events?tournament_id=150914&from=2026-11-16&to=2026-11-17 HTTP/1.1"
        ));
        assert!(
            second.starts_with("GET /api/v1/events?tournament_id=150914&from=2026-11-16&to=2026-11-17&cursor=a%2Bb%2Fc%3D%20d%26x HTTP/1.1"),
            "{second}"
        );
    }

    #[test]
    fn does_not_follow_redirect_to_another_local_server() {
        let destination = TcpListener::bind((Ipv4Addr::LOCALHOST, 0)).unwrap();
        destination.set_nonblocking(true).unwrap();
        let redirect = format!(
            "HTTP/1.1 302 Found\r\nLocation: http://{}/target\r\nContent-Length: 0\r\nConnection: close\r\n\r\n",
            destination.local_addr().unwrap()
        );
        let address = server(redirect.into_bytes(), Duration::ZERO);
        let mut transport = local_transport(address, Duration::from_secs(2));
        assert!(matches!(
            fetch(&mut transport, 1),
            Err(FetchError::Http(302))
        ));
        assert!(
            matches!(destination.accept(), Err(error) if error.kind() == std::io::ErrorKind::WouldBlock)
        );
    }

    #[test]
    fn caps_response_and_reports_oversized_body() {
        let mut reply =
            b"HTTP/1.1 200 OK\r\nContent-Length: 1048577\r\nConnection: close\r\n\r\n".to_vec();
        reply.extend(vec![b' '; 1_048_577]);
        let address = server(reply, Duration::ZERO);
        let mut transport = local_transport(address, Duration::from_secs(5));
        assert!(matches!(
            fetch(&mut transport, 1),
            Err(FetchError::BodyTooLarge)
        ));
    }

    #[test]
    fn times_out_on_slow_server() {
        let address = server(Vec::new(), Duration::from_millis(500));
        let mut transport = local_transport(address, Duration::from_millis(100));
        assert!(matches!(
            fetch(&mut transport, 1),
            Err(FetchError::Transport)
        ));
    }

    #[test]
    fn rejects_non_loopback_and_zero_timeout() {
        let remote = SocketAddr::from(([192, 0, 2, 1], 80));
        let local = SocketAddr::from(([127, 0, 0, 1], 80));
        assert!(LocalHttp::new(remote, Duration::from_secs(1), query()).is_err());
        assert!(LocalHttp::new(local, Duration::ZERO, query()).is_err());
    }

    #[test]
    fn rejects_invalid_tournament_and_date_range() {
        let day = date!(2026 - 11 - 16);
        let next = date!(2026 - 11 - 17);
        assert!(TournamentQuery::new(0, day, next, day).is_err());
        assert!(TournamentQuery::new(1, day, day, day).is_err());
        assert!(TournamentQuery::new(1, next, day, day).is_err());
    }

    #[test]
    fn validates_inclusive_from_and_exclusive_to_window() {
        let today = date!(2026 - 01 - 01);
        let earliest = date!(2025 - 12 - 25);
        let latest = date!(2026 - 01 - 31);
        assert!(TournamentQuery::new(1, earliest, latest, today).is_ok());
        assert!(TournamentQuery::new(1, date!(2025 - 12 - 24), latest, today).is_err());
        assert!(TournamentQuery::new(1, earliest, date!(2026 - 02 - 01), today).is_err());
        assert!(TournamentQuery::new(1, latest, latest, today).is_err());
        assert!(
            TournamentQuery::new(1, Date::MIN, Date::MIN.next_day().unwrap(), Date::MIN).is_err()
        );
        assert!(
            TournamentQuery::new(1, Date::MAX.previous_day().unwrap(), Date::MAX, Date::MAX)
                .is_err()
        );
    }
}
