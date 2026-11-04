package com.atenea.unimarket.dev;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:sqlite:target/dev-proxy-login-test.db")
@ActiveProfiles("dev")
class DevProxyLoginTests {

    private static final String PUBLIC_HOST = "silver-doodle-wxpgg9g7vwjc947r-8080.app.github.dev";
    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER).build();

    @LocalServerPort
    int port;

    @Test
    void loginBehindCodespacesProxyRedirectsToPublicOrigin() throws Exception {
        assertLoginRedirect(true);
    }

    @Test
    void localLoginStillRedirectsToLocalOrigin() throws Exception {
        assertLoginRedirect(false);
    }

    private void assertLoginRedirect(boolean proxied) throws Exception {
        var login = client.send(request("/login", proxied).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(login.statusCode()).isEqualTo(200);
        var csrf = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(login.body());
        assertThat(csrf.find()).as("login form contains CSRF token").isTrue();
        String cookie = login.headers().firstValue("set-cookie").orElseThrow().split(";", 2)[0];
        String form = "subject=demo-ana&_csrf=" + URLEncoder.encode(csrf.group(1), StandardCharsets.UTF_8);
        var response = client.send(request("/dev/login", proxied)
                .header("Cookie", cookie)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(302);
        URI publicOrigin = URI.create(proxied ? "https://" + PUBLIC_HOST : "http://localhost:" + port);
        URI target = publicOrigin.resolve(response.headers().firstValue("location").orElseThrow());
        assertThat(target).isEqualTo(publicOrigin.resolve("/"));
    }

    private HttpRequest.Builder request(String path, boolean proxied) {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (proxied) {
            request.header("X-Forwarded-Host", PUBLIC_HOST)
                    .header("X-Forwarded-Proto", "https")
                    .header("X-Forwarded-Port", "443");
        }
        return request;
    }
}
