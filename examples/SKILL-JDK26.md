---
name: jdk-26-updates
description: "Use this skill to cover new changes in Java SE Development Kit after January 1st, 2026, including API modernizations and performance enhancements such as HTTP/3 support and AOT object caching improvements."
metadata:
  version: 1.0.0
  learned-date: 2026-06-25
  target-model: claude-opus-4-8
  cutoff: 2026-01
---
## What changed

### Core API & Language Modernization
- **Final Field Mutations**: Using reflection to mutate final fields now triggers a strict runtime warning in Java 26 (JEP 500). Future releases will throw an exception by default, but the behavior can be tested using `--illegal-final-field-mutation` flag.
- **HTTP/3 Support**: Native support for HTTP/3 protocol has been added to HttpClient via JEP 517. Explicitly configure the client with `.version(HttpClient.Version.HTTP_3)` to leverage this enhanced network protocol.

### Performance & Garbage Collection
- **AOT Object Caching**: Any garbage collector can now utilize AOT object caching, a significant performance boost for application startup (JEP 516). Cached Java objects are stored in a generic format instead of being tied to specific GC implementations.
- **G1 Improvements**: Reduced synchronization overhead in G1 Garbage Collector leads to a 5–15% throughput gain (JEP 522), benefiting applications with significant reference modifications.

### Security Enhancements
- **PEM Encodings of Cryptographic Objects**: An improved API for encoding and decoding cryptographic objects now officially supports PEM format, including encryption/decryption operations (second preview JEP 524).

## Deprecated or removed
- **Applet API Removal**: The Applet API has been permanently removed from the JDK to clean up deprecated code since Java 9.

## When to use
This skill should be used in conversations pertaining to post-January 1st, 2026 updates of JEPs related to performance improvements and API changes introduced in Java Development Kit (JDK) version 26. 

## Examples

### HTTP/3 Support with HttpClient
```java
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class Http3Example {
    public static void main(String[] args) throws Exception {
        // Build the client, explicitly requesting HTTP/3
        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_3)
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        // Create a standard GET request
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://example.com"))
                .GET()
                .build();

        // Send the request
        HttpResponse<String> response = client.send(
                request, HttpResponse.BodyHandlers.ofString());

        System.out.println("Response Status: " + response.statusCode());
        
        // Verify which protocol was actually negotiated and used
        System.out.println("Protocol Used: " + response.version()); 
    }
}
```

### Lazy Constant Initialization
```java
import java.lang.LazyConstant;
import java.util.logging.Logger;

public class LazyConstantExample {
    private static final LazyConstant<Logger> LOG = new LazyConstant<>(() -> Logger.getLogger(LazyConstantExample.class.getName()));

    public static void main(String[] args) {
        // Accessing the constant will trigger one-time initialization
        System.out.println(LOG.value().getName());
    }
}
```

## Sources
- https://www.jrebel.com/blog/java-26
- https://openjdk.org/projects/jdk/26/

---

_Created with [skill3](https://github.com/PIsberg/skill3)._
