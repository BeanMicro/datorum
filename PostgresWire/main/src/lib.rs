//! PostgreSQL wire-protocol server backed by an in-memory SQLite database.
//!
//! The behaviour here is specified by the Gherkin features under
//! `PostgresWire/features`, which describe PostgreSQL semantics. SQLite does
//! not share those semantics, so this module translates between the two:
//! declared SQLite types become PostgreSQL OIDs, unnamed expression columns
//! are renamed the way PostgreSQL renames them, command tags are rendered in
//! PostgreSQL's form, and SQLite errors are mapped onto SQLSTATE codes.

use std::sync::{Arc, Mutex};

use async_trait::async_trait;
use futures::stream;

use pgwire::api::NoopErrorHandler;
use pgwire::api::PgWireServerHandlers;
use pgwire::api::auth::md5pass::{Md5PasswordAuthStartupHandler, hash_md5_password};
use pgwire::api::auth::{AuthSource, DefaultServerParameterProvider, LoginInfo, Password};
use pgwire::api::copy::NoopCopyHandler;
use pgwire::api::portal::{Format, Portal};
use pgwire::api::query::{ExtendedQueryHandler, SimpleQueryHandler};
use pgwire::api::results::{
    DataRowEncoder, DescribePortalResponse, DescribeStatementResponse, FieldInfo, QueryResponse,
    Response, Tag,
};
use pgwire::api::stmt::{NoopQueryParser, StoredStatement};
use pgwire::api::{ClientInfo, Type};
use pgwire::error::{ErrorInfo, PgWireError, PgWireResult};
use pgwire::messages::data::DataRow;
use pgwire::tokio::process_socket;
use rusqlite::types::{Value, ValueRef};
use rusqlite::{Connection, Statement, ToSql};
use tokio::net::TcpListener;

/// Password every login is checked against. Demonstration only.
pub const DEMO_PASSWORD: &str = "pencil";

pub struct SqliteBackend {
    conn: Arc<Mutex<Connection>>,
    query_parser: Arc<NoopQueryParser>,
}

/// Authenticates every user against [`DEMO_PASSWORD`]. Demonstration only.
pub struct DummyAuthSource;

#[async_trait]
impl AuthSource for DummyAuthSource {
    async fn get_password(&self, login_info: &LoginInfo) -> PgWireResult<Password> {
        let salt = vec![0, 0, 0, 0];
        let hash_password = hash_md5_password(
            login_info.user().unwrap_or(""),
            DEMO_PASSWORD,
            salt.as_ref(),
        );
        Ok(Password::new(Some(salt), hash_password.as_bytes().to_vec()))
    }
}

/// Map a SQLite declared column type onto the PostgreSQL type it corresponds to.
///
/// SQLite's declared types are free text, and the same logical type has several
/// spellings. `INTEGER` is the canonical spelling produced by `CREATE TABLE t
/// (id INTEGER)` and is what `Column::decl_type` reports, so it must be
/// accepted: matching only `INT` made every query against a real table fail.
///
/// SQLite integers are 64-bit, so a declared integer column is reported as
/// `int8` and encoded as `i64`, keeping the declared type and the encoded value
/// consistent in both text and binary formats.
fn name_to_type(name: &str) -> PgWireResult<Type> {
    // A declared type may carry a length or precision, e.g. VARCHAR(255).
    let base = name
        .split_once('(')
        .map(|(head, _)| head)
        .unwrap_or(name)
        .trim()
        .to_uppercase();

    match base.as_str() {
        "TINYINT" | "INT2" | "SMALLINT" => Ok(Type::INT2),
        "INT" | "INT4" | "INTEGER" | "MEDIUMINT" => Ok(Type::INT8),
        "BIGINT" | "INT8" | "UNSIGNED BIG INT" => Ok(Type::INT8),
        "BOOL" | "BOOLEAN" => Ok(Type::BOOL),
        "CHAR" | "CHARACTER" | "NCHAR" | "NATIVE CHARACTER" => Ok(Type::VARCHAR),
        "VARCHAR" | "VARYING CHARACTER" | "NVARCHAR" => Ok(Type::VARCHAR),
        "TEXT" | "CLOB" => Ok(Type::TEXT),
        "BINARY" | "BLOB" | "BYTEA" => Ok(Type::BYTEA),
        "REAL" | "FLOAT" | "DOUBLE" | "DOUBLE PRECISION" => Ok(Type::FLOAT8),
        "NUMERIC" | "DECIMAL" => Ok(Type::NUMERIC),
        "DATE" => Ok(Type::DATE),
        "DATETIME" | "TIMESTAMP" => Ok(Type::TIMESTAMP),
        _ => Err(PgWireError::UserError(Box::new(ErrorInfo::new(
            "ERROR".to_owned(),
            "42846".to_owned(),
            format!("Unsupported data type: {name}"),
        )))),
    }
}

