package com.vaultpi.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 多语言（i18n）：支持英语、法语、德语等，便于欧洲市场与 GDPR 体验。
 * 通过 Accept-Language 请求头解析语言，错误提示返回对应语言。
 */
@Configuration
public class LocaleConfig implements WebMvcConfigurer {

    private static final List<Locale> SUPPORTED = Arrays.asList(
        Locale.ENGLISH,
        Locale.FRENCH,
        Locale.GERMAN
    );

    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver r = new AcceptHeaderLocaleResolver();
        r.setDefaultLocale(Locale.ENGLISH);
        r.setSupportedLocales(SUPPORTED);
        return r;
    }

    @Bean
    public MessageSource messageSource() {
        ReloadableResourceBundleMessageSource ms = new ReloadableResourceBundleMessageSource();
        ms.setBasename("classpath:messages");
        ms.setDefaultEncoding("UTF-8");
        ms.setDefaultLocale(Locale.ENGLISH);
        ms.setCacheSeconds(300);
        return ms;
    }
}
