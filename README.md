# Clone including all submodules
git clone --recurse-submodules https://github.com/BeanMicro/datorum.git

cd datorum

# The submodules (goose, cucumber-rs) are public and use HTTPS URLs so
# that a plain clone works without SSH keys. If you prefer to push over SSH,
# rewrite the URLs locally rather than editing .gitmodules:
git config --global url."git@github.com:".insteadOf "https://github.com/"

# If .gitmodules URLs changed since you cloned, re-point the existing checkouts
git submodule sync --recursive

# If already cloned, initialize and update submodules
git submodule update --init --recursive

# On subsequent pulls, update submodules as well
git pull --recurse-submodules




# Run every test in the workspace
cargo test --workspace

# Run the Gherkin suite in PostgresWire/features (make test)
cargo test -p datorum-postgres-wire --test cucumber

# Run a single test by name, with output
cargo test -p datorum-postgres-wire string_test -- --nocapture


# Demos (see the Makefile)
make demo-server    # datorum-server: startup banner, Avro schema demo, waits for Ctrl+C
make demo-client    # client stub
make demo-h3        # HTTP/3 server over QUIC on [::1]:4433
make demo-pgwire    # PostgreSQL wire-protocol demo on 127.0.0.1:5433
