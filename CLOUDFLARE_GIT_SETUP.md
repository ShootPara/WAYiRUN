# WAYiRUN — Cloudflare and Git setup

## 1 Target environment

Section 4 is now implemented and deployed. See [worker/STATUS.md](worker/STATUS.md) for resource identifiers, verification, and the remaining Section 5 GitHub connection. Sections below preserve the setup instructions; resource names are now verified rather than merely proposed.

Keep the existing repository: `https://github.com/ShootPara/RunningApp`, with this checkout at `<repository-root>`. Use the same Windows user that runs this workspace. No new repository, separate checkout, or Cloudflare Pages project is needed.

The planned cloud application is a Cloudflare Worker with D1 for run records. Later, R2 stores photos and the Worker serves the desktop application. Start with a separate development Worker/database; production and real run uploads come after authentication and ownership checks. Proposed resource names are `wayirun-dev` and `wayirun-dev-db`; these are names to create, not existing verified resources.

## 2 Do now — GitHub login on this computer

Git and Node/npm are installed. GitHub CLI was not found on PATH during this check. Install GitHub CLI in PowerShell:

```powershell
winget install --id GitHub.cli --exact
```

Open a new PowerShell window after installation, then run:

```powershell
gh auth login --hostname github.com --git-protocol https --web
gh auth setup-git
gh auth status
gh repo view ShootPara/RunningApp
```

Complete the browser login as an account with write access to `ShootPara/RunningApp`. This lets local Git operations and pull requests use your authenticated account. No password or access token needs to be pasted into this conversation. [GitHub login](https://cli.github.com/manual/gh_auth_login), [Git credential setup](https://cli.github.com/manual/gh_auth_setup-git).

## 3 Do now — Cloudflare login on this computer

In PowerShell, run:

```powershell
Set-Location '<repository-root>'
npx.cmd wrangler@4 login
npx.cmd wrangler@4 whoami
```

Accept npm's install prompt if shown, then complete Cloudflare's browser authorization. Use the Cloudflare account intended for WAYiRUN. Wrangler stores its login locally; the worker project will pin an exact Wrangler version when created. Tell me the intended account name and account ID if more than one account is listed. Account IDs are configuration, not secret API tokens. [Wrangler login and account inspection](https://developers.cloudflare.com/workers/wrangler/commands/general/).

These logins give this local workspace usable credentials. They are separate from signing into GitHub or Cloudflare in a browser tab. Restart the desktop app if a newly installed CLI is not visible to it.

## 4 What I will prepare in the repository

With those logins available, the cloud milestone will add `worker/` containing the Worker source, package lock, Wrangler configuration, D1 migrations, tests, and documented deploy commands. I will create and bind the development database and Worker in the selected account. There is no need to manually design tables or create placeholder secrets now.

Use `codex/` feature branches for work and a dedicated development deployment branch named `development`. Preserve the existing default branch and history. Tests run before deployment; only approved migration files are applied to the development database. Keep production resources separate. Your request authorizes preparing Git/cloud access for subsequent work; it does not require pushing the entire current uncommitted workspace during the permission fix.

## 5 Connect Git to Cloudflare after the Worker code exists

Do this after `worker/package.json` and `worker/wrangler.jsonc` have been added and pushed. Connecting now would point Cloudflare at files that do not exist yet.

In Cloudflare's Workers & Pages area, connect the development Worker to GitHub using Workers Builds. Authorize the GitHub integration for **only `ShootPara/RunningApp`**. Use these intended settings:

| Setting | Value |
| --- | --- |
| Worker | `wayirun-dev`, matching the Wrangler configuration |
| Repository | `ShootPara/RunningApp` |
| Deployment branch | `development` |
| Root directory | `worker` |
| Build command | `npm ci && npm test` |
| Deploy command | `npm run deploy:dev` |

I will define `deploy:dev` before these settings are enabled, including the development-only migration/deploy sequence. Disable other-branch auto-deployment initially. Android-only changes can later be excluded using build watch paths. Cloudflare's label “production branch” means the selected branch for that Worker; this Worker still belongs to our development environment. Do not point its D1 binding at a production database.

Cloudflare Workers Builds supports GitHub integration, selected-branch deployments, and configurable root/build/deploy commands. Worker names must match the configuration. [Git integration](https://developers.cloudflare.com/workers/ci-cd/builds/git-integration/), [build configuration](https://developers.cloudflare.com/workers/ci-cd/builds/configuration/).

You may need to approve Cloudflare's GitHub app installation in the browser once. After that, pushes to `development` can trigger deployment; local Wrangler access lets me manage migrations, bindings, and Worker configuration during authorized development. Do not enable a second GitHub Actions deployment pipeline for the same branch.

## 6 Secrets and later account setup

Keep authentication tokens in the CLI credential stores. Worker runtime secrets belong in Cloudflare Worker secrets; local test values belong in ignored `.dev.vars` files. Never put secrets in Git, APKs, screenshots, or chat. Build-time secrets and Worker runtime secrets are different settings. Existing ignore rules already exclude local environment and Wrangler state files. [Wrangler configuration](https://developers.cloudflare.com/workers/wrangler/configuration/).

No R2 bucket, custom domain, Google OAuth client, or OpenAI key is needed for this environment preparation. Google OAuth needs the final Android application identifier, signing certificate, and chosen web origin; I will provide the exact values when that integration is ready. Do not register the current provisional debug application as the production client. Photos/R2 and AI keys remain later milestones.

## 7 Ready-to-work checklist

- GitHub CLI login succeeds and can access `ShootPara/RunningApp` with write rights.
- Wrangler login succeeds and the intended Cloudflare account is identified.
- Tell me the account name/ID; do not send the credentials.
- I prepare and verify `worker/`, the development resources, and the deployment branch.
- Connect Workers Builds using Section 5 once its files exist.

The original guide did not create resources. The later user-authorized Section 4 execution created the development resources and prepared the cloud-only Git branches; see worker/STATUS.md. Automatic Workers Builds Git integration remains the separate Section 5 step.
