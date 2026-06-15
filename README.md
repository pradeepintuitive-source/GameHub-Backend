# GameHub Backend

This repository contains the GameHub backend (Spring Boot).

## Run locally

1. Set JDK 21 in your shell (example path used by this project):

```bash
export JAVA_HOME=/Users/swethamurthy1/.jdk/jdk-21.0.10/jdk-21.0.10+7/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
```

2. Provide database environment variables (Railway will inject these in production):

```bash
export GAMEHUB_DB_URL='jdbc:postgresql://postgres.railway.internal:5432/<db>'
export GAMEHUB_DB_USERNAME='<user>'
export GAMEHUB_DB_PASSWORD='<password>'
```

3. Run the app:

```bash
mvn spring-boot:run
```

Swagger UI will be available at: `http://localhost:8080/swagger-ui/index.html`

## Push to GitHub

1. Initialize repository (if not already):

```bash
git init
git add .
git commit -m "Initial import: GameHub backend with OpenAPI" 
```

2. Add remote and push:

```bash
git remote add origin git@github.com:<your-username>/<repo>.git
git branch -M main
git push -u origin main
```

## Deploy on Railway

1. Create a Railway project and add the GitHub repository as the deployment source.
2. Add the Postgres and (optionally) Redis services in Railway.
3. In your Railway service variables, add the mappings (Railway will provide the values):

```
GAMEHUB_DB_URL=${{ Postgres.DATABASE_URL }}
GAMEHUB_DB_USERNAME=${{ Postgres.PGUSER }}
GAMEHUB_DB_PASSWORD=${{ Postgres.PGPASSWORD }}
```

4. Configure the build command (default `mvn -B -DskipTests package`) and the start command (example):

```
java -jar target/gamehub-1.0.0.jar
```

Railway will build and run your app and inject the required environment variables.

## Notes

- Do NOT check in secrets to the repository.
- Swagger is provided by `springdoc-openapi` and the UI is available at `/swagger-ui/index.html`.
