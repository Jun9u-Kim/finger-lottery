package com.lgtm.finger_lottery;

import android.content.Context;
import android.content.res.Configuration;
import android.os.LocaleList;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Supported UI languages, ordered by the population of their primary target countries. */
final class AppLanguage {
    static final class Option {
        final String tag;
        final String code;
        final String label;

        Option(String tag, String code, String label) {
            this.tag = tag;
            this.code = code;
            this.label = label;
        }
    }

    static final List<Option> OPTIONS = Collections.unmodifiableList(Arrays.asList(
            new Option("hi", "HI", "हिन्दी · India"),
            new Option("zh-CN", "ZH", "中文（简体）· China"),
            new Option("en", "EN", "English · United States"),
            new Option("id", "ID", "Bahasa Indonesia · Indonesia"),
            new Option("ur", "UR", "اردو · Pakistan"),
            new Option("pt-BR", "PT", "Português · Brazil"),
            new Option("bn", "BN", "বাংলা · Bangladesh"),
            new Option("ru", "RU", "Русский · Russia"),
            new Option("am", "AM", "አማርኛ · Ethiopia"),
            new Option("es", "ES", "Español · Mexico"),
            new Option("ja", "JA", "日本語 · Japan"),
            new Option("ar", "AR", "العربية · Egypt"),
            new Option("fil", "FIL", "Filipino · Philippines"),
            new Option("fr", "FR", "Français · DR Congo"),
            new Option("vi", "VI", "Tiếng Việt · Vietnam"),
            new Option("fa", "FA", "فارسی · Iran"),
            new Option("tr", "TR", "Türkçe · Turkey"),
            new Option("de", "DE", "Deutsch · Germany"),
            new Option("th", "TH", "ไทย · Thailand"),
            new Option("sw", "SW", "Kiswahili · Tanzania"),
            new Option("ko", "KO", "한국어 · 한국")
    ));

    private AppLanguage() { }

    static Option find(String tag) {
        for (Option option : OPTIONS) {
            if (option.tag.equals(tag)) return option;
        }
        for (Option option : OPTIONS) {
            if (option.tag.equals("ko")) return option;
        }
        return OPTIONS.get(0);
    }

    static Context localizedContext(Context base, String languageTag) {
        Configuration configuration = new Configuration(base.getResources().getConfiguration());
        Locale locale = Locale.forLanguageTag(languageTag);
        configuration.setLocale(locale);
        configuration.setLocales(new LocaleList(locale));
        configuration.setLayoutDirection(locale);
        return base.createConfigurationContext(configuration);
    }
}
