import { test } from 'node:test';
import assert from 'node:assert/strict';
import { captureCards, reflowCards } from '../../main/resources/static/js/reflow.js';

function fixture() {
    const animations = [];
    const layer = {
        children: [], animate() {},
        getBoundingClientRect: () => ({ left: 10, top: 20 }),
        append(card) { this.children.push(card); },
        replaceChildren() { this.children = []; },
    };
    function card(id, left, content = id) {
        return {
            dataset: { serviceId: id }, textContent: content, style: {}, left,
            getBoundingClientRect() { return { left: this.left, top: 20, width: 180, height: 200 }; },
            cloneNode() { return card(id, this.left, this.textContent); },
            remove() { layer.children = layer.children.filter(item => item !== this); },
            animate(frames, options) {
                const animation = { element: this, frames, options, finished: new Promise(() => {}), cancelled: false,
                    cancel() { this.cancelled = true; } };
                animations.push(animation);
                return animation;
            },
        };
    }
    return { layer, card, animations };
}

test('reflow moves retained cards, fades removals and additions, and cleans up interrupted animations', () => {
    const { layer, card, animations } = fixture();
    const a = card('a', 10), b = card('b', 210), c = card('c', 410);
    const previous = captureCards({ children: [a, b, c] });
    c.left = 10; a.left = 210;
    const d = card('d', 410);
    const stop = reflowCards(previous, { children: [c, a, d] }, layer);
    assert.equal(animations.length, 4);
    assert.ok(animations.every(animation => (animation.options.delay ?? 0) === 0));
    assert.deepEqual(animations.find(animation => animation.element === c).frames,
        [{ transform: 'translate(400px, 0px)' }, { transform: 'translate(0, 0)' }]);
    assert.equal(animations.find(animation => animation.element === a).frames[0].transform, 'translate(-200px, 0px)');
    assert.deepEqual(animations.find(animation => animation.element === d).frames, [{ opacity: 0 }, { opacity: 1 }]);
    assert.equal(layer.children[0].dataset.serviceId, 'b');
    assert.equal(layer.children[0].style.left, '200px');
    stop();
    assert.ok(animations.every(animation => animation.cancelled));
    assert.equal(layer.children.length, 0);
});

test('cards that stay in place do not animate, even when their text changes', () => {
    const { layer, card, animations } = fixture();
    const a = card('a', 10);
    const before = captureCards({ children: [a] });
    reflowCards(before, { children: [a] }, layer);
    assert.equal(animations.length, 0);
    a.textContent = 'New price';
    reflowCards(before, { children: [a] }, layer);
    assert.equal(animations.length, 0);
});

test('empty results fade removed cards, and reduced motion or missing browser support skip animation', () => {
    const { layer, card, animations } = fixture();
    const previous = captureCards({ children: [card('a', 10)] });
    const stop = reflowCards(previous, null, layer);
    assert.equal(animations.length, 1);
    stop();
    const original = globalThis.matchMedia;
    try {
        globalThis.matchMedia = () => ({ matches: true });
        reflowCards(previous, null, layer);
        assert.equal(animations.length, 1);
    } finally {
        if (original) globalThis.matchMedia = original;
        else delete globalThis.matchMedia;
    }
    delete layer.animate;
    reflowCards(previous, null, layer);
    assert.equal(animations.length, 1);
});
