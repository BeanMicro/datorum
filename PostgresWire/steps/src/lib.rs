//! Step definitions for the Gherkin features under `PostgresWire/features`.
//!
//! The MessageFlow features describe individual PostgreSQL protocol messages,
//! so these steps drive the server with a raw frontend/backend codec rather
//! than a higher-level client, which would hide the very messages under test.
//! Each scenario gets its own server on an ephemeral port with its own
//! in-memory database, so scenarios cannot observe one another.

use bytes::{Buf, Bytes, BytesMut};
use cucumber::{given, then, when};
use futures::{SinkExt, StreamExt};
use tokio::net::{TcpListener, TcpStream};
use tokio_util::codec::{Decoder, Encoder, Framed};

use pgwire::api::auth::md5pass::hash_md5_password;
use pgwire::error::PgWireError;
use pgwire::messages::extendedquery::{Bind, Describe, Execute, Parse, Sync as SyncMessage};
use pgwire::messages::response::TransactionStatus;
use pgwire::messages::simplequery::Query;
use pgwire::messages::startup::{Authentication, Password, PasswordMessageFamily, Startup};
use pgwire::messages::{PgWireBackendMessage, PgWireFrontendMessage};

use datorum_postgres_wire::{DEMO_PASSWORD, serve};

/// OID of the PostgreSQL `int4` type, used when declaring parameter types.
const OID_INT4: u32 = 23;

/// Frontend view of the protocol: encodes frontend messages, decodes backend
/// ones. pgwire ships an equivalent codec but marks it `#[non_exhaustive]`,
/// which makes it unconstructable from outside that crate.
#[derive(Debug)]
struct ClientCodec;

impl Decoder for ClientCodec {
    type Item = PgWireBackendMessage;
    type Error = PgWireError;

    fn decode(&mut self, src: &mut BytesMut) -> Result<Option<Self::Item>, Self::Error> {
        PgWireBackendMessage::decode(src)
    }
}

impl Encoder<PgWireFrontendMessage> for ClientCodec {
    type Error = PgWireError;

    fn encode(
        &mut self,
        item: PgWireFrontendMessage,
        dst: &mut BytesMut,
    ) -> Result<(), Self::Error> {
        item.encode(dst)
    }
}

type Connection = Framed<TcpStream, ClientCodec>;

#[derive(Debug, Default, cucumber::World)]
pub struct PostgresWireWorld {
    // Toy arithmetic and string state, used by example.feature.
    numbers: Vec<i32>,
    result: Option<i32>,
    text: Option<String>,

    /// Wire connection to the server under test, once one has been opened.
    connection: Option<Connection>,
    /// Every backend message received since the connection was opened.
    received: Vec<PgWireBackendMessage>,
}

impl PostgresWireWorld {
    /// Start a server on an ephemeral port and connect to it.
    async fn connect(&mut self) {
        let listener = TcpListener::bind("127.0.0.1:0")
            .await
            .expect("failed to bind an ephemeral port");
        let addr = listener
            .local_addr()
            .expect("listener has no local address");

        tokio::spawn(async move {
            let _ = serve(listener).await;
        });

        let socket = TcpStream::connect(addr)
            .await
            .expect("failed to connect to the server under test");
        self.connection = Some(Framed::new(socket, ClientCodec));
    }

    fn connection(&mut self) -> &mut Connection {
        self.connection
            .as_mut()
            .expect("no connection: a `client is authenticated` step must run first")
    }

    async fn send(&mut self, message: PgWireFrontendMessage) {
        self.connection()
            .send(message)
            .await
            .expect("failed to send a frontend message");
    }

    /// Read backend messages until one satisfies `stop`, recording each.
    async fn read_until<F>(&mut self, stop: F)
    where
        F: Fn(&PgWireBackendMessage) -> bool,
    {
        loop {
            let message = self
                .connection()
                .next()
                .await
                .expect("server closed the connection")
                .expect("failed to decode a backend message");

            let done = stop(&message);
            self.received.push(message);
            if done {
                return;
            }
        }
    }

