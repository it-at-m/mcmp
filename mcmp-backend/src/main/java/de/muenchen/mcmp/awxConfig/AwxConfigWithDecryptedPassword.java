package de.muenchen.mcmp.awxConfig;

public interface AwxConfigWithDecryptedPassword {
    Long getId();

    String getApiDescription();

    String getApiUsername();

    String getApiPassword();

    String getApiEndpoint();

    boolean getEnabled();
}
