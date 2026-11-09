// Capture visual positions before Preact applies the next keyed list.
export function captureCards(list) {
    return new Map([...list?.children || []].map(card => [card.dataset.serviceId, {
        rect: card.getBoundingClientRect(),
        clone: card.cloneNode(true),
    }]));
}

export function reflowCards(previous, list, layer) {
    if (!previous || !layer || globalThis.matchMedia?.('(prefers-reduced-motion: reduce)').matches) return () => {};
    const cards = [...list?.children || []];
    if (typeof layer.animate !== 'function') return () => {};
    const animations = [];
    const origin = layer.getBoundingClientRect();
    const currentIds = new Set(cards.map(card => card.dataset.serviceId));
    const removed = [...previous].filter(([id]) => !currentIds.has(id));
    function play(element, frames, duration, remove = false) {
        const animation = element.animate(frames, {
            duration, easing: 'cubic-bezier(.2,.7,.2,1)', fill: 'both',
        });
        animations.push(animation);
        animation.finished.catch(() => {}).finally(() => {
            animation.cancel();
            if (remove) element.remove();
        });
    }
    for (const [, before] of removed) {
        const ghost = before.clone;
        Object.assign(ghost.style, {
            position: 'absolute', left: `${before.rect.left - origin.left}px`,
            top: `${before.rect.top - origin.top}px`, width: `${before.rect.width}px`,
            height: `${before.rect.height}px`,
        });
        layer.append(ghost);
        play(ghost, [{ opacity: 1 }, { opacity: 0 }], 120, true);
    }
    for (const card of cards) {
        const before = previous.get(card.dataset.serviceId);
        if (!before) {
            play(card, [{ opacity: 0 }, { opacity: 1 }], 250);
            continue;
        }
        const after = card.getBoundingClientRect();
        const dx = before.rect.left - after.left;
        const dy = before.rect.top - after.top;
        if (dx || dy) {
            play(card, [{ transform: `translate(${dx}px, ${dy}px)` }, { transform: 'translate(0, 0)' }], 390);
        }
    }
    return () => {
        animations.forEach(animation => animation.cancel());
        layer.replaceChildren();
    };
}
