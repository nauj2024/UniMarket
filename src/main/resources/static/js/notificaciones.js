import { h, render } from 'preact';
import { useEffect, useRef, useState } from 'preact/hooks';
import htm from 'htm';
import { createInbox } from './notificaciones-store.js';

const html = htm.bind(h);
const types = {
    SOLICITUD_RECIBIDA: { title: 'Recibiste una solicitud', theme: 'info', icon: 'inbox' },
    CONTRAPROPUESTA_RECIBIDA: { title: 'Tienes una nueva contrapropuesta', theme: 'warning', icon: 'exchange' },
    ACUERDO_CONFIRMADO: { title: 'Acuerdo confirmado', theme: 'primary', icon: 'check' },
    SOLICITUD_RECHAZADA: { title: 'Tu solicitud fue rechazada', theme: 'secondary', icon: 'close' },
};
const paths = {
    bell: 'M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4',
    inbox: 'M4 4h16l2 12v4H2v-4L4 4M2 15h6l2 3h4l2-3h6',
    exchange: 'M4 7h16m-4-4 4 4-4 4M20 17H4m4-4-4 4 4 4',
    check: 'm5 12 4 4L19 6', close: 'm6 6 12 12M18 6 6 18',
    arrow: 'M5 12h14m-5-5 5 5-5 5',
};
const dateFormat = new Intl.DateTimeFormat('es-CO', {
    day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit', timeZone: 'America/Bogota',
});
const relativeFormat = new Intl.RelativeTimeFormat('es-CO', { numeric: 'auto' });
function timeLabel(value) {
    const seconds = Math.max(0, Math.floor((Date.now() - new Date(value)) / 1000));
    if (seconds < 60) return 'Ahora';
    if (seconds < 3600) return relativeFormat.format(-Math.floor(seconds / 60), 'minute');
    if (seconds < 86400) return relativeFormat.format(-Math.floor(seconds / 3600), 'hour');
    return dateFormat.format(new Date(value));
}
function Icon({ name }) {
    return html`<svg class="app-icon" viewBox="0 0 24 24" aria-hidden="true"><path d=${paths[name]}/></svg>`;
}
function useInbox(inbox) {
    const [state, setState] = useState(inbox.getSnapshot);
    useEffect(() => {
        const unsubscribe = inbox.subscribe(setState);
        setState(inbox.getSnapshot());
        return unsubscribe;
    }, [inbox]);
    return state;
}

function Notification({ item, inbox, solicitudes, dismissible, onDismiss }) {
    const timer = useRef();
    const [busy, setBusy] = useState(false);
    const type = types[item.tipo];
    const href = `${solicitudes}/${item.solicitudId}#historial`;
    const cancel = () => clearTimeout(timer.current);
    const enter = () => {
        cancel();
        if (!item.leida) timer.current = setTimeout(() => inbox.read(item.id), 700);
    };
    useEffect(() => cancel, []);
    async function dismiss() {
        cancel();
        setBusy(true);
        if (await inbox.dismiss(item.id)) onDismiss(item.id);
        setBusy(false);
    }
    async function navigate(event) {
        // Preserve native new-tab and modified-click behavior.
        if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
        event.preventDefault();
        cancel();
        await inbox.read(item.id);
        location.assign(href);
    }
    return html`<li class="notification-item d-flex align-items-start text-start gap-4 p-6 border-bottom"
        onMouseEnter=${enter} onMouseLeave=${cancel}>
        <span class=${`notification-type d-flex align-items-center justify-content-center rounded-5 flex-shrink-0 fg-${type.theme} bg-subtle-${type.theme}`} title=${type.title}>
            <${Icon} name=${type.icon}/>
        </span>
        <a class="notification-content flex-fill min-w-0 text-decoration-none fg-body" href=${href}
            onFocus=${enter} onBlur=${cancel} onClick=${navigate}>
            <span class="visually-hidden">${item.leida ? 'Leída. ' : 'Sin leer. '}</span>
            <strong class=${`d-block fs-sm ${item.leida ? 'fw-medium' : 'fw-semibold'}`}>${type.title}</strong>
            <p class="fs-xs fg-3 mt-1 mb-2">${item.mensaje}</p>
            <span class="d-flex align-items-center gap-3 fs-xs fg-3">
                <time dateTime=${item.fechaCreacion} title=${dateFormat.format(new Date(item.fechaCreacion))}>${timeLabel(item.fechaCreacion)}</time>
                ${!item.leida && html`<span class="notification-dot flex-shrink-0" aria-hidden="true"></span>`}
            </span>
        </a>
        ${dismissible && html`<button type="button" class="btn-text notification-dismiss p-0 flex-shrink-0 fg-3"
            aria-label=${`Descartar: ${type.title}`} title="Descartar" disabled=${busy} onClick=${dismiss}>
            <${Icon} name="close"/>
        </button>`}
    </li>`;
}

