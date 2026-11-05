package xml;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface XmlField {
    boolean label() default false; // значение поля выводится рядом с idref
    String name() default "";          // имя тега/атрибута (по умолчанию имя поля)
    boolean attribute() default false; // писать как атрибут (только простые типы)
}