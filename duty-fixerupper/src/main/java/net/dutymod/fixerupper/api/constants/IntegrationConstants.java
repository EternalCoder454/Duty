package net.dutymod.fixerupper.api.constants;

public class IntegrationConstants {
    /**
     * Deliberately still the ModernFix key.
     *
     * <p>Third-party mods declare their integration under this name in their own metadata.
     * Duty replaces ModernFix rather than sitting alongside it, so honouring the original key
     * means those integrations keep working; renaming it would silently break every one of
     * them for no benefit.
     */
    public static final String INTEGRATIONS_KEY = "modernfix:integration";

    public static final String CLIENT_INTEGRATION_CLASS = "client_entrypoint";
    public static final String INTEGRATION_CLASS = "entrypoint";
}
