package com.demo.singpay.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final String trustedProxyIps;

    public WebMvcConfig(@Value("${app.trusted-proxy-ips:}") String trustedProxyIps) {
        this.trustedProxyIps = trustedProxyIps;
    }

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(new RateLimitInterceptor(trustedProxyIps))
            .addPathPatterns("/api/**")
            .excludePathPatterns("/api/webhooks/**");
    }
}
