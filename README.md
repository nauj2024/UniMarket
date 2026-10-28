# UniMarket

Requires Java 21.

Google login requires `config/application-local.yaml` with the OAuth client credentials.
Use `config/application-local.example.yaml` as the template. The local file is excluded from Git.
Register `http://localhost:8080/login/oauth2/code/google` as the redirect URI in Google Cloud.

Run:

```bash
./mvnw spring-boot:run
```

Open http://localhost:8080.

Test:

```bash
./mvnw test
```

Build:

```bash
./mvnw clean package
```

Run the built application:

```bash
java -jar target/unimarket-0.0.1-SNAPSHOT.jar
```
