import { useEffect, useLayoutEffect, useRef } from 'preact/hooks';
import { captureCards, reflowCards } from './reflow.js';

export function useCardReflow(cards) {
    const listRef = useRef(null);
    const ghostRef = useRef(null);
    const before = useRef(null);
    const stop = useRef(() => {});

    function capture() {
        // Measure the current visual positions before cancelling an interrupted move.
        before.current = captureCards(listRef.current);
        stop.current();
    }

    useLayoutEffect(() => {
        stop.current = reflowCards(before.current, listRef.current, ghostRef.current);
        before.current = null;
    }, [cards]);

    useEffect(() => () => stop.current(), []);

    return { listRef, ghostRef, capture };
}
