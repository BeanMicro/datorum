// Server/main/src/bin/client.rs
pub mod proto {
    tonic::include_proto!("datorum");
}

use proto::datorum_service_client::DatorumServiceClient;
use proto::{HealthCheckRequest, QueryRequest};
use tonic::transport::Channel;

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    let mut client = DatorumServiceClient::connect("http://[::1]:50051").await?;

    let health = client
        .health_check(tonic::Request::new(HealthCheckRequest {
            service: "rust-client".into(),
        }))
        .await?;
    println!("HealthCheck status: {:?}", health.into_inner().status);

    let query = client
        .execute_query(tonic::Request::new(QueryRequest {
            query: "SELECT * FROM users".into(),
            parameters: vec![],
        }))
        .await?;
    println!("ExecuteQuery success: {}", query.get_ref().success);

    let mut stream = client
        .stream_query(tonic::Request::new(QueryRequest {
            query: "SELECT id, name FROM users".into(),
            parameters: vec![],
        }))
        .await?
        .into_inner();
    while let Some(item) = stream.message().await? {
        println!("{:?}", item.row);
    }

    Ok(())
}