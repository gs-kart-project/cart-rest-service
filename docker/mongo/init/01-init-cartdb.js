// GS Kart — local-dev MongoDB provisioning (source of truth).
// Auto-runs on container first-boot (mounted into /docker-entrypoint-initdb.d/) as the root user.
// Secrets are read from container env (populated from .env) — nothing secret is committed.
db = db.getSiblingDB(process.env.CART_MONGO_DATABASE);
db.createUser({
  user: process.env.CART_MONGO_USER,
  pwd: process.env.CART_MONGO_PASSWORD,
  roles: [{ role: "readWrite", db: process.env.CART_MONGO_DATABASE }],
});
