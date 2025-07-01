#!/bin/bash

echo "Testing Datorum Server with Dynamic Schema Support"
echo "=================================================="

# Start the server in the background
cd /Users/mac/workspace/bean/micro/datorum/Server/main
echo "Starting server..."
cargo run --bin datorum-server &
SERVER_PID=$!

# Wait for server to start
sleep 3

echo ""
echo "Testing client functionality..."
echo "Running client (this should work exactly as before)..."

# Run the client
cargo run --bin client

# Clean up
echo ""
echo "Stopping server..."
kill $SERVER_PID

echo "Test completed!"
