docker run -d \
  -p 8080:8080 \
  -v /path/to/local/certs:/app/certs \
  -e USE_CERTS=true \
  -e DOCUMENTUM_CERT_PATH=/app/certs/client.p12 \
  -e DOCUMENTUM_CERT_PASSWORD=your_password \
  unzipper-service