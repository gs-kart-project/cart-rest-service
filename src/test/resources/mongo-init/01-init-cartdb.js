// Test-only mirror of docker/mongo/init/01-init-cartdb.js, used by the Testcontainers
// integration harness (AbstractIntegrationTest) to provision the same app-user auth model
// as local dev, against a throwaway container.
db = db.getSiblingDB(process.env.CART_MONGO_DATABASE);
db.createUser({
  user: process.env.CART_MONGO_USER,
  pwd: process.env.CART_MONGO_PASSWORD,
  roles: [{ role: "readWrite", db: process.env.CART_MONGO_DATABASE }],
});
