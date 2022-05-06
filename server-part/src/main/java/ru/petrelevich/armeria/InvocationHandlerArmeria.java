package ru.petrelevich.armeria;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

public class InvocationHandlerArmeria implements InvocationHandler {
    private final Object object;

    public InvocationHandlerArmeria(Object object) {
        this.object = object;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        return method.invoke(object, args);
    }
}
