package buncheoleasy.user.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class NicknameConverter implements AttributeConverter<Nickname, String> {

  @Override
  public String convertToDatabaseColumn(Nickname attribute) {
    return attribute == null ? null : attribute.value();
  }

  @Override
  public Nickname convertToEntityAttribute(String dbData) {
    return dbData == null ? null : Nickname.of(dbData);
  }
}
