import { h, render } from 'preact';
import htm from 'htm';
import { createInbox } from './store.js';
import { useInbox } from './use-inbox.js';
import { NotificationContent } from './components/content.js';
import { NotificationDropdown } from './components/dropdown.js';

const html = htm.bind(h);

function Notifications({ inbox, urls, history = false }) {
    const { state, undo, dismissed, restore } = useInbox(inbox, history);
    const content = html`<${NotificationContent}
        state=${state} inbox=${inbox} solicitudes=${urls.solicitudes}
        history=${history} undo=${undo} onDismiss=${dismissed} onRestore=${restore} />`;

    if (history) {
        return html`<section class="notification-history border rounded-7 overflow-hidden"
            aria-label="Notificaciones">${content}</section>`;
    }
    return html`<${NotificationDropdown} unread=${state.sinLeer} href=${urls.historial}>
        ${content}
    </${NotificationDropdown}>`;
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
    render(html`<${Notifications} inbox=${inbox} urls=${urls}/>`, desktop);
    if (history) render(html`<${Notifications} inbox=${inbox} urls=${urls} history=${true}/>`, history);
    inbox.connect();
    addEventListener('pagehide', () => inbox.close());
    addEventListener('pageshow', event => { if (event.persisted) inbox.connect(); });
    document.addEventListener('visibilitychange', () => { if (!document.hidden) inbox.refresh(); });
}
