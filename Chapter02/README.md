# Chapter 4: Take Tracing for a HotROD Ride

In this chapter, we are going to look at concrete examples of the diagnostic and troubleshooting tools provided by
a tracing system.The chapter will:

* Introduce HotROD, an example application provided by the Jaeger project,which is built with microservices and instrumented with the OpenTracing API (we will discuss OpenTracing in detail in Chapter 4, Instrumentation Basics with OpenTracing)
* Use Jaeger's user interface to understand the architecture and the data flow of the HotROD application
* Compare standard logging output of the application with contextual logging capabilities of distributed tracing
* Investigate and attempt to fix the root causes of latency in the application
* Demonstrate the distributed context propagation features of OpenTracing

## Meet the HotROD

HotROD is a mock-up "ride-sharing" application (ROD stands for Rides on
Demand) that is maintained by the Jaeger project. We will discuss its architecture later, but first let's try to run it. If you are using Docker, you can run it with this command:

```shell

$ docker run --rm -it \
    --link jaeger \
    -p8080-8083:8080-8083 \
    jaegertracing/example-hotrod:1.6 \
    all \
    --jaeger-agent.host-port=jaeger:6831

```

To run HotROD from downloaded binaries, run the following command:

```shell

$ example-hotrod all

```

If we are running both the Jaeger all-in-one and the HotROD application from the binaries, they bind their ports directly to the host network and are able to find each other without any additional configuration, due to the default values of the flags.

Sometimes users experience issues with getting traces from the HotROD
application due to the default UDP settings in the OS. Jaeger client libraries batch up to 65,000 bytes per UDP packet, which is still a safe number to send via the loopback interface (that is, localhost ) without packet fragmentation. However,macOS, for example, has a much lower default for the maximum datagram size.Rather than adjusting the OS settings, another alternative is to use the HTTP protocol between Jaeger clients and the Jaeger backend. This can be done by passing the following flag to the HotROD application:

```
--jaeger-agent.host-port=http://localhost:14268/api/traces

```

Or, if using the Docker networking namespace:

```

--jaeger-agent.host-port=http://jaeger:14268/api/traces

```

Once the HotROD process starts, the logs written to the standard output will show the microservices starting several servers on different ports (for better readability, we removed the timestamps and references to the source files):

```

INFO Starting all services
INFO Starting {"service": "route", "address":"http://127.0.0.1:8083"}
INFO Starting {"service": "frontend", "address":"http://127.0.0.1:8080"}
INFO Starting {"service": "customer", "address":"http://127.0.0.1:8081"}
INFO TChannel listening {"service": "driver",  "hostPort":"127.0.0.1:8082"}

```

Let's navigate to the application's web frontend at http://127.0.0.1:8080/ :


## Start Jaeger

If you are using Docker, you can run Jaeger all-in-one with the following command:

```bash

$ docker run -d --name jaeger \
    -p 6831:6831/udp \
    -p 16686:16686 \
    -p 14268:14268 \
    jaegertracing/all-in-one:1.6
    

```
Jaeger Web UI :http://localhost:16686/

## Idebtifying sources of latency

https://github.com/jaegertracing/jaeger/blob/master/examples/hotrod/services/customer/database.go

```go

    if !config.MySQLMutexDisabled {
		// simulate misconfigured connection pool that only gives one connection at a time
		d.lock.Lock(ctx)
		defer d.lock.Unlock()
	}

	// simulate RPC delay
	delay.Sleep(config.MySQLGetDelay, config.MySQLGetDelayStdDev)


```
## Reference

* [Yuri Shkuro, Take OpenTracing for a HotROD ride, May 2017](https://medium.com/opentracing/take-opentracing-for-a-hotrod-ride-f6e3141f7941)
* [Yuri Shkuro, Evolving Distributed Tracing at Uber Engineering, Uber
Engineering blog, February 2017](https://eng.uber.com/distributed-
tracing/)
* [Natasha Woods, CNCF Hosts Jaeger, Cloud Native Computing Foundation
blog, September 2017](https://www.cncf.io/blog/2017/09/13/cncf-
hosts-jaeger/)
* [The OpenTracing Authors. Semantic Conventions, The OpenTracing
Specification](https://github.com/opentracing/specification/blob/
master/semantic_conventions.md)