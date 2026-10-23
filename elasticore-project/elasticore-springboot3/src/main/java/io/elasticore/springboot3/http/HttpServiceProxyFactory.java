package io.elasticore.springboot3.http;

import io.elasticore.runtime.port.*;
import io.elasticore.springboot3.bean.ApplicationContextProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.io.FileNotFoundException;
import java.lang.reflect.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Factory class for creating dynamic proxy instances of HTTP service interfaces.
 * This factory generates proxy objects that intercept method calls and convert them
 * into HTTP requests based on method annotations.
 * 
 * <p>The proxy factory enables seamless integration between service interfaces and
 * external HTTP APIs by automatically handling:</p>
 * <ul>
 *   <li>HTTP method mapping from annotations</li>
 *   <li>URL construction with path and query parameters</li>
 *   <li>Authentication header management</li>
 *   <li>Request/response serialization</li>
 * </ul>
 * 
 * @author ElastiCore Framework
 * @since 1.0
 */
public class HttpServiceProxyFactory {


    private final static Logger logger = LoggerFactory.getLogger(HttpServiceProxyFactory.class);

    /**
     * Creates a dynamic proxy instance for the specified HTTP service interface.
     * The proxy intercepts method calls and converts them into HTTP requests.
     * 
     * @param <T> the type of the service interface
     * @param serviceInterface the service interface class to proxy
     * @param externalService the external service configuration
     * @param beanFactory the Spring bean factory for dependency resolution
     * @param environment the Spring environment for configuration access
     * @return a proxy instance implementing the service interface
     */
    public static <T> T createService(Class<T> serviceInterface, ExternalService externalService, BeanFactory beanFactory, Environment environment) {
        return (T) Proxy.newProxyInstance(
                serviceInterface.getClassLoader(),
                new Class<?>[]{serviceInterface},
                new HttpInvocationHandler(serviceInterface, externalService, beanFactory, environment)
        );
    }

    /**
     * InvocationHandler implementation that processes method calls on HTTP service proxies.
     * This handler intercepts method invocations and translates them into appropriate
     * HTTP requests using the configured endpoint information.
     */
    private static class HttpInvocationHandler implements InvocationHandler {

        private final Class<?> serviceInterface;

        private final ExternalService externalService;

        private final BeanFactory beanFactory;

        private final Environment environment;

        private ApplicationContext applicationContext;

        /**
         * Constructs a new HttpInvocationHandler with the specified configuration.
         * 
         * @param serviceInterface the service interface being proxied
         * @param externalService the external service configuration
         * @param beanFactory the Spring bean factory for dependency resolution
         * @param environment the Spring environment for configuration access
         */
        public HttpInvocationHandler(Class<?> serviceInterface, ExternalService externalService, BeanFactory beanFactory,  Environment environment) {
            this.serviceInterface = serviceInterface;
            this.externalService = externalService;
            this.beanFactory = beanFactory;
            this.environment = environment;
        }

        /**
         * Retrieves the Spring ApplicationContext instance.
         * Uses lazy initialization and caches the context for subsequent calls.
         * 
         * @return the ApplicationContext instance, or null if not available
         */
        protected ApplicationContext getApplicationContext() {
            if(applicationContext==null) {
                applicationContext = ApplicationContextProvider.getApplicationContext();
            }

            return applicationContext;
        }

        /**
         * Where this proxy looks up its beans (auth provider, transformer): the
         * bean factory it was made for, else the static context. Not the static
         * context first - another context may have set it, or closed it.
         */
        protected BeanFactory beans() {
            return beanFactory != null ? beanFactory : getApplicationContext();
        }

        /**
         * Handles standard Object methods (hashCode, equals, toString) for the proxy.
         * These methods are handled locally without making HTTP requests.
         * 
         * @param proxy the proxy instance
         * @param method the method being invoked
         * @param args the method arguments
         * @return the result for standard Object methods, or null if not a standard method
         */
        private Object presetMethod(Object proxy, Method method, Object[] args) {
            if ("hashCode".equals(method.getName())) {
                return System.identityHashCode(proxy);
            } else if ("equals".equals(method.getName())) {
                return proxy == args[0];
            } else if ("toString".equals(method.getName())) {
                return "Proxy for " + proxy.getClass().getName();
            }

            return null;
        }

