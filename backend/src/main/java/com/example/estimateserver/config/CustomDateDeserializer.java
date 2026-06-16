package com.example.estimateserver.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

@Component
public class CustomDateDeserializer extends JsonDeserializer<Date> {

    private static final SimpleDateFormat FORMATTER = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @Override
    public Date deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        String dateStr = parser.getText();
        try {
            return FORMATTER.parse(dateStr);
        } catch (ParseException e) {
            throw new IOException("Cannot parse date: " + dateStr, e);
        }
    }
}