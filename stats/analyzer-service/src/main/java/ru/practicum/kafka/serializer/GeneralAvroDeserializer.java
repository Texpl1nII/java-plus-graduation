package ru.practicum.kafka.serializer;

import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;

public class GeneralAvroDeserializer<T extends SpecificRecordBase> implements Deserializer<T> {

    private Class<T> targetClass;

    public GeneralAvroDeserializer() {
    }

    public GeneralAvroDeserializer(Class<T> targetClass) {
        this.targetClass = targetClass;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void configure(Map<String, ?> configs, boolean isKey) {
        String targetClassName = (String) configs.get("specific.avro.value.class");
        if (targetClassName != null) {
            try {
                targetClass = (Class<T>) Class.forName(targetClassName);
            } catch (ClassNotFoundException e) {
                throw new SerializationException("Cannot find target class: " + targetClassName, e);
            }
        }
    }

    @Override
    public T deserialize(String topic, byte[] data) {
        if (data == null) {
            return null;
        }

        try (ByteArrayInputStream in = new ByteArrayInputStream(data)) {
            BinaryDecoder decoder = DecoderFactory.get().binaryDecoder(in, null);
            SpecificDatumReader<T> reader = new SpecificDatumReader<>(targetClass);
            return reader.read(null, decoder);
        } catch (IOException e) {
            throw new SerializationException("Error deserializing Avro message from topic: " + topic, e);
        }
    }

    @Override
    public void close() {
    }
}