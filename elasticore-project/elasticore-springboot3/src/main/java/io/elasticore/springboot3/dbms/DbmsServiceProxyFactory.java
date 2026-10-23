package io.elasticore.springboot3.dbms;

import io.elasticore.runtime.port.DbmsService;
import io.elasticore.springboot3.bean.ApplicationContextProvider;
import io.elasticore.springboot3.util.ReflectUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

import java.lang.reflect.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * Factory class for creating dynamic proxy instances of DBMS service interfaces.
 * This factory generates proxy objects that intercept method calls and delegate them
 * to the underlying DBMS execution framework.
 * 
 * <p>The proxy factory enables seamless integration between service interfaces and
 * the DBMS layer by automatically handling method invocations, parameter mapping,
 * and result type conversion.</p>
 * 
 * @author ElastiCore Framework
 * @since 1.0
 */
public class DbmsServiceProxyFactory {

    /**
     * Creates a dynamic proxy instance for the specified service interface.
     * The proxy intercepts method calls and delegates them to the DBMS execution layer.
     * 
     * @param <T> the type of the service interface
     * @param serviceInterface the service interface class to proxy
     * @param dbmsPortService the DBMS service configuration
     * @param beanFactory the Spring bean factory for dependency resolution
     * @param environment the Spring environment for configuration access
     * @return a proxy instance implementing the service interface
     */
    public static <T> T createService(Class<T> serviceInterface, DbmsService dbmsPortService, BeanFactory beanFactory, Environment environment) {

        return (T) Proxy.newProxyInstance(
                serviceInterface.getClassLoader(),
                new Class<?>[]{serviceInterface},
                new DbmsInvocationHandler(serviceInterface, dbmsPortService, beanFactory, environment)
        );

    }


    /**
     * InvocationHandler implementation that processes method calls on DBMS service proxies.
     * This handler intercepts method invocations and translates them into appropriate
     * DBMS operations using the configured SQL executor.
     */
    static class DbmsInvocationHandler implements InvocationHandler {

        private final Class<?> serviceInterface;

        private final DbmsService externalService;

        private final BeanFactory beanFactory;

        private final Environment environment;

        private ApplicationContext applicationContext;

        /**
         * Constructs a new DbmsInvocationHandler with the specified configuration.
         * 
         * @param serviceInterface the service interface being proxied
         * @param externalService the DBMS service configuration
         * @param beanFactory the Spring bean factory for dependency resolution
         * @param environment the Spring environment for configuration access
         */
        public DbmsInvocationHandler(Class<?> serviceInterface, DbmsService externalService, BeanFactory beanFactory, Environment environment) {
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
                try {
                    applicationContext = ApplicationContextProvider.getApplicationContext();
                }catch (Throwable e) {
                    e.printStackTrace();
                }
            }

            return applicationContext;
        }

        /**
         * The SQL executor: from the bean factory this proxy was made for, else
         * from the static context. Not the static context first - another
         * context may have set it, or closed it. Null when neither has one.
         */
        protected DbmsSqlExecutor executor() {
            if (beanFactory != null) {
                try {
                    DbmsSqlExecutor own = beanFactory.getBean(DbmsSqlExecutor.class);
                    if (own != null) return own;
                } catch (NoUniqueBeanDefinitionException ambiguous) {
                    // two executors in its own context: say so, do not borrow another context's
                    throw ambiguous;
                } catch (NoSuchBeanDefinitionException notThere) {
                    // a factory without one: the static context may have it
                }
            }
            ApplicationContext context = getApplicationContext();
            return context == null ? null : context.getBean(DbmsSqlExecutor.class);
        }