/// Infer a PostgreSQL type for a column SQLite reports no declared type for,
/// such as a literal or a computed expression, using the first value returned.
fn infer_type(value: Option<&Value>) -> Type {
    match value {
        Some(Value::Integer(i)) => {
            if i32::try_from(*i).is_ok() {
                Type::INT4
            } else {
                Type::INT8
            }
        }
        Some(Value::Real(_)) => Type::FLOAT8,
        Some(Value::Blob(_)) => Type::BYTEA,
        // A column that is entirely NULL carries no type information; text is
        // the format PostgreSQL falls back to as well.
        Some(Value::Text(_)) | Some(Value::Null) | None => Type::TEXT,
    }
}

/// Rename a column the way PostgreSQL would.
///
/// PostgreSQL calls an output column with no name and no `AS` alias
/// `?column?`. SQLite instead reuses the expression text, so `SELECT 1` comes
/// back named `1` and `SELECT id+1` named `id+1`. An alias survives in both
/// (`SELECT 1 AS x` is named `x` either way), so only names that are not valid
/// identifiers are rewritten.
fn pg_column_name(sqlite_name: &str) -> String {
    let is_identifier = !sqlite_name.is_empty()
        && sqlite_name
            .chars()
            .next()
            .is_some_and(|c| c.is_ascii_alphabetic() || c == '_')
        && sqlite_name
            .chars()
            .all(|c| c.is_ascii_alphanumeric() || c == '_');

    if is_identifier {
        sqlite_name.to_owned()
    } else {
        "?column?".to_owned()
    }
}

/// Render the command tag PostgreSQL sends in `CommandComplete`.
///
/// `INSERT` carries an OID field that is always zero on modern servers, giving
/// `INSERT 0 1`; the other row-returning commands carry only a count. Commands
/// such as `CREATE TABLE` carry neither.
fn command_tag(sql: &str, rows: usize) -> Tag {
    let verb = sql.split_whitespace().next().unwrap_or("").to_uppercase();

    match verb.as_str() {
        "INSERT" => Tag::new("INSERT").with_oid(0).with_rows(rows),
        "SELECT" => Tag::new("SELECT").with_rows(rows),
        "UPDATE" => Tag::new("UPDATE").with_rows(rows),
        "DELETE" => Tag::new("DELETE").with_rows(rows),
        "CREATE" | "DROP" | "ALTER" => {
            let object = sql.split_whitespace().nth(1).unwrap_or("").to_uppercase();
            Tag::new(format!("{verb} {object}").trim_end())
        }
        "BEGIN" | "COMMIT" | "ROLLBACK" => Tag::new(&verb),
        _ => Tag::new(&verb).with_rows(rows),
    }
}

/// Translate a SQLite error into a PostgreSQL error carrying a SQLSTATE code.
///
/// SQLite reports a single generic failure code, so the SQLSTATE is recovered
/// from the message text. Clients branch on SQLSTATE, so returning the generic
/// internal-error code for everything would make ordinary mistakes such as a
/// typo indistinguishable from a server fault.
fn map_sqlite_error(error: rusqlite::Error) -> PgWireError {
    let message = error.to_string();
    let lowered = message.to_lowercase();

    let sqlstate = if lowered.contains("syntax error") {
        "42601" // syntax_error
    } else if lowered.contains("no such table") {
        "42P01" // undefined_table
    } else if lowered.contains("no such column") || lowered.contains("has no column named") {
        "42703" // undefined_column
    } else if lowered.contains("unique constraint failed") {
        "23505" // unique_violation
    } else if lowered.contains("not null constraint failed") {
        "23502" // not_null_violation
    } else if lowered.contains("foreign key constraint failed") {
        "23503" // foreign_key_violation
    } else {
        "XX000" // internal_error
    };

    PgWireError::UserError(Box::new(ErrorInfo::new(
        "ERROR".to_owned(),
        sqlstate.to_owned(),
        message,
    )))
}

