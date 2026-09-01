use datorum_postgres_wire::serve;
use tokio::net::TcpListener;

#[tokio::main]
pub async fn main() -> std::io::Result<()> {
    let server_addr = "127.0.0.1:5432";
    let listener = TcpListener::bind(server_addr).await?;
    println!("Listening to {}", server_addr);

    serve(listener).await
}
