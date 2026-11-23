package com.atenea.unimarket.e2e;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.SelectOption;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Drives the main journey in a real browser against the dev profile: a new student completes the
 * profile and publishes a service, another student finds it with the live catalog search and
 * requests it, and both negotiate until the agreement activates the service.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class KeyFeaturesIT {

    private static final Path DATABASE = Path.of("target", "e2e-key-features.db");
    private static final String TITLE = "Taller de programación en Python";
    private static final Pattern SERVICE_URL = Pattern.compile(".*/servicios/(\\d+)$");

    private static Playwright playwright;
    private static Browser browser;

    @LocalServerPort
    int port;

    private BrowserContext sellerContext;
    private BrowserContext buyerContext;

    /** Starts every run from an empty database, so the new student is not registered yet. */
    @DynamicPropertySource
    static void freshDatabase(DynamicPropertyRegistry registry) throws IOException {
        for (String suffix : new String[] {"", "-journal", "-wal", "-shm"}) {
            Files.deleteIfExists(Path.of(DATABASE + suffix));
        }
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DATABASE);
    }

    @BeforeAll
    static void launchBrowser() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch();
    }

    @AfterAll
    static void closeBrowser() {
        if (playwright != null) {
            playwright.close();
        }
    }

    @BeforeEach
    void openContexts() {
        sellerContext = browser.newContext(contextOptions("seller"));
        buyerContext = browser.newContext(contextOptions("buyer"));
    }

    /** Pass -De2e.videoDir=<dir> to record one video per user, written when the context closes. */
    private Browser.NewContextOptions contextOptions(String user) {
        var options = new Browser.NewContextOptions().setBaseURL("http://localhost:" + port).setLocale("es-CO");
        String videoDir = System.getProperty("e2e.videoDir");
        if (videoDir != null) {
            options.setRecordVideoDir(Path.of(videoDir, user)).setRecordVideoSize(1280, 720);
        }
        return options;
    }

    @AfterEach
    void closeContexts() {
        sellerContext.close();
        buyerContext.close();
    }

    @Test
    void newStudentPublishesAServiceThatAnotherStudentFindsNegotiatesAndHires() {
        Page seller = sellerContext.newPage();
        Page buyer = buyerContext.newPage();

        // Onboarding: a first login is sent to the profile form, which requires the data policy.
        login(seller, "Nuevo estudiante");
        assertThat(seller.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Completa tu perfil")))
                .isVisible();
        seller.getByLabel("Nombre").fill("Sofía Martínez");
        seller.getByLabel("Teléfono de contacto").fill("3109876543");
        seller.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Guardar perfil")).click();
        assertThat(seller.getByRole(AriaRole.ALERT))
                .containsText("Debes aceptar la política de tratamiento de datos personales.");
        seller.getByLabel("He leído y acepto").check();
        seller.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Guardar perfil")).click();
        assertThat(seller.getByText("Tu perfil fue creado.")).isVisible();
        assertThat(seller.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Sofía Martínez")))
                .isVisible();

        // Publish a service with a weekly slot.
        seller.navigate("/servicios/nuevo");
        seller.getByLabel("Categoría").selectOption(new SelectOption().setLabel("Tutorías"));
        seller.getByLabel("Título").fill(TITLE);
        seller.getByLabel("Descripción").fill("Fundamentos de Python con ejercicios prácticos.");
        seller.getByLabel("Tarifa por hora").fill("40000");
        seller.getByLabel("Horas por semana").fill("3");
        seller.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Agregar franja")).click();
        seller.getByLabel("Día").selectOption(new SelectOption().setLabel("Miércoles"));
        seller.getByLabel("Desde").fill("10:00");
        seller.getByLabel("Hasta").fill("12:00");
        seller.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Publicar servicio").setExact(true))
                .click();
        assertThat(seller.getByText("Tu servicio fue publicado.")).isVisible();
        assertThat(seller.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(TITLE))).isVisible();
        assertThat(seller.locator(".rate-panel")).containsText("$40.000 COP");
        assertThat(seller.locator(".schedule-list")).containsText("Miércoles");
        String serviceId = serviceId(seller);

        // Catalog: live search and the category filter narrow the results without reloading the page.
        login(buyer, "Ana Rodríguez");
        buyer.navigate("/servicios");
        Locator cards = buyer.locator(".service-card");
        Locator ourCard = cards.filter(new Locator.FilterOptions().setHasText(TITLE));
        assertThat(ourCard).isVisible();
        assertThat(cards.filter(new Locator.FilterOptions().setHasText("Diseño de pósters académicos")))
                .isVisible();
        buyer.getByLabel("Buscar").fill("python");
        assertThat(cards).hasCount(1);
        assertThat(ourCard).containsText("Sofía Martínez");
        assertThat(ourCard).containsText("$40.000 COP");
        buyer.getByLabel("Buscar").fill("");
        assertThat(cards.filter(new Locator.FilterOptions().setHasText("Diseño de pósters académicos")))
                .isVisible();
        buyer.locator("label.chip").filter(new Locator.FilterOptions().setHasText("Tutorías")).click();
        assertThat(buyer.getByLabel("Tutorías")).isChecked();
        assertThat(cards.filter(new Locator.FilterOptions().setHasText("Diseño de pósters académicos")))
                .hasCount(0);
        assertThat(ourCard).isVisible();
        ourCard.getByRole(AriaRole.LINK).click();

        // Purchase request with a lower rate than the published one.
        buyer.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Solicitar servicio")).click();
        buyer.getByLabel("Tarifa por hora").fill("35000");
        buyer.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Enviar solicitud")).click();
        assertThat(buyer.getByText("Tu solicitud fue enviada.")).isVisible();
        assertThat(state(buyer)).hasText("NEGOCIANDO");
        assertThat(buyer.getByText("Esperando respuesta")).isVisible();

        // The seller is notified, cannot change the conditions while negotiating, and counterproposes.
        String request = "Ana Rodríguez quiere contratar «" + TITLE + "» a $35.000 COP por hora (3 horas por semana).";
        notification(seller, request);
        seller.navigate("/servicios/" + serviceId + "/editar");
        assertThat(seller.getByText("Este servicio tiene negociaciones en curso")).isVisible();
        assertThat(seller.getByLabel("Tarifa por hora")).not().isEditable();
        assertThat(seller.getByLabel("Horas por semana")).not().isEditable();
        notification(seller, request).click();
        seller.getByLabel("Nueva tarifa por hora").fill("38000");
        seller.getByLabel("Nuevas horas semanales").fill("4");
        seller.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Enviar contrapropuesta")).click();
        assertThat(seller.getByText("Tu contrapropuesta ha sido enviada.")).isVisible();
        assertThat(seller.locator("table tbody tr")).hasCount(2);

        // The buyer accepts the counterproposal and the agreement is confirmed.
        notification(buyer, "Sofía Martínez envió una contrapropuesta para «" + TITLE
                + "» a $38.000 COP por hora (4 horas por semana).").click();
        buyer.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Aceptar propuesta vigente")).click();
        assertThat(buyer.getByText("Aceptaste la propuesta.")).isVisible();
        assertThat(state(buyer)).hasText("ACTIVA");

        String agreement = "Acuerdo confirmado para «" + TITLE + "» entre Ana Rodríguez y Sofía Martínez"
                + " a $38.000 COP por hora (4 horas por semana). El servicio está activo.";
        notification(seller, agreement);
        notification(buyer, agreement);

        // An active service leaves the catalog.
        buyer.navigate("/servicios");
        buyer.getByLabel("Buscar").fill("python");
        assertThat(buyer.getByText("No encontramos servicios con esos criterios.")).isVisible();
        assertThat(cards).hasCount(0);
    }

    private static void login(Page page, String account) {
        page.navigate("/login");
        page.getByLabel("Cuenta de prueba").selectOption(new SelectOption().setLabel(account));
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Ingresar en desarrollo")).click();
        page.waitForURL(url -> !url.endsWith("/login"));
    }

    /** Opens the notifications page and returns the link containing that exact message. */
    private static Locator notification(Page page, String message) {
        page.navigate("/notificaciones");
        Locator link = page.locator("#notificaciones-historial").getByRole(AriaRole.LINK)
                .filter(new Locator.FilterOptions()
                        .setHas(page.getByText(message, new Page.GetByTextOptions().setExact(true))));
        assertThat(link).isVisible();
        return link;
    }

    private static Locator state(Page page) {
        return page.locator(".facts div").filter(new Locator.FilterOptions().setHasText("Estado")).locator("dd");
    }

    private static String serviceId(Page page) {
        Matcher matcher = SERVICE_URL.matcher(page.url());
        if (!matcher.matches()) {
            throw new AssertionError("Not on a service page: " + page.url());
        }
        return matcher.group(1);
    }
}