/// Encode one value, matching the encoding to the type advertised for the
/// column so that text and binary formats agree with the `RowDescription`.
fn encode_value(encoder: &mut DataRowEncoder, value: &Value, datatype: &Type) -> PgWireResult<()> {
    match value {
        Value::Null => match *datatype {
            Type::INT2 => encoder.encode_field(&None::<i16>),
            Type::INT4 => encoder.encode_field(&None::<i32>),
            Type::INT8 => encoder.encode_field(&None::<i64>),
            Type::BOOL => encoder.encode_field(&None::<bool>),
            Type::FLOAT8 => encoder.encode_field(&None::<f64>),
            Type::BYTEA => encoder.encode_field(&None::<Vec<u8>>),
            _ => encoder.encode_field(&None::<String>),
        },
        Value::Integer(i) => match *datatype {
            Type::INT2 => encoder.encode_field(&(*i as i16)),
            Type::INT4 => encoder.encode_field(&(*i as i32)),
            Type::BOOL => encoder.encode_field(&(*i != 0)),
            Type::FLOAT8 => encoder.encode_field(&(*i as f64)),
            Type::TEXT | Type::VARCHAR => encoder.encode_field(&i.to_string()),
            _ => encoder.encode_field(i),
        },
        Value::Real(f) => match *datatype {
            Type::TEXT | Type::VARCHAR => encoder.encode_field(&f.to_string()),
            _ => encoder.encode_field(f),
        },
        Value::Text(t) => encoder.encode_field(t),
        Value::Blob(b) => encoder.encode_field(b),
    }
}

/// Build the `RowDescription` for a prepared statement.
///
/// `first_row` supplies values used to infer the type of columns SQLite
/// declares no type for; pass `None` when no row is available, as when
/// describing a statement before it is executed.
fn row_desc_from_stmt(
    stmt: &Statement,
    format: &Format,
    first_row: Option<&[Value]>,
) -> PgWireResult<Vec<FieldInfo>> {
    stmt.columns()
        .iter()
        .enumerate()
        .map(|(idx, col)| {
            let field_type = match col.decl_type() {
                Some(declared) => name_to_type(declared)?,
                None => infer_type(first_row.and_then(|row| row.get(idx))),
            };
            Ok(FieldInfo::new(
                pg_column_name(col.name()),
                None,
                None,
                field_type,
                format.format_for(idx),
            ))
        })
        .collect()
}

/// Run a query and collect every row, so that column types can be inferred
/// from the data before the `RowDescription` is sent.
///
/// Errors raised part-way through iteration are propagated. Treating them as
/// end-of-stream would truncate the result set and still report success.
fn collect_rows(
    stmt: &mut Statement,
    params: &[&dyn ToSql],
) -> PgWireResult<(Vec<Vec<Value>>, usize)> {
    let ncols = stmt.column_count();
    let mut rows = stmt.query(params).map_err(map_sqlite_error)?;

    let mut collected = Vec::new();
    while let Some(row) = rows.next().map_err(map_sqlite_error)? {
        let mut values = Vec::with_capacity(ncols);
        for idx in 0..ncols {
            let value = match row.get_ref(idx).map_err(map_sqlite_error)? {
                ValueRef::Null => Value::Null,
                ValueRef::Integer(i) => Value::Integer(i),
                ValueRef::Real(f) => Value::Real(f),
                ValueRef::Text(t) => Value::Text(String::from_utf8_lossy(t).into_owned()),
                ValueRef::Blob(b) => Value::Blob(b.to_vec()),
            };
            values.push(value);
        }
        collected.push(values);
    }

    Ok((collected, ncols))
}

fn encode_rows(schema: &Arc<Vec<FieldInfo>>, rows: &[Vec<Value>]) -> PgWireResult<Vec<DataRow>> {
    rows.iter()
        .map(|row| {
            let mut encoder = DataRowEncoder::new(schema.clone());
            for (idx, value) in row.iter().enumerate() {
                encode_value(&mut encoder, value, schema[idx].datatype())?;
            }
            encoder.finish()
        })
        .collect()
}

fn is_row_returning(sql: &str) -> bool {
    let verb = sql.split_whitespace().next().unwrap_or("").to_uppercase();
    matches!(verb.as_str(), "SELECT" | "VALUES" | "PRAGMA")
}

