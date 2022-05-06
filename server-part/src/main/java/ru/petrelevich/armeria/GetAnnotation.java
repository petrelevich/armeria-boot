package ru.petrelevich.armeria;

import com.linecorp.armeria.server.annotation.Get;

import java.lang.annotation.Annotation;

public class GetAnnotation implements Get {
    private final String path;

    public GetAnnotation(String path) {
        this.path = path;
    }

    @Override
    public String value() {
        return path;
    }

    @Override
    public Class<? extends Annotation> annotationType() {
        return Get.class;
    }
}
