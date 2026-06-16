package com.example.estimateserver.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

@Component
public class CustomDateSerializer extends JsonSerializer<Date> {

    private static final SimpleDateFormat FORMATTER = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @Override
    public void serialize(Date date, JsonGenerator gen, SerializerProvider provider) throws IOException {
        gen.writeString(FORMATTER.format(date));
    }
}