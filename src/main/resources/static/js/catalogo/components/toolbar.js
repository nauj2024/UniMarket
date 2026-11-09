import { h } from 'preact';
import htm from 'htm';

const html = htm.bind(h);

export function CatalogToolbar({ total, filtered, orders, order, url, onOrderChange, onReset }) {
    return html`
        <div class="catalog-results-head">
            <p class="mb-0">
                <strong>${total === 1 ? '1 servicio' : `${total} servicios`}</strong>
                ${filtered && html` <a href=${url} onClick=${event => {
                    event.preventDefault();
                    onReset();
                }}>Quitar filtros</a>`}
            </p>
            <div class="catalog-sort">
                <label for="orden">Ordenar por</label>
                <select id="orden" name="orden" form="filtros" value=${order}
                    class="form-control form-control-sm w-auto"
                    onChange=${event => onOrderChange(event.currentTarget.value)}>
                    ${orders.map(order => html`<option value=${order.value}>${order.label}</option>`)}
                </select>
            </div>
        </div>`;
}