function Notifications({ inbox, urls, history = false }) {
    const state = useInbox(inbox);
    const [open, setOpen] = useState(false);
    const [undo, setUndo] = useState(null);
    const bell = useRef();
    const container = useRef();
    const items = history ? state.notificaciones : state.notificaciones.filter(n => !n.descartada).slice(0, 3);
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

    const content = html`
        ${state.error && html`<div class="alert theme-warning m-4 fs-xs" role="alert">
            <span>${state.error}</span><button type="button" class="btn-text btn-sm" onClick=${inbox.refresh}>Reintentar</button>
        </div>`}
        ${!state.enVivo && html`<p class="fs-xs fg-3 px-6 pt-4 mb-0" role="status">Reconectando las notificaciones…</p>`}
        ${state.cargando ? html`<p class="p-6 mb-0 fg-3" role="status">Cargando notificaciones…</p>`
            : items.length ? html`<ul class="notification-list m-0 p-0">${items.map(item => html`
                <${Notification} key=${item.id} item=${item} inbox=${inbox} solicitudes=${urls.solicitudes}
                    dismissible=${!history} onDismiss=${setUndo}/>
            `)}</ul>` : html`<div class="text-center p-9">
                <span class="fg-primary"><${Icon} name="bell"/></span>
                <h3 class="fs-md mt-5 mb-3">${history ? 'Todavía no tienes notificaciones' : 'Estás al día'}</h3>
                <p class="fs-sm fg-3 mb-0">${history ? 'Aquí verás las novedades de tus solicitudes.' : 'Puedes consultar las anteriores en el historial.'}</p>
            </div>`}
        ${undo !== null && html`<div class="d-flex align-items-center justify-content-between gap-3 border-top px-6 py-3 fs-xs" role="status">
            <span>Notificación descartada</span>
            <button type="button" class="btn-text btn-sm fg-primary" onClick=${async () => {
                if (await inbox.restore(undo)) setUndo(null);
            }}>Deshacer</button>
        </div>`}
    `;
    if (history) return html`<section class="notification-history border rounded-7 overflow-hidden" aria-label="Notificaciones">${content}</section>`;
    return html`<div ref=${container}>
        <button ref=${bell} type="button" class="btn-text notification-bell position-relative p-0"
            aria-label=${`Notificaciones, ${state.sinLeer} sin leer`} aria-expanded=${open}
            aria-controls="notification-panel" onClick=${() => setOpen(value => !value)}>
            <${Icon} name="bell"/>
            ${state.sinLeer > 0 && html`<span class="notification-dot notification-bell-dot" aria-hidden="true"></span>`}
        </button>
        ${open && html`<section id="notification-panel" class="notification-panel position-absolute border rounded-7 shadow overflow-hidden" aria-label="Notificaciones">
            <div class="d-flex align-items-center justify-content-between gap-3 px-6 py-5 border-bottom">
                <h2 class="fs-md m-0">Notificaciones</h2>
                <span class="fs-xs fg-3">${state.sinLeer ? `${state.sinLeer} sin leer` : 'Todo al día'}</span>
            </div>
            ${content}
            <a class="d-flex align-items-center justify-content-between gap-4 px-6 py-5 border-top fs-sm fw-semibold text-decoration-none fg-primary" href=${urls.historial}>
                Ver todas las notificaciones<${Icon} name="arrow"/>
            </a>
        </section>`}
    </div>`;
}

const desktop = document.querySelector('#notificaciones-desktop');
if (desktop) {
    const history = document.querySelector('#notificaciones-historial');
    const initial = document.querySelector('#notificaciones-iniciales');
    const urls = desktop.dataset;
    const inbox = createInbox({
        ...urls, historial: Boolean(history), initial: initial ? JSON.parse(initial.textContent) : null,
        csrf: { header: document.querySelector('meta[name="_csrf_header"]').content,
            token: document.querySelector('meta[name="_csrf"]').content },
    });
    desktop.replaceChildren();
    history?.replaceChildren();
    render(html`<${Notifications} inbox=${inbox} urls=${urls}/>`, desktop);
    if (history) render(html`<${Notifications} inbox=${inbox} urls=${urls} history=${true}/>`, history);
    inbox.connect();
    addEventListener('pagehide', () => inbox.close());
    addEventListener('pageshow', event => { if (event.persisted) inbox.connect(); });
    document.addEventListener('visibilitychange', () => { if (!document.hidden) inbox.refresh(); });
}