        /**
         * Handles standard Object methods (hashCode, equals, toString) for the proxy.
         * These methods are handled locally without delegation to the DBMS layer.
         * 
         * @param proxy the proxy instance
         * @param method the method being invoked
         * @param args the method arguments
         * @return the result for standard Object methods, or null if not a standard method
         */
        private Object presetMethod(Object proxy, Method method,  Object[] args) {
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
         * Extracts or creates a Pageable object from the input parameter.
         * Supports various input formats including direct Pageable objects,
         * Maps with page/size keys, and objects with page/size fields.
         * 
         * @param input the input object that may contain pagination information
         * @return a Pageable object with pagination settings, defaults to page 0 size 50
         */
        protected Pageable getPageableObject(Object input) {
            Pageable pageable = null;

            if (input instanceof Pageable) {
                return (Pageable) input;
            }

            int defaultPage = 0;
            int defaultPageSize = 50;

            for (Method method : input.getClass().getMethods()) {
                if ((method.getName().equals("getPageable") || method.getName().equals("pageable"))
                        && Pageable.class.isAssignableFrom(method.getReturnType())
                        && method.getParameterCount() == 0) {
                    try {
                        return (Pageable) method.invoke(input);
                    } catch (Exception e) {

                    }
                }
            }

            if (input instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) input;
                Object pageVal = map.get("pageNumber");
                if(pageVal==null)
                    pageVal = map.get("page");

                Object sizeVal = map.get("sizeVal");
                if(sizeVal==null)
                    sizeVal = map.get("size");

                if (pageVal instanceof Number && sizeVal instanceof Number) {
                    return PageRequest.of(((Number) pageVal).intValue(), ((Number) sizeVal).intValue());
                }
            }else {
                try {
                    Class c = input.getClass();
                    Field pageField = ReflectUtils.getField(c,"pageNumber");
                    if(pageField==null)
                        pageField = ReflectUtils.getField(c,"page");

                    Object pageVal = pageField.get(input);
                    if (pageVal instanceof Integer)
                        defaultPage= (Integer) pageVal;


                    Field sizeField = ReflectUtils.getField(c,"sizeVal");
                    if(sizeField==null)
                        sizeField = ReflectUtils.getField(c,"size");

                    Object sizeVal = sizeField.get(input);

                    if (sizeVal instanceof Integer) {
                        defaultPageSize= (Integer) sizeVal;
                    }
                } catch (NullPointerException | IllegalAccessException ignored) {

                }
            }

            return PageRequest.of(defaultPage, defaultPageSize);
        }

        /**
         * Processes method invocations on the proxy object.
         * This method intercepts all calls to the service interface and translates them
         * into appropriate DBMS operations based on the method signature and return type.
         * 
         * @param proxy the proxy instance that the method was invoked on
         * @param method the Method instance corresponding to the interface method invoked
         * @param args an array of objects containing the values of the arguments passed
         * @return the result of the DBMS operation
         * @throws Throwable if the underlying DBMS operation fails
         */
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

            Object preset = presetMethod(proxy, method, args);
            if (preset != null)
                return preset;

            //String processId, I input, Class<O> outputType, String dataSource)

            DbmsSqlExecutor executor = executor();
            if(executor==null)
                return null;

            String processId = externalService.id() + "." + method.getName();
            String datasource = externalService.datasource();

            String methodName = method.getName();



            Parameter[] parameters = method.getParameters();

            Object input = null;

            boolean isSelfObj = true;
            if(parameters.length==1) {
                Class<?> firstParamType = parameters[0].getType();

                // A value - long, a Long (what a generated port declares for "long"), a
                // String, a date - is bound by the parameter's name; an object by its getters.
                if (BeanUtils.isSimpleValueType(firstParamType)) {
                    isSelfObj = false;
                }
            }
            else{
                isSelfObj = false;
            }
            if(!isSelfObj)
            {
                Map<String, Object> paramMap = new HashMap<>();

                for (int i = 0; i < parameters.length; i++) {
                    paramMap.put(parameters[i].getName(), args[i]);
                }
                input = paramMap;
            }
            else {
                input = args[0];
            }


            Class<?> returnType = method.getReturnType();
            Class<?> qeuryReturnType = null;

