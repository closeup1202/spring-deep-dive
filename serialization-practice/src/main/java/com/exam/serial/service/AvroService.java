package com.exam.serial.service;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.*;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Service
public class AvroService {

    // 간단한 사용자 스키마 정의 (JSON 형태)
    private static final String USER_SCHEMA_JSON = "{"
            + "\"type\": \"record\","
            + "\"name\": \"User\","
            + "\"fields\": ["
            + "  {\"name\": \"name\", \"type\": \"string\"},"
            + "  {\"name\": \"age\", \"type\": \"int\"},"
            + "  {\"name\": \"phoneNumber\", \"type\": \"string\"}"
            + "]}";

    private final Schema schema = new Schema.Parser().parse(USER_SCHEMA_JSON);

    public byte[] serializeUser(String name, int age, String phoneNumber) throws IOException {
        // 1. 레코드 생성
        GenericRecord user = new GenericData.Record(schema);
        user.put("name", name);
        user.put("age", age);
        user.put("phoneNumber", phoneNumber);

        // 2. 직렬화 (Binary)
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        DatumWriter<GenericRecord> datumWriter = new GenericDatumWriter<>(schema);
        BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(outputStream, null);
        
        datumWriter.write(user, encoder);
        encoder.flush();
        
        return outputStream.toByteArray();
    }

    public GenericRecord deserializeUser(byte[] data) throws IOException {
        DatumReader<GenericRecord> datumReader = new GenericDatumReader<>(schema);
        BinaryDecoder decoder = DecoderFactory.get().binaryDecoder(data, null);
        return datumReader.read(null, decoder);
    }
}
