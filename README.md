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

With the current code, Spring Security handles the browser login flow:

1. The frontend opens `/oauth2/authorization/google`
2. Google redirects back to `/login/oauth2/code/google`
3. Spring Security creates the authenticated session
4. The frontend calls `/api/google/oauth`
5. The backend returns the logged-in user's `email` and `name`

## React Native note (important)

The current implementation is **session-based** (`oauth2Login()`), which is a great skeleton for a browser-based app.

For a **React Native** app, a token-based flow is usually a better fit than relying on browser cookies. Common patterns:

- **Recommended (mobile-first): Supabase Auth owns Google login**
  - React Native signs in with Google via Supabase Auth.
  - The app calls Spring Boot APIs with `Authorization: Bearer <supabase-jwt>`.
  - Spring Boot validates the JWT (you'd typically switch to `oauth2ResourceServer().jwt()` and validate against Supabase's JWKS).

- **Alternative: Spring Boot owns Google login, then issues an app token**
  - React Native opens the backend Google login URL in a browser.
  - Backend completes Google OAuth, then returns/redirects with an app token (JWT) that the mobile app stores securely.

If you keep the current cookie/session approach with React Native, you’ll need to ensure the in-app browser and your API client consistently share cookies, which is often fragile.

## Future Google OAuth2 implementation notes

This is intentionally a skeleton. Common next steps (depending on whether you're building web vs React Native) are:

- **Define the post-login UX**: redirect to your frontend (common) instead of returning `{"ok": true}` when there is no saved request.
- **Persist application users**: create/update a `User` row after login (email, name, Google subject) and optionally map roles/permissions.
- **Decide session vs token auth**: for React Native, plan on a token/JWT approach (either Supabase Auth JWTs or your own JWTs).
- **Add CORS + credentials (if cross-origin)**: if your frontend is on a different origin, configure CORS and allow credentials so cookies are sent.
- **Revisit CSRF**: CSRF is disabled right now; if you keep cookie-based sessions in production, choose an explicit CSRF strategy.
- **Logout**: add a clear logout endpoint and document how the frontend should clear auth state.

## Suggested next implementation step

If you keep the current Spring Boot OAuth approach (browser-based), the next backend step is to add:

- a `User` entity
- a JPA repository
- a service that creates or updates the user record in Supabase Postgres after successful Google login

That gives you:

- Google for identity
- Supabase for persistent user storage
- Spring Boot as the API layer your React Native app talks to

If you switch to the mobile-first Supabase Auth pattern, the next backend step becomes:

- configure Spring Security as a **resource server** that validates Supabase JWTs
- use the JWT subject/claims to identify the caller
