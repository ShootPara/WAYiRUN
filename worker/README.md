# WAYiRUN — Development Worker

## 1 Current boundary

This contains the cloud foundation, Google account sessions, and resumable completed-run storage transport. Google phone sign-in is user-confirmed. Run storage now requires a verified session and provides immutable upload receipts and owner-isolated reads. Android's archive encoder/import/queue and remote discard are not connected yet. See AUTH_CONTRACT.md and RUN_STORAGE_CONTRACT.md for exact behavior and limits.

Migration 0001 stores foundation metadata, 0002 adds accounts/sessions/challenges, and 0003 adds run manifests and chunks. Migrations are additive and preserve existing accounts and sessions. Do not edit applied migrations. There is no sample user, hardcoded token, or development authentication bypass.

## 2 Local development

Use Node 22 or 24 and the committed npm lockfile. From this directory:

```powershell
npm.cmd ci
npm.cmd test
npm.cmd run db:migrate:local
npm.cmd run dev
```

`npm test` compiles TypeScript and runs Node tests against Miniflare/workerd and real local D1 bindings. Each runtime is disposed after the test. It checks migration readiness, constraints, unsupported routes/methods, HEAD, closed API access, and development deployment guards. Test data is ephemeral and never uses the remote database. `db:migrate:local` uses Wrangler's separate local database. Apply migrations a second time to verify the ledger reports no pending work.

Generated `build/`, `.wrangler/`, and `node_modules/` are ignored by the repository. Do not commit `.dev.vars` or CLI credentials. Exact development-tool versions and integrity hashes are in package-lock.json; npm audit checks these dependencies separately from application behavior.

## 3 Development deployment

Configuration binds only the new `wayirun-dev-db` to Worker `wayirun-dev` in the selected account. IDs in configuration are resource identifiers, not credentials. There is no production environment, route, custom domain, R2 bucket, or scheduled trigger.

```powershell
npm.cmd run deploy:dev
```

This runs tests, checks the specific account/Worker/database target, dry-runs the bundle, applies pending remote D1 migrations, then deploys. A failed stage stops subsequent stages. The initial additive migration is safe to remain in place if a deploy fails. Later schema changes must preserve compatibility with the previously deployed Worker; migration and deployment are not a single transaction. Do not automatically reverse migrations or delete data on failure.

The target guard is protection against an accidental configuration change, not a substitute for restricted Cloudflare credentials. Local Wrangler OAuth supplies access. For account changes, review both configuration and guard explicitly. `deploy:dev` accepts no extra arguments and supports only this development target.

After deployment, verify `/healthz` and `/readyz` return 200, unknown paths return 404, and unauthenticated `/api/runs` and `/api/run-uploads` requests return 401. No remote test writes any user data. Runtime logs are disabled in configuration and responses expose no database IDs, credentials, request headers, or stack traces.

## 4 Git integration

Remote repository: `ShootPara/RunningApp`. `development` is the deployment branch; feature work uses `codex/` branches. Existing main/history and pending Android edits are preserved. The cloud-only commit does not capture uncommitted phone changes.

Once the Cloudflare GitHub app has been authorized for this repository, configure Workers Builds:

| Field | Value |
| --- | --- |
| Worker | `wayirun-dev` |
| Branch | `development` |
| Root | `worker` |
| Build command | `npm ci && npm test` |
| Deploy command | `npm run deploy:dev` |

The user connected Git on September 15, 2026. Their screenshot confirms the intended settings and also enables non-production branch builds with `npx wrangler versions upload`. These upload preview versions instead of promoting the live deployment; preview URLs remain disabled in Wrangler. Push only the development branch for this deployment to avoid an unnecessary second build. Do not add a second deployment pipeline for the same branch.

## 5 Next implementation

Connect Android using a validated archive encoder/decoder, immutable start-time account ownership, durable retries, and the user-approved explicit import action in settings. Keep new settings behind the gear. Existing local runs are never assigned automatically. Preserve offline local recording and implement discard/queue reconciliation before enabling uploads. Production OAuth and full deletion reconciliation remain separate milestones. Announcement selection and phone music verification remain deferred.

## 6 References

[D1 migrations](https://developers.cloudflare.com/d1/wrangler-commands/), [local D1 separation](https://developers.cloudflare.com/d1/best-practices/local-development/), [Miniflare testing](https://developers.cloudflare.com/workers/testing/miniflare/writing-tests/), [Workers Builds configuration](https://developers.cloudflare.com/workers/ci-cd/builds/configuration/).