    async fn read_until_ready_for_query(&mut self) {
        self.read_until(|message| matches!(message, PgWireBackendMessage::ReadyForQuery(_)))
            .await;
    }

    /// Perform the startup handshake, answering the MD5 password challenge.
    async fn authenticate(&mut self, application_name: &str) {
        self.connect().await;

        let mut startup = Startup::new();
        startup
            .parameters
            .insert("user".to_owned(), "any_user".to_owned());
        startup
            .parameters
            .insert("database".to_owned(), "datorum".to_owned());
        startup
            .parameters
            .insert("application_name".to_owned(), application_name.to_owned());
        self.send(PgWireFrontendMessage::Startup(startup)).await;

        // The server challenges with MD5 before it will send AuthenticationOk.
        self.read_until(|message| {
            matches!(
                message,
                PgWireBackendMessage::Authentication(
                    Authentication::MD5Password(_) | Authentication::Ok
                )
            )
        })
        .await;

        if let Some(PgWireBackendMessage::Authentication(Authentication::MD5Password(salt))) =
            self.received.last()
        {
            let hashed = hash_md5_password("any_user", DEMO_PASSWORD, salt);
            self.send(PgWireFrontendMessage::PasswordMessageFamily(
                PasswordMessageFamily::Password(Password::new(hashed)),
            ))
            .await;
        }

        self.read_until_ready_for_query().await;
    }

    fn command_tags(&self) -> Vec<&str> {
        self.received
            .iter()
            .filter_map(|message| match message {
                PgWireBackendMessage::CommandComplete(complete) => Some(complete.tag.as_str()),
                _ => None,
            })
            .collect()
    }
}

/// Split a `DataRow` body back into its column values.
///
/// The body is a field count followed by, per column, a big-endian length and
/// that many bytes; a length of -1 marks a NULL.
fn decode_data_row(data: &BytesMut, field_count: i16) -> Vec<Option<String>> {
    let mut buf = Bytes::copy_from_slice(data.as_ref());
    let mut values = Vec::with_capacity(field_count as usize);

    for _ in 0..field_count {
        if buf.remaining() < 4 {
            break;
        }
        let len = buf.get_i32();
        if len < 0 {
            values.push(None);
        } else {
            let len = len as usize;
            let bytes = buf.copy_to_bytes(len.min(buf.remaining()));
            values.push(Some(String::from_utf8_lossy(&bytes).into_owned()));
        }
    }

    values
}

// ---------------------------------------------------------------------------
// example.feature: arithmetic and strings
// ---------------------------------------------------------------------------

#[given(expr = "two numbers {int} and {int}")]
fn two_numbers(world: &mut PostgresWireWorld, a: i32, b: i32) {
    world.numbers = vec![a, b];
}

#[when("they are added")]
fn they_are_added(world: &mut PostgresWireWorld) {
    world.result = Some(world.numbers.iter().sum());
}

#[then(expr = "the result should be {int}")]
fn result_should_be(world: &mut PostgresWireWorld, expected: i32) {
    assert_eq!(world.result, Some(expected));
}

#[given(expr = "a string {string}")]
fn a_string(world: &mut PostgresWireWorld, text: String) {
    world.text = Some(text);
}

#[when(expr = "compared to {string}")]
fn compared_to(world: &mut PostgresWireWorld, other: String) {
    let text = world.text.as_ref().expect("no text set");
    assert_eq!(text, &other);
}

#[then("they should be equal")]
fn they_should_be_equal(_world: &mut PostgresWireWorld) {}

// ---------------------------------------------------------------------------
// MessageFlow/Startup.feature
// ---------------------------------------------------------------------------

