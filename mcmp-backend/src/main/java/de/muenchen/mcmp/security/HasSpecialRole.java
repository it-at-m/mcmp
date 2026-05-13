package de.muenchen.mcmp.security;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasAnyRole('" +
        AuthUtils.ROLE_ADMIN + "', '" +
        AuthUtils.ROLE_WINDOWS + "', '" +
        AuthUtils.ROLE_LINUX + "', '" +
        AuthUtils.ROLE_ORACLE + "', '" +
        AuthUtils.ROLE_NON_ORACLE + "', '" +
        AuthUtils.ROLE_NETWORK + "', '" +
        AuthUtils.ROLE_OPERATOR + "', '" +
        AuthUtils.ROLE_SECURITY + "')")
public @interface HasSpecialRole {
}