# Giving a computer write access to this repo

This repo is `ifriedenson/mcq-tracker-anddoid`. Everything below is a **you-on-github.com** action: only the repository owner can grant access, so this page describes the steps a person performs — it does not mean access has already been granted.

Pick one of the three paths. Each is enough on its own.

## Repo access at a glance

| Who | How they write |
| --- | --- |
| Repository owner / collaborators | Their own GitHub account, via one of the three paths below |
| Agent / cloud sandbox | Server-side credential injected by the host's Git integration at push time |
| Anyone without write access | Cannot push |

Pushes from this project land on the remote's `main` branch, so a push takes effect for everyone immediately — coordinate first if others are working on it. If you want `main` protected, enable a ruleset or branch protection rule under the repository's **Settings**.

## 1. GitHub CLI (simplest)

On the machine that needs to push:

```bash
gh auth login          # choose GitHub.com → HTTPS → authenticate in browser
gh auth setup-git      # makes gh the git credential helper for github.com
git clone https://github.com/ifriedenson/mcq-tracker-anddoid.git
```

`gh auth setup-git` is the step people forget: without it, `git push` still asks for a password. Verify with `gh auth status`. The account you log in with needs push rights on this repository (it is yours, or you are a collaborator with write access).

## 2. Fine-grained personal access token (no browser on that machine)

1. On GitHub: **Settings → Developer settings → Personal access tokens → Fine-grained tokens → Generate new token**.
2. **Repository access:** Only select repositories → `mcq-tracker-anddoid`.
3. **Permissions → Repository permissions → Contents: Read and write.** That single permission is enough to clone, commit and push. Nothing else is required.
4. Copy the token once — GitHub never shows it again.

Then teach git to use it:

```bash
git config --global credential.helper store
git clone https://github.com/ifriedenson/mcq-tracker-anddoid.git
# at the Username prompt: your GitHub username
# at the Password prompt: paste the token (not your account password)
```

`store` keeps the token in plaintext in `~/.git-credentials`. If your machine has a real keychain, prefer it:

```bash
# macOS
git config --global credential.helper osxkeychain
# Linux with libsecret installed
git config --global credential.helper libsecret
```

Fine-grained tokens expire — set an expiry and note the date, and revoke the token if a machine is lost or retired.

## 3. SSH deploy key (one repo, one machine, no account-wide access)

Best when an unattended machine or CI box should push to this repository and nothing else.

```bash
ssh-keygen -t ed25519 -C "deploy-key@mcq-tracker-anddoid" -f ~/.ssh/mcq_tracker_deploy
```

Add the **public** half (`~/.ssh/mcq_tracker_deploy.pub`) on GitHub: repo → **Settings → Deploy keys → Add deploy key**, and tick **Allow write access** — without that checkbox the key is read-only.

Then point the clone at SSH instead of HTTPS:

```bash
git remote set-url origin git@github.com:ifriedenson/mcq-tracker-anddoid.git
ssh -T git@github.com          # should answer "successfully authenticated"
```

If the key is not the default `~/.ssh/id_ed25519`, either pass `-i ~/.ssh/mcq_tracker_deploy` on each command or add a `~/.ssh/config` entry. A deploy key can be attached to only one repository, and an unencrypted private key gives whoever holds the file exactly the access you granted.

## How this sandbox is set up

For comparison with your own machine — this is what was observed here, not a recommendation:

- `git remote -v` is plain HTTPS with **no credentials embedded**: `https://github.com/ifriedenson/mcq-tracker-anddoid.git`.
- No credential helper is configured (`git config --list` has no `credential.*` entry), there is no global `~/.gitconfig`, and **no token-like environment variable is set** in the sandbox.
- `gh` is installed (v2.45.0) but **not logged in**: `gh auth status` reports "You are not logged into any GitHub hosts".
- Consequently system git cannot authenticate by itself — a direct `git ls-remote` fails with `could not read Username for 'https://github.com'`.
- Writes still work because the agent's authenticated Git integration attaches a write-capable GitHub credential **server-side at push time**; the token never exists inside this sandbox. Commits are made locally on a `freebuff/<chat-id>` branch and pushed from there to the remote's `main`.
- Committer identity is not configured either: commits here reuse the repository's existing author identity, supplied per-command as `GIT_AUTHOR_*` / `GIT_COMMITTER_*` environment variables rather than written into any `git config`.

The practical consequence: nothing in this repo, and nothing in the sandbox filesystem, contains a reusable credential — so moving work to your own machine means authenticating there with one of the three paths above.

## Never commit secrets

Do not commit tokens, `.env` files, private keys, or keystores. If you create a `.env` for local values, list it in `.gitignore` before your first commit — a token added once stays in history until it is rewritten or revoked, and revoking it is the only real fix.

To revoke: PATs under **Settings → Developer settings → Personal access tokens**; deploy keys under the repo's **Settings → Deploy keys**; CLI sessions with `gh auth logout`.
