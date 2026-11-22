import { h } from 'preact';
import { Icon } from './icon.js';
import { Notification } from './item.js';
import htm from 'htm';

const html = htm.bind(h);

const categories = [
    { label: 'Negociación', types: ['SOLICITUD_RECIBIDA', 'CONTRAPROPUESTA_RECIBIDA'] },
    { label: 'Acuerdos', types: ['ACUERDO_CONFIRMADO'] },
    { label: 'Cancelaciones', types: ['SOLICITUD_RECHAZADA'] },
];

export function NotificationContent({ state, inbox, solicitudes, history, grouped, undo, onDismiss, onRestore }) {
    const items = history ? state.notificaciones : state.notificaciones.filter(n => !n.descartada).slice(0, 3);
    const list = notifications => html`<ul class="notification-list m-0 p-0">${notifications.map(item => html`
        <${Notification} key=${item.id} item=${item} inbox=${inbox} solicitudes=${solicitudes}
            dismissible=${!history} onDismiss=${onDismiss}/>
    `)}</ul>`;
    const historyGroups = () => categories.map(({ label, types }) => {
        const group = items.filter(item => types.includes(item.tipo));
        if (group.length === 0) return null;
        return html`<section class="notification-group" key=${label}>
            <h2 class="notification-category fs-sm fw-semibold m-0">${label}</h2>
            ${list(group)}
        </section>`;
    });

    return html`
        ${state.error && html`<div class="alert theme-warning m-4 fs-xs" role="alert">
            <span>${state.error}</span><button type="button" class="btn-text btn-sm" onClick=${inbox.refresh}>Reintentar</button>
        </div>`}
        ${!state.enVivo && html`<p class="fs-xs fg-3 px-6 pt-4 mb-0" role="status">Reconectando las notificaciones…</p>`}
        ${state.cargando ? html`<p class="p-6 mb-0 fg-3" role="status">Cargando notificaciones…</p>`
            : items.length ? (grouped ? historyGroups() : list(items)) : html`<div class="text-center p-9">
                <span class="fg-primary"><${Icon} name="bell"/></span>
                <h3 class="fs-md mt-5 mb-3">${history ? 'Todavía no tienes notificaciones' : 'Estás al día'}</h3>
                <p class="fs-sm fg-3 mb-0">${history ? 'Aquí verás las novedades de tus solicitudes.' : 'Puedes consultar las anteriores en el historial.'}</p>
            </div>`}
        ${undo !== null && html`<div class="d-flex align-items-center justify-content-between gap-3 border-top px-6 py-3 fs-xs" role="status">
            <span>Notificación descartada</span>
            <button type="button" class="btn-text btn-sm fg-primary" onClick=${onRestore}>Deshacer</button>
        </div>`}
    `;
}
