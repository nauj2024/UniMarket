import { h, render } from 'preact';
import { useState } from 'preact/hooks';
import htm from 'htm';
import { createInbox } from './store.js';
import { useInbox } from './use-inbox.js';
import { NotificationContent } from './components/content.js';
import { NotificationDropdown } from './components/dropdown.js';

const html = htm.bind(h);
const groupingPreference = 'unimarket.notificationHistory.groupByCategory';

function preferredGrouping() {
    try {
        return localStorage.getItem(groupingPreference) !== 'false';
    } catch {
        return true;
    }
}

function Notifications({ inbox, urls, history = false }) {
    const { state, undo, dismissed, restore } = useInbox(inbox, history);
    const [grouped, setGrouped] = useState(() => history && preferredGrouping());
    const content = html`<${NotificationContent}
        state=${state} inbox=${inbox} solicitudes=${urls.solicitudes}
        history=${history} grouped=${grouped} undo=${undo} onDismiss=${dismissed} onRestore=${restore} />`;

    if (history) {
        function changeGrouping(event) {
            const checked = event.currentTarget.checked;
            setGrouped(checked);
            try {
                localStorage.setItem(groupingPreference, String(checked));
            } catch {
                // The current view still works when the browser blocks storage.
            }
        }
        return html`
            <label class="d-inline-flex align-items-center gap-2 mb-4 fs-sm fw-medium"
                for="agrupar-notificaciones">
                <span class="switch theme-primary">
                    <input id="agrupar-notificaciones" type="checkbox" role="switch"
                        checked=${grouped} onChange=${changeGrouping} />
                </span>
                Agrupar por categoría
            </label>
            <section class="notification-history border rounded-7 overflow-hidden"
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
