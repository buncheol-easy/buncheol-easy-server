package buncheoleasy.user.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class PhoneNumberConverter implements AttributeConverter<PhoneNumber, String> {

  @Override
  public String convertToDatabaseColumn(PhoneNumber attribute) {
    return attribute == null ? null : attribute.value();
  }

  @Override
  public PhoneNumber convertToEntityAttribute(String dbData) {
    return dbData == null ? null : PhoneNumber.of(dbData);
  }
}
