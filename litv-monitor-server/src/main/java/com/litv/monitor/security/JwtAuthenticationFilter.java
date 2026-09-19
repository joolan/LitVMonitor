package com.litv.monitor.security;

import com.litv.monitor.mapper.SecuritySettingMapper;
import com.litv.monitor.service.SecuritySettingsService;
import com.litv.monitor.service.UserSessionService;
import com.litv.monitor.util.IpUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;
    private final SecuritySettingsService securitySettingsService;
    private final UserSessionService userSessionService;
    private final SecuritySettingMapper securitySettingMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String clientIp = getClientIp(request);

        // Check IP whitelist/blacklist
        if (!securitySettingsService.isIpAllowed(clientIp)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":403,\"message\":\"IP地址被拒绝访问\",\"data\":null}");
            return;
        }

        String token = getTokenFromRequest(request);

        if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {
            String username = jwtTokenProvider.getUsernameFromToken(token);
            String jti = jwtTokenProvider.getJtiFromToken(token);

            // Check if session is still active (auth endpoints are exempt so that
            // logout always succeeds even with an expired/invalidated session)
            if (jti != null && !isAuthEndpoint(request.getServletPath())) {
                if (!userSessionService.isSessionActive(jti)) {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":401,\"message\":\"会话已失效，请重新登录\",\"data\":null}");
                    return;
                }
            }

            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            // Authorities always come from the database, never from the token claim,
            // so role changes (e.g. demotion) take effect immediately.
            java.util.List<SimpleGrantedAuthority> authorities = new java.util.ArrayList<>();
            userDetails.getAuthorities().forEach(auth ->
                authorities.add(new SimpleGrantedAuthority(auth.getAuthority()))
            );

            // Force password change for flagged accounts (e.g. bootstrap admin)
            if (jwtTokenProvider.isMustChangePassword(token)
                    && !isPasswordChangeAllowed(request.getServletPath())) {
                response.setStatus(428);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":428,\"message\":\"首次登录需先修改密码\",\"data\":null}");
                return;
            }

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userDetails, null, authorities);
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }

    private boolean isPasswordChangeAllowed(String servletPath) {
        if (servletPath == null) return false;
        return servletPath.startsWith("/auth/")
                || servletPath.startsWith("/status/")
                || servletPath.equals("/version")
                || servletPath.equals("/user/profile");
    }

    private boolean isAuthEndpoint(String servletPath) {
        return servletPath != null && servletPath.startsWith("/auth/");
    }

    private String getTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    private String getClientIp(HttpServletRequest request) {
        return IpUtils.getClientIp(request, securitySettingMapper);
    }
}