        /**
         * Extracts the HTTP method from the endpoint annotation.
         * 
         * @param endpoint the HttpEndpoint annotation
         * @return the HttpMethod enum value
         */
        private HttpMethod getHttpMethod(HttpEndpoint endpoint) {
            String httpMethod = endpoint.method().toUpperCase();
            return HttpMethod.valueOf(httpMethod);
        }

        /**
         * Constructs the list of HTTP headers for the request.
         * Includes authorization headers from configuration.
         * 
         * @return a list of header maps for the HTTP request
         */
        private List<Map<String, String>> getHeaderMapList() {
            List<Map<String, String>> headerMapList = new ArrayList<>();
            Map<String, String> header = new HashMap<>();
           //header.put("Cookie", "_ga="); // for SFA


            String authKey = environment.getProperty(this.externalService.id() + ".authKey"
                    , this.externalService.authKey());
            if(authKey!=null && authKey.length()>0) {
                header.put("Authorization" ,authKey);
            }

            String apiKey = environment.getProperty(  this.externalService.id() + ".apiKey", "");

            if (apiKey != null && !apiKey.isEmpty()) {
                String authorization = header.put("Authorization", "Bearer " + apiKey);
            }
            headerMapList.add(header);

            return headerMapList;
        }

        /**
         * Resolves the effective HTTP timeout configuration for this external service.
         * Each timeout value is resolved independently following the precedence:
         * per-port runtime property -&gt; DSL annotation value -&gt; global runtime
         * property -&gt; framework default.
         *
         * @return the resolved timeout configuration (never null)
         */
        private HttpTimeoutConfig resolveTimeoutConfig() {
            return new HttpTimeoutConfig(
                resolveTimeout("connectTimeout", this.externalService.connectTimeout(), HttpTimeoutConfig.DEFAULT_CONNECT_TIMEOUT),
                resolveTimeout("readTimeout", this.externalService.readTimeout(), HttpTimeoutConfig.DEFAULT_READ_TIMEOUT),
                resolveTimeout("writeTimeout", this.externalService.writeTimeout(), HttpTimeoutConfig.DEFAULT_WRITE_TIMEOUT),
                resolveTimeout("responseTimeout", this.externalService.responseTimeout(), HttpTimeoutConfig.DEFAULT_RESPONSE_TIMEOUT));
        }

        /**
         * Resolves a single timeout value (in milliseconds) by precedence:
         * <ol>
         *   <li>per-port runtime property: {@code {serviceId}.{name}}</li>
         *   <li>DSL annotation value (when &gt;= 0)</li>
         *   <li>global runtime property: {@code elasticore.http.{name}}</li>
         *   <li>framework hardcoded default</li>
         * </ol>
         *
         * @param name        the timeout property/attribute name (e.g. "connectTimeout")
         * @param dslValue     the value declared in DSL via @timeout ({@code -1} when unset)
         * @param hardDefault  the framework default to use when nothing else is set
         * @return the resolved timeout value in milliseconds
         */
        private int resolveTimeout(String name, int dslValue, int hardDefault) {
            // per-port runtime property (application.yml) - overridable without rebuild
            Integer perPort = parseTimeoutProperty(this.externalService.id() + "." + name);
            // global runtime property (application.yml) - base for all ports
            Integer global = parseTimeoutProperty("elasticore.http." + name);
            // Precedence: per-port property > DSL @timeout > global property > framework default
            return HttpTimeoutConfig.resolveTimeoutValue(perPort, dslValue, global, hardDefault);
        }

