# Chapter 5: Instrumentation of Asynchronous Applications

In this chapter, we attempt to instrument an online chat application, "Tracing Talk", 
which uses asynchronous messaging-based interactions between microservices built on top of Apache Kafka.
We see how metadata context can be passed through messaging systems using the OpenTracing primitives, 
and how causal relationships between spans can be modeled differently than in the plain RPC scenarios.

The chapter also illustrates how in-process context propagation can be achieved automatically even when
using asynchronous programming model based on Futures.

## Demo
* [Mahmoud (Moody) Saada's Demo](https://github.com/saada/tracing-kafka)
Once the application is running, we will be able to access its web frontend at  http://localhost:8080/ .
* The JavaScript frontend is implemented with React ( https://reactjs.org/ ). The static assets are served by the chat-api microservice running on port 8080 . The frontend polls for all messages every couple of seconds, which is not the most efficient way of implementing the chat application, but keeps it simple to allow us to focus on the backend messaging.
* The chat-api microservice receives API calls from the frontend to record new messages or to retrieve all already-accumulated messages. It publishes new messages to a Kafka topic, as a way of asynchronous communication with the other microservices. It reads the accumulated messages from Redis, where they are stored by the storage-service microservice.
* The storage-service microservice reads messages from Kafka and stores them in Redis in a set.
* The giphy-service microservice also reads messages from Kafka and checks whether they start with a /giphy <topic> string. The internal message structure has an image field and if that field is empty, the service makes a remote HTTP call to the giphy.com REST API, querying for 10 images related to the <topic>. Then the service picks one of them randomly, stores its URL in the image field of the message, and publishes the message to the same Kafka topic, where it will be again picked up by the storage-service
microservice and updated in Redis.
* The Apache Zookeeper is used internally by Kafka to keep the state of the topics and subscriptions.
