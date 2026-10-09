package com.stemotion.core.enums;

import com.stemotion.core.enums.StemEnums.StemSubject;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class StemSubjectConverter implements AttributeConverter<StemSubject, String> {
    @Override public String convertToDatabaseColumn(StemSubject value) {
        return value == null ? null : value == StemSubject.ComputerScience ? "CS" : value.name();
    }
    @Override public StemSubject convertToEntityAttribute(String value) {
        return value == null ? null : "CS".equals(value) ? StemSubject.ComputerScience : StemSubject.valueOf(value);
    }
}