        /**
         * Reads and parses a port property as a non-negative integer, defaulting to 0
         * when the property is absent, blank, or not a valid integer.
         *
         * @param key the property key to read from the Spring Environment
         * @return the parsed port, or 0 when unset/invalid
         */
        private int parsePort(String key) {
            String v = environment.getProperty(key);
            if (v == null || v.trim().isEmpty()) {
                return 0;
            }
            try {
                int parsed = Integer.parseInt(v.trim());
                return parsed >= 0 ? parsed : 0;
            } catch (NumberFormatException e) {
                logger.warn("Invalid port property '{}': '{}' (must be a non-negative integer)", key, v);
                return 0;
            }
        }

        /**
         * Reads and parses a timeout property as a non-negative integer (milliseconds).
         *
         * @param key the property key to read from the Spring Environment
         * @return the parsed value, or null when absent/blank/invalid
         */
        private Integer parseTimeoutProperty(String key) {
            String v = environment.getProperty(key);
            if (v == null || v.trim().isEmpty()) {
                return null;
            }
            try {
                int parsed = Integer.parseInt(v.trim());
                return parsed >= 0 ? parsed : null;
            } catch (NumberFormatException e) {
                logger.warn("Invalid timeout property '{}': '{}' (must be a non-negative integer in ms)", key, v);
                return null;
            }
        }


        /**
         * URL-encodes a string value using UTF-8 encoding.
         * Replaces '+' with '%20' for proper space encoding.
         * 
         * @param value the string to encode
         * @return the URL-encoded string
         * @throws RuntimeException if encoding fails
         */
        private static String encode(String value) {
            try {
                return URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
                        .replace("+", "%20");
            } catch (Exception e) {
                throw new RuntimeException("URL encoding fail", e);
            }
        }


        /**
         * Processes method invocations on the proxy object.
         * This method intercepts all calls to the service interface and translates them
         * into appropriate HTTP requests based on the method annotations.
         * 
         * @param proxy the proxy instance that the method was invoked on
         * @param method the Method instance corresponding to the interface method invoked
         * @param args an array of objects containing the values of the arguments passed
         * @return the result of the HTTP request
         * @throws Throwable if the HTTP request fails or other errors occur
         */
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            Object preset = presetMethod(proxy, method, args);
            if (preset != null)
                return preset;

            HttpEndpoint endpoint = method.getAnnotation(HttpEndpoint.class);
            HttpMethod httpMethod = getHttpMethod(endpoint);
            String callUrl = endpoint.url();
            String contentType = endpoint.contentType();
            String svrUrl = environment.getProperty(this.externalService.id() + ".url", this.externalService.url());
            List<Map<String, String>> header = getHeaderMapList();

            String keyNames = endpoint.paramNames(); // e.g., "catalogId,modelId"
            String[] paramNames = keyNames != null ? keyNames.split(",") : new String[0];

            Map<String, Object> pathParams = new HashMap<>();
            Map<String, Object> queryParams = new LinkedHashMap<>();
            Object mainReqObject = null;
            HttpAuthProvider authProvider = null;

            // Check if this is a multipart request with @MultipartContentType annotations
            boolean isMultipartWithContentTypes = false;
            Parameter[] methodParams = method.getParameters();
            if (contentType != null && contentType.toLowerCase().contains("multipart/form-data")) {
                logger.debug("[MULTIPART-CHECK] contentType: {}, paramCount: {}", contentType, methodParams.length);
                for (Parameter param : methodParams) {
                    if (param.isAnnotationPresent(MultipartContentType.class)) {
                        isMultipartWithContentTypes = true;
                        logger.debug("[MULTIPART-CHECK] Found @MultipartContentType on param: {}", param.getName());
                        break;
                    }
                }
            }

            // For multipart with content types, collect all non-primitive params
            Map<String, Object> multipartParts = isMultipartWithContentTypes ? new LinkedHashMap<>() : null;
            Map<String, String> multipartContentTypes = isMultipartWithContentTypes ? new HashMap<>() : null;

