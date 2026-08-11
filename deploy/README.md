# Deploying Mneme

The Compose stack contains the lightweight Mneme encrypted-backup API. Place
search and reverse geocoding run on the Android device and do not use this
server.

For local development, start the API:

```bash
cd deploy
cp .env.example .env
docker compose up -d --build mneme-server
```

The Android emulator reaches the API at
`http://10.0.2.2:8080`; debug builds permit this local cleartext address.

Create a one-time app token for a phone:

```bash
docker compose exec mneme-server /mneme token create --name "My phone"
```

The command prints the plaintext token once. Store it in the Android app; the
server retains only an Argon2id hash. Tokens can be inspected and revoked:

```bash
docker compose exec mneme-server /mneme token list
docker compose exec mneme-server /mneme token revoke TOKEN_ID
```

Enter the URL and token under **Settings → Self-hosted backup**. Mneme encrypts
the manifest and original photos on Android before upload, offers **Back up
now**, and schedules a network-constrained backup about every six hours.

## Cloudflare Tunnel

Mneme uses two independent credentials: `CLOUDFLARE_TUNNEL_TOKEN` connects the
container to Cloudflare, while a Mneme app token authenticates one Android
device. Create a remotely managed tunnel and route its public hostname to
`http://mneme-server:8080`, set the Cloudflare token in `.env`, then enable the
optional Compose profile:

```bash
docker compose --profile tunnel up -d
```

The persistent `mneme-data` volume contains the SQLite catalog and opaque
encrypted objects. Back it up as part of normal host administration.

## Updating

Release tags publish matching versioned and `latest` images to Docker Hub. To
update the server while keeping its named data volume:

```bash
docker compose pull mneme-server
docker compose up -d mneme-server
```

The Android app compares the connected server's embedded version with the
latest GitHub release and displays these commands when it is behind.
