package com.stemotion.core.enums;
import com.stemotion.core.enums.StemEnums.StemSubject;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class StemSubjectConverterTest {
    @Test void readsBothSchemasAndWritesSupabaseLabel() {
        var converter = new StemSubjectConverter();
        assertThat(converter.convertToEntityAttribute("CS")).isEqualTo(StemSubject.ComputerScience);
        assertThat(converter.convertToEntityAttribute("ComputerScience")).isEqualTo(StemSubject.ComputerScience);
        for (var subject : StemSubject.values()) {
            assertThat(converter.convertToEntityAttribute(converter.convertToDatabaseColumn(subject))).isEqualTo(subject);
        }
    }
}
