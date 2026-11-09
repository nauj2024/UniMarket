import { test } from 'node:test';
import assert from 'node:assert/strict';
import { catalogReducer } from '../../main/resources/static/js/catalogo/catalog-state.js';

function fixture() {
    return {
        filters: { q: '', categories: [], order: 'fecha', page: 0 },
        results: {
            servicios: [{ id: 1, titulo: 'Tutoría', tarifaPorHora: 20000 }],
            total: 1, pagina: 0, hayMas: false,
        },
        request: null, busy: false, error: '',
    };
}

test('unchanged cards retain their identity while pagination and totals update', () => {
    const initial = fixture();
    const searching = catalogReducer(initial, { type: 'search', filters: initial.filters, immediate: true });
    const results = {
        servicios: initial.results.servicios.map(card => ({ ...card })),
        total: 12, pagina: 1, hayMas: true,
    };
    const completed = catalogReducer(searching, { type: 'success', results });
    assert.equal(completed.results.servicios, initial.results.servicios);
    assert.deepEqual(completed.results, { ...results, servicios: initial.results.servicios });
    assert.equal(completed.busy, false);
});

test('price changes and reordered cards replace the displayed list', () => {
    const initial = fixture();
    initial.results.servicios.push({ id: 2, titulo: 'Diseño', tarifaPorHora: 30000 });
    const reordered = { ...initial.results, servicios: [...initial.results.servicios].reverse() };
    const afterReorder = catalogReducer(initial, { type: 'success', results: reordered });
    assert.equal(afterReorder.results.servicios, reordered.servicios);

    const repriced = {
        ...reordered,
        servicios: reordered.servicios.map(card => ({ ...card, tarifaPorHora: 40000 })),
    };
    const afterPriceChange = catalogReducer(afterReorder, { type: 'success', results: repriced });
    assert.equal(afterPriceChange.results.servicios, repriced.servicios);
});

test('a failed search keeps the displayed results and submitting again creates a fresh request', () => {
    const initial = fixture();
    const filters = { ...initial.filters, q: 'tutoría' };
    const searching = catalogReducer(initial, { type: 'search', filters, immediate: false });
    const failed = catalogReducer(searching, { type: 'failure' });
    assert.equal(failed.results, initial.results);
    assert.equal(failed.busy, false);
    assert.ok(failed.error);

    const retrying = catalogReducer(failed, { type: 'search', filters, immediate: true });
    assert.notEqual(retrying.request, searching.request);
    assert.equal(retrying.request.immediate, true);
    assert.equal(retrying.filters, filters);
    assert.equal(retrying.results, initial.results);
    assert.equal(retrying.busy, true);
    assert.equal(retrying.error, '');
});