            if(args!=null) {
                for (int i = 0; i < args.length; i++) {
                    Object arg = args[i];

                    if (arg instanceof HttpAuthProvider) {
                        authProvider = (HttpAuthProvider) arg;
                        continue;
                    }

                    String paramName = (i < paramNames.length) ? paramNames[i].trim() : "arg" + i;

                    if (isPrimitiveOrWrapper(arg)) {

                        Object value = arg;
                        if (arg != null && arg.getClass().isArray()) {
                            int length = Array.getLength(arg);
                            List<String> items = new ArrayList<>();
                            for (int k = 0; k < length; k++) {
                                Object element = Array.get(arg, k);
                                items.add(String.valueOf(element));
                            }
                            value = String.join(",", items);
                        }


                        if (callUrl.contains("{" + paramName + "}")) {
                            pathParams.put(paramName, value);
                        } else {
                            queryParams.put(paramName, value);
                        }
                    } else {
                        // Non-primitive parameter handling
                        if (isMultipartWithContentTypes) {
                            // NEW: Collect into multipart parts for per-field content type support
                            multipartParts.put(paramName, arg);
                            logger.debug("[MULTIPART-ARGS] Added to multipartParts: {}", paramName);

                            // Read @MultipartContentType annotation if present
                            if (i < methodParams.length) {
                                MultipartContentType mct = methodParams[i].getAnnotation(MultipartContentType.class);
                                if (mct != null) {
                                    multipartContentTypes.put(paramName, mct.value());
                                    logger.debug("[MULTIPART-ARGS] Added contentType for {}: {}", paramName, mct.value());
                                }
                            }
                        } else {
                            // EXISTING: Single body object behavior (unchanged)
                            mainReqObject = arg;
                        }
                    }
                }
            }

            // Build MultipartRequest if applicable
            if (isMultipartWithContentTypes && multipartParts != null && !multipartParts.isEmpty()) {
                mainReqObject = new MultipartRequest(multipartParts, multipartContentTypes);
                logger.debug("[MULTIPART-BUILD] Created MultipartRequest with {} parts", multipartParts.size());
            }


            if (authProvider == null && beans() != null) {
                String authProviderBeanId = this.externalService.id() + ".httpAuthProvider";
                try {
                    authProvider = (HttpAuthProvider) beans().getBean(authProviderBeanId);
                } catch (NoSuchBeanDefinitionException e) {
                } catch (BeansException e) {
                    logger.warn(authProviderBeanId + " bean error.", e);
                } catch (ClassCastException e) {
                    logger.warn(authProviderBeanId + " is not a HttpAuthProvider.", e);
                }
            }



            for (Map.Entry<String, Object> entry : pathParams.entrySet()) {
                callUrl = callUrl.replace("{" + entry.getKey() + "}", entry.getValue() != null ? entry.getValue().toString() : "");
            }

            callUrl = callUrl.replaceAll("\\{[^/]+}", "");

            if (!queryParams.isEmpty()) {
                String queryString = queryParams.entrySet().stream()
                        .map(e -> e.getKey() + "=" + (e.getValue() != null ? encode(e.getValue().toString()) : ""))
                        .collect(Collectors.joining("&"));

                if (callUrl.contains("?")) {
                    callUrl += "&" + queryString;
                } else {
                    callUrl += "?" + queryString;
                }

                /*
                if (mainReqObject == null && !callUrl.contains("{")) {
                    mainReqObject = queryParams;
                }

                 */
            }


            Type resolvedReturnType = method.getGenericReturnType();

            HttpTransformer httpTransformer = null;

            String httpTransformFactoryBeanId = this.externalService.id() + ".httpTransformerFactory";
            try {
                HttpTransformerFactory httpTransformerFactory = beans() == null ? null : (HttpTransformerFactory) beans().getBean(httpTransformFactoryBeanId);
                if(httpTransformerFactory!=null) {
                    httpTransformer = httpTransformerFactory.getHttpTransformer(proxy, method, args ,authProvider);
                    resolvedReturnType = httpTransformer.getReturnType();

                    authProvider = httpTransformer;
                }
            } catch (NoSuchBeanDefinitionException e) {
            } catch (BeansException e) {
                logger.warn(httpTransformFactoryBeanId + " bean error.", e);
            } catch (ClassCastException e) {
                logger.warn(httpTransformFactoryBeanId + " is not a HttpTransformerFactory.", e);
            } catch (Exception e){
                logger.error(httpTransformFactoryBeanId + " has problem.", e);
            }

