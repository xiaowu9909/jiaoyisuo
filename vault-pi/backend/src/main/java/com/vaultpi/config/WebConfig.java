package com.vaultpi.config;

import com.vaultpi.user.repository.MemberRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final String SESSION_MEMBER_ID = "memberId";
    private static final String DEFAULT_ORIGINS = "http://localhost:5173,http://127.0.0.1:5173,http://localhost:5174,http://127.0.0.1:5174";

    private final MemberRepository memberRepository;

    @Value("${app.cors.allowed-origins:http://localhost:5173,http://127.0.0.1:5173,http://localhost:5174,http://127.0.0.1:5174}")
    private String allowedOriginsConfig;

    @Value("${app.upload.dir:./uploads}")
    private String uploadDir;

    @Value("${vaultpi.security.csrf-enabled:false}")
    private boolean csrfEnabled;

    @Value("${vaultpi.ratelimit.enabled:true}")
    private boolean rateLimitEnabled;
    @Value("${vaultpi.ratelimit.login-per-min:60}")
    private int rateLimitLoginPerMin;
    @Value("${vaultpi.ratelimit.register-per-min:5}")
    private int rateLimitRegisterPerMin;
    @Value("${vaultpi.ratelimit.order-per-min:30}")
    private int rateLimitOrderPerMin;

    public WebConfig(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Bean
    public FilterRegistrationBean<TraceIdFilter> traceIdFilterRegistration() {
        FilterRegistrationBean<TraceIdFilter> bean = new FilterRegistrationBean<>(new TraceIdFilter());
        bean.addUrlPatterns("/api/*", "/actuator/*");
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return bean;
    }

    @Bean
    public FilterRegistrationBean<com.vaultpi.config.CsrfFilter> csrfFilterRegistration() {
        FilterRegistrationBean<com.vaultpi.config.CsrfFilter> bean =
            new FilterRegistrationBean<>(new com.vaultpi.config.CsrfFilter(csrfEnabled));
        bean.addUrlPatterns("/api/*");
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 2);
        return bean;
    }

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(StringRedisTemplate redisTemplate) {
        FilterRegistrationBean<RateLimitFilter> bean = new FilterRegistrationBean<>(
            new RateLimitFilter(rateLimitEnabled, rateLimitLoginPerMin, rateLimitRegisterPerMin, rateLimitOrderPerMin, redisTemplate));
        bean.addUrlPatterns("/api/*");
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 3);
        return bean;
    }

    @Value("${vaultpi.security.csp:default-src 'self'; script-src 'none'; object-src 'none'; base-uri 'self'}")
    private String securityCsp;
    @Value("${vaultpi.security.hsts-max-age:31536000}")
    private long securityHstsMaxAge;
    @Value("${vaultpi.security.hsts-enabled:false}")
    private boolean securityHstsEnabled;

    @Bean
    public FilterRegistrationBean<SecurityHeadersFilter> securityHeadersFilterRegistration() {
        FilterRegistrationBean<SecurityHeadersFilter> bean = new FilterRegistrationBean<>(
            new SecurityHeadersFilter(securityCsp, securityHstsMaxAge, securityHstsEnabled));
        bean.addUrlPatterns("/*");
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 4);
        return bean;
    }

    @Bean
    public FilterRegistrationBean<XssFilter> xssFilterRegistration() {
        FilterRegistrationBean<XssFilter> bean = new FilterRegistrationBean<>(new XssFilter());
        bean.addUrlPatterns("/api/*");
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 5);
        return bean;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String path = Paths.get(uploadDir).toAbsolutePath().normalize().toString().replace("\\", "/");
        if (!path.endsWith("/")) path += "/";
        registry.addResourceHandler("/uploads/**").addResourceLocations("file:" + path);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        List<String> origins = Arrays.stream((allowedOriginsConfig != null ? allowedOriginsConfig : "").split(","))
            .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
        if (origins.isEmpty()) {
            origins = Arrays.asList(DEFAULT_ORIGINS.split(","));
        }
        registry.addMapping("/**")
            .allowedOrigins(origins.toArray(new String[0]))
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD")
            .allowedHeaders("Content-Type", "Authorization", "Accept", "Origin", "X-Requested-With", "X-CSRF-TOKEN")
            .allowCredentials(true)
            .exposedHeaders("x-auth-token")
            .maxAge(3600);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RequireLoginInterceptor()).addPathPatterns("/api/**");
        registry.addInterceptor(new AdminRoleInterceptor(memberRepository)).addPathPatterns("/api/admin/**");
    }

    /** 最先执行：显式为预检(OPTIONS)和跨域请求添加 CORS 头，避免被其它过滤器抢先响应 */
    @Bean
    public FilterRegistrationBean<SimpleCorsFilter> simpleCorsFilterRegistration() {
        FilterRegistrationBean<SimpleCorsFilter> bean = new FilterRegistrationBean<>(new SimpleCorsFilter());
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }

    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilterRegistration() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        List<String> origins = Arrays.stream((allowedOriginsConfig != null ? allowedOriginsConfig : "").split(","))
            .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
        if (origins.isEmpty()) {
            origins = Arrays.asList(DEFAULT_ORIGINS.split(","));
        }
        config.setAllowedOrigins(origins);
        config.setAllowedHeaders(Arrays.asList("Content-Type", "Authorization", "Accept", "Origin", "X-Requested-With", "X-CSRF-TOKEN"));
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD"));
        config.setExposedHeaders(Arrays.asList("x-auth-token"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        FilterRegistrationBean<CorsFilter> bean = new FilterRegistrationBean<>(new CorsFilter(source));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return bean;
    }
}
