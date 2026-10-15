package com.forcitis.one.platform.eventing.infrastructure;

import com.forcitis.one.kernel.IdRecords;
import com.forcitis.one.kernel.Uuids;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.DeserializationConfig;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationConfig;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.deser.ValueDeserializerModifier;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.ValueSerializerModifier;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * A typed id (a one-component record whose name ends in Id) is written as the text of its value, never as {"value": …}: payloads
 * of events and outbound messages carry ids as plain strings whatever the type behind them is (G-C5). Reading accepts the text,
 * and a bare number for ids made of a long. The module is a bean of the shared mapper, so it holds for HTTP requests and responses
 * as well; openApiHasNoValueWrapper keeps the interface contract free of the wrapper.
 */
@Component
class ScalarIdModule extends SimpleModule {

    ScalarIdModule() {
        super("scalar-ids");
        setSerializerModifier(new ValueSerializerModifier() {
            @Override
            public ValueSerializer<?> modifySerializer(SerializationConfig config, BeanDescription.Supplier description,
                    ValueSerializer<?> serializer) {
                return IdRecords.isIdRecord(description.getBeanClass()) ? new IdSerializer(description.getBeanClass()) : serializer;
            }
        });
        setDeserializerModifier(new ValueDeserializerModifier() {
            @Override
            public ValueDeserializer<?> modifyDeserializer(DeserializationConfig config, BeanDescription.Supplier description,
                    ValueDeserializer<?> deserializer) {
                return IdRecords.isIdRecord(description.getBeanClass()) ? new IdDeserializer(description.getBeanClass()) : deserializer;
            }
        });
    }

    private static final class IdSerializer extends StdSerializer<Object> {

        IdSerializer(Class<?> type) {
            super(type, false);
        }

        @Override
        public void serialize(Object id, JsonGenerator generator, SerializationContext context) {
            generator.writeString(id.toString());
        }
    }

    private static final class IdDeserializer extends ValueDeserializer<Object> {

        private final Class<?> type;
        private final Constructor<?> constructor;
        private final Class<?> valueType;

        IdDeserializer(Class<?> type) {
            this.type = type;
            this.valueType = type.getRecordComponents()[0].getType();
            try {
                this.constructor = type.getDeclaredConstructor(valueType);
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException(type.getName() + " has no canonical constructor", e);
            }
        }

        @Override
        public Object deserialize(JsonParser parser, DeserializationContext context) {
            JsonToken token = parser.currentToken();
            if (token != JsonToken.VALUE_STRING && token != JsonToken.VALUE_NUMBER_INT) {
                return context.handleUnexpectedToken(type, parser);
            }
            try {
                return constructor.newInstance(valueOf(parser.getString()));
            } catch (IllegalArgumentException e) {
                return context.handleWeirdStringValue(type, parser.getString(), "not an id of %s", type.getSimpleName());
            } catch (InvocationTargetException e) {
                if (e.getCause() instanceof IllegalArgumentException || e.getCause() instanceof NullPointerException) {
                    return context.handleWeirdStringValue(type, parser.getString(), "not an id of %s: %s", type.getSimpleName(),
                            e.getCause().getMessage());
                }
                throw new IllegalStateException(e.getCause());
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(type.getName() + " cannot be built", e);
            }
        }

        private Object valueOf(String text) {
            if (valueType == long.class) {
                return Long.parseLong(text);
            }
            if (valueType == int.class) {
                return Integer.parseInt(text);
            }
            if (valueType == UUID.class) {
                return Uuids.parseCanonical(text);
            }
            if (valueType == String.class) {
                return text;
            }
            throw new IllegalStateException("no scalar form for " + valueType.getName() + " in " + type.getName());
        }
    }
}
