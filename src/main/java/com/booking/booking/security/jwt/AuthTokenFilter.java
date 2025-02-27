package com.booking.booking.security.jwt;

import com.booking.booking.security.user.HotelUserDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// 모든 요청마다 JWT 토큰 검증하는 필터
@Component
public class AuthTokenFilter extends OncePerRequestFilter {

    // 토큰 검증 처리 도구
    @Autowired
    private JwtUtils jwtUtils;

    // 사용자 정보를 DB에서 찾을 도구
    @Autowired
    private HotelUserDetailsService userDetailsService;

    // 에러 로그를 남길 도구
    private static final Logger logger = LoggerFactory.getLogger(AuthTokenFilter.class);

    // 모든 HTTP 요청에 대해 JWT 토큰을 검증하는 필터 메소드
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        try {
            // 1. HTTP 요청 헤더에서 JWT 토큰 추출 ('Bearer ' 제거)
            String jwt = parseJwt(request);

            // 2. 토큰이 존재하고 유효한 경우
            if (jwt != null && jwtUtils.validateToken(jwt)) {
                // 3. JWT 토큰에서 사용자 이메일(subject)을 꺼내고
                String email = jwtUtils.getUserNameFromToken(jwt);

                // 4. 이메일로 DB에서 사용자 정보 조회
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);

                // 5. 인증 객체 생성 및 SecurityContext에 저장
                var authentication = new UsernamePasswordAuthenticationToken(
                        userDetails, // principal: 인증된 사용자의 정보
                        null, // credentials: 비밀번호 (보안을 위해 null로 설정)
                        userDetails.getAuthorities() // authorities: 사용자의 권한 목록
                );

                // 이 인증 요청이 어디서 왔는지(IP주소, 세션ID, 시간 등) 기록해둬
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                // 이 사용자는 인증됐다고 저장해둬 (나중에 재로그인 없이 서비스 이용)
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        catch (Exception e){
            logger.error("Cannot set user authentication: {} ", e.getMessage()); // 문제가 생기면 로그 남겨
            SecurityContextHolder.getContext().setAuthentication(null); // 인증 정보 비움
        }

        // 검증이 끝났으면 다음 단계로 넘어가
        filterChain.doFilter(request, response);
    }

    // 요청 헤더에서 토큰을 이렇게 꺼내
    private String parseJwt(HttpServletRequest request) {
        // 1. Authorization 헤더를 찾아서
        String headerAuth = request.getHeader("Authorization");

        // 2. Bearer 토큰이 맞다면 앞부분 자르고 토큰만 반환 --> Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
        if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer ")) {
            return headerAuth.substring(7);
        }

        // 3. 헤더가 없거나, Bearer 토큰이 아닌 경우 null 반환
        return null;
    }
}
