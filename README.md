# UniMarket

## Cómo Ejecutar en Codespaces 

1. Abre el Codespaces
2. Abre la terminal y ejecuta: ´./mvnw spring-boot:run -Dspring-boot.run.profiles=dev´
3. Ve a la pestaña de puertos
4. Selecciona el globo de navegación y abre UniMarket

## Cómo abrir el Orender

preview main  [here.](https://unimarket-main.onrender.com/)

## Cómo descargar y correr el proyecto en local

1. **Clona el repositorio:**
   ```bash
   git clone [https://github.com/nauj2024/UniMarket](https://github.com/nauj2024/UniMarket)
   cd unimarket

Requires Java 21.

java -version

Modificar la versión para ArchLinux:
# Instala Java 21 si no lo tienes
sudo pacman -S jdk21-openjdk

# Comprueba las versiones disponibles en el sistema
archlinux-java status

# Configura Java 21 como predeterminado
sudo archlinux-java set java-21-openjdk

Run:

```bash
./mvnw spring-boot:run
```

Open http://localhost:8080.

Behind an HTTPS proxy (for example `tailscale serve`), run with the `render` profile so the app honors `X-Forwarded-*` headers:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=render
```

Without it, the app sends Google an `http://` callback and login fails with `redirect_uri_mismatch`. Register the proxy's `https://<host>/login/oauth2/code/google` as an authorized redirect URI on the OAuth client.

Test:

```bash
# Test
./mvnw test

# Build
./mvnw clean package

# Run build
java -jar target/unimarket-0.0.1-SNAPSHOT.jar
```

## Env

### Render

- `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_ID`
- `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_SECRET`
- `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_SCOPE=openid,email,profile`

## Desarrollo sin Google

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

El perfil `dev` usa `unimarket-dev.db` y permite elegir una identidad simulada en
`/login`: Ana, Carlos, Laura o un estudiante nuevo que debe completar su perfil.
Carga los datos de `src/main/resources/db/dev/data.sql` una sola vez. Los cambios
se conservan entre reinicios. Para restaurar los datos de ejemplo, detén la app,
elimina `unimarket-dev.db` y vuelve a iniciarla con `dev`.

Los demás perfiles usan el inicio de sesión con Google y no cargan datos de ejemplo.

La migración `V5__datos_demo.sql` se retiró. Si una base existente ya la aplicó,
Flyway detectará esa migración como ausente. Antes de actualizar esa base, haz una
copia de seguridad con la app detenida y ejecuta desde la raíz del proyecto:

```bash
./mvnw flyway:repair -Dflyway.url=jdbc:sqlite:unimarket.db
./mvnw flyway:validate -Dflyway.url=jdbc:sqlite:unimarket.db
```

Sustituye `unimarket.db` si la base está en otra ruta. El plugin usa las migraciones
actuales de `src/main/resources/db/migration` y marca V5 como eliminada. Esto
conserva los datos existentes, incluidos los de demostración; no los borra
automáticamente. Una base local descartable puede recrearse con la app detenida.
V5 queda retirada: la próxima migración debe ser V6 o posterior.
