
test:
	cargo test --test cucumber

demo-server:
	cargo run -p datorum-server --bin datorum-server
demo-client:
	cargo run -p datorum-server --bin client
