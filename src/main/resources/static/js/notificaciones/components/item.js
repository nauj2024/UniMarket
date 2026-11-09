import { h } from 'preact';
import { useEffect, useRef, useState } from 'preact/hooks';
import { Icon } from './icon.js';
import htm from 'htm';

const html = htm.bind(h);

const types = {
    SOLICITUD_RECIBIDA: { title: 'Recibiste una solicitud', theme: 'info', icon: 'inbox' },
    CONTRAPROPUESTA_RECIBIDA: { title: 'Tienes una nueva contrapropuesta', theme: 'warning', icon: 'exchange' },
    ACUERDO_CONFIRMADO: { title: 'Acuerdo confirmado', theme: 'primary', icon: 'check' },
    SOLICITUD_RECHAZADA: { title: 'Tu solicitud fue rechazada', theme: 'secondary', icon: 'close' },
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

export function Notification({ item, inbox, solicitudes, dismissible, onDismiss }) {
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