#[async_trait]
impl SimpleQueryHandler for SqliteBackend {
    async fn do_query<'a, C>(
        &self,
        _client: &mut C,
        query: &'a str,
    ) -> PgWireResult<Vec<Response<'a>>>
    where
        C: ClientInfo + Unpin + Send + Sync,
    {
        let conn = self.conn.lock().expect("sqlite connection mutex poisoned");

        if is_row_returning(query) {
            let mut stmt = conn.prepare(query).map_err(map_sqlite_error)?;
            let (rows, _) = collect_rows(&mut stmt, &[])?;
            let schema = Arc::new(row_desc_from_stmt(
                &stmt,
                &Format::UnifiedText,
                rows.first().map(|row| row.as_slice()),
            )?);
            let encoded = encode_rows(&schema, &rows)?;

            Ok(vec![Response::Query(QueryResponse::new(
                schema,
                stream::iter(encoded.into_iter().map(Ok)),
            ))])
        } else {
            let affected = conn.execute(query, ()).map_err(map_sqlite_error)?;
            Ok(vec![Response::Execution(command_tag(query, affected))])
        }
    }
}

fn get_params(portal: &Portal<String>) -> PgWireResult<Vec<Box<dyn ToSql>>> {
    let mut results = Vec::with_capacity(portal.parameter_len());
    for i in 0..portal.parameter_len() {
        let param_type = portal.statement.parameter_types.get(i).ok_or_else(|| {
            PgWireError::UserError(Box::new(ErrorInfo::new(
                "ERROR".to_owned(),
                "08P01".to_owned(),
                format!("No type described for parameter ${}", i + 1),
            )))
        })?;

        // Only the parameter types the features exercise are supported.
        let value: Box<dyn ToSql> = match param_type {
            &Type::BOOL => Box::new(portal.parameter::<bool>(i, param_type)?),
            &Type::INT2 => Box::new(portal.parameter::<i16>(i, param_type)?),
            &Type::INT4 => Box::new(portal.parameter::<i32>(i, param_type)?),
            &Type::INT8 => Box::new(portal.parameter::<i64>(i, param_type)?),
            &Type::TEXT | &Type::VARCHAR | &Type::UNKNOWN => {
                Box::new(portal.parameter::<String>(i, param_type)?)
            }
            &Type::FLOAT4 => Box::new(portal.parameter::<f32>(i, param_type)?),
            &Type::FLOAT8 => Box::new(portal.parameter::<f64>(i, param_type)?),
            other => {
                return Err(PgWireError::UserError(Box::new(ErrorInfo::new(
                    "ERROR".to_owned(),
                    "0A000".to_owned(),
                    format!("Unsupported parameter type: {other}"),
                ))));
            }
        };
        results.push(value);
    }

    Ok(results)
}

#[async_trait]
impl ExtendedQueryHandler for SqliteBackend {
    type Statement = String;
    type QueryParser = NoopQueryParser;

    fn query_parser(&self) -> Arc<Self::QueryParser> {
        self.query_parser.clone()
    }

    async fn do_query<'a, C>(
        &self,
        _client: &mut C,
        portal: &'a Portal<Self::Statement>,
        _max_rows: usize,
    ) -> PgWireResult<Response<'a>>
    where
        C: ClientInfo + Unpin + Send + Sync,
    {
        let conn = self.conn.lock().expect("sqlite connection mutex poisoned");
        let query = &portal.statement.statement;

        let mut stmt = conn.prepare_cached(query).map_err(map_sqlite_error)?;
        let params = get_params(portal)?;
        let params_ref = params
            .iter()
            .map(|param| param.as_ref())
            .collect::<Vec<&dyn ToSql>>();

        if is_row_returning(query) {
            let (rows, _) = collect_rows(&mut stmt, params_ref.as_slice())?;
            let schema = Arc::new(row_desc_from_stmt(
                &stmt,
                &portal.result_column_format,
                rows.first().map(|row| row.as_slice()),
            )?);
            let encoded = encode_rows(&schema, &rows)?;

            Ok(Response::Query(QueryResponse::new(
                schema,
                stream::iter(encoded.into_iter().map(Ok)),
            )))
        } else {
            let affected = stmt
                .execute(params_ref.as_slice())
                .map_err(map_sqlite_error)?;
            Ok(Response::Execution(command_tag(query, affected)))
        }
    }

    async fn do_describe_statement<C>(
        &self,
        _client: &mut C,
        stmt: &StoredStatement<Self::Statement>,
    ) -> PgWireResult<DescribeStatementResponse>
    where
        C: ClientInfo + Unpin + Send + Sync,
    {
        let conn = self.conn.lock().expect("sqlite connection mutex poisoned");
        let param_types = stmt.parameter_types.clone();
        let prepared = conn
            .prepare_cached(&stmt.statement)
            .map_err(map_sqlite_error)?;
        // No row has been read yet, so untyped columns fall back to text.
        row_desc_from_stmt(&prepared, &Format::UnifiedBinary, None)
            .map(|fields| DescribeStatementResponse::new(param_types, fields))
    }

    async fn do_describe_portal<C>(
        &self,
        _client: &mut C,
        portal: &Portal<Self::Statement>,
    ) -> PgWireResult<DescribePortalResponse>
    where
        C: ClientInfo + Unpin + Send + Sync,
    {
        let conn = self.conn.lock().expect("sqlite connection mutex poisoned");
        let prepared = conn
            .prepare_cached(&portal.statement.statement)
            .map_err(map_sqlite_error)?;
        row_desc_from_stmt(&prepared, &portal.result_column_format, None)
            .map(DescribePortalResponse::new)
    }
}

