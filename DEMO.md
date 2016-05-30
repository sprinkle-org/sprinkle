Demo of the capabilities of the Sprinkle toolkit
========
Run the demo server with the following command form the root directory (make sure nothing runs on the local ports 8080 and 9901 to 9907):
```
     gradle runExamples
```
 Use any of the following commands to demo the various capabilities:
```
     curl -v http://localhost:8080/simpleGet/
     curl -H "Content-type: application/json" -v -X POST -d '{"body": "this is a request body"}' http://localhost:8080/simplePost/
```
 Send the following multiple times and observe the timestamp in the response remains the same for some period, because coming from the cache:
```
     curl -v http://localhost:8080/cache/
```
 The query parameter is the key used to cache the response content, change the key to observe how it affects the response:
```
     curl -v http://localhost:8080/cacheWithKey/?key=100
```
 Alternative/shorter implementation of the above:
```
     curl -v http://localhost:8080/cacheWithKeyHiddenCache/?key=100
```
 Try the following multiple times - the call goes to non-existent backend - and observe the circuit breaker changing to closed/open/half-open state:
```
     curl -v http://localhost:8080/circuitBreaker/
```
 Run the following, which generates load against the circuit breaker configuration and observe changing from closes to half-open to closed:
```
     curl -v http://localhost:8080/circuitBreakerLoad/
```
 To demo recover, recoverWith and fallbackTo run the following commands:
```
     curl -v http://localhost:8080/recover/
     curl -v http://localhost:8080/recoverEx/
     curl -v http://localhost:8080/recoverWith/
     curl -v http://localhost:8080/recoverWithEx/
     curl -v http://localhost:8080/fallbackTo/
     curl -v http://localhost:8080/fallbackToEx/
     curl -v http://localhost:8080/multipleFallbackTo/
```
 Execute the following to demonstrate calling multiple services in sequence and composing the result:
```
     curl -v http://localhost:8080/multipleServices/
     curl -v http://localhost:8080/multipleServices2/
```
 Demonstrate calling multiple services protected with circuit breaker:
```
     curl -v http://localhost:8080/multipleServicesCircuitBreaker/
```
 Execute multiple services in parallel:
```
     curl -v http://localhost:8080/parallel/
     curl -v http://localhost:8080/parallelCompose/
```
 Demonstrate various functional patterns:
```
     curl -v http://localhost:8080/extractProcessEnrich/
     curl -v http://localhost:8080/switchCase/?target=first
     curl -v http://localhost:8080/switchCase/?target=second
     curl -v http://localhost:8080/switchCase/?target=third
     curl -v http://localhost:8080/processSequence/?ignore=first
     curl -v http://localhost:8080/processSequence/?ignore=second
     curl -v http://localhost:8080/processSequence/?ignore=third
     curl -v http://localhost:8080/normalize/
```
Demonstrate truly complex orchestrations of six services with fallback, recovery and parallel processing. 
You can disable/enable the simulator services on the various ports to see how the composition behaves
```
     curl -v http://localhost:8080/complexOrchestration/?id=123
     curl -v http://localhost:8080/complexOrchestrationOptimized/?id=123
```
Demonstrate constricting a fallback composition with circuit breaker
```
     curl -v http://localhost:8080/fallbackCircuitBreaker/?id=123
```