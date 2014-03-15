package com.github.kubatatami.judonetworking.controllers.json;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.Deserializers;
import com.fasterxml.jackson.databind.deser.Deserializers.Base;
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdScalarSerializer;

import java.io.IOException;
import java.lang.reflect.Field;

public class EnumAnnotationModule extends SimpleModule {

    protected static ObjectMapper mapper = new ObjectMapper();

    public EnumAnnotationModule() {
        super("enum-annotation", new Version(1, 0, 0, "", EnumAnnotationModule.class.getPackage().getName(), ""));
        addSerializer(Enum.class, new EnumAnnotationSerializer());
    }

    @Override
    public void setupModule(SetupContext context) {
        super.setupModule(context);
        Base deser = new Deserializers.Base() {
            @SuppressWarnings("unchecked")
            @Override
            public JsonDeserializer<?> findEnumDeserializer(Class<?> type,
                                                            DeserializationConfig config, BeanDescription beanDesc)
                    throws JsonMappingException {

                return new EnumAnnotationDeserializer((Class<Enum<?>>) type);
            }
        };
        context.addDeserializers(deser);
    }


    public static class EnumAnnotationSerializer extends StdScalarSerializer<Enum> {

        public EnumAnnotationSerializer() {
            super(Enum.class, false);
        }

        @Override
        public void serialize(Enum value, JsonGenerator jgen, SerializerProvider provider) throws IOException {
            try {
                JsonProperty property = value.getDeclaringClass().getDeclaredField(value.name()).getAnnotation(JsonProperty.class);
                if (property != null) {
                    jgen.writeString(property.value());
                    return;
                }
            } catch (NoSuchFieldException e) {
            }
            mapper.writeValue(jgen, value);
        }
    }

    public static class EnumAnnotationDeserializer extends StdScalarDeserializer<Enum<?>> {

        protected EnumAnnotationDeserializer(Class<Enum<?>> clazz) {
            super(clazz);

        }

        @Override
        public Enum<?> deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
            String text = jp.getText();
            try {

                for (Field field : getValueClass().getDeclaredFields()) {
                    if (field.isEnumConstant()) {
                        JsonProperty property = field.getAnnotation(JsonProperty.class);
                        if (property != null && property.value().equals(text)) {
                            return (Enum<?>) field.get(null);
                        }
                    }
                }


            } catch (Exception e) {
            }
            return (Enum<?>) mapper.readValue("\"" + text + "\"", getValueClass());
        }

    }

}