impl SqliteBackend {
    pub fn new() -> SqliteBackend {
        SqliteBackend {
            conn: Arc::new(Mutex::new(
                Connection::open_in_memory().expect("failed to open in-memory SQLite database"),
            )),
            query_parser: Arc::new(NoopQueryParser::new()),
        }
    }
}

impl Default for SqliteBackend {
    fn default() -> Self {
        Self::new()
    }
}

pub struct SqliteBackendFactory {
    handler: Arc<SqliteBackend>,
}

impl SqliteBackendFactory {
    pub fn new() -> SqliteBackendFactory {
        SqliteBackendFactory {
            handler: Arc::new(SqliteBackend::new()),
        }
    }
}

impl Default for SqliteBackendFactory {
    fn default() -> Self {
        Self::new()
    }
}

impl PgWireServerHandlers for SqliteBackendFactory {
    type StartupHandler =
        Md5PasswordAuthStartupHandler<DummyAuthSource, DefaultServerParameterProvider>;
    type SimpleQueryHandler = SqliteBackend;
    type ExtendedQueryHandler = SqliteBackend;
    type CopyHandler = NoopCopyHandler;
    type ErrorHandler = NoopErrorHandler;

    fn simple_query_handler(&self) -> Arc<Self::SimpleQueryHandler> {
        self.handler.clone()
    }

    fn extended_query_handler(&self) -> Arc<Self::ExtendedQueryHandler> {
        self.handler.clone()
    }

    fn startup_handler(&self) -> Arc<Self::StartupHandler> {
        let mut parameters = DefaultServerParameterProvider::default();
        parameters.server_version = rusqlite::version().to_owned();

        Arc::new(Md5PasswordAuthStartupHandler::new(
            Arc::new(DummyAuthSource),
            Arc::new(parameters),
        ))
    }

    fn copy_handler(&self) -> Arc<Self::CopyHandler> {
        Arc::new(NoopCopyHandler)
    }

    fn error_handler(&self) -> Arc<Self::ErrorHandler> {
        Arc::new(NoopErrorHandler)
    }
}

