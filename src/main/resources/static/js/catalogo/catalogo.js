import { h, render } from 'preact';
import htm from 'htm';
import { useCatalog } from './use-catalog.js';
import { CatalogFilters } from './components/filters.js';
import { CatalogToolbar } from './components/toolbar.js';
import { CatalogResults } from './components/results.js';

const html = htm.bind(h);

function Catalogo({ initial, categories, orders, api, url }) {
    const { state, update, reset, animation } = useCatalog({ initial, orders, api, url });
    const { filters, results, busy, error } = state;
    const filtered = filters.q.trim() !== '' || filters.categories.length > 0;

    return html`
        <${CatalogFilters}
            filters=${filters} categories=${categories} busy=${busy} url=${url}
            onChange=${update} />
        <${CatalogToolbar}
            total=${results.total} filtered=${filtered} orders=${orders}
            order=${filters.order} url=${url} onReset=${reset}
            onOrderChange=${order => update({ ...filters, order, page: 0 }, true)} />
        <${CatalogResults}
            results=${results} filtered=${filtered} busy=${busy} error=${error}
            url=${url} animation=${animation}
            onPageChange=${page => update({ ...filters, page }, true)} />`;
}

const root = document.getElementById('catalogo');
if (root) {
    const { resultado: initial, categorias: categories, ordenes: orders } =
        JSON.parse(document.getElementById('catalogo-inicial').textContent);
    const { api, url } = root.dataset;
    render(html`<${Catalogo} initial=${initial} categories=${categories} orders=${orders} api=${api} url=${url} />`, root);
}
