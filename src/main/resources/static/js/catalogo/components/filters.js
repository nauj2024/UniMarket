import { h } from 'preact';
import { useRef } from 'preact/hooks';
import htm from 'htm';

const html = htm.bind(h);

export function CatalogFilters({ filters, categories, busy, url, onChange }) {
    const searchInput = useRef(null);

    function categoryChip(category) {
        return html`<label class="chip" key=${category.value}>
            <input type="checkbox" name="categoria" value=${category.value}
                checked=${filters.categories.includes(category.value)}
                onChange=${event => onChange({ ...filters, page: 0,
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

    return html`
        <form method="get" action=${url} id="filtros" class="panel catalog-filters"
            onSubmit=${event => { event.preventDefault(); onChange({ ...filters, page: 0 }, true); }}>
            <div class="catalog-search">
                <label for="q" class="visually-hidden">Buscar</label>
                <div class="catalog-search-field" data-has-query=${filters.q.length > 0}>
                    <input type="search" id="q" name="q" ref=${searchInput} value=${filters.q} maxlength="100" class="form-control"
                        placeholder="Palabra clave, título o nombre del ofertador"
                        onInput=${event => onChange({ ...filters, q: event.currentTarget.value, page: 0 })} />
                    ${busy && html`<span class="catalog-search-progress" aria-hidden="true"></span>`}
                    ${filters.q.length > 0 && html`<button type="button" class="catalog-search-clear"
                        aria-label="Borrar búsqueda" onClick=${() => {
                            onChange({ ...filters, q: '', page: 0 }, true);
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
        </form>`;
}
