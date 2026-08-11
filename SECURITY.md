# Security policy

## Reporting a vulnerability or exposed credential

Please do not open a public issue for a suspected vulnerability, private key,
access token, or other sensitive value. Use GitHub's **Report a vulnerability**
button on the repository's Security page instead.

Include the affected version, the smallest reproducible example you can share,
and the impact you observed. Do not include real diary data or active
credentials unless they are necessary to reproduce the issue.

## Supported versions

Security fixes are made against the latest published release.

## Secrets and local data

Mneme credentials belong in local `.env` files or encrypted GitHub Actions
secrets, never in commits. Android signing keys, server data, diary databases,
backup payloads, and Cloudflare credentials must remain outside this
repository. If a credential is accidentally committed, revoke or rotate it
immediately; removing it in a later commit is not sufficient.
