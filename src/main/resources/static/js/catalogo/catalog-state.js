// Compare only what the cards display, in display order.
export function sameCards(previous, next) {
    const fields = ['id', 'titulo', 'categoria', 'ofertador', 'tarifaPorHora', 'intensidadHoraria'];
    return previous.length === next.length && previous.every((card, index) =>
        fields.every(field => card[field] === next[index][field]));
}

export function catalogReducer(state, action) {
    switch (action.type) {
        case 'search':
            return {
                ...state,
                filters: action.filters,
                request: action,
                busy: true,
                error: '',
            };
        case 'success':
            return {
                ...state,
                results: sameCards(state.results.servicios, action.results.servicios)
                    ? { ...action.results, servicios: state.results.servicios }
                    : action.results,
                busy: false,
            };
        case 'failure':
            return {
                ...state,
                busy: false,
                error: 'No pudimos actualizar los resultados. Vuelve a buscar o recarga la página.',
            };
        default:
            return state;
    }
}
