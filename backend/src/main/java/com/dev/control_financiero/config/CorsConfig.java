package com.dev.control_financiero.config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.web.cors.*;
import org.springframework.web.filter.CorsFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import java.util.*;
@Configuration
public class CorsConfig {
 @Bean @Primary public CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.origins:http://localhost:*,http://127.0.0.1:*,https://*.use2.devtunnels.ms}") String origins) {
  var c=new CorsConfiguration(); c.setAllowedOriginPatterns(Arrays.stream(origins.split(",")).map(String::trim).filter(s->!s.isEmpty()).toList());
  c.setAllowedMethods(List.of("GET","POST","PUT","DELETE","OPTIONS")); c.setAllowedHeaders(List.of("*")); c.setAllowCredentials(true);
  var source=new UrlBasedCorsConfigurationSource(); source.registerCorsConfiguration("/**",c); return source;
 }
 @Bean public FilterRegistrationBean<CorsFilter> corsFilterRegistration(CorsConfigurationSource source) {
  var registration=new FilterRegistrationBean<>(new CorsFilter(source)); registration.setOrder(0); return registration;
 }
}
