
.PHONY: test test-all demo-server demo-client demo-h3 demo-pgwire

test:
	cargo test -p datorum-postgres-wire-steps --test cucumber

test-all:
	cargo test --workspace

demo-server:
	cargo run -p datorum-server --bin datorum-server
demo-client:
	cargo run -p datorum-server --bin client

# HTTP/3 file server over QUIC; serves 200 OK unless --dir is given
demo-h3:
	cargo run -p datorum-server --bin server

# Minimal PostgreSQL wire-protocol demo on 127.0.0.1:5433
demo-pgwire:
	cargo run -p datorum-server --bin sample
