// HttpConfig.java

package com.connorgillmead.chat.server.http;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * HttpConfig is a utility class that provides methods for handling HTTP-related
 * configurations and operations in the chat server.
 * It includes methods for loading HTML resources, parsing form data, and formatting
 * durations.
 * This class is not meant to be instantiated.
 */
public final class HttpConfig {
    private HttpConfig() { }

    /**
     * Loads an HTML resource from the classpath.
     * This method reads the content of an HTML file from the resources directory
     * and returns it as a string.
     *
     * @param resourcePath The path to the HTML resource file.
     * @return The content of the HTML file as a string.
     * @throws IOException If an I/O error occurs while reading the file.
     */
    public static String loadHtml(String resourcePath) throws IOException {
        try (InputStream input = HttpConfig.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new FileNotFoundException("Cannot find resource: " + resourcePath);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Parses form data from a URL-encoded string into a map of key-value pairs.
     * This method is used to extract parameters from the form data submitted via
     * HTTP POST.
     *
     * @param formData The URL-encoded form data as a string.
     * @return A map containing the parsed key-value pairs.
     */
    public static Map<String, String> parseFormData(String formData) {
        Map<String, String> map = new HashMap<>();
        for (String pair : formData.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                String key = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
                String val = URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                map.put(key, val);
            }
        }
        return map;
    }

    /**
     * Formats a Duration object into a human-readable string.
     * This method takes a Duration object and converts it into a string
     * representation
     *
     * @param duration The Duration object to format.
     * @return A string representation of the duration in a human-readable format.
     */
    public static String formatDuration(Duration duration) {
        // Constants for time units.
        final int totalHours = 24;
        final int totalOther = 60;

        // Calculate the number of days, hours, minutes, and seconds from the Duration
        // object.
        long days = duration.toDays();
        long hours = duration.toHours() % totalHours;
        long minutes = duration.toMinutes() % totalOther;
        long seconds = duration.getSeconds() % totalOther;
        StringBuilder stringBuilder = new StringBuilder();
        if (days > 0) {
            stringBuilder.append(days).append(" Days ");
        }
        if (hours > 0 || days > 0) {
            stringBuilder.append(hours).append(" Hours ");
        }
        if (minutes > 0 || hours > 0 || days > 0) {
            stringBuilder.append(minutes).append(" Minutes ");
        }
        stringBuilder.append(seconds).append(" Seconds");
        return stringBuilder.toString();
    }
}
