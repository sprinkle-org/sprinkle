## ---WORK IN PROGRESS---

Sprinkle - User Guide
======

Functional programming allows the creation of unbelievably concise and powerful code, where paradigms like parallelism, asynchronous non-blocking processing, recovery from failure etc. are either implicit, or achievable with a truly minor efforts once the concepts are mastered.

The solution for making large web-based systems truly scalable has been known for some time – non-blocking, asynchronous processing leveraging APIs and frameworks build on Java NIO. However, applying just the J2EE concepts and imperative programming style makes asynchronous programming difficult and inaccessible for the average programmer – as a result we are unable to build non-blocking applications, or at least we are unable to build them at scale.

Sprinkle is a toolkit built with the intention of making the asynchronous programming easy and mainstream by leveraging Java 8 functional capabilities(lambdas, completable futures) and functional thinking (monads, monoids, functors). It borrows ideas from Scala core libraries, and multiple Scala toolkits - like Spray(http://spray.io/) and Akka(http://akka.io/).

Furthermore, Sprinkle is non-intrusive - it does not force you to change your existing code, does not take over your application by forcing you to implement certain interfaces and inherit classes - it is based solely on function compositions.

### Functional programming concepts in Sprinkle

#### Declaring functions, higher-order functions
A functional programming language (according to the “loose” definition), is one where the functions are first-class citizens. Variables of type function can be declared just like other objects in the OO languages, they can be passed as arguments, and returned as results. According to this definition, Java 1.8 officially clarifies as a functional language.
Here is an example of a traditional approach of declaring a function in Java - the functions addHeader takes as an argument a variables of type Request, and return Request as a result:
```
public class Functions {
    public static Request addAuthHeader(Request request){
        String header = "Authorization";
        String value = "Basic dXNlcm5hbWU6cGFzc3dvcmQ=";

        return request.header(header, value);
    }
}
```
The function above can be invoked as follow:
```
Request requestArg = ...

Request request = Functions.addAuthHeader(requestArg);
```
If we want to pass this function as argument, we should use the following notation:
```
Functions::addAuthHeader
```

An alternative approach to define a function(new in Java 1.8) is as follow:
```
public class Functions {
    public static Function<Request, Request> addHeader(String header, String value){
        // The argument “a” is of type “Request”, the return type is also “Request”
        // The compiler determines the type of “a” without an explicit definition - this is called type inference
        return a -> {
            return a.header(header, value);
        };
    }

    // Variable of type “Function” that can be invoked or passed as an argument
    public static Function<Request, Request> addAuthHeaderFunc = addHeader("Authorization”, "Basic dXNlcm5hbWU6cGFzc3dvcmQ=");
}
```
If the function addAuthHeaderFunc has to be passed as an argument, it can simply be used as a regular variable:
```
someFunc(addAuthHeaderFunc);
```
A call to the function that this variable references to, can be made as follow:
```
Request requestArg = ...

Request request = addAuthHeaderFunc.apply(requestArg);
```

Here the flexibility of the second approach is apparent - a new function can be easily constructed not just for adding authorization header, but any other header.

A function that takes another function as a parameter, or returns a function as a result is called “higher-order” function in the functional programming - according to this definition, addHeader is a higher-order function. This approach allows a lot of flexibility and power - all of the classical OO patterns for example can be expressed that way.

Sprinkle toolkit is implemented completely on top of higher-order functions and function compositions.

#### Currying
Curring is a technique of constructing a function out of another function that takes multiple arguments by filling some of the parameters of the original function. 
If we have the following funciton:
```
BiFunction<Integer, Integer, Integer> add = (a, b) -> {
    return a + b 
};
```
By "curring" the function above we can construct a new function:
```
Function<Integer, Integer> addFive = a -> {
    return add.apply(a, 5);
}
```
#### Closures
Closure is the ability of a function to access variables from the environment, to "close" around them. These variables must be defined "final" in Java.
As an example, the following function closes around the 
```
final int n = 5;

Function<Integer, Integer> addOperand = a -> {
        // The function "closes" around n
        return a + n;
    }
```
#### Pure functions
Function with no side effects (side effects are writing to some file or DB storage, depending on some state for the calculations etc.) is called a pure function in the functional programming. Essentially the pure function just takes an input and returns an output. If such a function is called with the same parameters it always returns the same result. The general recommendation is to keep at least 80% of the functions in you code pure - the testing is simplified as a result, and it is made easy to reason about the outcome of certain code execution.

Sprinkle is built mostly on top of pure functions - in Java these are static functions that do not depend on on an object state.

#### “Try” monad
When composing functions, we can come with certain rules (algebraic laws) that simplify the relationship between those functions, which in turn can simplify the code (we eliminate big part of the possible scenarios), and also makes it easier to reason about the outcome of the execution of certain logic. Java 1.8 introduces Optional type(monad), which is an example for such approach. Please read the Oracle documentation to get the idea - http://www.oracle.com/technetwork/articles/java/java8-optional-2175753.html.

“Optional” eliminates the needs to deal with null results or arguments - essentially an argument of type “Optional” can never be null, which removes all kind of checks and verifications that we normally put in the Java code to prevent the side effects of having nulls.

Unfortunately Java 1.8 stops here, it does not come with other useful monads like Try and Either that exist in the other more-functional languages like Scala and Haskell.

Sprinkle toolkit deals with this by introducing its own Try monad. The rule, or algebraic law that is promoted here is that a function can never throw an exception. Instead it returns result that is of Try type, and it wraps either the actual value of the calculation, or any exception that may have occurred.

Here is an example how to use Try directly:
```
public Try<Integer> getInteger(String value){
    try {
        // This is returned when everything okay
        return Try.success(Integer.parseInt(value));
    } catch(Exception ex){
        // If an exception is thrown, wrap it in Try
        return Try.failure(ex);
    }
}
...
// Using the value later on
Try<Integer> result = getInteger(someInputValue);
// “a” contains the value just when it it is there, “map” extracts the value out of the monad when it exists
// Multiple calls can be chained that way
Try<Integer> finalResult = result.map(a -> a * 2);

if(finalResult.isSuccess()){
    Integer value = finalResult.get();
    // Do something with this value
    ...
} else {
    System.out.println(“Exception: ” + finalResult.getException());
}
```
Do we have to rewrite all our functions now to use Try type as a result? The answer is “no”. The Sprinkle toolkit provides logic for converting functions with one argument that can trow an exception to one that returns the Try monad and can never throw exceptions(this is called “lifting” in the functional programming). For example if we have the following function:
```
public class Functions {
    // Function of type Request -> Request that throws an exeption
    public static Request addAuthHeader(Request request) throws Exception {
        ...
    }
}
```
We can easily convert it to function that does not throw an exception anymore, just returns a result of Try type:
```
import static org.sprinkle.Pipeline.lift;
...
// The new function is of type Request -> Try<Request>, and it never throws an exception
Function<Request, Try<Request>> addAuthHeaderLifted = lift(Functions::addAuthHeader);
```

#### Tuples
Often we need to return a result from a function that contains multiple values. In Java we are used to create a dedicated class that holds the multiple values and provides type safety. Scala for examples comes with a universal type-safe solution called “tuple”. Tuples do not exist in Java, so the Sprinkle toolkit introduces them.
Here is an example of tuple with 3 values:
```
import static org.sprinkle.Tuple.*;
...
// Create a tuple of type Tuple3<String, Integer, Date>
Tuple3 myTuple = tuple3(“Hi”, new Integer(100), new Date());
...
// Get the values out of the tuple when needed
String myString = myTuple._1();
Integer myInt = myTuple._2();
Date myDate = myTuple._3();

// The following won’t compile, because the type safety is enforced, _3() returns Date:
String newString = myTuple._3();
```
Sprinkle comes with tuples with up to ten arguments - Tuple2 to Tuple10. If more argument are needed they can be easily added.

### Building a pipeline
Pipeline is a monad that wraps around functions. It is used to pipe asynchronous and synchronous functions together, and build one composite function at the end.
The pipeline monad handles any exceptions and asynchronous aspects the individual functions, allowing the user to focus just on the business logic. There is a lot of flexibility on how the pipeline is built.

The pipeline chains synchronous or asynchronous functions by passing the successful result of one function to the next. These functions should never throw an exception, instead they have to wrap the output within the Try monad. Any function(sync or async) can easily be lifted to a state where it throws no exceptions.

As an example, if we have functions with the following signatures:

```
class MyFunctions {
    // Sync function that builds an HTTP GET request object
    public static Request get(String url) throws Exception {...}

    // Sync function
    public static Request addAuthentication(Request httpRequest) throws Exception {...}

    // Async function that is making the actual async HTTP call
    public static CompletableFuture<StringBuffer> sendReceive(Request httpRequest) throws Exception {...}

    // Sync function that unmarshalls the result
    public static String unmarshallToString(StringBuffer response) throws Exception {...}
}
```
We can lift these functions, so they always return result, never throw exceptions:
```
Function<String, Try<Request>> tryGet = lift(MyFunctions::get);
Function<Request, Try<Request>> tryAddAuthentication = lift(MyFunctions::addAuthentication);
Function<Request, CompletableFuture<Try<StringBuffer>>> trySendReceive = liftAsync(MyFunctions::sendReceive);
Function<StringBuffer, Try<String>> tryUnmarshallToString = lift(MyFunctions::unmarshallToString);
```
Then a composite pipeline function can be built:
```
Function<String, CompletableFuture<Try<String>>> pipelineFunc =
    Pipeline.<String, Request>createPipeline(tryGet)     // Create the initial pipeline monad that wraps the first function
            .<Request>pipeToSync(tryAddAuthentication)   // Create a new pipeline monad by binding the next function - pipelineToSync is using flatMap internally
            .<StringBuffer>pipeToAsync(trySendReceive)   // Bind an async function the same way
            .<String>pipeToSync(tryUnmarshallToString)   // Bind a sync function to the async one
            .get();                                      // Finally get the value out of the last monad - the complete composite function
```
The function types can be inferred, so the above can be simplified further:
```
Function<String, CompletableFuture<Try<String>>> pipelineFunc =
    Pipeline.createPipeline(tryGet)
            .pipeToSync(tryAddAuthentication)
            .pipeToAsync(trySendReceive)
            .pipeToSync(tryUnmarshallToString)
            .get();
```
The above function reference can be used easily just like any other function:
```
CompletableFuture<Try<String>> result = pipelineFunc.apply("http://localhost:9903/v1");

// When the above is completed the lambda is called. The result parameter “a” is of type Try<String>
result.thenAccept(a -> {
    if(a.isSuccess()){
        System.out.println("Success: " + a.get());
    } else {
        // Failure
        System.out.println("Failure: " + a.getException().toString());
    }
});
```
Here is another example that uses a pipeline POST request

```
        // Create an async HTTP client...
        HttpClient httpClient = new HttpClient();
        httpClient.start();

        ContentProvider provider = new StringContentProvider("{\"id\"=\"100\"}");

        //...and the function that makes the call
        Function<HttpClient, CompletableFuture<Try<String>>> pipelineFunc =
                Pipeline.createPipeline(constructPost("http://localhost:9903/v1", provider, "application/json"))
                        .pipeToSync(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                        .pipeToAsync(trySendReceive)
                        .pipeToSync(unmarshallToString())
                        .get();

        // Call the composite funciton
        CompletableFuture<Try<String>> result = pipelineFunc.apply(httpClient);

        // Process the request when ready
        result.thenAccept(a -> {
            if(a.isSuccess()){
                System.out.println("Success: " + a.get());
            } else {
                // Failure
                System.out.println("Failure: " + a.getException().toString());
            }
        });
```

### Caching futures

The Sprinkle toolkit provides caching capabilities - it allows the caching of completable future objects. When such an object is requested from the cache a completable future is created using a pipeline function for example, then stored. Any other client that requests an object object with the same key gets back the already store future. Once the future is completed, all the registered listeners are notified. Therefore, no multiple calls are made to a remote service, and no duplicate expensive calculations are performed. Furthermore, this approach does not block threads - all the calling threads used to process the requests are released to the pool and just once the cached future is completed new ones are obtained again to finish the job. As a result, a truly small number of threads can serve multiple requests.

Here are some examples how to use the caching.

First, the cache static definitions are imported:
```
import static org.sprinkle.CacheTool.*;
```
The HttpClient and the respective “get” request function is constructed:
```
        HttpClient httpClient = new HttpClient();
        httpClient.start();

        Function<String, Request> get = constructGet.apply(httpClient);
```
The “get” function takes the URL string as a parameter, and this becomes later the cache key.
Next the pipeline function is defined:
```
        Function<String, CompletableFuture<Try<String>>> pipelineFunc =
                Pipeline.createPipeline(lift(get))
                        .pipeToSync(pipelineLog("Pipeline executed"))
                        .pipeToSync(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                        .pipeToAsync(trySendReceive)
                        .pipeToSync(unmarshallToString())
                        .get();
```
“PipelineLog” function just prints a message when the composite function is executed, so it can be tracked how many calls are made.

Now the cache builder is constructed (check the Guava cache documentation for more information on that: https://code.google.com/p/guava-libraries/wiki/CachesExplained):
```
        CacheBuilder cacheBuilder = CacheBuilder.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(10, TimeUnit.MINUTES);
```
The pipeline function is wrapped with the cache:
```
        // This is an actual Guava cache
        Cache<String, CompletableFuture<Try<String>>> cache = constructCache(cacheBuilder, pipelineFunc);
        // This is the composite function that supports caching
        Function<String, CompletableFuture<Try<String>>> cacheGet = withCache(cache);
```
The “cacheGet” function has the same signature like the original pipeline function, just with some additional layers that take care of the cache management.
If the final composite function is called multiple times within a short period, just one message “Pipeline executed” will be printed. The rest of the calls will get the value from the cache:
```
        // Get the value - makes the call
        CompletableFuture<Try<String>> future1 = cacheGet.apply("http://localhost:9903/v1");

        // Get the value - retrieve from the cache
        CompletableFuture<Try<String>> future2 = cacheGet.apply("http://localhost:9903/v1");

        // Get the value - retrieve from the cache
        CompletableFuture<Try<String>> future3 = cacheGet.apply("http://localhost:9903/v1");

        // Get the value - makes the call
        CompletableFuture<Try<String>> future4 = cacheGet.apply("http://localhost:9904/v1");

        // Just print the result
        future1.thenAccept(a -> {
            if(a.isSuccess()){
                System.out.println("Success future1: " + a.get());
            } else {
                // Failure
                System.out.println("Failure future1: " + a.getException().toString());
            }
        });
        ...
```
The above usage of the cache provides access to the cache object. This has certain benefits - for example, if we want to reload a value, we can just delete the item with the respective key from cache - the pipeline function will be executed again, and load the latest data.

There is an alternative, more convenient approach if we don’t need a reference to the cache object - the cache is part of the pipeline function definition:
```
        CacheBuilder cacheBuilder = CacheBuilder.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(10, TimeUnit.MINUTES);
                        
        Function<String, CompletableFuture<Try<String>>> pipelineCacheFunc = withCache(
                            cacheBuilder,
                            Pipeline.createPipeline(constructUrl)
                                    .pipeToSync(tryConstructGet.apply(getHttpClient()))
                                    .pipeToSync(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                                    .pipeToAsync(trySendReceive)
                                    .pipeToSync(unmarshallToString())
                                    .get());
```
A third approach is to pass a parameter function that extracts the cache key from the function parameter. The extracted key value is used a cache key, and as a parameter to the inner function. Here is an example:
```
// Composite function that gets the servlet async context as a parameter
Function<AsyncContext, CompletableFuture<Try<String>>> pipelineCacheFunc;

// Function that extracts a value used as a cache key
Function<AsyncContext, Try<String>> extractKey = a -> {
                // I.e. URI is /v1?key=100
                String key = Try.success(a.getRequest().getParameter("key"));
            };
            
// Guava cache builder
CacheBuilder cacheBuilder = CacheBuilder.newBuilder()
                    .maximumSize(1000)
                    .expireAfterWrite(10, TimeUnit.SECONDS);

// Composite function that gets the servlet async context as a parameter and caches the result of the inner function with the extracted key
Function<AsyncContext, CompletableFuture<Try<String>>> pipelineCacheFunc = 
                withCache(
                    cacheBuilder,
                    Pipeline.createPipeline(constructUrl)
                            .pipeToSync(tryConstructGet.apply(getHttpClient()))
                            .pipeToSync(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                            .pipeToAsync(trySendReceive)
                            .pipeToSync(unmarshallToString())
                            .get(),
                    extractKey);
```

### Circuit breaker

Circuit breaker is a pattern used to protect a system when one or more dependent services breaks down. In that case instead of having cascading failures, the system isolates the call, and starts returning a predefined response or exception until the dependent service recovers.

Sprinkle comes with a complete, easy to use, non-intrusive implementation of the circuit breaker.

To use the circuit breaker import all the static declaration:

```
import static org.sprinkle.CircuitBreaker.*;
```
Define the HTTP client. This may require some tuning if a large number of request will be sent:
```
        HttpClient httpClient = new HttpClient();

        httpClient.setMaxRequestsQueuedPerDestination(10000);
        httpClient.setMaxConnectionsPerDestination(10000);
        httpClient.setConnectTimeout(1000);

        httpClient.setIdleTimeout(60000);
        httpClient.start();
```
The executor service holds a thread pool of 10 requests in this example. This small number of threads is capable of serving hundreds of http requests because of the async nature of calls:
```
        ExecutorService executorService = Executors.newFixedThreadPool(10);
```
Next the circuit breaker state manager is initialized with the max number of sequential failure requests before the breaker moves to “open” state, duration of how long is stays open before it goes to “half-open”, and an executor service that provides threads used for the state change callbacks - these calls are unfrequent, therefore, the same executor service that deals with the http calls can be used, or a dedicated thread pool can be defined.
```
        CircuitBreakerStateManager circuitBreakerStateManager =
                CircuitBreakerStateManager.createStateManager(10, Duration.ofSeconds(5), executorService);
```
Callbacks that listen to the circuit breaker state change can be defined. As explained earlier they run on their own threads and do not interfere with the HTTP calls:
```
        circuitBreakerStateManager.addClosedListener(() -> System.out.println("Circuit breaker CLOSED"));
        circuitBreakerStateManager.addOpenListener(() -> System.out.println("Circuit breaker OPEN"));
        circuitBreakerStateManager.addHalfOpenListener(() -> System.out.println("Circuit breaker HALF-OPEN"));
```
In the pipeline definition the original function “trySendReceive” is wrapped in “withCircuitBreaker” function with the appropriate arguments:
```
        Function<Request, CompletableFuture<Try<String>>> pipelineFunc =
                Pipeline.createPipeline(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                        .pipeToAsync(withCircuitBreaker(trySendReceive, executorService, circuitBreakerStateManager))
                        .pipeToSync(unmarshallToString())
                        .get();
```
A function that is used to generate the HTTP request to a service must be defined, this is an example with http get:
```
        Function<String, Request> get = constructGet.apply(httpClient);
```
Here is an example how you can use the above definitions. This snippet of code generates 10,000 requests within 20 seconds. If the target service gets overwhelmed and starts returning errors, the circuit breaker detects that, moves to an open state and start returning CircuitBreakerOpenException allowing the dependent system to recover. Once the reset time expires it attempts to close by sending a single request and evaluating the result. If successful it moves back to a closed state.
```
        for(int i = 0; i <= 10000; i++){
            // Start 500 threads at a time
            if(i > 0 && i % 500 == 0){
                System.out.println("> Executed " + i + " requests...");
                Thread.sleep(1000);
            }

            CompletableFuture<Try<String>> result = pipelineFunc.apply(get.apply("http://localhost:9903/v1"));

            result.thenAccept(a -> {
                if(a.isSuccess()){
                    System.out.println("Success: " + a.get());
                } else {
                    // Failure
                    System.out.println("Failure: " + a.getException().toString());
                }
            });
        }
```
### FallbackTo, recover, recoverWith

#### Recover
“Recover” allows a failed call to be transformed to a different response, depending on the type of the exception. This requires a definition of a recover function that takes throwable as an argument, and returns the expected response type for the main pipeline function. Here is an example:
```
        Function<Throwable, String> recoverFunc = t -> {
            if(t instanceof TimeoutException){
                // Look for a particular exception
                return "{\"errorMessage\": \"Got TimeoutException exception, trying to recover...\"}";
            } else {
                // Must have a catch all exception
                return "{\"errorMessage\": \"Got some error, trying to recover...\"}";
            }
        };
```
The rest of the definitions are as usual:
```
        HttpClient httpClient = new HttpClient();
        httpClient.start();

        Function<String, Request> get = constructGet.apply(httpClient);

        // Construct the get request...
        Request getRequest = get.apply("http://localhost:9901/v1");
```
The recover function can be wired within the pipeline definition:
```
        Function<Request, CompletableFuture<Try<String>>> pipelineFunc =
                Pipeline.createPipeline(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                        .pipeToAsync(trySendReceive)
                        .pipeToSync(unmarshallToString())
                        .recover(recoverFunc)
                        .get();
```
The actual usage is as follow:
```
        CompletableFuture<Try<String>> result = pipelineFunc.apply(getRequest);

        result.thenAccept(a -> {
            if(a.isSuccess()){
                System.out.println("Success: " + a.get());
            } else {
                // Failure
                System.out.println("Failure: " + a.getException().toString());
            }
        });
```
A flavor of a recover function is recoverEx - it requires tuple of two arguments on the input - the exception originating from the previous function and the original parameter passed to that function. Here is an example:
```
// The input is a tuple2 of the exception and the argument passed to the function that would be recovered
Function<Tuple2<Throwable, String>, String> recoverFunc = t -> {
    if(t._1() instanceof TimeoutException){
        // Look for a particular exception
        return "{\"errorMessage\": \"Got TimeoutException, recovering... Original error: " + t._1().getMessage() +
                "; Original input: " + t._2() + "\"}";
    } else {
        // Catch all exception
        return "{\"errorMessage\": \"Got some error, recovering... Original error: " + t._1().getMessage() +
                "; Original input: " + t._2() + "\"}";
    }
};

// Composite function with recover
Function<String, CompletableFuture<Try<String>>> pipelineGetFuncWithRecover =
    Pipeline.createPipeline(tryConstructGet.apply(getHttpClient()))
                        .pipeToSync(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                        .pipeToAsync(trySendReceive)
                        .pipeToSync(unmarshallToString())
                        .recoverEx(recoverFunc)
                        .get();
```
#### RecoverWith
“RecoverWith” allows a failed call to recover with a successful value or the exception to be transformed to a different error. The recoverWith function in that case takes a throwable as a parameter, and returns Try<T> as a response. Here is a sample function:
```
        Function<Throwable, Try<String>> recoverWithFunc = t -> {
            if(t instanceof TimeoutException){
                // Transform to a predefined success value
                return Try.success("{\"action\": \"Convert error to success value...\"}");
            } else {
                // Convert to a a different exception
                return Try.failure(new RuntimeException("{\"errorMessage\": \"Convert error to some other error...\"}"));
            }
        };
```
A sample recoverWith usage is as follow:
```
        Function<Request, CompletableFuture<Try<String>>> pipelineFunc =
                Pipeline.createPipeline(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                        .pipeToAsync(trySendReceive)
                        .pipeToSync(unmarshallToString())
                        .recoverWith(recoverWithFunc)
                        .get();

        ...
        CompletableFuture<Try<String>> result = pipelineFunc.apply(getRequest);

        result.thenAccept(a -> {
            if(a.isSuccess()){
                System.out.println("Success: " + a.get());
            } else {
                // Failure
                System.out.println("Failure: " + a.getException().toString());
            }
        });
```
RecoverWithEx is a flavor of recoverWith that takes a tuple2 with the exception and the argument passed to the function to be recovered, which allows to create much richer logic. An example follows:
```
// The recoverWith function - on a particular exception type transforms the result to success, or to a different exception otherwise
Function<Tuple2<Throwable, String>, Try<String>> recoverWithFunc = t -> {
                if(t._1() instanceof TimeoutException){
                    // Look for a particular exception
                    return Try.success("{\"errorMessage\": \"Got TimeoutException, recovering with... Original error: " + t._1().getMessage() +
                            "; Original input: " + t._2() + "\"}");
                } else {
                    // Must have a catch all exception
                    return Try.failure(new RuntimeException("{\"errorMessage\": \"Convert an error to some other error... Original error: " + t._1().getMessage() +
                            "; Original input: " + t._2() + "\"}"));
                }
            };
            
// The final composite function
Function<String, CompletableFuture<Try<String>>> pipelineFunc =
    Pipeline.createPipeline(tryConstructGet.apply(getHttpClient()))
                    .pipeToSync(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                    .pipeToAsync(trySendReceive)
                    .pipeToSync(unmarshallToString())
                    .recoverWithEx(recoverWithFunc)
                    .get();
```
#### FallbackTo
“FallbackTo” allows an async function that returns a CompletableFuture to recover with another future. As an example, two destinations can be defined - primary and fallback:
```
        // Create an async HTTP client...
        HttpClient httpClient = new HttpClient();
        httpClient.start();

        //...and a get function that depends on this client
        Function<String, Request> get = constructGet.apply(httpClient);

        // Construct the get request for two separate destinations
        Request getRequestPrimary = get.apply("http://localhost:9901/v1");
        Request getRequestFallback = get.apply("http://localhost:9903/v1");
```
Then define a normal pipeline function:
```
        Function<Request, CompletableFuture<Try<String>>> pipelineFunc =
                Pipeline.createPipeline(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                        .pipeToAsync(trySendReceive)
                        .pipeToSync(unmarshallToString())
                        .get();
```
The fallbackTo function takes Throwable as an argument and returns a CompleatbleFuture:
```
        Function<Throwable, CompletableFuture<Try<String>>> fallbackToFunc = t -> {
            return pipelineFunc.apply(getRequestFallback);
        };
```
The function with a fallback can be defined like that:
```
        Function<Request, CompletableFuture<Try<String>>> pipelineFallbackTo =
                Pipeline.createAsyncPipeline(pipelineFunc)
                        .fallbackTo(fallbackToFunc)
                        .get();
```
And the actual usage is no different than the normal pipeline function call:
```
        CompletableFuture<Try<String>> result = pipelineFallbackTo.apply(getRequestPrimary);

        result.thenAccept(a -> {
            if(a.isSuccess()){
                System.out.println("Success: " + a.get());
            } else {
                // Failure
                System.out.println("Failure: " + a.getException().toString());
            }
        });
```
FallbackToEx again takes the exception and the original argument of the function that we want to recover:
```
// FallbackToEx function definition
Function<Tuple2<Throwable, String>, CompletableFuture<Try<String>>> fallbackToExFunc = t -> {
                // Do something with the exception and the original input
                ...
            };
            
// Use it in a composition
Function<Request, CompletableFuture<Try<String>>> pipelineFallbackTo =
        Pipeline.createAsyncPipeline(pipelineFunc)
                .fallbackTo(fallbackToExFunc)
                .get();
```
TODO: demonstrate compensation transactions

### Combinations
Combination between the circuit breaker and recover, fallback etc. are truly effective - for example using the circuit breaker all the calls to a problematic service can be stopped and redirected to an alternative destination, then moved back once the system recovers.

#### Calling multiple async functions(services)
With Sprinkle toolkit multiple functions can get be executed easily in a sequence or in parallel, and the result of each execution could be passed to the next function, or the successful output of all to be aggregated at the end. Here is an example on how a sequential calls will work.

Define first multiple independent functions that call remote services - in reality these functions will have unique logic, reach out to different destinations, possibly use different credentials:
```
    Function<String, CompletableFuture<Try<String>>> pipelineFuncFirst =
            Pipeline.createPipeline(tryConstructGet.apply(getHttpClient()))
                    .pipeToSync(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                    .pipeToAsync(trySendReceive)
                    .pipeToSync(unmarshallToString())
                    .get();

    Function<String, CompletableFuture<Try<String>>> pipelineFuncSecond =
            Pipeline.createPipeline(tryConstructGet.apply(getHttpClient()))
                    .pipeToSync(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                    .pipeToAsync(trySendReceive)
                    .pipeToSync(unmarshallToString())
                    .get();

    Function<String, CompletableFuture<Try<String>>> pipelineFuncThird =
            Pipeline.createPipeline(tryConstructGet.apply(getHttpClient()))
                    .pipeToSync(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                    .pipeToAsync(trySendReceive)
                    .pipeToSync(unmarshallToString())
                    .get();
```
Add functions that compose the results of the atomic functions above:
```
    Function<String, CompletableFuture<Try<Tuple2<String, String>>>> innerFunc2 = s -> {
        return composeTuple2(s, pipelineFuncSecond.apply(urlSecondary));
    };

    Function<Tuple2<String, String>, CompletableFuture<Try<Tuple3<String, String, String>>>> innerFunc3 = t -> {
        return composeTuple3(t, pipelineFuncThird.apply(urlTertiary));
    };
```
Create the final composite function, use it later:
```
    Function<String, CompletableFuture<Try<Tuple3<String, String, String>>>> pipelineFunc =
            Pipeline.createAsyncPipeline(pipelineFuncFirst)
                                .pipeToAsync(innerFunc2)
                                .pipeToAsync(innerFunc3)
                                .get();
```
#### Calling multiple async functions(services) with circuit breaker
When multiple functions are called with fallbackTo, the primary one can be protected with a circuit breaker. That way, of it fails multiple times, the circuit breaker will open and all the subsequent calls will do to the alternative function.
Here is an example of a primary and a secondary function:
```
    Function<String, CompletableFuture<Try<String>>> pipelineFuncFirst =
            Pipeline.createPipeline(tryConstructGet.apply(getHttpClient()))
                    .pipeToSync(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                    .pipeToAsync(trySendReceive)
                    .pipeToSync(unmarshallToString())
                    .get();

    Function<String, CompletableFuture<Try<String>>> pipelineFuncSecond =
            Pipeline.createPipeline(tryConstructGet.apply(getHttpClient()))
                    .pipeToSync(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                    .pipeToAsync(trySendReceive)
                    .pipeToSync(unmarshallToString())
                    .get();
```
The secondary function can easily be wrapped in what becomes a failover function:
```
    Function<Throwable, CompletableFuture<Try<String>>> recoverFunction = t -> {
        return pipelineFuncSecond.apply(urlSecondary);
    };
```
The circuit breaker definition is as follow:
```
    ExecutorService executorService = Executors.newFixedThreadPool(10);

    CircuitBreaker.CircuitBreakerStateManager circuitBreakerStateManager =
            CircuitBreaker.CircuitBreakerStateManager.createStateManager(5, Duration.ofSeconds(5), Executors.newFixedThreadPool(2));

```
The composite function definition that combines fallbackTo and the circuit breaker is as follow:
```
    private Function<String, CompletableFuture<Try<String>>> pipelineFunc =
        Pipeline.createAsyncPipeline(withCircuitBreaker(pipelineFuncFirst, executorService, circuitBreakerStateManager))
                               .fallbackTo(recoverFunction)
                               .get();
```
#### Parallel execution
Execution of multiple function in parallel is straightforward. Define the individual functions first:
```
    Function<String, CompletableFuture<Try<String>>> pipelineFuncFirst =
            Pipeline.createPipeline(tryConstructGet.apply(getHttpClient()))
                    .pipeToSync(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                    .pipeToAsync(trySendReceive)
                    .pipeToSync(unmarshallToString())
                    .get();

    Function<String, CompletableFuture<Try<String>>> pipelineFuncSecond =
            Pipeline.createPipeline(tryConstructGet.apply(getHttpClient()))
                    .pipeToSync(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                    .pipeToAsync(trySendReceive)
                    .pipeToSync(unmarshallToString())
                    .get();

    Function<String, CompletableFuture<Try<String>>> pipelineFuncThird =
            Pipeline.createPipeline(tryConstructGet.apply(getHttpClient()))
                    .pipeToSync(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                    .pipeToAsync(trySendReceive)
                    .pipeToSync(unmarshallToString())
                    .get();
```
Combine them in a single function that gets a tuple of multiple parameters and returns a tuple of the output from the functions if all successful, or the wrapped exceptions if any of them fails
```
Function<Tuple3<String, String, String>, CompletableFuture<Tuple3<Try<String>, Try<String>, Try<String>>>> pipelineFunc =
    pipelineFunc = Pipeline.par(pipelineFuncFirst, pipelineFuncSecond, pipelineFuncThird);
```
#### Extract-process-enrich pattern
Extract/process/enrich pattern in the functional programming works in the following manner: a parameter is extracted from the input, it is used to make a call to a function, and the successful result is used to enrich the original input, or to construct a completely new output.
PipelineContext holds the original input, and also the enriched output
```
    public static class PipelineContext {
        ...
    }
```
The "extract" function takes a PipelineContext as a parameter and extract a single value out of it:
```
    Function<PipelineContext, Try<String>> extractFunc = a -> {
        return Try.success(a.getUrlOne());
    };
```
The "process" async function takes the extracted parameter and produces an appropriate output:
```
    Function<String, CompletableFuture<Try<String>>> processFunc =
            Pipeline.createPipeline(tryConstructGet.apply(getHttpClient()))
                    .pipeToSync(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                    .pipeToAsync(trySendReceive)
                    .pipeToSync(unmarshallToString())
                    .get();
```
The "enrich" function gets a tuple2 of the original input and the successful results of the process function, and returns an enriched object of PipelineContext
```
    Function<Tuple2<PipelineContext, String>, Try<PipelineContext>> enrichFunc = a -> {
        // If true functional programmer, "a" should be immutable
        a._1().setResponseOne(a._2());
        return Try.success(a._1());
    };
```
ExtractProcessEnrich is a higher-order function that takes three individual functions as parameters that correspond to the three steps of the pattern.
```
    Function<PipelineContext, CompletableFuture<Try<PipelineContext>>> pipelineFunc =
        extractProcessEnrich(extractFunc, processFunc, enrichFunc);
```
#### Switch/case pattern
Switch/case pattern gets a list of tuple2 that hold a condition function that evaluates to boolean and a function that produces the useful result. Here is an example:
```
    Function<String, CompletableFuture<Try<String>>> pipelineFunc =
        switchCase(Arrays.<Tuple2<Predicate<String>, Function<String, CompletableFuture<Try<String>>>>>asList(
                            tuple(condition.apply("first"), pipelineFuncFirst),
                            tuple(condition.apply("second"), pipelineFuncSecond),
                            tuple(condition.apply("third"), pipelineFuncThird)
                        ), new RuntimeException("No matching condition"));
```
#### Sequence processing pattern
Sequence processing executes a functions one after other where the correspondent condition function matches. The signature is similar to the one in the switch/case pattern:
```
    Function<PipelineContext, CompletableFuture<Try<PipelineContext>>> pipelineFunc =
        processSequence(Arrays.<Tuple2<Predicate<PipelineContext>, Function<PipelineContext, CompletableFuture<Try<PipelineContext>>>>>asList(
                            tuple(condition.apply("first"), pipelineFuncOne),
                            tuple(condition.apply("second"), pipelineFuncTwo),
                            tuple(condition.apply("third"), pipelineFuncThree)));
```
For complete working examples take a look at [PipelineServer.java](examples/src/main/java/org/sprinkle/examples/PipelineServer.java) and at the [demo guide](DEMO.md).