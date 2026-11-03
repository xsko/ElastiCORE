package io.elasticore.springboot3.http;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.elasticore.runtime.port.HttpAuthProvider;
import io.netty.channel.ChannelOption;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.apache.commons.lang3.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.*;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.http.HttpEntity;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.util.UriComponentsBuilder;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.ProxyProvider;

import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import javax.net.ssl.SSLException;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Utility class for making HTTP API calls using Spring WebFlux WebClient.
 * Provides methods for creating WebClient instances and performing HTTP operations
 * with support for various content types, authentication, and error handling.
 * 
 * <p>This class offers convenient methods for:</p>
 * <ul>
 *   <li>Creating configured WebClient instances with timeout and SSL settings</li>
 *   <li>Performing HTTP exchanges with automatic serialization/deserialization</li>
 *   <li>Handling authentication providers and custom headers</li>
 *   <li>Supporting both Mono and Flux reactive responses</li>
 * </ul>
 * 
 * @author ElastiCore Framework
 * @since 1.0
 */
public class HttpApiClient {

    private static final Logger log = LoggerFactory.getLogger(HttpApiClient.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);


    /**
     * Creates a configured WebClient instance with the specified base URL and SSL settings.
     * The WebClient is configured with default timeouts and connection settings.
     * 
     * @param url the base URL for the WebClient
     * @param ignoreSSL whether to ignore SSL certificate validation (useful for development)
     * @return a configured WebClient instance
     */
    public static WebClient createWebClient(String url, boolean ignoreSSL) {
        return createWebClient(url, ignoreSSL, null, 0, HttpTimeoutConfig.defaults());
    }

    /**
     * Creates a configured WebClient instance with the specified base URL.
     * SSL certificate validation is enabled by default.
     *
     * @param url the base URL for the WebClient
     * @return a configured WebClient instance
     */
    public static WebClient createWebClient(String url) {
        return createWebClient(url, false);
    }

    /**
     * Creates a configured WebClient instance with proxy settings.
     *
     * @param url the base URL for the WebClient
     * @param ignoreSSL whether to ignore SSL certificate validation
     * @param proxyHost the proxy server hostname (null to disable proxy)
     * @param proxyPort the proxy server port
     * @return a configured WebClient instance
     */
    public static WebClient createWebClient(String url, boolean ignoreSSL, String proxyHost, int proxyPort) {
        return createWebClient(url, ignoreSSL, proxyHost, proxyPort, HttpTimeoutConfig.defaults());
    }

