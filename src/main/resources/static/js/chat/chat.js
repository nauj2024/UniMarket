// Actualiza la conversación sin recargar: consulta cada pocos segundos si hay mensajes nuevos.
// Se pausa mientras la pestaña no está visible y se reanuda, con una consulta inmediata, al volver a ella.

const INTERVALO_MS = 5000;
const INTERVALO_MAXIMO_MS = 60000;

/** Id del último mensaje que la página ya muestra (0 si no hay ninguno). */
export function ultimoIdDe(lista) {
    const ids = [...lista.querySelectorAll('li[data-id]')].map(item => Number(item.dataset.id));
    return ids.length > 0 ? Math.max(...ids) : 0;
}

/** Crea el <li> de un mensaje. El texto entra con textContent, que nunca interpreta HTML. */
export function crearItem(documento, mensaje) {
    const item = documento.createElement('li');
    item.dataset.id = mensaje.id;

    const fila = documento.createElement('article');
    fila.className = 'service-row';

    const autor = documento.createElement('h3');
    autor.textContent = mensaje.propio ? 'Tú' : mensaje.autor;

    const contenido = documento.createElement('p');
    contenido.className = 'prose mb-0';
    contenido.textContent = mensaje.contenido;

    const meta = documento.createElement('p');
    meta.className = 'service-meta';
    const hora = documento.createElement('time');
    hora.dateTime = mensaje.fechaIso;
    hora.textContent = mensaje.fechaTexto;
    meta.append(hora);

    fila.append(autor, contenido, meta);
    item.append(fila);
    return item;
}

export function iniciarChat(documento = document, buscar = fetch) {
    const lista = documento.getElementById('lista-mensajes');
    const url = lista?.dataset.urlMensajes;
    if (!url) return; // chat nuevo: todavía no hay conversación que actualizar

    let ultimoId = ultimoIdDe(lista);
    let espera = INTERVALO_MS;
    let temporizador = null;
    let enCurso = false;

    function agregar(mensajes) {
        for (const mensaje of mensajes) {
            if (mensaje.id <= ultimoId) continue; // ya está en la página
            lista.append(crearItem(documento, mensaje));
            ultimoId = mensaje.id;
        }
        if (mensajes.length > 0) documento.getElementById('sin-mensajes')?.remove();
    }

    async function consultar() {
        if (enCurso) return;
        enCurso = true;
        try {
            const respuesta = await buscar(`${url}?despues=${ultimoId}`, {
                headers: { Accept: 'application/json' },
            });
            if (respuesta.redirected || !respuesta.ok ||
                !respuesta.headers.get('content-type')?.includes('application/json')) {
                throw new Error('consulta fallida');
            }
            agregar(await respuesta.json());
            espera = INTERVALO_MS;
        } catch {
            espera = Math.min(espera * 2, INTERVALO_MAXIMO_MS); // si falla, reintenta más despacio
        } finally {
            enCurso = false;
        }
        programar();
    }

    /** Agenda la próxima consulta, salvo que la pestaña esté oculta. */
    function programar() {
        clearTimeout(temporizador);
        temporizador = documento.hidden ? null : setTimeout(consultar, espera);
    }

    documento.addEventListener('visibilitychange', () => {
        clearTimeout(temporizador);
        temporizador = null;
        if (!documento.hidden) consultar();
    });

    programar();
}

if (typeof document !== 'undefined') iniciarChat();