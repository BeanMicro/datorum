# Clone including all submodules
git clone --recurse-submodules https://github.com/BeanMicro/datorum.git

cd datorum

# The submodules (goose, cucumber-rs, tonic) are public and use HTTPS URLs so
# that a plain clone works without SSH keys. If you prefer to push over SSH,
# rewrite the URLs locally rather than editing .gitmodules:
git config --global url."git@github.com:".insteadOf "https://github.com/"

# If .gitmodules URLs changed since you cloned, re-point the existing checkouts
git submodule sync --recursive

# If already cloned, initialize and update submodules
git submodule update --init --recursive

# On subsequent pulls, update submodules as well
git pull --recurse-submodules




# Run all integration tests
cargo test --test http3_e2e_tests

# Run specific test
cargo test --test http3_e2e_tests test_http3_server_e2e

# Run with output
cargo test --test http3_e2e_tests -- --nocapture
