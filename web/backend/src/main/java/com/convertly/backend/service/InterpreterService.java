package com.convertly.backend.service;

import com.convertly.backend.dto.ToolDtos.ToolRunRequest;
import com.convertly.backend.dto.ToolDtos.ToolRunResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class InterpreterService {
    private final ObjectMapper objectMapper;
    private final String host;
    private final int port;
    private final Duration timeout;
    private final int maxFrameBytes;

    public InterpreterService(
        ObjectMapper objectMapper,
        @Value("${convertly.interpreter.host:127.0.0.1}") String host,
        @Value("${convertly.interpreter.port:7878}") int port,
        @Value("${convertly.interpreter.timeout:PT15S}") Duration timeout,
        @Value("${convertly.interpreter.max-frame-bytes:4194304}") int maxFrameBytes
    ) {
        this.objectMapper = objectMapper;
        this.host = host;
        this.port = port;
        this.timeout = timeout;
        this.maxFrameBytes = maxFrameBytes;
    }

    public ToolRunResponse run(ToolRunRequest request) {
        byte[] responseBytes = sendFrame(writeRequestJson(request));
        String responseJson = new String(responseBytes, StandardCharsets.UTF_8);
        InterpreterResult result = readResponse(responseJson);

        return new ToolRunResponse(
            request.source(),
            request.instruction(),
            result.output(),
            result.originalOutput(),
            request.options(),
            result.status()
        );
    }

    private byte[] writeRequestJson(ToolRunRequest request) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("source", request.source());
            payload.put("script", request.instruction());
            payload.put("instruction", request.instruction());
            payload.put("options", request.options());
            return objectMapper.writeValueAsBytes(payload);
        } catch (IOException exception) {
            throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Interpreter request could not be serialized",
                exception
            );
        }
    }

    private byte[] sendFrame(byte[] payload) {
        if (payload.length > maxFrameBytes) {
            throw new ResponseStatusException(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "Interpreter request exceeds the maximum frame size"
            );
        }

        int timeoutMillis = Math.toIntExact(timeout.toMillis());

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMillis);
            socket.setSoTimeout(timeoutMillis);

            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
            output.writeInt(payload.length);
            output.write(payload);
            output.flush();

            DataInputStream input = new DataInputStream(socket.getInputStream());
            int responseLength = input.readInt();

            if (responseLength < 0 || responseLength > maxFrameBytes) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Interpreter response exceeds the maximum frame size"
                );
            }

            byte[] response = input.readNBytes(responseLength);
            if (response.length != responseLength) {
                throw new EOFException("Incomplete interpreter frame");
            }

            return response;
        } catch (EOFException exception) {
            throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "Interpreter closed the TCP connection before sending a complete frame",
                exception
            );
        } catch (IOException exception) {
            throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "Interpreter TCP request failed",
                exception
            );
        } catch (ArithmeticException exception) {
            throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Interpreter timeout is too large",
                exception
            );
        }
    }

    private InterpreterResult readResponse(String responseJson) {
        try {
            JsonNode root = objectMapper.readTree(responseJson);

            if (root.isTextual()) {
                String output = root.asText();
                return new InterpreterResult(output, output, "completed");
            }

            String output = firstText(root, "output", "result", "stdout", "content");
            String status = firstText(root, "status", "state");

            if (output == null || output.isBlank()) {
                String error = firstText(root, "error", "message");
                if (error != null && !error.isBlank()) {
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Interpreter failed: " + error);
                }

                throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Interpreter response did not contain an output field"
                );
            }

            return new InterpreterResult(
                output,
                firstText(root, "originalOutput", "original_output", "rawOutput", "raw_output", "output"),
                status == null || status.isBlank() ? "completed" : status
            );
        } catch (IOException exception) {
            throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "Interpreter response was not valid JSON",
                exception
            );
        }
    }

    private String firstText(JsonNode root, String... fields) {
        for (String field : fields) {
            JsonNode value = root.path(field);
            if (value.isTextual()) {
                return value.asText();
            }
        }

        return null;
    }

    private record InterpreterResult(String output, String originalOutput, String status) {
    }
}
