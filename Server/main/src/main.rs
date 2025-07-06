mod avro_example;
mod avro_ex_doc;

#[tokio::main]
pub async fn main() -> Result<(), Box<dyn std::error::Error>> {
    let addr = "[::1]:50051".parse::<std::net::SocketAddr>()?;

    println!("Datorum server starting on {}", addr);

    // Demonstrate Avro round-trip (can be removed or gated by a feature later)
    // avro_example::demo_avro_roundtrip()?;
    avro_ex_doc::test_schemas();

    Ok(())
}
