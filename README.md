# TransferHelper Backend

Spring Boot backend service with Docker setup for team development.

## Requirements

- Java 21 (for local non-Docker run)
- Docker Desktop (or Docker Engine + Compose)

## Run with Docker (recommended)

From the project root, start backend + PostgreSQL:

```bash
docker compose up --build
```

App will be available at:

- http://localhost:8080

PostgreSQL will be available at:

- host: `localhost`
- port: `5432`
- database: `transferhelper`
- username: `transferhelper`
- password: `transferhelper`

To stop everything:

```bash
docker compose down
```

To stop and remove DB volume too:

```bash
docker compose down -v
```

## Run locally without Docker

```bash
./gradlew bootRun
```

On Windows, use:

```powershell
.\gradlew.bat bootRun
```

By default, local run uses in-memory H2.

## Configuration notes

The app reads datasource and JPA settings from environment variables with safe defaults in [src/main/resources/application.properties](src/main/resources/application.properties).

This allows:

- local development with H2 by default
- containerized development with PostgreSQL via `docker-compose.yml`

## Connect to Supabase

Supabase uses PostgreSQL, so your Spring Boot app connects to it like any other Postgres database.

Your backend is already set up for this in [src/main/resources/application.properties](src/main/resources/application.properties):

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_DRIVER_CLASS_NAME`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `SPRING_JPA_HIBERNATE_DDL_AUTO`

To point the backend at Supabase instead of local Docker Postgres, set these environment variables before starting the app:

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://YOUR_SUPABASE_HOST:5432/postgres?sslmode=require
SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.postgresql.Driver
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=YOUR_SUPABASE_DB_PASSWORD
SPRING_JPA_HIBERNATE_DDL_AUTO=update
```

Notes:

- The database is usually `postgres` unless you created another one.
- The username is commonly `postgres`.
- Use the database password from Supabase project settings.
- `sslmode=require` is important for hosted Postgres connections.

## Connect to Google OAuth2

Google OAuth2 is already wired in these classes:

- [SecurityConfig](src/main/java/com/transferhelper/backend/security/SecurityConfig.java)
- [GoogleClientRegistrationConfig](src/main/java/com/transferhelper/backend/security/GoogleClientRegistrationConfig.java)
- [GoogleOAuth2LoginSuccessHandler](src/main/java/com/transferhelper/backend/security/GoogleOAuth2LoginSuccessHandler.java)
- [GoogleOAuthController](src/main/java/com/transferhelper/backend/security/GoogleOAuthController.java)

The backend expects these environment variables:

```bash
GOOGLE_CLIENT_ID=your-google-client-id
GOOGLE_CLIENT_SECRET=your-google-client-secret
APP_AUTH_JWT_SECRET=at-least-32-bytes-change-this-in-production
APP_AUTH_SUCCESS_REDIRECT_URI=http://localhost:8081/auth/callback
APP_AUTH_ALLOWED_REDIRECT_URIS=http://localhost:8081/auth/callback,http://localhost:19006/auth/callback,transferme://auth/callback
APP_CORS_ALLOWED_ORIGINS=http://localhost:8081,http://localhost:19006,http://localhost:3000
```

Create a Google OAuth client in Google Cloud Console and add this redirect URI:

```text
http://localhost:8080/login/oauth2/code/google
```

If deployed, also add your production callback URL with the same path:

```text
https://your-domain.com/login/oauth2/code/google
```

## Current login flow in this backend

Spring Security handles the Google OAuth2 browser flow, then the backend issues an app JWT for React Native/web API calls:

1. The frontend opens `/oauth2/authorization/google?redirect_uri=YOUR_CALLBACK_URI`
2. Google redirects back to `/login/oauth2/code/google`
3. The backend creates or updates an `app_user` row
4. The backend redirects to the allowed callback URI with `token` and `token_type=Bearer`
5. The frontend stores the token and sends it on API calls:

```text
Authorization: Bearer YOUR_TOKEN
```

The frontend can then call:

- `/api/auth/me` for the current authenticated user
- `/api/google/oauth` as a backwards-compatible alias for the same user info

## React Native note (important)

Because React Native does not reliably share backend session cookies between the browser login and API requests, this backend now uses the mobile-friendly pattern: Spring Boot owns Google OAuth2 and returns a bearer token to the app callback.

For Expo, `transferme://auth/callback` is included in the default allowed redirect list. For web development, `http://localhost:8081/auth/callback` and `http://localhost:19006/auth/callback` are included by default.

## Future Google OAuth2 implementation notes

Common next steps are:

- Add frontend callback handling that extracts and stores the bearer token.
- Add logout behavior on the frontend by deleting the stored token.
- Add refresh tokens or shorter-lived access tokens before production.
- Set `APP_AUTH_JWT_SECRET` to a strong Render secret, not the dev default.
- Apply the Supabase migration that creates `app_user` before testing against hosted Supabase.
