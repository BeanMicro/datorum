use std::{net::SocketAddr, sync::Arc};

use anyhow::{Context, Result};
use h3::server::RequestStream;
use http::Response;
use quinn::{Endpoint, ServerConfig};
use rcgen::generate_simple_self_signed;
use tokio::task;
use tracing::{debug, error, info};

/// Run a basic HTTP/3 server on the provided socket address.
/// This uses an in-memory self-signed certificate suitable only for local development.
pub async fn run_http3(addr: SocketAddr) -> Result<()> {
    let endpoint = make_server_endpoint(addr)?;
    info!(?addr, "HTTP/3 endpoint listening");

    while let Some(connecting) = endpoint.accept().await {
        task::spawn(handle_connection(connecting));
    }

    Ok(())
}

fn make_server_endpoint(addr: SocketAddr) -> Result<Endpoint> {
    // Generate self-signed cert for localhost usage
    let cert = generate_simple_self_signed(["localhost".into(), "127.0.0.1".into()])
        .context("failed to generate self-signed certificate")?;

    let key_der = cert.key_pair.serialize_der();
    let cert_der = cert.cert.der().to_vec();

    let rustls_cert = rustls::Certificate(cert_der);
    let rustls_key = rustls::PrivateKey(key_der);

    let mut crypto = rustls::ServerConfig::builder()
        .with_safe_defaults()
        .with_no_client_auth()
        .with_single_cert(vec![rustls_cert], rustls_key)
        .context("build rustls server config")?;

    // ALPN for HTTP/3
    crypto.alpn_protocols = vec![b"h3".to_vec()];

    let mut server_config = ServerConfig::with_crypto(Arc::new(crypto));

    // Tune transport for HTTP/3
    let mut transport = quinn::TransportConfig::default();
    transport.max_concurrent_uni_streams(100_u32.into());
    transport.max_concurrent_bidi_streams(100_u32.into());
    server_config.transport = Arc::new(transport);

    let endpoint = Endpoint::server(server_config, addr)
        .context("create quinn endpoint")?;

    Ok(endpoint)
}

async fn handle_connection(connecting: quinn::Connecting) {
    match connecting.await {
        Ok(connection) => {
            info!(addr = %connection.remote_address(), "HTTP/3 connection established");
            if let Err(e) = handle_h3(connection).await {
                error!(error = ?e, "connection error");
            }
        }
        Err(e) => error!(error = ?e, "failed QUIC handshake"),
    }
}

async fn handle_h3(connection: quinn::Connection) -> Result<()> {
    let mut h3_conn = h3::server::Connection::new(h3_quinn::Connection::new(connection))
        .await
        .context("create h3 connection")?;

    loop {
        match h3_conn.accept().await {
            Ok(Some((req, stream))) => {
                debug!(method = ?req.method(), path = ?req.uri().path(), "request");
                task::spawn(handle_request(req, stream));
            }
            Ok(None) => {
                debug!("connection closed by peer");
                return Ok(());
            }
            Err(e) => {
                // Some errors are normal connection shutdowns
                let error_str = e.to_string();
                if error_str.contains("closed") || error_str.contains("reset") {
                    debug!(error = ?e, "graceful close");
                    return Ok(());
                } else {
                    return Err(anyhow::Error::from(e)).context("h3 accept loop error");
                }
            }
        }
    }
}

async fn handle_request(
    req: http::Request<()>,
    mut stream: RequestStream<h3_quinn::BidiStream<bytes::Bytes>, bytes::Bytes>
) {
    println!("Hello World");

    let body_bytes = format!("Hello from HTTP/3: {}\n", req.uri().path());

    let response = Response::builder()
        .status(200)
        .header("content-type", "text/plain; charset=utf-8")
        .header("server", "datorum-h3")
        .body(())
        .unwrap();

    if let Err(e) = stream.send_response(response).await {
        error!(error = ?e, "failed to send response");
        return;
    }

    if let Err(e) = stream.send_data(body_bytes.into()).await {
        error!(error = ?e, "failed to send data");
        return;
    }

    if let Err(e) = stream.finish().await {
        error!(error = ?e, "failed to finish stream");
    }
}
