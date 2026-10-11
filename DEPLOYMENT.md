# Backend deployment · Railway

This repository is standalone. The Dockerfile, Maven project and `railway.json` are at its root.

1. Open the existing **WarrantyHub backend service** in Railway. Change its GitHub source to `c03-renusri/WarrantyHub-backend`, branch `main`. If missing, allow the Railway GitHub integration to see this repository.
2. Change Root Directory from `/backend` to `/` and the config file path to `/railway.json`. Use the root Dockerfile.
3. Keep the existing **MySQL service**, its persistent volume, public backend domain and all existing environment variables. Do not create a replacement database or reset migrations.
4. Keep the five `MYSQL…` references pointing to the existing database service, a valid `JWT_SECRET`, and `CORS_ORIGINS=https://warranty-hub-one.vercel.app`.
5. Leave `DB_URL`, `DB_USER`, `DB_PASSWORD` unset when using Railway references. They override those references. A blank `MYSQLDATABASE` prevents Flyway from finding its schema.
6. Deploy and confirm `/actuator/health/readiness` returns HTTP 200 with `{"status":"UP"}`. Check authenticated login, products, private receipts and profile/photo flows from the frontend.

The root `railway.json` configures Docker builds, readiness and restart policy. No new migration is required just because code moved into a separate repository: V1, V2 and V3 contents match the previous project. Preserve the original JWT signing key if you intend to retain existing sessions; do not publish it.

Changing the Git connection requires access to the owner's Railway service settings. Keep the old combined repository until the new source connection is verified. The public backend remains `https://warrantyhub-production.up.railway.app`.

Backups must include accounts, products, receipts, profile photos, service records and Flyway history. Backend files use private database storage, so no local uploads directory or additional volume is required. Use a single replica until shared rate limiting is configured.
