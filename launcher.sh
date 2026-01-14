#!/bin/bash

IMAGE_NAME="unzipper-service"
CONTAINER_NAME="unzipper-service"
PORT="8080"

function show_usage {
    echo "Usage: $0 {build|up|down}"
    echo "  build : Build the Docker image"
    echo "  up    : Run the Docker container (detached)"
    echo "  down  : Stop and remove the Docker container"
}

if [ $# -eq 0 ]; then
    show_usage
    exit 1
fi

case "$1" in
    build)
        echo "Building Docker image '$IMAGE_NAME'..."
        docker build -t $IMAGE_NAME .
        ;;
    up)
        echo "Starting container '$CONTAINER_NAME' on port $PORT..."
        # Check if container exists (running or stopped) and force remove
        if [ "$(docker ps -aq -f name=^/${CONTAINER_NAME}$)" ]; then
            echo "Removing existing container..."
            docker rm -f $CONTAINER_NAME
        fi

        # Ensure network exists
        docker network create unzip-network 2>/dev/null || true

        docker run -d \
            --name "$CONTAINER_NAME" \
            --network unzip-network \
            --cpus="4.0" \
            --memory="2g" \
            -p "$PORT:8080" \
            -e USE_CERTS="false" \
            -e DOCUMENTUM_CERT_PATH="" \
            -e DOCUMENTUM_CERT_PASSWORD="" \
            "$IMAGE_NAME"
            # To use certs, mount the directory and set env vars:
            # -v /path/to/local/certs:/app/certs \
            # -e USE_CERTS="true" \
            # -e DOCUMENTUM_CERT_PATH="/app/certs/client.p12" \
            # -e DOCUMENTUM_CERT_PASSWORD="password"

        echo "Container started. Logs:"
        docker logs --tail 10 $CONTAINER_NAME
        ;;
    down)
        echo "Stopping and removing container '$CONTAINER_NAME'..."
        if [ "$(docker ps -aq -f name=^/${CONTAINER_NAME}$)" ]; then
            docker rm -f $CONTAINER_NAME
            echo "Container removed."
        else
            echo "Container not found."
        fi
        ;;
    *)
        show_usage
        exit 1
        ;;
esac
