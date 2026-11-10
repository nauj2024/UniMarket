import { useEffect, useState } from 'preact/hooks';

export function useInbox(inbox, history) {
    const [state, setState] = useState(inbox.getSnapshot);
    const [undo, setUndo] = useState(null);

    useEffect(() => {
        const unsubscribe = inbox.subscribe(setState);
        setState(inbox.getSnapshot());
        return unsubscribe;
    }, [inbox]);
    useEffect(() => {
        if (undo === null) return;
        const timer = setTimeout(() => setUndo(null), 5000);
        return () => clearTimeout(timer);
    }, [undo]);
    // The mobile navigation keeps a normal link to the full page.
    useEffect(() => {
        if (history) return;
        const link = document.querySelector('#notificaciones-movil');
        link?.setAttribute('aria-label', `Notificaciones, ${state.sinLeer} sin leer`);
        link?.querySelector('[data-notification-dot]')?.classList.toggle('d-none', !state.sinLeer);
    }, [state.sinLeer, history]);

    async function restore() {
        if (await inbox.restore(undo)) setUndo(null);
    }

    return { state, undo, dismissed: setUndo, restore };
}