    /**
     * Creates a configured WebClient instance with proxy and explicit timeout settings.
     *
     * @param url the base URL for the WebClient
     * @param ignoreSSL whether to ignore SSL certificate validation
     * @param proxyHost the proxy server hostname (null to disable proxy)
     * @param proxyPort the proxy server port
     * @param timeout the timeout configuration (null falls back to framework defaults)
     * @return a configured WebClient instance
     */
    public static WebClient createWebClient(String url, boolean ignoreSSL, String proxyHost, int proxyPort, HttpTimeoutConfig timeout) {
        final HttpTimeoutConfig tc = (timeout != null) ? timeout : HttpTimeoutConfig.defaults();
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, tc.getConnectTimeout())
                .responseTimeout(Duration.ofMillis(tc.getResponseTimeout()))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(tc.getReadTimeout(), TimeUnit.MILLISECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(tc.getWriteTimeout(), TimeUnit.MILLISECONDS)));

        if (proxyHost != null && !proxyHost.isEmpty()) {
            httpClient = httpClient.proxy(spec -> spec
                    .type(ProxyProvider.Proxy.HTTP)
                    .host(proxyHost)
                    .port(proxyPort));
            log.debug("WebClient configured with proxy: {}:{}", proxyHost, proxyPort);
        }

        if (ignoreSSL) {
            httpClient = httpClient.secure(sslContextSpec -> {
                try {
                    sslContextSpec.sslContext(SslContextBuilder.forClient()
                            .trustManager(InsecureTrustManagerFactory.INSTANCE).build());
                } catch (SSLException e) {
                    log.error(e.getMessage(), e);
                }
            });
        }

        return WebClient.builder()
                .baseUrl(url)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                // Jackson 2 in elasticore-springboot3, Jackson 3 in -springboot4.
                .codecs(HttpCodecs::configure)
                .build();
    }

    /**
     * Performs an HTTP exchange with the specified parameters.
     * 
     * @param <V> the type of the request body
     * @param <T> the type of the response body
     * @param httpMethod the HTTP method to use
     * @param url the base URL for the request
     * @param path the path to append to the base URL
     * @param sendObj the request body object
     * @param responseType the expected response type
     * @return a Mono containing the response
     */
    public static <V, T> Mono<T> exchange(HttpMethod httpMethod, String url, String path, V sendObj, ParameterizedTypeReference<T> responseType) {
        return exchange(httpMethod, createWebClient(url), path, sendObj, responseType, null, MediaType.APPLICATION_JSON);
    }

    /**
     * Performs an HTTP exchange with custom headers.
     * 
     * @param <V> the type of the request body
     * @param <T> the type of the response body
     * @param httpMethod the HTTP method to use
     * @param url the base URL for the request
     * @param path the path to append to the base URL
     * @param sendObj the request body object
     * @param responseType the expected response type
     * @param headerMapList list of header maps to include in the request
     * @return a Mono containing the response
     */
    public static <V, T> Mono<T> exchange(HttpMethod httpMethod, String url, String path, V sendObj, ParameterizedTypeReference<T> responseType,
                                          List<Map<String, String>> headerMapList) {
        return exchange(httpMethod, createWebClient(url), path, sendObj, responseType, headerMapList, MediaType.APPLICATION_JSON);
    }


    /**
     * Performs an HTTP exchange with authentication provider and custom content type.
     * 
     * @param <V> the type of the request body
     * @param <T> the type of the response body
     * @param httpMethod the HTTP method to use
     * @param url the base URL for the request
     * @param path the path to append to the base URL
     * @param sendObj the request body object
     * @param responseType the expected response type
     * @param headerMapList list of header maps to include in the request
     * @param provider the authentication provider to process the request
     * @param contentType the content type for the request
     * @return a Mono containing the response
     */
    public static <V, T> Mono<T> exchange(HttpMethod httpMethod, String url, String path, V sendObj, ParameterizedTypeReference<T> responseType,
                                          List<Map<String, String>> headerMapList, HttpAuthProvider provider, String contentType) {

        Object inputObject = sendObj;
        if (provider != null) {
            if (headerMapList == null) {
                headerMapList = new ArrayList<>();
            }
            Object replaceInputObj = provider.process(sendObj, headerMapList);
            if(replaceInputObj!=null) {
                inputObject = replaceInputObj;
            }
        }
        MediaType mediaType = MediaType.APPLICATION_JSON;
        if(contentType!=null) {
            mediaType = MediaType.parseMediaType(contentType);
        }
        return exchange(httpMethod, createWebClient(url), path, inputObject, responseType, headerMapList,mediaType);
    }


    /**
     * Performs an HTTP exchange using an existing WebClient with custom headers.
     * 
     * @param <V> the type of the request body
     * @param <T> the type of the response body
     * @param httpMethod the HTTP method to use
     * @param webClient the configured WebClient instance to use
     * @param path the path to append to the WebClient's base URL
     * @param sendObj the request body object
     * @param responseType the expected response type
     * @param headerMapList list of header maps to include in the request
     * @return a Mono containing the response
     */
    public static <V, T> Mono<T> exchange(HttpMethod httpMethod, WebClient webClient, String path, V sendObj, ParameterizedTypeReference<T> responseType,
                                          List<Map<String, String>> headerMapList) {
        return exchange(httpMethod, webClient, path, sendObj, responseType, headerMapList, MediaType.APPLICATION_JSON);
    }

    /**
     * Performs an HTTP exchange with custom headers and media type.
     * 
     * @param <V> the type of the request body
     * @param <T> the type of the response body
     * @param httpMethod the HTTP method to use
     * @param url the base URL for the request
     * @param path the path to append to the base URL
     * @param sendObj the request body object
     * @param responseType the expected response type
     * @param headerMapList list of header maps to include in the request
     * @param mediaType the media type for the request
     * @return a Mono containing the response
     */
    public static <V, T> Mono<T> exchange(HttpMethod httpMethod, String url, String path, V sendObj, ParameterizedTypeReference<T> responseType,
                                          List<Map<String, String>> headerMapList, MediaType mediaType) {
        return exchange(httpMethod, createWebClient(url), path, sendObj, responseType, headerMapList, mediaType);
    }

    /**
     * Decodes a URL-encoded string using UTF-8 encoding.
     * 
     * @param value the string to decode
     * @return the decoded string, or null if input is null
     * @throws RuntimeException if decoding fails
     */
    private static String decode(String value) {
        if (value == null) {
            return null;
        }
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8.toString());
        } catch (Exception e) {
            throw new RuntimeException("URL decode fail", e);
        }
    }

    /**
     * Core HTTP exchange method using an existing WebClient with full configuration options.
     * This method handles both GET and non-GET requests, with automatic query parameter conversion
     * for GET requests and proper body handling for other methods.
     *
     * @param <V> the type of the request body
     * @param <T> the type of the response body
     * @param httpMethod the HTTP method to use (GET, POST, PUT, DELETE, etc.)
     * @param webClient the configured WebClient instance to use
     * @param path the path to append to the WebClient's base URL, may include query parameters
     * @param sendObj the request body object (for non-GET requests) or query parameters (for GET requests)
     * @param responseType the expected response type reference
     * @param headerMapList list of header maps to include in the request
     * @param mediaType the content type for the request body
     * @return a Mono containing the response body
     * @throws HttpPortException if the other side answers a 4xx or 5xx status (with its body and headers)
     */
    public static <V, T> Mono<T> exchange(
            HttpMethod httpMethod,
            WebClient webClient,
            String path,
            V sendObj,
            ParameterizedTypeReference<T> responseType,
            List<Map<String, String>> headerMapList,
            MediaType mediaType) {
        WebClient.RequestBodyUriSpec requestSpec = webClient.method(httpMethod);

        if (httpMethod == HttpMethod.GET) {
            URI inputUri = URI.create(path); // path  "/v1/catalogs/bmw/cars2/?modelId=xxx&page=1" OK
            String pathOnly = inputUri.getPath(); // "/v1/catalogs/bmw/cars2"

            MultiValueMap<String, String> mergedParams = new LinkedMultiValueMap<>();
            //mergedParams.addAll(UriComponentsBuilder.fromUri(inputUri).build().getQueryParams()); // path query
            mergedParams.addAll(getQueryParamConvert(sendObj)); // sendObj

            UriComponentsBuilder.fromUri(inputUri).build().getQueryParams().forEach((key, values) -> {
                for (String value : values) {
                    mergedParams.add(key, decode(value));
                }
            });

            return configureRequest(
                            requestSpec.uri(uriBuilder -> {
                                URI uri = uriBuilder
                                        .path(pathOnly)
                                        .queryParams(mergedParams)
                                        .build();
                                log.debug("HTTP[GET] URI: {}", uri);
                                return uri;
                            }),
                            sendObj,
                            headerMapList,
                            mediaType)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, HttpApiClient::toPortException)
                    .bodyToMono(responseType);
        } else {
            String[] pathParts = path.split("\\?", 2);

            String basePath = pathParts[0];
            String queryStr = pathParts.length > 1 ? pathParts[1] : null;

            return configureRequest(
                            requestSpec.uri(uriBuilder -> {
                                uriBuilder.path(basePath);
                                if (queryStr != null) {
                                    // As it always parsed: a trailing "&" adds no empty parameter.
                                    @SuppressWarnings("StringSplitter")
                                    String[] queryParams = queryStr.split("&");
                                    for (String param : queryParams) {
                                        String[] keyValue = param.split("=", 2);
                                        String key = keyValue[0];
                                        String value = keyValue.length > 1 ? keyValue[1] : "";
                                        uriBuilder.queryParam(key, value);
                                    }
                                }
                                return uriBuilder.build();
                            }),
                            sendObj,
                            headerMapList,
                            mediaType)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, HttpApiClient::toPortException)
                    .bodyToMono(responseType);
        }
    }


    /**
     * Performs an HTTP exchange and returns a ResponseEntity with headers and status.
     * 
     * @param <V> the type of the request body
     * @param <T> the type of the response body
     * @param httpMethod the HTTP method to use
     * @param url the base URL for the request
     * @param path the path to append to the base URL
     * @param sendObj the request body object
     * @param responseType the expected response type
     * @param headerMapList list of header maps to include in the request
     * @return a Mono containing the ResponseEntity
     */
    public static <V, T> Mono<ResponseEntity<T>> exchangeResponseEntity(HttpMethod httpMethod, String url, String path, V sendObj, ParameterizedTypeReference<T> responseType,
                                                                        List<Map<String, String>> headerMapList) {
        return exchangeResponseEntity(httpMethod, createWebClient(url), path, sendObj, responseType, headerMapList, MediaType.APPLICATION_JSON);
    }

    /**
     * Performs an HTTP exchange with authentication provider and returns a ResponseEntity.
     * This method provides access to response headers and status codes in addition to the response body.
     *
     * @param <V> the type of the request body
     * @param <T> the type of the response body
     * @param httpMethod the HTTP method to use
     * @param url the base URL for the request
     * @param path the path to append to the base URL
     * @param sendObj the request body object
     * @param responseType the expected response type
     * @param headerMapList list of header maps to include in the request
     * @param provider the authentication provider to process the request
     * @param contentType the content type for the request
     * @return a Mono containing the ResponseEntity with headers, status, and body
     */
    public static <V, T> Mono<ResponseEntity<T>> exchangeResponseEntity(HttpMethod httpMethod, String url, String path, V sendObj, ParameterizedTypeReference<T> responseType,
                                                                        List<Map<String, String>> headerMapList, HttpAuthProvider provider, String contentType) {
        Object inputObject = sendObj;
        if (provider != null) {
            if (headerMapList == null) {
                headerMapList = new ArrayList<>();
            }
            Object replaceInputObj = provider.process(sendObj, headerMapList);
            if (replaceInputObj != null) {
                inputObject = replaceInputObj;
            }
        }
        MediaType mediaType = MediaType.APPLICATION_JSON;
        if (contentType != null) {
            mediaType = MediaType.parseMediaType(contentType);
        }
        return exchangeResponseEntity(httpMethod, createWebClient(url), path, inputObject, responseType, headerMapList, mediaType);
    }

    /**
     * Performs an HTTP exchange with proxy and SSL settings and returns a ResponseEntity.
     *
     * @param <V> the type of the request body
     * @param <T> the type of the response body
     * @param httpMethod the HTTP method to use
     * @param url the base URL for the request
     * @param path the path to append to the base URL
     * @param sendObj the request body object
     * @param responseType the expected response type
     * @param headerMapList list of header maps to include in the request
     * @param provider the authentication provider
     * @param contentType the content type for the request
     * @param ignoreSSL whether to ignore SSL certificate validation
     * @param proxyHost the proxy server hostname (null to disable proxy)
     * @param proxyPort the proxy server port
     * @return a Mono containing the ResponseEntity with headers, status, and body
     */
    public static <V, T> Mono<ResponseEntity<T>> exchangeResponseEntity(HttpMethod httpMethod, String url, String path, V sendObj, ParameterizedTypeReference<T> responseType,
                                                                        List<Map<String, String>> headerMapList, HttpAuthProvider provider, String contentType,
                                                                        boolean ignoreSSL, String proxyHost, int proxyPort) {
        return exchangeResponseEntity(httpMethod, url, path, sendObj, responseType, headerMapList, provider, contentType,
                ignoreSSL, proxyHost, proxyPort, HttpTimeoutConfig.defaults());
    }

    /**
     * Performs an HTTP exchange with proxy, SSL and timeout settings and returns a ResponseEntity.
     *
     * @param <V> the type of the request body
     * @param <T> the type of the response body
     * @param httpMethod the HTTP method to use
     * @param url the base URL for the request
     * @param path the path to append to the base URL
     * @param sendObj the request body object
     * @param responseType the expected response type
     * @param headerMapList list of header maps to include in the request
     * @param provider the authentication provider
     * @param contentType the content type for the request
     * @param ignoreSSL whether to ignore SSL certificate validation
     * @param proxyHost the proxy server hostname (null to disable proxy)
     * @param proxyPort the proxy server port
     * @param timeout the timeout configuration (null falls back to framework defaults)
     * @return a Mono containing the ResponseEntity with headers, status, and body
     */
    public static <V, T> Mono<ResponseEntity<T>> exchangeResponseEntity(HttpMethod httpMethod, String url, String path, V sendObj, ParameterizedTypeReference<T> responseType,
                                                                        List<Map<String, String>> headerMapList, HttpAuthProvider provider, String contentType,
                                                                        boolean ignoreSSL, String proxyHost, int proxyPort, HttpTimeoutConfig timeout) {
        Object inputObject = sendObj;
        if (provider != null) {
            if (headerMapList == null) {
                headerMapList = new ArrayList<>();
            }
            Object replaceInputObj = provider.process(sendObj, headerMapList);
            if (replaceInputObj != null) {
                inputObject = replaceInputObj;
            }
        }
        MediaType mediaType = MediaType.APPLICATION_JSON;
        if (contentType != null) {
            mediaType = MediaType.parseMediaType(contentType);
        }
        return exchangeResponseEntity(httpMethod, createWebClient(url, ignoreSSL, proxyHost, proxyPort, timeout), path, inputObject, responseType, headerMapList, mediaType);
    }

    /**
     * Performs an HTTP exchange using an existing WebClient and returns a ResponseEntity.
     * This method provides access to response headers and status codes in addition to the response body.
     *
     * @param <V> the type of the request body
     * @param <T> the type of the response body
     * @param httpMethod the HTTP method to use (GET, POST, PUT, DELETE, etc.)
     * @param webClient the configured WebClient instance to use
     * @param path the path to append to the WebClient's base URL, may include query parameters
     * @param sendObj the request body object (for non-GET requests) or query parameters (for GET requests)
     * @param responseType the expected response type reference
     * @param headerMapList list of header maps to include in the request
     * @param mediaType the content type for the request body
     * @return a Mono containing the ResponseEntity with headers, status, and body
     */
    public static <V, T> Mono<ResponseEntity<T>> exchangeResponseEntity(
            HttpMethod httpMethod, WebClient webClient, String path, V sendObj,
            ParameterizedTypeReference<T> responseType, List<Map<String, String>> headerMapList, MediaType mediaType) {

        WebClient.RequestBodyUriSpec requestSpec = webClient.method(httpMethod);

        if (httpMethod == HttpMethod.GET) {
            // Parse path to separate base path and query parameters (same logic as exchange method)
            URI inputUri = URI.create(path);
            String pathOnly = inputUri.getPath();

            MultiValueMap<String, String> mergedParams = new LinkedMultiValueMap<>();
            mergedParams.addAll(getQueryParamConvert(sendObj));

            // Merge query parameters from path
            UriComponentsBuilder.fromUri(inputUri).build().getQueryParams().forEach((key, values) -> {
                for (String value : values) {
                    mergedParams.add(key, decode(value));
                }
            });

            return configureRequest(
                            requestSpec.uri(uriBuilder -> {
                                URI uri = uriBuilder
                                        .path(pathOnly)
                                        .queryParams(mergedParams)
                                        .build();
                                log.debug("HTTP[GET] ResponseEntity URI: {}", uri);
                                return uri;
                            }),
                            sendObj,
                            headerMapList,
                            mediaType)
                    .exchangeToMono(response -> {
                        response.headers()
                                .asHttpHeaders()
                                .forEach((key, value) -> log.debug("Response Header: {} -> {}", key, value));

                        return toEntity(response, responseType);
                    });
        } else {
            String[] pathParts = path.split("\\?", 2);
            String basePath = pathParts[0];
            String queryStr = pathParts.length > 1 ? pathParts[1] : null;

            return configureRequest(
                            requestSpec.uri(uriBuilder -> {
                                uriBuilder.path(basePath);
                                if (queryStr != null) {
                                    // As it always parsed: a trailing "&" adds no empty parameter.
                                    @SuppressWarnings("StringSplitter")
                                    String[] queryParams = queryStr.split("&");
                                    for (String param : queryParams) {
                                        String[] keyValue = param.split("=", 2);
                                        String key = keyValue[0];
                                        String value = keyValue.length > 1 ? keyValue[1] : "";
                                        uriBuilder.queryParam(key, value);
                                    }
                                }
                                return uriBuilder.build();
                            }),
                            sendObj,
                            headerMapList,
                            mediaType)
                    .exchangeToMono(response -> {
                        response.headers()
                                .asHttpHeaders()
                                .forEach((key, value) -> log.debug("Response Header: {} -> {}", key, value));

                        return toEntity(response, responseType);
                    })
                    .doOnSubscribe(subscription -> {
                        log.debug(
                                "[WebClient-Request] POST {} with body type: {}",
                                basePath,
                                sendObj != null ? sendObj.getClass().getSimpleName() : "null");
                    });
        }
    }


    /**
     * Performs an HTTP exchange and returns a Flux for streaming responses.
     * 
     * @param <V> the type of the request body
     * @param <T> the type of the response elements
     * @param httpMethod the HTTP method to use
     * @param url the base URL for the request
     * @param path the path to append to the base URL
     * @param sendObj the request body object
     * @param responseType the expected response element type
     * @param headerMapList list of header maps to include in the request
     * @param mediaType the media type for the request
     * @return a Flux containing the response elements
     */
    public static <V, T> Flux<T> exchangeForFlux(HttpMethod httpMethod, String url, String path, V sendObj, ParameterizedTypeReference<T> responseType,
                                                 List<Map<String, String>> headerMapList, MediaType mediaType) {
        return exchangeForFlux(httpMethod, createWebClient(url), path, sendObj, responseType, headerMapList, mediaType);
    }

    /**
     * Performs an HTTP exchange using an existing WebClient and returns a Flux for streaming responses.
     * This method is useful for handling responses that contain multiple elements or streaming data.
     * 
     * @param <V> the type of the request body
     * @param <T> the type of the response elements
     * @param httpMethod the HTTP method to use
     * @param webClient the configured WebClient instance to use
     * @param path the path to append to the WebClient's base URL
     * @param sendObj the request body object (for non-GET requests) or query parameters (for GET requests)
     * @param responseType the expected response element type reference
     * @param headerMapList list of header maps to include in the request
     * @param mediaType the content type for the request body
     * @return a Flux containing the response elements
     */
    public static <V, T> Flux<T> exchangeForFlux(HttpMethod httpMethod, WebClient webClient, String path, V sendObj, ParameterizedTypeReference<T> responseType,
                                                 List<Map<String, String>> headerMapList, MediaType mediaType) {
        WebClient.RequestBodyUriSpec requestSpec = webClient.method(httpMethod);
        return configureRequest(requestSpec.uri(uriBuilder -> uriBuilder.path(path)
                .queryParams(getQueryParamConvert(sendObj)).build()), sendObj, headerMapList, mediaType)
                .retrieve()
                .bodyToFlux(responseType);
    }


    private static WebClient.RequestBodySpec configureRequest(WebClient.RequestBodySpec requestSpec, Object sendObj,
                                                              List<Map<String, String>> headerMapList, MediaType mediaType) {

        if (headerMapList != null) {
            requestSpec.headers(httpHeaders -> httpHeaders.addAll(getHeaders(headerMapList)));
        }

        // Set Content-Type explicitly for non-multipart requests
        // Multipart requests let BodyInserters set Content-Type with boundary automatically
        if (mediaType != null && !mediaType.isCompatibleWith(MediaType.MULTIPART_FORM_DATA)) {
            requestSpec.contentType(mediaType);
        }

        /*
        if (ObjectUtils.isNotEmpty(sendObj)) {
            //String jsonBody = OBJECT_MAPPER.writeValueAsString(sendObj);
            //log.info("Request Body: {}", jsonBody);
            requestSpec.bodyValue(sendObj);
        }
         */

        if (ObjectUtils.isNotEmpty(sendObj)) {
            if (mediaType != null && mediaType.isCompatibleWith(MediaType.MULTIPART_FORM_DATA)) {
                // NEW: Handle MultipartRequest with per-field content types
                if (sendObj instanceof MultipartRequest) {
                    MultipartRequest mr = (MultipartRequest) sendObj;
                    // Use MultipartBodyBuilder for explicit control over part encoding
                    MultipartBodyBuilder builder = new MultipartBodyBuilder();

                    for (Map.Entry<String, Object> entry : mr.getParts().entrySet()) {
                        String partName = entry.getKey();
                        Object partValue = entry.getValue();

                        // Skip null values
                        if (partValue == null) {
                            log.debug("Multipart part '{}' has null value, skipping", partName);
                            continue;
                        }

                        log.debug("Processing multipart part '{}', type: {}", partName, partValue.getClass().getName());

                        if (mr.hasContentType(partName)) {
                            MediaType partMediaType = MediaType.parseMediaType(mr.getContentType(partName));

                            // For application/json, serialize to JSON string
                            if (partMediaType.isCompatibleWith(MediaType.APPLICATION_JSON)) {
                                try {
                                    String jsonValue = OBJECT_MAPPER.writeValueAsString(partValue);
                                    // Use asyncPart with explicit headers for JSON data
                                    builder.part(partName, jsonValue, partMediaType);
                                    log.debug("Added JSON part '{}', length: {}", partName, jsonValue.length());
                                } catch (Exception e) {
                                    log.warn("Failed to serialize object to JSON for part '{}': {}", partName, e.getMessage());
                                    builder.part(partName, partValue);
                                }
                            } else {
                                // Non-JSON with custom content type
                                builder.part(partName, partValue, partMediaType);
                            }
                        } else {
                            // No specific content type - add directly (for Resource/file - Spring handles correctly)
                            builder.part(partName, partValue);
                            log.debug("Added part '{}' directly (auto content-type)", partName);
                        }
                    }

                    log.debug("Multipart request built with MultipartBodyBuilder");
                    requestSpec.body(BodyInserters.fromMultipartData(builder.build()));
                }
                // EXISTING: MultiValueMap handling (unchanged)
                else if (sendObj instanceof MultiValueMap) {
                    requestSpec.body(BodyInserters.fromMultipartData((MultiValueMap<String, ?>) sendObj));
                }
                // EXISTING: POJO auto-convert (unchanged)
                else {
                    MultiValueMap<String, Object> multipartData = convertToMultiValueMap(sendObj);
                    requestSpec.body(BodyInserters.fromMultipartData(multipartData));
                }
            } else if (mediaType != null && mediaType.isCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED)) {
                if (sendObj instanceof String) {
                    // String bypass
                    requestSpec.bodyValue(sendObj);
                } else if (sendObj instanceof MultiValueMap) {
                    requestSpec.body(BodyInserters.fromFormData((MultiValueMap<String, String>) sendObj));
                } else {
                    // POJO  -> MultiValueMap<String, String> auto transform
                    MultiValueMap<String, String> formData = toFormData(sendObj);
                    requestSpec.body(BodyInserters.fromFormData(formData));
                }
            } else {
                requestSpec.bodyValue(sendObj);
            }
        }

        return requestSpec;
    }



    private static MultiValueMap<String, Object> convertToMultiValueMap(Object obj) {
        MultiValueMap<String, Object> multipartData = new LinkedMultiValueMap<>();

        if (obj == null) {
            return multipartData;
        }

        Class<?> clazz = obj.getClass();

        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                    continue;
                }

                field.setAccessible(true);
                try {
                    Object value = field.get(obj);
                    if (value != null) {
                        multipartData.add(field.getName(), value);
                    }
                } catch (IllegalAccessException e) {
                    log.warn("Failed to access field: {}", field.getName(), e);
                }
            }
            clazz = clazz.getSuperclass();
        }

        return multipartData;
    }

    /**
     * Converts a POJO to MultiValueMap for application/x-www-form-urlencoded requests.
     * Extracts all non-static fields and converts their values to strings.
     *
     * @param obj the POJO to convert
     * @return MultiValueMap containing field names and string values
     */
    public static MultiValueMap<String, String> toFormData(Object obj) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();

        if (obj == null) {
            return formData;
        }

        Class<?> clazz = obj.getClass();

        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                    continue;
                }

                field.setAccessible(true);
                try {
                    Object value = field.get(obj);
                    if (value != null) {
                        formData.add(field.getName(), value.toString());
                    }
                } catch (IllegalAccessException e) {
                    log.warn("Failed to access field: {}", field.getName(), e);
                }
            }
            clazz = clazz.getSuperclass();
        }

        return formData;
    }

    private static BodyInserter<Object, ReactiveHttpOutputMessage> getBodyInserter(Object sendObj, MediaType mediaType) {
        if (MediaType.APPLICATION_JSON.equals(mediaType)) {
            return BodyInserters.fromValue(sendObj);
        } else if (MediaType.APPLICATION_XML.equals(mediaType)) {
            return BodyInserters.fromValue(sendObj.toString());
        } else {
            return BodyInserters.fromValue(sendObj.toString());
        }
    }

    private static <V> MultiValueMap<String, String> getQueryParamConvert(V sendObj) {
        MultiValueMap<String, String> multiValueMap = new LinkedMultiValueMap<>();
        if (sendObj != null) {
            Map<String, String> paramMap = OBJECT_MAPPER.convertValue(sendObj, new TypeReference<Map<String, String>>() {
            });
            paramMap.entrySet().stream()
                    .filter(entry -> entry.getValue() != null)
                    .forEach(entry -> multiValueMap.add(entry.getKey(), entry.getValue()));
        }
        return multiValueMap;
    }

    private static <V> MultiValueMap<String, String> getQueryParamConvert_old(V sendObj) {
        MultiValueMap<String, String> multiValueMap = new LinkedMultiValueMap<>();
        if (sendObj != null) {
            multiValueMap.setAll(OBJECT_MAPPER.convertValue(sendObj, new TypeReference<>() {
            }));
        }
        return multiValueMap;
    }

    // HttpHeaders, not MultiValueMap: Spring Framework 7 no longer makes the
    // one a kind of the other, and HttpHeaders.addAll(HttpHeaders) is the
    // call both 6 and 7 accept.
    private static HttpHeaders getHeaders(List<Map<String, String>> headerMapList) {
        HttpHeaders headers = new HttpHeaders();
        if (headerMapList != null) {
            headerMapList.forEach(map -> map.forEach(headers::add));
        }
        return headers;
    }

    // ============================================================================
    // RestTemplate-based Multipart Request (with Content-Length support)
    // ============================================================================

    /**
     * Creates a RestTemplate configured for multipart requests with Content-Length.
     * Unlike WebClient, RestTemplate properly calculates Content-Length for multipart bodies.
     *
     * @param ignoreSSL whether to ignore SSL certificate validation
     * @param proxyHost the proxy server hostname (null to disable proxy)
     * @param proxyPort the proxy server port
     * @return configured RestTemplate instance
     */
    private static RestTemplate createRestTemplateForMultipart(boolean ignoreSSL, String proxyHost, int proxyPort, HttpTimeoutConfig timeout) {
        if (timeout == null) {
            timeout = HttpTimeoutConfig.defaults();
        }
        SimpleClientHttpRequestFactory factory = ignoreSSL ? trustingEveryCertificate() : new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout.getConnectTimeout());
        factory.setReadTimeout(timeout.getReadTimeout());

        if (proxyHost != null && !proxyHost.isEmpty()) {
            factory.setProxy(new Proxy(Proxy.Type.HTTP,
                    new InetSocketAddress(proxyHost, proxyPort)));
            log.debug("RestTemplate configured with proxy: {}:{}", proxyHost, proxyPort);
        }

        return new RestTemplate(factory);
    }

    /**
     * A request factory whose HTTPS connections accept any certificate and any host name.
     *
     * The trust is set on each connection this factory opens. It used to be set as the
     * JVM-wide default, so one port with ignoreSSL switched certificate checks off for
     * every other HTTPS connection in the application - other ports, other libraries -
     * from its first multipart call on. If no TLS context can be made, the factory
     * validates as usual.
     */
    private static SimpleClientHttpRequestFactory trustingEveryCertificate() {
        final SSLContext sslContext;
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
            };
            sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
        } catch (Exception e) {
            log.error("Failed to configure SSL trust all: {}", e.getMessage());
            return new SimpleClientHttpRequestFactory();
        }
        return new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
                if (connection instanceof HttpsURLConnection) {
                    HttpsURLConnection https = (HttpsURLConnection) connection;
                    https.setSSLSocketFactory(sslContext.getSocketFactory());
                    https.setHostnameVerifier((hostname, session) -> true);
                }
                super.prepareConnection(connection, httpMethod);
            }
        };
    }

    /**
     * The headers as a log line may show them: the values of every header that may
     * carry a credential - Authorization, cookies, sessions, passwords, tokens,
     * secrets, signatures and anything named a key (api_key, Ocp-Apim-Subscription-Key,
     * x-functions-key) - are masked. Masking a harmless header costs nothing; showing
     * a key puts it in every log the application ships.
     */
    static Map<String, List<String>> withCredentialsMasked(HttpHeaders headers) {
        Map<String, List<String>> shown = new LinkedHashMap<>();
        headers.forEach((name, values) -> {
            String lower = name.toLowerCase(Locale.ROOT);
            boolean credential = lower.contains("auth") || lower.contains("cookie") || lower.contains("session")
                    || lower.contains("password") || lower.contains("token") || lower.contains("secret")
                    || lower.contains("signature") || lower.contains("key");
            shown.put(name, credential ? Collections.singletonList("****") : values);
        });
        return shown;
    }

    /** A URL as a log line may show it: the query string, where some APIs take their key, is masked. */
    static String withQueryMasked(Object url) {
        String text = String.valueOf(url);
        int query = text.indexOf('?');
        return query < 0 ? text : text.substring(0, query) + "?****";
    }

    /**
     * Sends a multipart request using RestTemplate to ensure Content-Length is properly set.
     * This method handles MultipartRequest with per-field content types.
     *
     * @param <T> the type of the response body
     * @param httpMethod the HTTP method to use
     * @param url the full URL for the request
     * @param multipartRequest the MultipartRequest containing parts and content types
     * @param headerMapList list of header maps to include in the request
     * @param responseType the expected response type class
     * @param ignoreSSL whether to ignore SSL certificate validation
     * @param proxyHost the proxy server hostname (null to disable proxy)
     * @param proxyPort the proxy server port
     * @return the response body
     */
    public static <T> T sendMultipartWithRestTemplate(
            HttpMethod httpMethod, String url, MultipartRequest multipartRequest,
            List<Map<String, String>> headerMapList, Class<T> responseType, boolean ignoreSSL,
            String proxyHost, int proxyPort) {
        return sendMultipartWithRestTemplate(httpMethod, url, multipartRequest, headerMapList, responseType,
                ignoreSSL, proxyHost, proxyPort, HttpTimeoutConfig.defaults());
    }

    /**
     * Sends a multipart request using RestTemplate without a proxy (convenience overload).
     *
     * @param <T> the type of the response body
     * @param httpMethod the HTTP method to use
     * @param url the full URL for the request
     * @param multipartRequest the MultipartRequest containing parts and content types
     * @param headerMapList list of header maps to include in the request
     * @param responseType the expected response type class
     * @param ignoreSSL whether to ignore SSL certificate validation
     * @return the response body
     */
    public static <T> T sendMultipartWithRestTemplate(
            HttpMethod httpMethod, String url, MultipartRequest multipartRequest,
            List<Map<String, String>> headerMapList, Class<T> responseType, boolean ignoreSSL) {
        return sendMultipartWithRestTemplate(httpMethod, url, multipartRequest, headerMapList, responseType,
                ignoreSSL, null, 0, HttpTimeoutConfig.defaults());
    }

    /**
     * Sends a multipart request using RestTemplate with explicit timeout settings.
     *
     * @param <T> the type of the response body
     * @param httpMethod the HTTP method to use
     * @param url the full URL for the request
     * @param multipartRequest the MultipartRequest containing parts and content types
     * @param headerMapList list of header maps to include in the request
     * @param responseType the expected response type class
     * @param ignoreSSL whether to ignore SSL certificate validation
     * @param proxyHost the proxy server hostname (null to disable proxy)
     * @param proxyPort the proxy server port
     * @param timeout the timeout configuration (null falls back to framework defaults)
     * @return the response body
     */
    public static <T> T sendMultipartWithRestTemplate(
            HttpMethod httpMethod, String url, MultipartRequest multipartRequest,
            List<Map<String, String>> headerMapList, Class<T> responseType, boolean ignoreSSL,
            String proxyHost, int proxyPort, HttpTimeoutConfig timeout) {

        RestTemplate restTemplate = createRestTemplateForMultipart(ignoreSSL, proxyHost, proxyPort, timeout);

        // Build multipart body
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

        for (Map.Entry<String, Object> entry : multipartRequest.getParts().entrySet()) {
            String partName = entry.getKey();
            Object partValue = entry.getValue();

            if (partValue == null) {
                log.debug("Multipart part '{}' has null value, skipping", partName);
                continue;
            }

            if (multipartRequest.hasContentType(partName)) {
                MediaType partMediaType = MediaType.parseMediaType(multipartRequest.getContentType(partName));

                // For JSON content type, serialize the object to JSON string
                if (partMediaType.isCompatibleWith(MediaType.APPLICATION_JSON)) {
                    try {
                        String jsonValue = OBJECT_MAPPER.writeValueAsString(partValue);
                        // Wrap with HttpEntity to specify Content-Type for this part
                        // CRITICAL: Include charset=UTF-8 for JSON content type (matching Postman behavior)
                        HttpHeaders partHeaders = new HttpHeaders();
                        partHeaders.setContentType(new MediaType("application", "json", StandardCharsets.UTF_8));
                        HttpEntity<String> partEntity = new HttpEntity<>(jsonValue, partHeaders);
                        body.add(partName, partEntity);
                        log.debug("Added JSON part '{}' with Content-Type: {}, length: {}",
                            partName, partHeaders.getContentType(), jsonValue.length());
                    } catch (Exception e) {
                        log.warn("Failed to serialize object to JSON for part '{}': {}", partName, e.getMessage());
                        body.add(partName, partValue);
                    }
                } else {
                    // Non-JSON with custom content type
                    HttpHeaders partHeaders = new HttpHeaders();
                    partHeaders.setContentType(partMediaType);
                    HttpEntity<?> partEntity = new HttpEntity<>(partValue, partHeaders);
                    body.add(partName, partEntity);
                    log.debug("Added part '{}' with Content-Type: {}", partName, partMediaType);
                }
            } else {
                // No specific content type - add directly
                body.add(partName, partValue);
                log.debug("Added part '{}' directly (auto content-type)", partName);
            }
        }

        // Build request headers
        // IMPORTANT: Do NOT set Content-Type explicitly for multipart
        // Let RestTemplate auto-generate boundary in Content-Type header
        HttpHeaders headers = new HttpHeaders();
        // headers.setContentType(MediaType.MULTIPART_FORM_DATA); // Let RestTemplate handle this with boundary
        if (headerMapList != null) {
            headerMapList.forEach(map -> map.forEach((key, value) -> {
                // Skip Content-Type as RestTemplate will set it with boundary
                if (!"Content-Type".equalsIgnoreCase(key)) {
                    headers.add(key, value);
                }
            }));
        }

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        // The request as sent, at DEBUG like the WebClient calls, with credentials masked:
        // these lines used to go out at INFO with the Authorization header in them, which
        // put a port's API key in every application log.
        restTemplate.getInterceptors().add((request, body1, execution) -> {
            if (log.isDebugEnabled()) {
                log.debug("Multipart request {} {} headers {} body {} bytes", request.getMethod(),
                        withQueryMasked(request.getURI()), withCredentialsMasked(request.getHeaders()), body1.length);
            }
            return execution.execute(request, body1);
        });

        log.debug("Sending multipart request via RestTemplate to: {} [{}]", withQueryMasked(url), httpMethod);

        ResponseEntity<T> response;
        try {
            response = restTemplate.exchange(url, httpMethod, requestEntity, responseType);
        } catch (RestClientResponseException e) {
            // An error status: the same exception the WebClient calls throw, with what the other side said.
            throw new HttpPortException(
                    null,
                    null,
                    e.getStatusCode().value(),
                    e.getResponseHeaders(),
                    e.getResponseBodyAsString(StandardCharsets.UTF_8),
                    e);
        }
        log.debug("RestTemplate response status: {}", response.getStatusCode());
        return response.getBody();
    }

    /**
     * The answer as an entity. An error status is read as the return type too (a port returns
     * it as a value); one whose body does not read as that type is an HttpPortException with
     * the body as text.
     */
    private static <T> Mono<ResponseEntity<T>> toEntity(
            ClientResponse response, ParameterizedTypeReference<T> responseType) {
        if (!response.statusCode().isError()) {
            return response.toEntity(responseType);
        }
        int status = response.statusCode().value();
        HttpHeaders headers = response.headers().asHttpHeaders();
        MediaType type = headers.getContentType();
        Charset charset = type != null && type.getCharset() != null ? type.getCharset() : StandardCharsets.UTF_8;
        return response.bodyToMono(byte[].class).defaultIfEmpty(new byte[0]).flatMap(bytes -> response.mutate()
                .body(Flux.<DataBuffer>just(DefaultDataBufferFactory.sharedInstance.wrap(bytes)))
                .build()
                .toEntity(responseType)
                .onErrorMap(unreadable ->
                        new HttpPortException(null, null, status, headers, new String(bytes, charset), unreadable)));
    }

    /** An error status answered to a WebClient call, as an HttpPortException with its body and headers. */
    static Mono<? extends Throwable> toPortException(ClientResponse response) {
        int status = response.statusCode().value();
        HttpHeaders headers = response.headers().asHttpHeaders();
        return response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(body -> new HttpPortException(null, null, status, headers, body, null));
    }

    /**
     * Sends a multipart request using RestTemplate and returns a Mono for reactive compatibility.
     *
     * @param <T> the type of the response body
     * @param httpMethod the HTTP method to use
     * @param url the full URL for the request
     * @param multipartRequest the MultipartRequest containing parts and content types
     * @param headerMapList list of header maps to include in the request
     * @param responseType the expected response type class
     * @param ignoreSSL whether to ignore SSL certificate validation
     * @param proxyHost the proxy server hostname (null to disable proxy)
     * @param proxyPort the proxy server port
     * @return a Mono containing the response body
     */
    public static <T> Mono<T> sendMultipartWithRestTemplateMono(
            HttpMethod httpMethod, String url, MultipartRequest multipartRequest,
            List<Map<String, String>> headerMapList, Class<T> responseType, boolean ignoreSSL,
            String proxyHost, int proxyPort) {

        return Mono.fromCallable(() ->
            sendMultipartWithRestTemplate(httpMethod, url, multipartRequest, headerMapList, responseType, ignoreSSL, proxyHost, proxyPort)
        );
    }


}