#[given(expr = "client sends StartupMessage\\(application_name: {string}\\)")]
async fn client_sends_startup(world: &mut PostgresWireWorld, application_name: String) {
    world.connect().await;

    let mut startup = Startup::new();
    startup
        .parameters
        .insert("user".to_owned(), "any_user".to_owned());
    startup
        .parameters
        .insert("database".to_owned(), "datorum".to_owned());
    startup
        .parameters
        .insert("application_name".to_owned(), application_name);
    world.send(PgWireFrontendMessage::Startup(startup)).await;
}

#[when("server processes StartupMessage")]
async fn server_processes_startup(world: &mut PostgresWireWorld) {
    // Startup completes only once the MD5 challenge has been answered, so the
    // handshake is driven to ReadyForQuery here.
    world
        .read_until(|message| {
            matches!(
                message,
                PgWireBackendMessage::Authentication(
                    Authentication::MD5Password(_) | Authentication::Ok
                )
            )
        })
        .await;

    if let Some(PgWireBackendMessage::Authentication(Authentication::MD5Password(salt))) =
        world.received.last()
    {
        let hashed = hash_md5_password("any_user", DEMO_PASSWORD, salt);
        world
            .send(PgWireFrontendMessage::PasswordMessageFamily(
                PasswordMessageFamily::Password(Password::new(hashed)),
            ))
            .await;
    }

    world.read_until_ready_for_query().await;
}

#[then("server responds with AuthenticationOk")]
fn responds_with_authentication_ok(world: &mut PostgresWireWorld) {
    assert!(
        world.received.iter().any(|message| matches!(
            message,
            PgWireBackendMessage::Authentication(Authentication::Ok)
        )),
        "no AuthenticationOk in {:?}",
        world.received
    );
}

#[then(expr = "server responds with ParameterStatus\\(name: {string}, value: {string}\\)")]
fn responds_with_parameter_status(world: &mut PostgresWireWorld, name: String, value: String) {
    let reported = world
        .received
        .iter()
        .filter_map(|message| match message {
            PgWireBackendMessage::ParameterStatus(status) => {
                Some((status.name.clone(), status.value.clone()))
            }
            _ => None,
        })
        .collect::<Vec<_>>();

    assert!(
        reported
            .iter()
            .any(|(got_name, got_value)| *got_name == name && *got_value == value),
        "no ParameterStatus {name}={value}; got {reported:?}"
    );
}

#[then(expr = "server responds with ReadyForQuery\\(status: {string}\\)")]
fn responds_with_ready_for_query(world: &mut PostgresWireWorld, status: String) {
    let expected = match status.as_str() {
        "I" => TransactionStatus::Idle,
        "T" => TransactionStatus::Transaction,
        "E" => TransactionStatus::Error,
        other => panic!("unknown transaction status: {other}"),
    };

    assert!(
        world.received.iter().any(|message| matches!(
            message,
            PgWireBackendMessage::ReadyForQuery(ready) if ready.status == expected
        )),
        "no ReadyForQuery({status}) in {:?}",
        world.received
    );
}

// ---------------------------------------------------------------------------
// MessageFlow/SimpleQuery.feature and ExtendedQuery.feature
// ---------------------------------------------------------------------------

#[given("client is authenticated")]
async fn client_is_authenticated(world: &mut PostgresWireWorld) {
    world.authenticate("cucumber").await;
    // Only the handshake matters to the steps that follow.
    world.received.clear();
}

#[given(expr = "a table {string} with an integer column {string}")]
async fn a_table_with_integer_column(world: &mut PostgresWireWorld, table: String, column: String) {
    world
        .send(PgWireFrontendMessage::Query(Query::new(format!(
            "CREATE TABLE {table} ({column} INTEGER)"
        ))))
        .await;
    world.read_until_ready_for_query().await;
    world.received.clear();
}

#[when(expr = "client sends Query\\(sql: {string}\\)")]
async fn client_sends_query(world: &mut PostgresWireWorld, sql: String) {
    world
        .send(PgWireFrontendMessage::Query(Query::new(sql)))
        .await;
    world.read_until_ready_for_query().await;
}

