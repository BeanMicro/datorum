use std::path::Path;
use cucumber::World as _;
use datorum_postgres_wire_steps::PostgresWireWorld;

#[tokio::main]
async fn main() {
    // override via FEATURES_DIR, default to tests/features
    let features_dir = std::env::var("FEATURES_DIR").unwrap_or_else(|_| "../features/".into());
    //
    //
    // ExampleWorld::run("postgres-wire/features/")
    //     .await;

    println!("Looking for features in: {}", features_dir);

    if !Path::new(&features_dir).exists() {
        eprintln!(
            "Error: Features directory '{}' does not exist!",
            features_dir
        );
        panic!("Features directory not found");
    }

    println!("Using features from: {}", features_dir);

    PostgresWireWorld::cucumber()
        .fail_on_skipped()
        .run(features_dir)
        .await;

    // Create a runtime and block on the cucumber tests
    // let rt = tokio::runtime::Runtime::new().unwrap();
    // rt.block_on(async {
    //     // ExampleWorld::run(features_dir).fail_on_skipped().await;
    //     let runner = ExampleWorld::cucumber()
    //         .fail_on_skipped();
    //
    //     runner.run(features_dir).await;
    // });
}
