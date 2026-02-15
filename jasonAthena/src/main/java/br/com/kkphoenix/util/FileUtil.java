package br.com.kkphoenix.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.nio.file.Files;
import java.nio.file.Paths;

public class FileUtil {

  /**
   * Reads the entire content of a file and returns it as a single String.
   *
   * @param filePath The path to the file.
   * @return The file content as a String.
   * @throws IOException If an I/O error occurs while reading the file.
   */
  public static String readFileAsString(String filePath) throws IOException {
    return Files.readString(Paths.get(filePath));
  }

  /** Reads the entire content of an InputStream and returns it as a single String. */
  public static String readStreamAsString(InputStream inputStream) throws IOException {
    try (inputStream) {
        return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  /** Reads a file and converts it to a Base64 encoded string. */
  public static String readFileAsBase64(String filePath) throws IOException {
      byte[] fileContent = Files.readAllBytes(Paths.get(filePath));
      return Base64.getEncoder().encodeToString(fileContent);
  }
}
