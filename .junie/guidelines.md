# Datorum Project Development Guidelines

This document provides guidelines and instructions for developing and maintaining the Datorum project.

## Build and Configuration Instructions

### Prerequisites
- Rust and Cargo (latest stable version recommended)
- No additional dependencies are required for the basic project

### Building the Project
To build the project, run:
```bash
cargo build
```

For a release build, use:
```bash
cargo build --release
```

The project is configured as a Rust workspace with a single member crate:
- `postgres`: A Rust crate that will likely handle PostgreSQL-related functionality (currently a simple "Hello, world!" application)

## Testing Information

### Running Tests
To run all tests in the project:
```bash
cargo test
```

To run tests for a specific crate:
```bash
cargo test -p datorum-postgres
```

To run a specific test:
```bash
cargo test string_test
```

### Adding New Tests
Tests are organized in the following way:
1. Unit tests can be added directly in the source files using the `#[cfg(test)]` attribute
2. Integration tests are placed in the `tests/` directory of each crate

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

### Test Configuration
Tests are configured in the Cargo.toml file of each crate. For example, in postgres/Cargo.toml:
```toml
[[test]]
name = "example" # this should be the same as the filename of your test target
```

## Code Style and Development Guidelines

### Code Style
- Follow the standard Rust style guidelines as enforced by `rustfmt`
- Run `cargo fmt` before committing changes to ensure consistent formatting

### Documentation
- Document public APIs using rustdoc comments (`///`)
- Include examples in documentation where appropriate

### Error Handling
- Use Rust's Result type for functions that can fail
- Provide meaningful error messages

### Commit Guidelines
- Write clear, concise commit messages
- Reference issue numbers in commit messages when applicable

### Development Workflow
1. Create a new branch for each feature or bugfix
2. Write tests for new functionality
3. Implement the feature or fix
4. Ensure all tests pass
5. Submit a pull request

## Project Structure
The project is organized as a Rust workspace with the following structure:
- `postgres/`: Crate for PostgreSQL-related functionality
  - `src/`: Source code
  - `tests/`: Integration tests

## Debugging
- Use `println!` or the `dbg!` macro for quick debugging
- For more complex debugging, consider using a debugging tool like `rust-gdb` or `rust-lldb`