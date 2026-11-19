package com.atenea.unimarket.negocio;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.atenea.unimarket.datos.modelo.FranjaHorario;
import com.atenea.unimarket.datos.modelo.Perfil;
import com.atenea.unimarket.datos.modelo.Usuario;
import com.atenea.unimarket.datos.repositorio.PerfilRepository;
import com.atenea.unimarket.datos.repositorio.UsuarioRepository;

@Service
public class PerfilService {

    /** Colombian mobile number: ten digits starting with 3, optionally with +57, spaces or dashes. */
    public static final String FORMATO_TELEFONO = "^\\s*(\\+?57[\\s-]*)?3([\\s-]*\\d){9}\\s*$";
    private static final Pattern TELEFONO = Pattern.compile(FORMATO_TELEFONO);

    private final PerfilRepository perfiles;
    private final UsuarioRepository usuarios;
    private final Clock reloj;
    private final TransactionTemplate transaccion;

    public PerfilService(PerfilRepository perfiles, UsuarioRepository usuarios, Clock reloj,
            PlatformTransactionManager transacciones) {
        this.perfiles = perfiles;
        this.usuarios = usuarios;
        this.reloj = reloj;
        this.transaccion = new TransactionTemplate(transacciones);
    }

    public Optional<Perfil> buscarPorUsuarioId(Integer usuarioId) {
        return perfiles.findByUsuarioId(usuarioId);
    }

    /**
     * Registers a Google identity by creating its user and profile together, so no user exists
     * without a profile. The email comes from the validated UNAL login, never from the form.
     * If the identity is already registered, including a concurrent duplicate submit, nothing is
     * saved and {@link PerfilYaExisteException} is thrown.
     * <p>
     * Registering requires accepting the data treatment policy, which the profile form enforces,
     * so the acceptance date is the registration date.
     * <p>
     * The transaction writes before it reads anything: on SQLite, a transaction that read first
     * and then tries to write can fail with SQLITE_BUSY instead of waiting for the lock. Writing
     * first makes a concurrent submit wait, then fail on the unique {@code google_sub}. The SQLite
     * dialect reports that as a generic {@link DataAccessException}, so after any failed insert
     * the identity is looked up again in a fresh read outside the rolled-back transaction.
     */
    public Perfil registrar(String googleSub, String correoInstitucional, String nombre,
            String telefonoContacto, List<FranjaHorario> horarios) {
        String nombreLimpio = normalizarNombre(nombre);
        String telefono = normalizarTelefono(telefonoContacto);
        String correo = correoInstitucional.toLowerCase(Locale.ROOT);
        Instant ahora = Instant.now(reloj);
        try {
            return transaccion.execute(estado -> {
                Usuario nuevo = new Usuario(googleSub, nombreLimpio, correo, ahora);
                nuevo.aceptarPoliticaDatos(ahora);
                Usuario usuario = usuarios.saveAndFlush(nuevo);
                return perfiles.saveAndFlush(new Perfil(usuario, telefono, horarios, ahora));
            });
        } catch (DataAccessException fallo) {
            if (usuarios.existsByGoogleSub(googleSub)) {
                throw new PerfilYaExisteException();
            }
            throw fallo;
        }
    }

    static String normalizarNombre(String nombre) {
        String limpio = nombre == null ? "" : nombre.strip().replaceAll("\\s+", " ");
        if (limpio.isEmpty() || limpio.length() > 100) {
            throw new IllegalArgumentException("Nombre inválido.");
        }
        return limpio;
    }

    static String normalizarTelefono(String telefono) {
        if (telefono == null || !TELEFONO.matcher(telefono).matches()) {
            throw new IllegalArgumentException("Teléfono con formato inválido.");
        }
        String digitos = telefono.replaceAll("\\D", "");
        return digitos.length() == 12 ? digitos.substring(2) : digitos;
    }
}
