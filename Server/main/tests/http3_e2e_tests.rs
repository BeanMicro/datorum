use std::{net::SocketAddr, time::Duration};
use anyhow::{Context, Result};
use tokio::time::{sleep, timeout};
use tracing::info;

mod http3_client;
use http3_client::Http3Client;

// Import server modules for testing
use datorum_server::{defaults, http3_server};

#[tokio::test]
async fn test_http3_server_e2e() -> Result<()> {
    // Initialize tracing for the test
    tracing_subscriber::fmt()
        .with_max_level(tracing::Level::DEBUG)
        .with_target(false)
        .try_init()
        .ok(); // Ignore error if already initialized

    // Use a different port for testing to avoid conflicts
    let test_port = defaults::PORT + 100; // Use port 1304 for testing
    let server_addr: SocketAddr = format!("[::1]:{}", test_port).parse()?;

    info!(?server_addr, "Starting HTTP/3 server for E2E test");

    // Start the HTTP/3 server in the background
    let server_handle = tokio::spawn(async move {
        if let Err(e) = http3_server::run_http3(server_addr).await {
            tracing::error!(error = ?e, "HTTP/3 server error in test");
        }
    });

    // Give the server a moment to start up
    sleep(Duration::from_millis(1000)).await;

    // Create HTTP/3 client
    let client = Http3Client::new().context("Failed to create HTTP/3 client")?;

    // Test 1: Basic GET request to root path
    info!("Testing GET request to root path");
    let response = timeout(
        Duration::from_secs(10),
        client.get(server_addr, "/")
    ).await
    .context("Request timed out")?
    .context("Failed to send GET request")?;

    assert!(response.contains("Hello from HTTP/3"), "Unexpected response: {}", response);
    info!("✓ Root path test passed");

    // Test 2: GET request to a custom path
    info!("Testing GET request to custom path");
    let response = timeout(
        Duration::from_secs(10),
        client.get(server_addr, "/test-endpoint")
    ).await
    .context("Request timed out")?
    .context("Failed to send GET request to custom path")?;

    assert!(response.contains("Hello from HTTP/3: /test-endpoint"), "Unexpected response: {}", response);
    info!("✓ Custom path test passed");

    // Test 3: Multiple concurrent requests
    info!("Testing multiple concurrent requests");
    let mut tasks = Vec::new();

    for i in 0..3 {
        let client_clone = Http3Client::new()?;
        let path = format!("/concurrent-test-{}", i);
        let task = tokio::spawn(async move {
            timeout(
                Duration::from_secs(10),
                client_clone.get(server_addr, &path)
            ).await
        });
        tasks.push(task);
    }

    // Wait for all concurrent requests to complete
    for (i, task) in tasks.into_iter().enumerate() {
        let result = task.await.context("Task panicked")?;
        let response = result.context("Request timed out")?.context("Request failed")?;
        assert!(response.contains(&format!("/concurrent-test-{}", i)),
               "Concurrent request {} failed: {}", i, response);
    }
    info!("✓ Concurrent requests test passed");

    // Clean up: abort the server
    server_handle.abort();

    info!("✅ All HTTP/3 E2E tests passed successfully!");
    Ok(())
}

#[tokio::test]
async fn test_http3_server_error_handling() -> Result<()> {
    // Initialize tracing for the test
    tracing_subscriber::fmt()
        .with_max_level(tracing::Level::DEBUG)
        .with_target(false)
        .try_init()
        .ok();

    // Test connection to a non-existent server
    let client = Http3Client::new().context("Failed to create HTTP/3 client")?;
    let non_existent_addr: SocketAddr = "[::1]:9999".parse()?;

    let result = timeout(
        Duration::from_secs(5),
        client.get(non_existent_addr, "/")
    ).await;

    // Should fail to connect
    assert!(result.is_err() || result.unwrap().is_err(),
           "Should fail to connect to non-existent server");

    info!("✓ Error handling test passed");
    Ok(())
}
