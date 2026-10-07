# 1 WAYiRUN Worker and browser

## 1.1 Scope

The Cloudflare Worker provides Google account sessions, owner-isolated run storage, deletion protection, encrypted per-account OpenAI-key storage, durable coaching, photos, location/weather context, publication, CSV export, private browser history and public individual-run pages.

D1 migrations `0001`–`0011` define the persisted schema. Browser assets under `web/` are bundled and served by the Worker.

## 1.2 Requirements

- Node.js 22 or 24.
- npm and the committed `package-lock.json`.
- Wrangler authentication only for explicitly authorized remote work.

## 1.3 Local development

```powershell
npm.cmd ci
npm.cmd test
npm.cmd run db:migrate:local
npm.cmd run dev
```

`npm test` compiles TypeScript, creates deploy output and runs the Node/Miniflare/workerd/D1 suite against isolated local state. Local tests must not contact remote databases or use real accounts.

Generated `build/`, `.wrangler/`, `node_modules/` and `.dev.vars*` are ignored.

## 1.4 Environments

| Concern | Development | Production |
| --- | --- | --- |
| Configuration | `wrangler.jsonc` | `wrangler.production.jsonc` |
| Worker | `wayirun-dev` | `wayirun-prod` |
| D1 | `wayirun-dev-db` | `wayirun-prod-db` |
| Origin | Development workers.dev origin | `https://wayirun.slopcopy.com` |
| Write mode | `frozen` | `normal` |

OAuth clients, rate-limit namespaces and D1 databases are distinct. The production configuration uses a custom domain and disables workers.dev access.

## 1.5 Deployment guards

Remote deployment is never an incidental build step. Development and production scripts verify the exact configured account, Worker, D1 database and environment before mutation.

```powershell
npm.cmd run deploy:dev
npm.cmd run deploy:prod
```

Run either command only when the active task explicitly authorizes that environment. Production deployment and smoke checks must not fall back to development configuration. A failed migration or deployment must be diagnosed and repaired forward; never edit an applied migration or reset production data casually.

## 1.6 Migrations

Applied migrations are immutable:

- `0001_bootstrap.sql`
- `0002_accounts.sql`
- `0003_run_uploads.sql`
- `0004_run_deletions.sql`
- `0005_openai_keys.sql`
- `0006_coaching_jobs.sql`
- `0007_run_photos.sql`
- `0008_run_locations.sql`
- `0009_public_runs.sql`
- `0010_run_weather.sql`
- `0011_photo_weather.sql`

Add a new numbered forward migration for future changes. Migration export/import tooling must preserve owner relationships, content hashes, deletion state and archive/media integrity.

## 1.7 Secrets and data

Configuration contains public origins, resource identifiers and OAuth client IDs required by the applications. Cloudflare credentials, session secrets, coaching keyring material, user API keys, private exports and backups must remain outside Git and logs.

See the colocated `*_CONTRACT.md` files for implementation-level API invariants. Current project status belongs in the root `CURRENT_STATE.md`; historical Worker plans and deployment diaries are under `docs/history/`.
