package com.atenea.unimarket.presentacion;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.atenea.unimarket.negocio.UsuarioService;

@Configuration
class ConfiguracionWeb implements WebMvcConfigurer {

    private final UsuarioService usuarios;

    ConfiguracionWeb(UsuarioService usuarios) {
        this.usuarios = usuarios;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new PerfilObligatorioInterceptor(usuarios))
                .excludePathPatterns(PerfilObligatorioInterceptor.FORMULARIO, "/login", "/dev/login", "/error",
                        "/css/**", "/js/**", "/images/**", "/favicon.ico");
    }
}
