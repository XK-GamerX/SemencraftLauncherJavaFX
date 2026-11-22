package com.semencraft.semencraftlauncherjavafx;

/**
 * Datos publicos de Supabase. El launcher solo lleva la clave publica: lo que necesita permisos
 * pasa por la funcion sc-api (SemencraftApi) o por las reglas RLS con la sesion del jugador.
 * Nunca hay que volver a meter aqui una clave secreta: el jar se puede descomprimir.
 */
final class SupabaseConfig {

    static final String URL_ENV = "SEMENCRAFT_SUPABASE_URL";
    static final String ANON_KEY_ENV = "SEMENCRAFT_SUPABASE_ANON_KEY";

    private static final String DEFAULT_URL = "https://zhvwcnawpcmkpgeqmnjb.supabase.co";
    private static final String DEFAULT_PUBLISHABLE_KEY = "sb_publishable_ToA4lpw4PSx2z27FD_cR4Q_nSN1D-Jl";

    private SupabaseConfig() {
    }

    static RuntimeConfig resolve() {
        return new RuntimeConfig(
                firstNonBlank(System.getenv(URL_ENV), DEFAULT_URL),
                firstNonBlank(System.getenv(ANON_KEY_ENV), DEFAULT_PUBLISHABLE_KEY)
        );
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    record RuntimeConfig(String url, String anonKey) {
        boolean hasUrl() {
            return url != null && !url.isBlank();
        }

        boolean hasAnonKey() {
            return anonKey != null && !anonKey.isBlank();
        }
    }
}
