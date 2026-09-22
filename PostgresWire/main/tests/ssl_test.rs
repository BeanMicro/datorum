//! Exercises the PostgreSQL SSLRequest handshake.
//!
//! Like the Cucumber steps, this drives the server library in-process on an
//! ephemeral port. That keeps the test free of a `psql` client, of a fixed port
//! to collide over, and of a child process that could outlive a failed
//! assertion.

use datorum_postgres_wire::serve;
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use tokio::net::{TcpListener, TcpStream};

/// The SSLRequest body, as defined by the PostgreSQL frontend/backend protocol:
/// the 32-bit code 1234 << 16 | 5679.
const SSL_REQUEST_CODE: i32 = 80_877_103;

#[tokio::test]
async fn declines_ssl_request_while_no_tls_acceptor_is_configured() {
    let listener = TcpListener::bind("127.0.0.1:0")
        .await
        .expect("failed to bind an ephemeral port");
    let addr = listener
        .local_addr()
        .expect("failed to read the bound address");

    tokio::spawn(async move {
        let _ = serve(listener).await;
    });

    let mut stream = TcpStream::connect(addr)
        .await
        .expect("failed to connect to the server under test");

    // SSLRequest is a startup-style packet with no message tag: a 32-bit length
    // that counts itself, followed by the request code, both big-endian.
    let mut request = Vec::with_capacity(8);
    request.extend_from_slice(&8i32.to_be_bytes());
    request.extend_from_slice(&SSL_REQUEST_CODE.to_be_bytes());
    stream
        .write_all(&request)
        .await
        .expect("failed to send the SSLRequest");
    stream
        .flush()
        .await
        .expect("failed to flush the SSLRequest");

    // The backend answers with a single byte: 'S' to continue into a TLS
    // handshake, 'N' to carry on unencrypted.
    let mut response = [0u8; 1];
    stream
        .read_exact(&mut response)
        .await
        .expect("server closed the connection without answering the SSLRequest");

    // `serve` passes no TLS acceptor to `process_socket`, so refusing is the
    // only correct answer. Wiring up TLS should flip this to 'S'.
    assert_eq!(
        response[0], b'N',
        "expected the backend to decline the SSLRequest with 'N', got {:#04x}",
        response[0]
    );
}
