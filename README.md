# Clone including all submodules
git clone --recurse-submodules git@github.com:BeanMicro/datorum.git

cd datorum

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
