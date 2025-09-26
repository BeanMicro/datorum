pub mod avro_example;
pub mod avro_ex_doc;
pub mod defaults;
pub mod http3_server;
mod http3_client;

#[tokio::main]
pub async fn main() -> Result<(), Box<dyn std::error::Error>> {
    tracing_subscriber::fmt()
        .with_env_filter(tracing_subscriber::EnvFilter::from_default_env())
        .with_target(false)
        .compact()
        .init();

    let port = defaults::PORT;
    let addr = format!("[::1]:{}", port).parse::<std::net::SocketAddr>()?;

    tracing::info!("Datorum server starting on {} (HTTP/3)", addr);

    // Start HTTP/3 server task
    let http3_handle = tokio::spawn(async move {
        if let Err(e) = http3_server::run_http3(addr).await {
            tracing::error!(error = ?e, "HTTP/3 server terminated with error");
        }
    });

    // Demonstrate Avro schemas (can be removed or gated by a feature later)
    avro_ex_doc::test_schemas();

    // Wait for Ctrl+C for graceful shutdown
    tokio::signal::ctrl_c().await?;
    tracing::info!("Shutdown signal received. Stopping server...");

    // Abort HTTP/3 server task (simplistic). Could implement graceful shutdown with channels.
    http3_handle.abort();

    Ok(())
}