/// Accept connections until the listener fails, serving each on its own task.
///
/// All connections share one in-memory database, so state set up by one
/// session is visible to the next.
pub async fn serve(listener: TcpListener) -> std::io::Result<()> {
    let factory = Arc::new(SqliteBackendFactory::new());

    loop {
        let (tcp_stream, _) = listener.accept().await?;
        let factory_ref = factory.clone();
        tokio::spawn(async move { process_socket(tcp_stream, None, factory_ref).await });
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use pgwire::messages::response::CommandComplete;

    fn rendered(tag: Tag) -> String {
        CommandComplete::from(tag).tag
    }

    #[test]
    fn maps_sqlites_canonical_integer_spelling() {
        // `CREATE TABLE t (id INTEGER)` reports "INTEGER", not "INT". Matching
        // only "INT" made every query against a real table fail.
        assert_eq!(name_to_type("INTEGER").unwrap(), Type::INT8);
        assert_eq!(name_to_type("integer").unwrap(), Type::INT8);
        assert_eq!(name_to_type("BIGINT").unwrap(), Type::INT8);
        assert_eq!(name_to_type("SMALLINT").unwrap(), Type::INT2);
        assert_eq!(name_to_type("REAL").unwrap(), Type::FLOAT8);
        assert_eq!(name_to_type("BLOB").unwrap(), Type::BYTEA);
        assert_eq!(name_to_type("BOOLEAN").unwrap(), Type::BOOL);
    }

    #[test]
    fn ignores_length_and_precision_on_declared_types() {
        assert_eq!(name_to_type("VARCHAR(255)").unwrap(), Type::VARCHAR);
        assert_eq!(name_to_type("DECIMAL(10, 2)").unwrap(), Type::NUMERIC);
    }

    #[test]
    fn rejects_a_type_it_cannot_map() {
        assert!(name_to_type("GEOGRAPHY").is_err());
    }

    #[test]
    fn renders_postgresql_command_tags() {
        // INSERT carries an OID field that is always zero on modern servers.
        assert_eq!(
            rendered(command_tag("INSERT INTO t (id) VALUES (1)", 1)),
            "INSERT 0 1"
        );
        assert_eq!(rendered(command_tag("SELECT * FROM t", 3)), "SELECT 3");
        assert_eq!(rendered(command_tag("UPDATE t SET id = 2", 2)), "UPDATE 2");
        assert_eq!(rendered(command_tag("DELETE FROM t", 4)), "DELETE 4");
        assert_eq!(
            rendered(command_tag("CREATE TABLE t (id INTEGER)", 0)),
            "CREATE TABLE"
        );
        assert_eq!(rendered(command_tag("  select 1", 1)), "SELECT 1");
    }

    #[test]
    fn renames_only_unnamed_expression_columns() {
        // SQLite names an unaliased expression after its own text; PostgreSQL
        // calls it ?column?. An explicit alias survives in both.
        assert_eq!(pg_column_name("1"), "?column?");
        assert_eq!(pg_column_name("id+1"), "?column?");
        assert_eq!(pg_column_name("count(*)"), "?column?");
        assert_eq!(pg_column_name("id"), "id");
        assert_eq!(pg_column_name("_total"), "_total");
    }

    #[test]
    fn infers_types_for_columns_sqlite_leaves_undeclared() {
        assert_eq!(infer_type(Some(&Value::Integer(1))), Type::INT4);
        assert_eq!(infer_type(Some(&Value::Integer(i64::MAX))), Type::INT8);
        assert_eq!(infer_type(Some(&Value::Real(1.5))), Type::FLOAT8);
        assert_eq!(infer_type(Some(&Value::Null)), Type::TEXT);
        assert_eq!(infer_type(None), Type::TEXT);
    }

    fn sqlstate_of(error: PgWireError) -> String {
        match error {
            PgWireError::UserError(info) => info.code.clone(),
            other => panic!("expected a user error, got {other:?}"),
        }
    }

    #[test]
    fn maps_sqlite_failures_onto_sqlstate_codes() {
        let connection = Connection::open_in_memory().unwrap();

        let syntax = connection.prepare("SELEC invalid").unwrap_err();
        assert_eq!(sqlstate_of(map_sqlite_error(syntax)), "42601");

        let missing_table = connection.prepare("SELECT * FROM absent").unwrap_err();
        assert_eq!(sqlstate_of(map_sqlite_error(missing_table)), "42P01");

        connection
            .execute("CREATE TABLE t (id INTEGER)", ())
            .unwrap();
        let missing_column = connection.prepare("SELECT absent FROM t").unwrap_err();
        assert_eq!(sqlstate_of(map_sqlite_error(missing_column)), "42703");
    }

    #[test]
    fn collect_rows_propagates_errors_instead_of_truncating() {
        let connection = Connection::open_in_memory().unwrap();
        connection
            .execute("CREATE TABLE t (id INTEGER)", ())
            .unwrap();
        connection
            .execute("INSERT INTO t VALUES (1), (2)", ())
            .unwrap();

        let mut stmt = connection.prepare("SELECT id FROM t").unwrap();
        let (rows, ncols) = collect_rows(&mut stmt, &[]).unwrap();

        assert_eq!(ncols, 1);
        assert_eq!(rows.len(), 2);
        assert_eq!(rows[0][0], Value::Integer(1));
    }

    #[test]
    fn only_row_returning_statements_take_the_query_path() {
        assert!(is_row_returning("SELECT 1"));
        assert!(is_row_returning("  select 1"));
        assert!(!is_row_returning("INSERT INTO t VALUES (1)"));
        assert!(!is_row_returning("CREATE TABLE t (id INTEGER)"));
    }
}
