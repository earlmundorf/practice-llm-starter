package com.coremcp.oauth;

import com.sap.cx.commerce.platform.oauth2.authorizationserver.custom.AbstractCustomAuthenticationToken;
import com.sap.cx.commerce.platform.oauth2.authorizationserver.custom.CustomAuthenticationConverter;
import com.sap.cx.commerce.platform.oauth2.authorizationserver.custom.CustomUserAuthenticationToken;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.util.Assert;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Re-introduces the OAuth2 Resource Owner Password Credentials grant on top of
 * SAP Commerce's Spring Authorization Server. Spring 6 / SAP 22.11 dropped the
 * grant from the default token endpoint converters, but exposes a
 * {@link CustomAuthenticationConverter} extension point that
 * {@code AuthorizationServerConfiguration} autowires into the chain.
 * <p>
 * The converter validates the user credentials against the existing
 * {@code wsAuthenticationProvider} (Hybris {@code CoreAuthenticationProvider})
 * and wraps the resulting authenticated principal in a
 * {@link CustomUserAuthenticationToken}. SAP's built-in
 * {@code CustomUserAuthenticationProvider} then mints the access + refresh tokens.
 */
public class PasswordGrantAuthenticationConverter implements CustomAuthenticationConverter {

    private static final String GRANT_TYPE_PARAM = "grant_type";
    private static final String USERNAME_PARAM = "username";
    private static final String PASSWORD_PARAM = "password";
    private static final String SCOPE_PARAM = "scope";

    private final AuthenticationProvider userAuthenticationProvider;

    public PasswordGrantAuthenticationConverter(final AuthenticationProvider userAuthenticationProvider) {
        Assert.notNull(userAuthenticationProvider, "userAuthenticationProvider must not be null");
        this.userAuthenticationProvider = userAuthenticationProvider;
    }

    @Override
    public AbstractCustomAuthenticationToken convert(final HttpServletRequest request) {
        if (!AuthorizationGrantType.PASSWORD.getValue().equals(request.getParameter(GRANT_TYPE_PARAM))) {
            return null;
        }

        final String username = request.getParameter(USERNAME_PARAM);
        final String password = request.getParameter(PASSWORD_PARAM);
        if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error(OAuth2ErrorCodes.INVALID_REQUEST, "username and password are required", null));
        }

        final OAuth2ClientAuthenticationToken clientAuth = resolveClientAuthentication();
        if (!clientAuth.getRegisteredClient().getAuthorizationGrantTypes().contains(AuthorizationGrantType.PASSWORD)) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT,
                            "Client is not authorized for the password grant", null));
        }

        final Authentication userAuth;
        try {
            userAuth = this.userAuthenticationProvider.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password));
        } catch (final org.springframework.security.core.AuthenticationException ex) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error(OAuth2ErrorCodes.INVALID_GRANT, ex.getMessage(), null), ex);
        }

        final Set<String> requestedScopes = parseScopes(request.getParameter(SCOPE_PARAM));
        final Set<String> grantedScopes = filterScopes(requestedScopes, clientAuth);
        final Map<String, Object> additionalParams = new HashMap<>();

        return new CustomUserAuthenticationToken(
                userAuth,
                clientAuth,
                AuthorizationGrantType.PASSWORD,
                additionalParams,
                grantedScopes,
                /* generateRefreshToken */ true);
    }

    private OAuth2ClientAuthenticationToken resolveClientAuthentication() {
        final Authentication current = SecurityContextHolder.getContext().getAuthentication();
        if (!(current instanceof OAuth2ClientAuthenticationToken)) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error(OAuth2ErrorCodes.INVALID_CLIENT,
                            "Client must authenticate before requesting a password grant token", null));
        }
        return (OAuth2ClientAuthenticationToken) current;
    }

    private static Set<String> parseScopes(final String scope) {
        if (scope == null || scope.isBlank()) {
            return Collections.emptySet();
        }
        return new HashSet<>(Arrays.asList(scope.trim().split("\\s+")));
    }

    private static Set<String> filterScopes(final Set<String> requested,
                                            final OAuth2ClientAuthenticationToken clientAuth) {
        final Set<String> allowed = clientAuth.getRegisteredClient().getScopes();
        if (requested.isEmpty()) {
            return new HashSet<>(allowed);
        }
        for (final String s : requested) {
            if (!allowed.contains(s)) {
                throw new OAuth2AuthenticationException(
                        new OAuth2Error(OAuth2ErrorCodes.INVALID_SCOPE,
                                "Requested scope not allowed for client: " + s, null));
            }
        }
        return new HashSet<>(requested);
    }
}
