import { h, render } from 'preact';
import { useEffect, useLayoutEffect, useRef, useState } from 'preact/hooks';
import htm from 'htm';
import { captureCards, reflowCards } from './reflow.js';

const html = htm.bind(h);
const pesos = new Intl.NumberFormat('es-CO');

// Compare only what the cards display, in display order.
function sameCards(previous, next) {
    const fields = ['id', 'titulo', 'categoria', 'ofertador', 'tarifaPorHora', 'intensidadHoraria'];
    return previous.length === next.length && previous.every((card, index) =>
        fields.every(field => card[field] === next[index][field]));
}

function Catalogo({ initial, categories, orders, api, url }) {
    const readLocation = () => {
        const params = new URLSearchParams(location.search);
        const order = params.get('orden');
        return {
            q: params.get('q') || '',
            categories: params.getAll('categoria'),
            order: orders.some(item => item.value === order) ? order : orders[0].value,
            page: Math.max(0, Number(params.get('pagina')) || 0),
        };
    };
    const [filters, setFilters] = useState(readLocation);
    const [results, setResults] = useState(initial);
    const searchInput = useRef(null);
    const cardsList = useRef(null);
    const ghostLayer = useRef(null);
    const beforeCards = useRef(null);
    const stopReflow = useRef(() => {});
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const [request, setRequest] = useState(0);
    const first = useRef(true);
    const sequence = useRef(0);
    const active = useRef(null);
    const delay = useRef(250);
    const filtered = filters.q.trim() !== '' || filters.categories.length > 0;

    function update(next, immediate = false) {
        // Invalidate immediately, including during the debounce interval.
        sequence.current++;
        active.current?.abort();
        delay.current = immediate ? 0 : 250;
        setFilters(next);
        setBusy(true);
        setError('');
        setRequest(value => value + 1);
    }

    function paramsFor(value) {
        const params = new URLSearchParams();
        if (value.q.trim()) params.set('q', value.q.trim());
        value.categories.forEach(id => params.append('categoria', id));
        params.set('orden', value.order);
        if (value.page) params.set('pagina', value.page);
        return params;
    }

    useEffect(() => {
        const restore = () => update(readLocation(), true);
        addEventListener('popstate', restore);
        return () => removeEventListener('popstate', restore);
    }, []);

    useLayoutEffect(() => {
        // Use the embedded first batch without a duplicate initial request.
        if (first.current) {
            first.current = false;
            return;
        }
        const version = sequence.current;
        const controller = new AbortController();
        active.current = controller;
        const timer = setTimeout(async () => {
            const params = paramsFor(filters);
            history.replaceState(null, '', `${url}?${params}`);
            try {
                const response = await fetch(`${api}?${params}`, {
                    signal: controller.signal,
                    headers: { Accept: 'application/json' },
                });
                if (response.redirected || !response.ok ||
                    !response.headers.get('content-type')?.includes('application/json')) {
                    throw new Error('search failed');
                }
                const data = await response.json();
                if (version === sequence.current) {
                    const cardsChanged = !sameCards(results.servicios, data.servicios);
                    if (cardsChanged) {
                        // Capture the current visual position before cancelling an interrupted move.
                        beforeCards.current = captureCards(cardsList.current);
                        stopReflow.current();
                    }
                    if (cardsChanged || results.total !== data.total ||
                        results.pagina !== data.pagina || results.hayMas !== data.hayMas) {
                        setResults(cardsChanged ? data : { ...data, servicios: results.servicios });
                    }
                }
            } catch (failure) {
                if (failure.name !== 'AbortError' && version === sequence.current) {
                    setError('No pudimos actualizar los resultados. Vuelve a buscar o recarga la página.');
                }
            } finally {
                if (version === sequence.current) setBusy(false);
            }
        }, delay.current);
        return () => {
            clearTimeout(timer);
            controller.abort();
        };
    }, [request]);

    useLayoutEffect(() => {
        stopReflow.current = reflowCards(beforeCards.current, cardsList.current, ghostLayer.current);
        beforeCards.current = null;
    }, [results.servicios]);

    useEffect(() => () => stopReflow.current(), []);

    function categoryChip(category) {
        return html`<label class="chip" key=${category.value}>
            <input type="checkbox" name="categoria" value=${category.value}
                checked=${filters.categories.includes(category.value)}
                onChange=${event => update({ ...filters, page: 0,
                    categories: event.currentTarget.checked
                        ? [...filters.categories, category.value]
                        : filters.categories.filter(id => id !== category.value),
                }, true)} />
            <span>${category.label}</span>
        </label>`;
    }

    // Selected categories stay visible and appear first.
    const sorted = [...categories].sort((a, b) =>
        Number(filters.categories.includes(b.value)) - Number(filters.categories.includes(a.value)));
    const visible = sorted.filter((category, index) => index < 10 || filters.categories.includes(category.value));
    const hidden = categories.filter(category => !visible.includes(category));

    const cards = html`
                <section aria-label="Servicios disponibles">
                    <ul class="service-cards" ref=${cardsList}>
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
        <form method="get" action=${url} id="filtros" class="panel catalog-filters"
            onSubmit=${event => { event.preventDefault(); update({ ...filters, page: 0 }, true); }}>
            <div class="catalog-search">
                <label for="q" class="visually-hidden">Buscar</label>
                <div class="catalog-search-field" data-has-query=${filters.q.length > 0}>
                    <input type="search" id="q" name="q" ref=${searchInput} value=${filters.q} maxlength="100" class="form-control"
                        placeholder="Palabra clave, título o nombre del ofertador"
                        onInput=${event => update({ ...filters, q: event.currentTarget.value, page: 0 })} />
                    ${busy && html`<span class="catalog-search-progress" aria-hidden="true"></span>`}
                    ${filters.q.length > 0 && html`<button type="button" class="catalog-search-clear"
                        aria-label="Borrar búsqueda" onClick=${() => {
                            update({ ...filters, q: '', page: 0 }, true);
                            searchInput.current.focus();
                        }}><span aria-hidden="true">×</span></button>`}
                </div>
                <button type="submit" class="btn-solid theme-primary">Buscar</button>
            </div>
            <fieldset class="catalog-chips">
                <legend class="visually-hidden">Categorías</legend>
                ${visible.map(categoryChip)}
                ${hidden.length > 0 && html`<details>
                    <summary class="chip chip-more">Ver todas (+${hidden.length})</summary>
                    ${hidden.map(categoryChip)}
                </details>`}
            </fieldset>
        </form>

        <div class="catalog-results-head">
            <p class="mb-0">
                <strong>${results.total === 1 ? '1 servicio' : `${results.total} servicios`}</strong>
                ${filtered && html` <a href=${url} onClick=${event => {
                    event.preventDefault();
                    update({ q: '', categories: [], order: orders[0].value, page: 0 }, true);
                }}>Quitar filtros</a>`}
            </p>
            <div class="catalog-sort">
                <label for="orden">Ordenar por</label>
                <select id="orden" name="orden" form="filtros" value=${filters.order}
                    class="form-control form-control-sm w-auto"
                    onChange=${event => update({ ...filters, order: event.currentTarget.value, page: 0 }, true)}>
                    ${orders.map(order => html`<option value=${order.value}>${order.label}</option>`)}
                </select>
            </div>
        </div>
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
            <ul class="catalog-ghost-layer" ref=${ghostLayer} aria-hidden="true" inert></ul>
            ${(results.pagina > 0 || results.hayMas) && html`
                <nav aria-label="Páginas del catálogo" class="d-flex gap-4 mt-6">
                    ${results.pagina > 0 && html`<button type="button" class="btn-outline theme-primary btn-sm"
                        disabled=${busy} onClick=${() => update({ ...filters, page: results.pagina - 1 }, true)}>Anterior</button>`}
                    <span>Página ${results.pagina + 1}</span>
                    ${results.hayMas && html`<button type="button" class="btn-outline theme-primary btn-sm"
                        disabled=${busy} onClick=${() => update({ ...filters, page: results.pagina + 1 }, true)}>Siguiente</button>`}
                </nav>`}
        </div>`;
}

const root = document.getElementById('catalogo');
if (root) {
    const { resultado: initial, categorias: categories, ordenes: orders } =
        JSON.parse(document.getElementById('catalogo-inicial').textContent);
    const { api, url } = root.dataset;
    render(html`<${Catalogo} initial=${initial} categories=${categories} orders=${orders} api=${api} url=${url} />`, root);
}
