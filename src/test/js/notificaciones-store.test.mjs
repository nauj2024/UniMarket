import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createInbox } from '../../main/resources/static/js/notificaciones/store.js';

const initial = { notificaciones: [{ id: 1, leida: false, descartada: false }], sinLeer: 1 };
const response = data => ({ ok: true, redirected: false, json: async () => data });
function setup(fetcher) {
    const events = { addEventListener(name, fn) { this[name] = fn; }, close() { this.closed = true; } };
    const inbox = createInbox({ api: '/api/notificaciones', eventos: '/eventos', historial: false,
        initial, csrf: { header: 'X-CSRF-TOKEN', token: 'test-token' }, fetcher, eventSource: () => events });
    return { inbox, events };
}
const flush = () => new Promise(resolve => setImmediate(resolve));

test('refreshes after a live event and reconnect, including changes missed while disconnected', async () => {
    let data = initial;
    const { inbox, events } = setup(async () => response(data));
    inbox.connect();
    await flush();
    events.onerror();
    assert.equal(inbox.getSnapshot().enVivo, false);
    data = { notificaciones: [{ id: 2, leida: false }], sinLeer: 2 };
    events.onopen();
    await flush();
    assert.equal(inbox.getSnapshot().enVivo, true);
    assert.equal(inbox.getSnapshot().sinLeer, 2);
    data = { ...data, sinLeer: 3 };
    events.cambio();
    await flush();
    assert.equal(inbox.getSnapshot().sinLeer, 3);
    inbox.close();
    assert.equal(events.closed, true);
});

test('ignores stale responses even when the transport does not honor cancellation', async () => {
    const resolves = [];
    const { inbox } = setup(() => new Promise(resolve => resolves.push(resolve)));
    const first = inbox.refresh();
    const second = inbox.refresh();
    resolves[1](response({ ...initial, sinLeer: 2 }));
    await second;
    resolves[0](response(initial));
    await first;
    assert.equal(inbox.getSnapshot().sinLeer, 2);
});

test('deduplicates reads, sends CSRF, and keeps state unchanged when saving fails', async () => {
    const calls = [];
    let resolvePost;
    const { inbox } = setup((url, options) => {
        calls.push({ url, options });
        if (options.method) return new Promise(resolve => { resolvePost = resolve; });
        return Promise.resolve(response({ notificaciones: [{ ...initial.notificaciones[0], leida: true }], sinLeer: 0 }));
    });
    const read = inbox.read(1);
    assert.equal(inbox.read(1), read);
    assert.equal(calls.length, 1);
    assert.equal(calls[0].options.headers['X-CSRF-TOKEN'], 'test-token');
    resolvePost(response());
    assert.equal(await read, true);
    assert.equal(inbox.getSnapshot().sinLeer, 0);
    const dismiss = inbox.dismiss(1);
    resolvePost({ ok: false });
    assert.equal(await dismiss, false);
    assert.equal(inbox.getSnapshot().notificaciones[0].descartada, false);
    assert.match(inbox.getSnapshot().error, /guardar/);
});

test('does not replace usable data with a redirected login response', async () => {
    const { inbox } = setup(async () => ({ ok: true, redirected: true }));
    await inbox.refresh();
    assert.equal(inbox.getSnapshot().sinLeer, 1);
    assert.match(inbox.getSnapshot().error, /cargar/);
});