            final Type finalReturnType = resolvedReturnType;
            ParameterizedTypeReference<?> responseType = new ParameterizedTypeReference<>() {
                @Override
                public Type getType() {
                    return finalReturnType;
                }
            };


            boolean ignoreSSL = Boolean.parseBoolean(
                environment.getProperty(this.externalService.id() + ".ignoreSSL", "false"));
            String proxyHost = environment.getProperty(this.externalService.id() + ".proxyHost");
            int proxyPort = parsePort(this.externalService.id() + ".proxyPort");

            HttpTimeoutConfig timeout = resolveTimeoutConfig();

            try {
                // Check if this is a MultipartRequest - use RestTemplate for proper Content-Length handling
                if (mainReqObject instanceof MultipartRequest) {
                    MultipartRequest multipartRequest = (MultipartRequest) mainReqObject;
                    String fullUrl = svrUrl + callUrl;
                    logger.debug("[MULTIPART] Using RestTemplate for MultipartRequest: {}", fullUrl);

                    // Apply auth provider if present
                    if (authProvider != null) {
                        Object transformed = authProvider.process(multipartRequest, header);
                        if (transformed instanceof MultipartRequest) {
                            multipartRequest = (MultipartRequest) transformed;
                        }
                    }

                    // Determine response class from return type
                    Class<?> responseClass = Object.class;
                    if (finalReturnType instanceof Class) {
                        responseClass = (Class<?>) finalReturnType;
                    } else if (finalReturnType instanceof ParameterizedType) {
                        Type rawType = ((ParameterizedType) finalReturnType).getRawType();
                        if (rawType instanceof Class) {
                            responseClass = (Class<?>) rawType;
                        }
                    }

                    Object body = HttpApiClient.sendMultipartWithRestTemplate(
                        httpMethod, fullUrl, multipartRequest, header, responseClass, ignoreSSL,
                        proxyHost, proxyPort, timeout);

                    if (httpTransformer != null) {
                        ArrayList responseHeaders = new ArrayList<>();
                        Object newResp = httpTransformer.transformResponse(body, responseHeaders);
                        return newResp;
                    }

                    return body;
                }

                // Standard WebClient path for non-multipart requests
                ResponseEntity<?> responseEntity = HttpApiClient.exchangeResponseEntity(httpMethod, svrUrl, callUrl,
                                mainReqObject, responseType, header, authProvider, contentType,
                                ignoreSSL, proxyHost, proxyPort, timeout)
                        .onErrorMap(WebClientResponseException.class, ex -> {
                            String errorDetail = String.format(
                                    "HTTP Error [%s %s] - Status: %s, Body: %s",
                                    httpMethod, svrUrl,
                                    ex.getStatusCode(),
                                    ex.getResponseBodyAsString()
                            );
                            return new IllegalStateException(errorDetail, ex);
                        })
                        .block();
                Object body = responseEntity.getBody();

                if(httpTransformer!=null) {

                    ArrayList responseHeaders = new ArrayList<>();
                    Map<String, String> headerMap = new HashMap<>();
                    responseEntity.getHeaders().forEach((key, values) ->
                            headerMap.put(key, String.join(",", values)));
                    responseHeaders.add(headerMap);

                    Object newResp = httpTransformer.transformResponse(body, responseHeaders);
                    return newResp;
                }

                return body;
            }catch (HttpApiClient.WebClientException we) {
                //we.printStackTrace();

                //throw new FileNotFoundException("xx");
                throw new ResponseStatusException(HttpStatusCode.valueOf(we.getStatus()), "API CALL FAIL: " + we.getMessage(), we);
            }
        }

