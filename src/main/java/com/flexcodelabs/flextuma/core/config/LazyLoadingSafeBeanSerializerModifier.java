package com.flexcodelabs.flextuma.core.config;

import java.util.List;

import org.hibernate.LazyInitializationException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;

public class LazyLoadingSafeBeanSerializerModifier extends BeanSerializerModifier {

    @Override
    public List<BeanPropertyWriter> changeProperties(SerializationConfig config, BeanDescription beanDesc,
            List<BeanPropertyWriter> beanProperties) {
        return beanProperties.stream()
                .map(LazyLoadingSafePropertyWriter::new)
                .map(BeanPropertyWriter.class::cast)
                .toList();
    }

    private static final class LazyLoadingSafePropertyWriter extends BeanPropertyWriter {

        private LazyLoadingSafePropertyWriter(BeanPropertyWriter base) {
            super(base);
        }

        @Override
        public void serializeAsField(Object bean, JsonGenerator gen, SerializerProvider prov) throws Exception {
            try {
                super.serializeAsField(bean, gen, prov);
            } catch (Exception ex) {
                if (!isLazyLoadingFailure(ex)) {
                    throw ex;
                }

                if (!gen.canOmitFields()) {
                    super.serializeAsOmittedField(bean, gen, prov);
                }
            }
        }

        private boolean isLazyLoadingFailure(Throwable throwable) {
            Throwable current = throwable;
            while (current != null) {
                if (current instanceof LazyInitializationException) {
                    return true;
                }
                if (current instanceof JsonMappingException jsonMappingException) {
                    current = jsonMappingException.getCause();
                    continue;
                }
                current = current.getCause();
            }
            return false;
        }
    }
}
