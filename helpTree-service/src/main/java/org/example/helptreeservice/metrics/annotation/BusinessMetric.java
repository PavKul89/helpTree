package org.example.helptreeservice.metrics.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface BusinessMetric {
    String value();
    String[] tags() default {};
}
