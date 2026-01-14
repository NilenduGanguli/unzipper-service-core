#!/bin/bash
set -e

# Build the docker image
echo "Building Docker image..."
docker build -t unzipper-service .

# Stop any running instance
docker stop unzipper-service || true
docker rm unzipper-service || true

# Run the container
echo "Starting container..."
docker run -d -p 8080:8080 --name unzipper-service unzipper-service

echo "Waiting for service to start..."
sleep 10

# Create test data
echo "Creating test zip..."
python3 create_test_zip.py

# Send request
echo "Sending request..."
curl -X POST -F "file=@test_payload.zip" http://localhost:8080/unzip | python3 -m json.tool

# Cleanup
echo "Cleaning up..."
# docker stop unzipper-service
