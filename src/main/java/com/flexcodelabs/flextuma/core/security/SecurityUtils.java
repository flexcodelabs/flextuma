package com.flexcodelabs.flextuma.core.security;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.GrantedAuthority;
import java.util.Set;
import java.util.stream.Collectors;

public class SecurityUtils {

	private SecurityUtils() {

	}

	public static Set<String> getCurrentUserAuthorities() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();

		if (auth == null ||
				!auth.isAuthenticated() ||
				auth instanceof AnonymousAuthenticationToken) {
			return Set.of();
		}

		return auth.getAuthorities().stream()
				.map(GrantedAuthority::getAuthority)
				.collect(Collectors.toSet());
	}
}