        /**
         * Helper method that checks if the object is primitive, wrapper, or string type.
         * This is used to determine whether an argument should be treated as a path/query parameter.
         * 
         * @param obj the object to check
         * @return true if the object is a primitive type, wrapper, or string
         */
        private boolean isPrimitiveOrWrapper(Object obj) {
            if (obj == null) return false;
            Class<?> clazz = obj.getClass();

            if (clazz.isArray()) {
                Class<?> componentType = clazz.getComponentType();
                return componentType.isPrimitive()
                        || componentType == String.class
                        || Number.class.isAssignableFrom(componentType)
                        || componentType == Boolean.class
                        || componentType == Character.class;
            }

            return clazz.isPrimitive()
                    || clazz == String.class
                    || Number.class.isAssignableFrom(clazz)
                    || clazz == Boolean.class
                    || clazz == Character.class;
        }



        public Object invoke_old(Object proxy, Method method, Object[] args) throws Throwable {

            Object preset = presetMethod(proxy, method, args);
            if (preset != null)
                return preset;


            Class<?> returnType = method.getReturnType();

            // Construct the ParameterizedTypeReference dynamically
            ParameterizedTypeReference<?> responseType = new ParameterizedTypeReference<Object>() {
                @Override
                public Type getType() {
                    return returnType;
                }
            };

            HttpEndpoint endpoint = method.getAnnotation(HttpEndpoint.class);

            HttpMethod httpMethod = getHttpMethod(endpoint);

            String svrUrl = environment.getProperty(this.externalService.id() + ".url"
                    , this.externalService.url());

            List<Map<String, String>> header = getHeaderMapList();



            String callUrl = endpoint.url();

            HttpAuthProvider authProvider = null;
            List orgList = new ArrayList();
            for (Object arg : args) {
                if (arg instanceof HttpAuthProvider) {
                    authProvider = (HttpAuthProvider) arg;
                } else
                    orgList.add(arg);
            }
            if(authProvider==null && beans() !=null) {
                String authProviderBeanId = this.externalService.id()+".httpAuthProvider";
                try {
                    authProvider = (HttpAuthProvider) beans().getBean(authProviderBeanId);
                }catch (BeansException re) {

                }catch (ClassCastException cce) {
                    logger.warn(authProviderBeanId+ " is not HttpAuthProvider implements. [ClassCastException]");
                }
            }


            if (orgList.size() > 1) {
                String params = toQueryString(orgList.get(1));
                if (callUrl.indexOf("?") > 0) {
                    callUrl = callUrl + "&" + params;
                } else {
                    callUrl = callUrl + "?" + params;
                }

            }

            Object mainReqObject = null;

            if (orgList.size() > 0) {
                mainReqObject = orgList.get(0);
            }

            String contentType = endpoint.contentType();


            Object shortenUrl = HttpApiClient.exchange(httpMethod, svrUrl, callUrl,
                            mainReqObject, responseType, header, authProvider ,contentType)

                    .onErrorMap(WebClientResponseException.class, ex ->
                            new IllegalStateException("HTTP Error: " + ex.getStatusCode(), ex)
                    )

                    .block();

            return shortenUrl;
        }
    }

    /**
     * Converts an object to a URL query string by extracting field values.
     * Uses reflection to access all declared fields and URL-encodes the values.
     * 
     * @param input the object to convert to query string
     * @return the URL-encoded query string
     * @throws Exception if reflection or encoding fails
     */
    public static String toQueryString(Object input) throws Exception {
        return Stream.of(input.getClass().getDeclaredFields())
                .peek(field -> field.setAccessible(true))
                .map(field -> {
                    try {
                        String name = field.getName();
                        Object value = field.get(input);
                        if (value != null) {
                            return URLEncoder.encode(name, StandardCharsets.UTF_8.name()) + "=" +
                                    URLEncoder.encode(value.toString(), StandardCharsets.UTF_8.name());
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    return null;
                })
                .filter(param -> param != null)
                .collect(Collectors.joining("&"));
    }
}