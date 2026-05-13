package de.muenchen.mcmp.configuration;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * A custom {@link JwtAuthenticationConverter}, which obtains the authorities via
 * {@link UserInfoAuthoritiesService} from the /userinfo endpoint of the OIDC provider.
 */
public class JwtUserInfoAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserInfoAuthoritiesService userInfoService;

    /**
     * Creates a new instance of {@link JwtUserInfoAuthenticationConverter}.
     *
     * @param userInfoService a {@link UserInfoAuthoritiesService}
     */
    public JwtUserInfoAuthenticationConverter(final UserInfoAuthoritiesService userInfoService) {
        this.userInfoService = userInfoService;
    }

    @Override
    public AbstractAuthenticationToken convert(final Jwt source) {
        var authorities = this.userInfoService.loadAuthorities(source);
        var principalName = userInfoService.extractUsername(source);
        return new JwtAuthenticationToken(source, authorities, principalName);
    }

}
