package ru.practicum.kafka.serializer;

import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;
import ru.practicum.ewm.stats.avro.EventSimilarityAvro;

import java.io.ByteArrayInputStream;
import java.io.IOException;

public class EventSimilarityAvroDeserializer implements Deserializer<EventSimilarityAvro> {

    @Override
    public EventSimilarityAvro deserialize(String topic, byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }

        try (ByteArrayInputStream in = new ByteArrayInputStream(data)) {
            BinaryDecoder decoder = DecoderFactory.get().binaryDecoder(in, null);
            SpecificDatumReader<EventSimilarityAvro> reader = new SpecificDatumReader<>(EventSimilarityAvro.getClassSchema());
            return reader.read(null, decoder);
        } catch (IOException e) {
            throw new SerializationException("Error deserializing EventSimilarityAvro from topic: " + topic, e);
        }
    }
}
