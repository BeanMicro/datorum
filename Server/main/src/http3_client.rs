use std::{net::SocketAddr, sync::Arc};
use anyhow::{Context, Result};
use http::{Method, Request};
use quinn::{ClientConfig, Endpoint};
use tokio::time::timeout;
use tracing::{debug, info};
use std::time::Duration;

/// HTTP/3 client for testing
pub struct Http3Client {
    endpoint: Endpoint,
}

impl Http3Client {
    /// Create a new HTTP/3 client with insecure TLS config for testing
    pub fn new() -> Result<Self> {
        let mut crypto = rustls::ClientConfig::builder()
            .with_safe_defaults()
            .with_custom_certificate_verifier(Arc::new(InsecureServerCertVerifier))
            .with_no_client_auth();

        crypto.alpn_protocols = vec![b"h3".to_vec()];

        let client_config = ClientConfig::new(Arc::new(crypto));
        let mut endpoint = Endpoint::client("[::]:0".parse().unwrap())?;
        endpoint.set_default_client_config(client_config);

        Ok(Self { endpoint })
    }

    /// Connect to an HTTP/3 server and send a GET request
    pub async fn get(&self, addr: SocketAddr, path: &str) -> Result<String> {
        info!(?addr, path, "Connecting to HTTP/3 server");

        // Connect to the server
        let connection = self.endpoint
            .connect(addr, "localhost")?
            .await
            .context("Failed to establish QUIC connection")?;

        debug!("QUIC connection established");

        // Create H3 connection
        let quinn_conn = h3_quinn::Connection::new(connection);
        let (mut driver, mut send_request) = h3::client::new(quinn_conn).await
            .context("Failed to create H3 connection")?;

        debug!("H3 connection established");

        // Spawn the driver task - the driver handles the connection in the background
        let _driver_handle = tokio::spawn(async move {
            // Drive the H3 connection
            futures::future::poll_fn(|cx| driver.poll_close(cx)).await.ok();
        });

        // Create the HTTP request
        let req = Request::builder()
            .method(Method::GET)
            .uri(format!("https://localhost{}", path))
            .header("user-agent", "datorum-http3-test-client")
            .body(())
            .context("Failed to build request")?;

        debug!(?req, "Sending HTTP/3 request");

        // Send the request
        let mut stream = send_request
            .send_request(req)
            .await
            .context("Failed to send HTTP/3 request")?;

        // Finish sending (no body)
        stream.finish().await.context("Failed to finish request stream")?;

        // Receive the response
        let response = stream
            .recv_response()
            .await
            .context("Failed to receive HTTP/3 response")?;

        debug!(?response, "Received HTTP/3 response");

        // Read response body
        let mut body = Vec::new();
        while let Some(chunk) = stream.recv_data().await? {
            use bytes::Buf;
            body.extend_from_slice(chunk.chunk());
        }

        let response_text = String::from_utf8(body).context("Invalid UTF-8 in response body")?;
        info!(status = ?response.status(), body = %response_text, "HTTP/3 response received");

        Ok(response_text)
    }
}

/// Insecure certificate verifier for testing (accepts self-signed certificates)
struct InsecureServerCertVerifier;

impl rustls::client::ServerCertVerifier for InsecureServerCertVerifier {
    fn verify_server_cert(
        &self,
        _end_entity: &rustls::Certificate,
        _intermediates: &[rustls::Certificate],
        _server_name: &rustls::ServerName,
        _scts: &mut dyn Iterator<Item = &[u8]>,
        _ocsp_response: &[u8],
        _now: std::time::SystemTime,
    ) -> Result<rustls::client::ServerCertVerified, rustls::Error> {
        Ok(rustls::client::ServerCertVerified::assertion())
    }
}
