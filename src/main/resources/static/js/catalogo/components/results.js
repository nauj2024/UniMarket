import { h } from 'preact';
import htm from 'htm';

const html = htm.bind(h);
const pesos = new Intl.NumberFormat('es-CO');

export function CatalogResults({ results, filtered, busy, error, url, animation, onPageChange }) {
    const cards = html`
                <section aria-label="Servicios disponibles">
                    <ul class="service-cards" ref=${animation.listRef}>
                        ${results.servicios.map(service => html`<li key=${service.id} data-service-id=${service.id}>
                            <article class="service-card">
                                <p class="service-card-category">${service.categoria}</p>
                                <h2><a href=${`${url}/${service.id}`}>${service.titulo}</a></h2>
                                <p class="service-meta">
                                    <span>${service.ofertador}</span> ·
                                    <span>${service.intensidadHoraria} h por semana</span>
                                </p>
                                <p class="service-card-rate">
                                    <data value=${service.tarifaPorHora}>$${pesos.format(service.tarifaPorHora)} COP</data><span>/hora</span>
                                </p>
                            </article>
                        </li>`)}
                    </ul>
                </section>`;

    return html`
        <p role="status" aria-live="polite" class="visually-hidden">
            ${busy ? 'Buscando…' : error ? '' : `${results.total} ${results.total === 1 ? 'servicio encontrado' : 'servicios encontrados'}`}
        </p>
        ${error && html`<p role="alert" class="alert theme-warning">${error}</p>`}
        <div aria-busy=${busy} class="catalog-results">
            ${results.servicios.length > 0 ? cards : html`<div class="panel">
                    <p class="empty-note">${filtered
                        ? 'No encontramos servicios con esos criterios. Prueba con otras palabras o quita algunos filtros.'
                        : 'Aún no hay servicios disponibles.'}</p>
                    ${!filtered && html`<a href=${`${url}/nuevo`} class="btn-solid theme-primary">Publicar un servicio</a>`}
                </div>`}
            <ul class="catalog-ghost-layer" ref=${animation.ghostRef} aria-hidden="true" inert></ul>
            ${(results.pagina > 0 || results.hayMas) && html`
                <nav aria-label="Páginas del catálogo" class="d-flex gap-4 mt-6">
                    ${results.pagina > 0 && html`<button type="button" class="btn-outline theme-primary btn-sm"
                        disabled=${busy} onClick=${() => onPageChange(results.pagina - 1)}>Anterior</button>`}
                    <span>Página ${results.pagina + 1}</span>
                    ${results.hayMas && html`<button type="button" class="btn-outline theme-primary btn-sm"
                        disabled=${busy} onClick=${() => onPageChange(results.pagina + 1)}>Siguiente</button>`}
                </nav>`}
        </div>`;
}