#[then(
    expr = "server responds with RowDescription\\(fields: [\\{ name: {string}, type: {string} \\}]\\)"
)]
fn responds_with_row_description(world: &mut PostgresWireWorld, name: String, type_name: String) {
    let expected = vec![(name, type_oid(&type_name))];
    let actual = world
        .received
        .iter()
        .filter_map(|message| match message {
            PgWireBackendMessage::RowDescription(description) => Some(
                description
                    .fields
                    .iter()
                    .map(|field| (field.name.clone(), field.type_id))
                    .collect::<Vec<_>>(),
            ),
            _ => None,
        })
        .collect::<Vec<_>>();

    assert!(
        actual.contains(&expected),
        "expected RowDescription {expected:?}, got {actual:?}"
    );
}

#[then(expr = "server responds with RowDescription\\(fields: []\\)")]
fn responds_with_empty_row_description(world: &mut PostgresWireWorld) {
    // PostgreSQL sends NoData rather than an empty RowDescription for a
    // statement that returns no columns, so either satisfies the feature.
    let has_empty = world.received.iter().any(|message| {
        matches!(message, PgWireBackendMessage::RowDescription(description)
            if description.fields.is_empty())
    });
    let has_no_data = world
        .received
        .iter()
        .any(|message| matches!(message, PgWireBackendMessage::NoData(_)));

    assert!(
        has_empty || has_no_data,
        "expected an empty RowDescription or NoData, got {:?}",
        world.received
    );
}

/// Map the type names the features use onto PostgreSQL type OIDs.
fn type_oid(name: &str) -> u32 {
    match name {
        "int2" => 21,
        "int4" => OID_INT4,
        "int8" => 20,
        "text" => 25,
        "varchar" => 1043,
        "float8" => 701,
        "bool" => 16,
        other => panic!("unmapped type name in feature: {other}"),
    }
}

#[then(expr = "server responds with DataRow\\(values: [{string}]\\)")]
fn responds_with_data_row(world: &mut PostgresWireWorld, value: String) {
    let expected = vec![value];
    let actual = world
        .received
        .iter()
        .filter_map(|message| match message {
            PgWireBackendMessage::DataRow(row) => Some(
                decode_data_row(&row.data, row.field_count)
                    .into_iter()
                    .map(|value| value.unwrap_or_else(|| "NULL".to_owned()))
                    .collect::<Vec<_>>(),
            ),
            _ => None,
        })
        .collect::<Vec<_>>();

    assert!(
        actual.contains(&expected),
        "expected DataRow {expected:?}, got {actual:?}"
    );
}

#[then(expr = "server responds with CommandComplete\\(tag: {string}\\)")]
fn responds_with_command_complete(world: &mut PostgresWireWorld, tag: String) {
    let tags = world.command_tags();
    assert!(
        tags.iter().any(|got| *got == tag),
        "expected CommandComplete tag {tag:?}, got {tags:?}"
    );
}

#[then(expr = "server responds with ErrorResponse\\(severity: {string}, code: {string}\\)")]
fn responds_with_error_response(world: &mut PostgresWireWorld, severity: String, code: String) {
    let errors = world
        .received
        .iter()
        .filter_map(|message| match message {
            PgWireBackendMessage::ErrorResponse(error) => Some(
                error
                    .fields
                    .iter()
                    .map(|(kind, value)| (*kind as char, value.clone()))
                    .collect::<Vec<_>>(),
            ),
            _ => None,
        })
        .collect::<Vec<_>>();

    // 'S' carries the severity and 'C' the SQLSTATE code.
    let matched = errors.iter().any(|fields| {
        let has_severity = fields
            .iter()
            .any(|(kind, value)| *kind == 'S' && *value == severity);
        let has_code = fields
            .iter()
            .any(|(kind, value)| *kind == 'C' && *value == code);
        has_severity && has_code
    });

    assert!(
        matched,
        "expected ErrorResponse severity={severity} code={code}, got {errors:?}"
    );
}