            if (List.class.isAssignableFrom(returnType)) {
                qeuryReturnType = extractGenericReturnType(method.getGenericReturnType());
                return executor.inqueryList(externalService, methodName, input,qeuryReturnType );
            } else if (returnType == Integer.class || returnType == int.class) {
                String lowerCaseMethodNm = methodName.toLowerCase();
                if(lowerCaseMethodNm.indexOf("update")>=0
                        || lowerCaseMethodNm.indexOf("delete")>=0
                        || lowerCaseMethodNm.indexOf("insert")>=0
                ) {
                    List<?> batchInput = resolveBatchInput(input);
                    if (batchInput != null) {
                        if (batchInput.isEmpty())
                            return 0;
                        return executor.executeBatchUpdate(externalService, methodName, batchInput);
                    }
                    return executor.executeUpdate(externalService, methodName, input );
                }

            } else if (Page.class.isAssignableFrom(returnType)) {
                qeuryReturnType = extractGenericReturnType(method.getGenericReturnType());
                Pageable pageable = getPageableObject(input);
                return executor.inqueryPage(externalService, methodName, input,qeuryReturnType, pageable);
            }

            return executor.inqueryFirst(externalService, methodName, input,returnType );

        }

        /**
         * Resolves the input object as a batch input list when it represents a
         * collection of complex elements (DTOs or Maps) that should each be executed
         * as an individual update statement.
         *
         * <p>Returns null when the input is not eligible for element-wise batch
         * execution so that the legacy single-execution path is preserved. A list of
         * simple values (String, Number, etc.) is intentionally excluded because such
         * lists are typically bound as-is to an IN clause parameter.</p>
         *
         * @param input the resolved method input (single argument or parameter map)
         * @return the batch input list, or null if not eligible for batch execution
         */
        static List<?> resolveBatchInput(Object input) {
            List<?> list = null;
            if (input instanceof List) {
                list = (List<?>) input;
            } else if (input != null && input.getClass().isArray()
                    && !input.getClass().getComponentType().isPrimitive()) {
                list = Arrays.asList((Object[]) input);
            }
            if (list == null)
                return null;

            Object first = null;
            for (Object e : list) {
                if (e != null) {
                    first = e;
                    break;
                }
            }
            if (first == null)
                return list; // empty or all-null list: nothing to execute

            if (isSimpleValueType(first.getClass()))
                return null;

            return list;
        }

        /**
         * Determines whether the given type is a simple value type that is bound
         * directly as a query parameter rather than treated as a parameter source object.
         *
         * @param type the type to check
         * @return true if the type is a simple value type, false otherwise
         */
        static boolean isSimpleValueType(Class<?> type) {
            return type == String.class
                    || Number.class.isAssignableFrom(type)
                    || type == Boolean.class
                    || type == Character.class
                    || type.isEnum()
                    || java.util.Date.class.isAssignableFrom(type)
                    || java.time.temporal.Temporal.class.isAssignableFrom(type)
                    || type == java.util.UUID.class;
        }

        /**
         * Extracts the first generic type parameter from a given Type.
         * Supports List<T>, Set<T>, Optional<T>, and other parameterized types.
         *
         * @param genericType The Type object to analyze.
         * @return The extracted generic type (Class<?>), or null if no valid generic type is found.
         */
        public static Class<?> extractGenericReturnType(Type genericType) {
            if (genericType instanceof ParameterizedType) {
                ParameterizedType paramType = (ParameterizedType) genericType;
                Type[] typeArgs = paramType.getActualTypeArguments();

                if (typeArgs.length > 0 && typeArgs[0] instanceof Class<?>) {
                    return (Class<?>) typeArgs[0]; // Extracts the generic type (e.g., TestDTO)
                } else {
                    System.out.println("[LOG] Generic type is not a concrete class: " + typeArgs[0].getTypeName());
                }
            }
            return null; // No generic type found
        }


    }
}
