import { h } from 'preact';
import { useEffect, useRef, useState } from 'preact/hooks';
import { Icon } from './icon.js';
import htm from 'htm';

const html = htm.bind(h);

export function NotificationDropdown({ unread, href, children }) {
    const [open, setOpen] = useState(false);
    const bell = useRef();
    const container = useRef();
    useEffect(() => {
        if (!open) return;
        const outside = event => { if (!container.current?.contains(event.target)) setOpen(false); };
        const escape = event => {
            if (event.key === 'Escape') { setOpen(false); bell.current.focus(); }
        };
        const resized = () => { if (!matchMedia('(min-width: 1024px)').matches) setOpen(false); };
        document.addEventListener('pointerdown', outside);
        document.addEventListener('keydown', escape);
        addEventListener('resize', resized);
        return () => {
            document.removeEventListener('pointerdown', outside);
            document.removeEventListener('keydown', escape);
            removeEventListener('resize', resized);
        };
    }, [open]);
    return html`<div ref=${container}>
        <button ref=${bell} type="button" class="btn-text notification-bell position-relative p-0"
            aria-label=${`Notificaciones, ${unread} sin leer`} aria-expanded=${open}
            aria-controls="notification-panel" onClick=${() => setOpen(value => !value)}>
            <${Icon} name="bell"/>
            ${unread > 0 && html`<span class="notification-dot notification-bell-dot" aria-hidden="true"></span>`}
        </button>
        ${open && html`<section id="notification-panel" class="notification-panel position-absolute border rounded-7 shadow overflow-hidden" aria-label="Notificaciones">
            <div class="d-flex align-items-center justify-content-between gap-3 px-6 py-5 border-bottom">
                <h2 class="fs-md m-0">Notificaciones</h2>
                <span class="fs-xs fg-3">${unread ? `${unread} sin leer` : 'Todo al día'}</span>
            </div>
            ${children}
            <a class="d-flex align-items-center justify-content-between gap-4 px-6 py-5 border-top fs-sm fw-semibold text-decoration-none fg-primary" href=${href}>
                Ver todas las notificaciones<${Icon} name="arrow"/>
            </a>
        </section>`}
    </div>`;
}
