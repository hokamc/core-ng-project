package core.framework.internal.web.bean;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author neo
 */
class BeanClassValidatorTest {
    @Test
    void validate() {
        new BeanClassValidator(TestBean.class, new BeanClassNameValidator()).validate();
    }

    @Test
    void validateWithList() {
        assertThatThrownBy(() -> new BeanClassValidator(List.class, new BeanClassNameValidator()).validate())
            .isInstanceOf(Error.class)
            .hasMessageContaining("class must be bean class");
    }
}