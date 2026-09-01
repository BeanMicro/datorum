#!/bin/bash

echo "Testing Datorum Server with Dynamic Schema Support"
echo "=================================================="

# Run from the repository root regardless of where this script is invoked from
cd "$(dirname "$0")/../.."
echo "Starting server..."
cargo run -p datorum-server --bin datorum-server &
SERVER_PID=$!

# Wait for server to start
sleep 3

echo ""
echo "Testing client functionality..."
echo "Running client (this should work exactly as before)..."

# Run the client
cargo run -p datorum-server --bin client

# Clean up
echo ""
echo "Stopping server..."
kill $SERVER_PID

echo "Test completed!"
