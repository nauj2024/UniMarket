import { useEffect, useLayoutEffect, useReducer, useRef } from 'preact/hooks';
import { catalogReducer, sameCards } from './reducer.js';
import { useCardReflow } from './animation/use-card-reflow.js';

export function useCatalog({ initial, orders, api, url }) {
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
    const [state, dispatch] = useReducer(catalogReducer, null, () => ({
        filters: readLocation(),
        results: initial,
        request: null,
        busy: false,
        error: '',
    }));
    const { results, request } = state;
    const animation = useCardReflow(results.servicios);
    const active = useRef(null);

    function update(filters, immediate = false) {
        // Invalidate immediately, including during the debounce interval.
        active.current?.abort();
        dispatch({ type: 'search', filters, immediate });
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
        if (!request) return;
        const controller = new AbortController();
        const { signal } = controller;
        active.current = controller;
        const timer = setTimeout(async () => {
            const params = paramsFor(request.filters);
            history.replaceState(null, '', `${url}?${params}`);
            try {
                const response = await fetch(`${api}?${params}`, {
                    signal,
                    headers: { Accept: 'application/json' },
                });
                if (response.redirected || !response.ok ||
                    !response.headers.get('content-type')?.includes('application/json')) {
                    throw new Error('search failed');
                }
                const data = await response.json();
                if (signal.aborted) return;
                if (!sameCards(results.servicios, data.servicios)) animation.capture();
                dispatch({ type: 'success', results: data });
            } catch {
                if (!signal.aborted) dispatch({ type: 'failure' });
            }
        }, request.immediate ? 0 : 250);
        return () => {
            clearTimeout(timer);
            controller.abort();
        };
    }, [request]);

    function reset() {
        update({ q: '', categories: [], order: orders[0].value, page: 0 }, true);
    }

    return { state, update, reset, animation };
}
