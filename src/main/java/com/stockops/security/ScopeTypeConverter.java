package com.stockops.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class ScopeTypeConverter implements AttributeConverter<ScopeType, String> {

    @Override
    public String convertToDatabaseColumn(final ScopeType attribute) {
        return attribute == null ? null : attribute.toPersistedValue();
    }

    @Override
    public ScopeType convertToEntityAttribute(final String dbData) {
        return ScopeType.fromPersistedValue(dbData);
    }
}
