package de.muenchen.mcmp.http;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.core5.http.*;
import org.apache.hc.core5.http.io.entity.EntityUtils;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Utility class providing methods for working with JSON data,
 * including HTTP client interactions and JSON serialization/deserialization.
 * <p>
 * This class is designed as a static utility and cannot be instantiated.
 * All methods are designed to simplify and standardize processing of JSON-related operations
 * in the context of HTTP communication.
 */
@Slf4j
public class JsonUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false)
            .configure(JsonReadFeature.ALLOW_TRAILING_COMMA.mappedFeature(), true)
            .configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);
    private static final int MAX_LOGGED_BODY_CHARS = 2048;

    // prevent instantiation
    private JsonUtils() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Performs an HTTP GET request to the specified URI using the provided HttpClient,
     * and deserializes the JSON response into an object of the specified type.
     *
     * @param <T>        the type of the object to which the JSON response should be mapped
     * @param httpClient the HttpClient instance used to execute the HTTP request; must not be null
     * @param uri        the URI to which the GET request is sent; must not be null
     * @param type       the class of the object to which the JSON response should be deserialized; must not be null
     * @return the object of the specified type deserialized from the JSON response
     * @throws NullPointerException if any of the parameters is null
     * @throws JsonHttpException    if an error occurs during the HTTP request or JSON deserialization
     */
    public static <T> T httpGet(final HttpClient httpClient, final String uri, final Class<T> type) {
        Objects.requireNonNull(httpClient, "httpClient");
        Objects.requireNonNull(uri, "uri");
        Objects.requireNonNull(type, "type");
        final HttpGet httpGet = new HttpGet(uri);
        httpGet.addHeader(HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.toString());
        try (final ClassicHttpResponse httpResponse = httpClient.executeOpen(null, httpGet, null)) {
            final String response = getResponse(uri, httpResponse);
            return readValue(response, type);
        } catch (Exception e) {
            throw new JsonHttpException("HTTP GET failed for URI=" + uri, e);
        }
    }

    /**
     * Processes the HTTP response from a given URI, ensuring that it returns a valid
     * JSON response body and performs necessary validations such as status codes and content type.
     * If the response status code is unexpected or the body is invalid, an exception is thrown.
     *
     * @param uri      the URI from which the HTTP response was received; must not be null.
     * @param response the HTTP response to be processed; must not be null.
     * @return the response body as a string if it is valid and conforms to expected standards.
     * @throws Exception if the response is invalid, unexpected, or does not contain valid JSON content.
     */
    private static String getResponse(final String uri, final ClassicHttpResponse response) throws Exception {
        Objects.requireNonNull(response, "response");
        final HttpEntity entity = response.getEntity();
        final String body = entity != null ? EntityUtils.toString(entity, StandardCharsets.UTF_8) : "";

        final int code = response.getCode();
        if (code < 200 || code >= 300) {
            final String truncated = truncate(body, MAX_LOGGED_BODY_CHARS);
            if (code >= 400 && code < 500) {
                log.warn("HTTP client error for URI={} status={} reason={} body={}", uri, code, response.getReasonPhrase(), truncated);
            } else {
                log.error("HTTP server/unexpected error for URI={} status={} reason={} body={}", uri, code, response.getReasonPhrase(), truncated);
            }
            throw new JsonHttpException("Unexpected HTTP status " + code + " for URI=" + uri);
        }

        // Content-Type check for JSON
        final Header contentTypeHeader = response.getHeader(HttpHeaders.CONTENT_TYPE);
        if (contentTypeHeader != null) {
            final ContentType ct = ContentType.parseLenient(contentTypeHeader.getValue());
            if (ct != null && ct.getMimeType() != null && !ContentType.APPLICATION_JSON.getMimeType().equalsIgnoreCase(ct.getMimeType())) {
                throw new JsonHttpException("Unexpected Content-Type '" + ct.getMimeType() + "' for URI=" + uri + ", expected application/json");
            }
        }

        // Accept 204 No Content explicitly, otherwise treat empty as error
        if (code == 204) {
            throw new JsonHttpException("Empty JSON response (204 No Content) for URI=" + uri);
        }

        if (body.isBlank()) {
            throw new JsonHttpException("Empty JSON response for URI=" + uri);
        }
        return body;
    }

    /**
     * Reads and converts a JSON string into an object of the specified type.
     *
     * @param <T>  the type of the object to which the JSON string should be converted
     * @param json the JSON string to be deserialized; must not be null
     * @param type the class of the object to which the JSON string should be mapped; must not be null
     * @return the object of the specified type deserialized from the JSON string
     * @throws Exception if an error occurs during JSON deserialization
     */
    private static <T> T readValue(final String json, final Class<T> type) throws Exception {
        Objects.requireNonNull(json, "json");
        Objects.requireNonNull(type, "type");
        return OBJECT_MAPPER.readValue(json, type);
    }

    /**
     * Truncates a given string to a specified maximum length. If the string exceeds the maximum length,
     * it appends "...(truncated)" to indicate truncation.
     *
     * @param s   the string to be truncated; can be null. If null, the method will return null.
     * @param max the maximum allowed length for the string. If the string's length is less than or equal to
     *            this value, the string is returned as is.
     * @return the truncated string if the original string exceeds the specified maximum length; otherwise, the original string.
     */
    private static String truncate(String s, int max) {
        if (s == null || s.length() <= max) return s;
        return s.substring(0, max) + "...(truncated)";
    }

    /**
     * Exception thrown when an error occurs while processing HTTP responses in JSON format.
     * This runtime exception is used to encapsulate errors related to JSON parsing
     * or handling that happen in the context of HTTP operations.
     * <p>
     * This class extends {@code RuntimeException}, providing constructors to specify
     * an error message and an optional underlying cause.
     */
    public static class JsonHttpException extends RuntimeException {
        public JsonHttpException(String message) {
            super(message);
        }

        public JsonHttpException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}