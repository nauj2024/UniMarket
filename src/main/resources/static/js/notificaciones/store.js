// One inbox and event connection per page, shared by the header and history.
export function createInbox({ api, eventos, historial, initial, csrf, fetcher = fetch,
    eventSource = url => new EventSource(url) }) {
    let state = { notificaciones: [], sinLeer: 0, ...initial, cargando: !initial, error: '', enVivo: true };
    let request;
    let source;
    let closed = false;
    const listeners = new Set();
    const pending = new Map();
    const update = values => {
        state = { ...state, ...values };
        listeners.forEach(listener => listener(state));
    };

    async function refresh() {
        if (closed) return;
        request?.abort();
        const current = new AbortController();
        request = current;
        try {
            const response = await fetcher(`${api}?historial=${historial}`, {
                signal: current.signal, headers: { Accept: 'application/json' }, cache: 'no-store',
            });
            if (!response.ok || response.redirected) throw new Error('No se pudieron cargar las notificaciones.');
            const data = await response.json();
            if (!closed && request === current) update({ ...data, cargando: false, error: '' });
        } catch (error) {
            if (!closed && request === current && error.name !== 'AbortError') {
                update({ cargando: false, error: 'No se pudieron cargar las notificaciones. Intenta de nuevo.' });
            }
        }
    }

    function action(id, operation, value = '') {
        const key = `${id}/${operation}`;
        if (pending.has(key)) return pending.get(key);
        const work = (async () => {
            try {
                const response = await fetcher(`${api}/${id}/${operation}${value}`, {
                    method: 'POST', headers: { [csrf.header]: csrf.token, Accept: 'application/json' },
                });
                if (!response.ok || response.redirected) throw new Error('No se pudo guardar el cambio.');
                await refresh();
                return true;
            } catch {
                if (!closed) update({ error: 'No se pudo guardar el cambio. Intenta de nuevo.' });
                return false;
            } finally {
                pending.delete(key);
            }
        })();
        pending.set(key, work);
        return work;
    }

    return {
        getSnapshot: () => state,
        subscribe(listener) { listeners.add(listener); return () => listeners.delete(listener); },
        refresh,
        read(id) {
            const item = state.notificaciones.find(n => n.id === id);
            return item?.leida ? Promise.resolve(true) : action(id, 'leer');
        },
        dismiss: id => action(id, 'descartar'),
        restore: id => action(id, 'descartar', '?descartada=false'),
        connect() {
            if (source) return;
            closed = false;
            source = eventSource(eventos);
            source.onopen = () => { update({ enVivo: true }); refresh(); };
            source.addEventListener('cambio', refresh);
            source.onerror = () => { if (!closed) update({ enVivo: false }); };
            refresh();
        },
        close() { closed = true; source?.close(); source = null; request?.abort(); },
    };
}