#[when(expr = "client sends Parse\\(name: {string}, query: {string}\\)")]
async fn client_sends_parse(world: &mut PostgresWireWorld, name: String, query: String) {
    // The feature expects ParameterDescription(types: ["int4"]), so the
    // parameter type is declared here rather than left to inference.
    let type_oids = if query.contains('$') {
        vec![OID_INT4]
    } else {
        Vec::new()
    };

    world
        .send(PgWireFrontendMessage::Parse(Parse::new(
            optional_name(&name),
            query,
            type_oids,
        )))
        .await;
}

#[when(expr = "client sends Bind\\(name: {string}, statement: {string}, parameters: [{int}]\\)")]
async fn client_sends_bind(
    world: &mut PostgresWireWorld,
    name: String,
    statement: String,
    parameter: i32,
) {
    world
        .send(PgWireFrontendMessage::Bind(Bind::new(
            optional_name(&name),
            optional_name(&statement),
            // 1 = binary format, matching the int4 encoding below.
            vec![1],
            vec![Some(Bytes::copy_from_slice(&parameter.to_be_bytes()))],
            vec![0],
        )))
        .await;
}

#[when(expr = "client sends Describe\\(type: {string}, name: {string}\\)")]
async fn client_sends_describe(world: &mut PostgresWireWorld, target: String, name: String) {
    let target_type = match target.as_str() {
        "Statement" => b'S',
        "Portal" => b'P',
        other => panic!("unknown describe target: {other}"),
    };

    world
        .send(PgWireFrontendMessage::Describe(Describe::new(
            target_type,
            optional_name(&name),
        )))
        .await;
}

#[when(expr = "client sends Execute\\(name: {string}, max_rows: {int}\\)")]
async fn client_sends_execute(world: &mut PostgresWireWorld, name: String, max_rows: i32) {
    world
        .send(PgWireFrontendMessage::Execute(Execute::new(
            optional_name(&name),
            max_rows,
        )))
        .await;
}

#[when("client sends Sync")]
async fn client_sends_sync(world: &mut PostgresWireWorld) {
    world
        .send(PgWireFrontendMessage::Sync(SyncMessage::new()))
        .await;
    // Sync is what makes the server flush the whole extended-query exchange.
    world.read_until_ready_for_query().await;
}

#[then("server responds with ParseComplete")]
fn responds_with_parse_complete(world: &mut PostgresWireWorld) {
    assert!(
        world
            .received
            .iter()
            .any(|message| matches!(message, PgWireBackendMessage::ParseComplete(_))),
        "no ParseComplete in {:?}",
        world.received
    );
}

#[then("server responds with BindComplete")]
fn responds_with_bind_complete(world: &mut PostgresWireWorld) {
    assert!(
        world
            .received
            .iter()
            .any(|message| matches!(message, PgWireBackendMessage::BindComplete(_))),
        "no BindComplete in {:?}",
        world.received
    );
}

#[then(expr = "server responds with ParameterDescription\\(types: [{string}]\\)")]
fn responds_with_parameter_description(world: &mut PostgresWireWorld, type_name: String) {
    let expected = vec![type_oid(&type_name)];
    let actual = world
        .received
        .iter()
        .filter_map(|message| match message {
            PgWireBackendMessage::ParameterDescription(description) => {
                Some(description.types.clone())
            }
            _ => None,
        })
        .collect::<Vec<_>>();

    assert!(
        actual.contains(&expected),
        "expected ParameterDescription {expected:?}, got {actual:?}"
    );
}

/// An empty name in the features means the unnamed statement or portal.
fn optional_name(name: &str) -> Option<String> {
    if name.is_empty() {
        None
    } else {
        Some(name.to_owned())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn decodes_a_data_row_into_column_values() {
        let mut data = BytesMut::new();
        data.extend_from_slice(&1i32.to_be_bytes());
        data.extend_from_slice(b"1");
        data.extend_from_slice(&(-1i32).to_be_bytes());

        assert_eq!(decode_data_row(&data, 2), vec![Some("1".to_owned()), None]);
    }
}
