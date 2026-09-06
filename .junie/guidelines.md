# Datorum Project Development Guidelines

This document provides guidelines and instructions for developing and maintaining the Datorum project.

## Build and Configuration Instructions

### Prerequisites
- Rust and Cargo (latest stable version recommended; the workspace sets a minimum of 1.87.0)
- Git, with the submodules checked out — see below

### Checking out the submodules
Two workspace members live in git submodules, so Cargo cannot even load the
workspace manifest until they are present. On a fresh clone:

```bash
git submodule update --init --recursive
```

Without this, `cargo build` fails before compiling anything with
`failed to load manifest for workspace member PostgresWire/main`.

### Building the Project
To build the project, run:
```bash
cargo build
```

For a release build, use:
```bash
cargo build --release
```

### Workspace members
- `PostgresWire/main` (`datorum-postgres-wire`): the PostgreSQL frontend/backend
  protocol server. It is both a library and a binary — `serve(listener)` is the
  library entry point, so tests can run a server in-process. Queries are executed
  against an in-memory SQLite database.
- `PostgresWire/steps` (`datorum-postgres-wire-steps`): Cucumber step definitions
  and the `cucumber` test target. It depends on the server library, which is why
  the target lives here rather than in `PostgresWire/main` — the other direction
  would be a dependency cycle.
- `Server/main` (`datorum-server`): the application server, where the HTTP/3
  (QUIC) and Apache Avro spikes live.
- `QualityEngineering/main` (`e2e`) and `QualityEngineering/steps` (`steps`): the
  load-test and end-to-end harnesses.
- `QualityEngineering/goose` and `QualityEngineering/cucumber-rs` (plus its
  `codegen` crate): vendored forks, pulled in as submodules.

## Testing Information

### Running Tests
Scope test runs to the crates this repository owns. A bare `cargo test
--workspace` also runs the vendored `goose` and `cucumber-rs` suites — dozens of
integration tests that bind ports and start mock servers, and which include
deliberately failing self-tests. They belong to upstream.

```bash
# The crates this repository owns
cargo test -p datorum-server \
           -p datorum-postgres-wire-steps --lib \
           -p e2e \
           -p steps

# The wire-protocol server
cargo test -p datorum-postgres-wire --lib --bins --test example --test ssl_test

# The Gherkin suite
cargo test -p datorum-postgres-wire-steps --test cucumber
# or, equivalently
make test
```

These are the same commands CI runs; see `.github/workflows/ci.yml`.

### Adding New Tests
Tests are organized in the following way:
1. Unit tests can be added directly in the source files using the `#[cfg(test)]` attribute
2. Integration tests are placed in the `tests/` directory of each crate
3. Behaviour is specified in Gherkin under `PostgresWire/features`, with the
   step definitions in `PostgresWire/steps/src/lib.rs`

Example of adding a new test:
```rust
#[cfg(test)]
mod tests {
    #[test]
    fn my_new_test() {
        // Test logic here
        assert!(true);
    }
}
```

### Testing against a running server
Start the server in-process on an ephemeral port rather than shelling out to
`cargo run` against a fixed port. Binding `127.0.0.1:0` lets the OS pick a free
port, and a spawned task dies with the test, so nothing can outlive a failed
assertion:

```rust
let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
let addr = listener.local_addr().unwrap();
tokio::spawn(async move { let _ = serve(listener).await; });
```

`PostgresWire/main/tests/ssl_test.rs` and the Cucumber steps both follow this
pattern.

### Test Configuration
Test targets are declared in the Cargo.toml of each crate. The `cucumber` target
sets `harness = false` because it supplies its own `main`:

```toml
[[test]]
name = "cucumber" # this should be the same as the filename of your test target
harness = false
```

It reads its feature files from `PostgresWire/features`, overridable with the
`FEATURES_DIR` environment variable.

## Code Style and Development Guidelines

### Code Style
- Follow the standard Rust style guidelines as enforced by `rustfmt`
- Run `cargo fmt` before committing changes to ensure consistent formatting
- `cargo clippy --all-targets` must be clean; `[workspace.lints]` denies
  `clippy::all` for the crates this repository owns

### Documentation
- Document public APIs using rustdoc comments (`///`)
- Include examples in documentation where appropriate

### Error Handling
- Use Rust's Result type for functions that can fail
- Provide meaningful error messages
- Map database failures onto the correct SQLSTATE rather than a catch-all; see
  the error-code mapping in `PostgresWire/main/src/lib.rs`

### Commit Guidelines
- Write clear, concise commit messages
- Reference issue numbers in commit messages when applicable
- Use British English

### Development Workflow
1. Create a new branch for each feature or bugfix
2. Write tests for new functionality
3. Implement the feature or fix
4. Ensure all tests pass
5. Submit a pull request

## Project Structure
The project is organized as a Rust workspace with the following structure:
- `PostgresWire/`: the wire-protocol server, its step definitions, and the
  `features/` Gherkin specifications
- `Server/`: the application server
- `QualityEngineering/`: test tooling, including the vendored submodules
- `contextmapper/`: Gradle harness for the ContextMapper DSL, driven by the
  helper scripts in `scripts/`
- `domain.cml`: the target domain model, written in the ContextMapper DSL
- `.github/workflows/`: CI, and the dev deployment pipeline that gates on it

Each Rust crate follows the usual layout, with sources in `src/` and integration
tests in `tests/`.

## Debugging
- Use `println!` or the `dbg!` macro for quick debugging
- For more complex debugging, consider using a debugging tool like `rust-gdb` or `rust-lldb`
