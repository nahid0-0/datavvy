package com.starkgroup.vms.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer;
import com.fasterxml.jackson.databind.deser.std.StringDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class JacksonConfig {

    /**
     * Trims every incoming JSON string and turns blank strings into {@code null},
     * so "   " is treated as missing by {@code @NotBlank}/{@code @NotNull} and
     * optional fields never get stored as whitespace.
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer trimmingStringsCustomizer() {
        SimpleModule module = new SimpleModule("TrimmingStrings");
        module.addDeserializer(String.class, new TrimmingStringDeserializer());
        return builder -> builder.modulesToInstall(module);
    }

    static final class TrimmingStringDeserializer extends StdScalarDeserializer<String> {

        TrimmingStringDeserializer() {
            super(String.class);
        }

        @Override
        public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            String value = StringDeserializer.instance.deserialize(parser, context);
            if (value == null) {
                return null;
            }
            String trimmed = value.strip();
            return trimmed.isEmpty() ? null : trimmed;
        }
    }
}
