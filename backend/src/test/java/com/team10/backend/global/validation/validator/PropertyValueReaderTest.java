package com.team10.backend.global.validation.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import lombok.Getter;
import org.junit.jupiter.api.Test;

class PropertyValueReaderTest {

    @Test
    void readsRecordAccessor() {
        RecordFixture fixture = new RecordFixture("record-value");

        Object value = PropertyValueReader.read(fixture, "name");

        assertThat(value).isEqualTo("record-value");
    }

    @Test
    void readsJavaBeanGetter() {
        GetterFixture fixture = new GetterFixture("getter-value");

        Object value = PropertyValueReader.read(fixture, "name");

        assertThat(value).isEqualTo("getter-value");
    }

    @Test
    void readsBooleanGetter() {
        BooleanGetterFixture fixture = new BooleanGetterFixture(true);

        Object value = PropertyValueReader.read(fixture, "active");

        assertThat(value).isEqualTo(true);
    }

    @Test
    void readsPrivateFieldWhenAccessorDoesNotExist() {
        FieldFixture fixture = new FieldFixture("field-value");

        Object value = PropertyValueReader.read(fixture, "name");

        assertThat(value).isEqualTo("field-value");
    }

    @Test
    void readsInheritedPrivateField() {
        ChildFieldFixture fixture = new ChildFieldFixture("parent-value");

        Object value = PropertyValueReader.read(fixture, "name");

        assertThat(value).isEqualTo("parent-value");
    }

    @Test
    void throwsExceptionWhenPropertyDoesNotExist() {
        RecordFixture fixture = new RecordFixture("record-value");

        assertThatThrownBy(() -> PropertyValueReader.read(fixture, "missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("검증 대상 필드를 찾을 수 없습니다: missing");
    }

    private record RecordFixture(String name) {
    }

    @Getter
    private static class GetterFixture {

        private final String name;

        private GetterFixture(String name) {
            this.name = name;
        }

    }

    @Getter
    private static class BooleanGetterFixture {

        private final boolean active;

        private BooleanGetterFixture(boolean active) {
            this.active = active;
        }

    }

    private static class FieldFixture {

        private final String name;

        private FieldFixture(String name) {
            this.name = name;
        }
    }

    private static class ParentFieldFixture {

        private final String name;

        private ParentFieldFixture(String name) {
            this.name = name;
        }
    }

    private static class ChildFieldFixture extends ParentFieldFixture {

        private ChildFieldFixture(String name) {
            super(name);
        }
    }
}
