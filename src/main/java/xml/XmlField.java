package xml;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface XmlField {
    String name() default "";          // имя тега/атрибута (по умолчанию имя поля)
    boolean attribute() default false; // писать как атрибут (только простые типы)
}