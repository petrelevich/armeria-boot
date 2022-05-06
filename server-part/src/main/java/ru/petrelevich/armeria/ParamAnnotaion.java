package ru.petrelevich.armeria;

import com.linecorp.armeria.server.annotation.Param;

import java.lang.annotation.Annotation;

public class ParamAnnotaion implements Param {
    private final String name;

    public ParamAnnotaion(String name) {
        this.name = name;
    }

    @Override
    public String value() {
        return name;
    }

    @Override
    public Class<? extends Annotation> annotationType() {
        return Param.class;
    }
}
