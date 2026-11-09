import { h } from 'preact';
import htm from 'htm';

const html = htm.bind(h);

const paths = {
    bell: 'M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4',
    inbox: 'M4 4h16l2 12v4H2v-4L4 4M2 15h6l2 3h4l2-3h6',
    exchange: 'M4 7h16m-4-4 4 4-4 4M20 17H4m4-4-4 4 4 4',
    check: 'm5 12 4 4L19 6', close: 'm6 6 12 12M18 6 6 18',
    arrow: 'M5 12h14m-5-5 5 5-5 5',
};
export function Icon({ name }) {
    return html`<svg class="app-icon" viewBox="0 0 24 24" aria-hidden="true"><path d=${paths[name]}/></svg>`;
}
