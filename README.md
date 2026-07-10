# cart-rest-service
This repository maintains code for Cart service of GS Kart project. This is capstone project at Scaler

## Local infra

MongoDB, Redis, and Kafka run in Compose for local dev, grouped under the "gskart-cart" project in
OrbStack/Docker.

1. Copy `.env.example` to `.env` and fill in real values (never commit `.env`):
   ```
   cp .env.example .env
   ```
2. Start the containers:
   ```
   docker compose up -d
   ```
   First boot runs `docker/mongo/init/01-init-cartdb.js`, which creates the `gskart-CartDb`
   database and the app user.
3. Run the app with the same env vars loaded (the app runs on the host, not in Compose):
   ```
   set -a; source .env; set +a
   ./mvnw spring-boot:run
   ```
