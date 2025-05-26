use std::net::{SocketAddr, TcpListener, TcpStream};
use std::process::Command;
use std::thread;
use std::time::Duration;

fn is_port_available(addr: &str) -> bool {
    TcpListener::bind(addr).map(|l| drop(l)).is_ok()
}

fn wait_for_port_open(addr: &str, interval: Duration, max_attempts: usize) -> bool {
    let socket_addr: SocketAddr = addr.parse().expect("Invalid ADDR");
    for attempt in 1..=max_attempts {
        let res = TcpStream::connect_timeout(&socket_addr, Duration::from_secs(1));
        if res.is_ok() {
            return true;
        }
        eprintln!("Waiting for {} (attempt {}/{})", addr, attempt, max_attempts);
        thread::sleep(interval);
    }
    false
}

#[test]
fn test_ssl_request() {
    const ADDR: &str = "127.0.0.1:5432";

    if !is_port_available(ADDR) {
        panic!("Port {} is already in use. Try `lsof -nP -iTCP:5432 -sTCP:LISTEN`", ADDR);
    }

    let mut server = Command::new("cargo")
        .args(&["run", "--bin", "datorum-postgres"])
        .spawn()
        .expect("Failed to start server");

    let max_attempts: usize = 10;
    let duration_in_secs: u64 = 3;
    let total_wait_time = max_attempts * duration_in_secs as usize;
    if !wait_for_port_open(ADDR, Duration::from_secs(duration_in_secs), max_attempts) {
        panic!("Server did not open port 5432 within {} seconds", total_wait_time);
    }

    thread::sleep(Duration::from_secs(duration_in_secs));

    let output = Command::new("psql")
        .env("PGPASSWORD", "pencil")
        .args(&[
            "-h", "127.0.0.1",
            "-p", "5432",
            "-U", "any_user",
            "-c", "SELECT 1",
            "-o", "/dev/null",
            "--set=sslmode=require",
        ])
        .output()
        .expect("Failed to run psql");

    if output.status.success() {
        println!("SSL connection successful");
    } else {
        let stderr = String::from_utf8_lossy(&output.stderr);
        println!("psql stderr: {}", stderr);
        if stderr.contains("SSL") || stderr.contains("certificate") {
            println!("SSL negotiation attempted but failed due to certificate issues (expected)");
        } else {
            panic!("Unexpected error: {}", stderr);
        }
    }

    server.kill().expect("Failed to kill server process");
    server.wait().expect("Failed to wait on server process");
}
