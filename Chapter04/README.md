# Chapter 4: Instrumentation Basics with OpenTracing

This chapter contains a step-by-step walk-through of instrumenting a simple application for tracing while evolving it from a monolith to a microservices-based application. The examples are provided in three programming languages (Go, Java, and Python), to illustrate the language-specific differences of applying the concepts of the OpenTracing APIs.

* Exercise 1: The Hello application
* Exercise 2: The first trace
* Exercise 3: Tracing functions and passing context
  * 3a) tracing individual functions
  * 3b) combining spans into a single trace
  * 3c) propagating context in-process
* Exercise 4: Tracing RPC requests
  * 4a) breaking up the monolith
  * 4b) passing context between processes
  * 4c) applying OpenTracing-recommended tags
* Exercise 5: Using "baggage"
* Exercise 6: Applying open-source auto-instrumentation

## Mysql database

```

$ docker-compose up -d

$ docker exec -i mysql mysql -uroot -pmysqlpwd <  database.sql

$ docker cp database.sql mysql:/

$ docker exec -it mysql /bin/bash
bash-4.2# cd /
bash-4.2# ls

bash-4.2# mysql -uroot -pmysqlpwd
Warning: Using a password on the command line interface can be insecure.
Welcome to the MySQL monitor.  Commands end with ; or \g.
Your MySQL connection id is 21
Server version: 5.6.44 MySQL Community Server (GPL)

Copyright (c) 2000, 2019, Oracle and/or its affiliates. All rights reserved.

Oracle is a registered trademark of Oracle Corporation and/or its
affiliates. Other names may be trademarks of their respective
owners.

Type 'help;' or '\h' for help. Type '\c' to clear the current input statement.

mysql> source database.sql
Query OK, 1 row affected (0.00 sec)
mysql> GRANT ALL ON chapter04.* TO 'root'@'127.0.0.1' IDENTIFIED BY 'mysqlpwd';
Query OK, 0 rows affected (0.00 sec)
```

Run Jaeger

```bash

$ docker run -d --name jaeger \
    -p 6831:6831/udp \
    -p 16686:16686 \
    -p 14268:14268 \
    jaegertracing/all-in-one:1.6

```

## Exercise 1 - the Hello application

In the first exercise, we are going to run a simple, single-process "Hello" application,and review its source code, so that we can later instrument it for distributed tracing.The application is implemented as a web service, which we can access by sending it HTTP requests like this:

```bash

$ curl http://localhost:8080/sayHello/John
Hello, John!
$ curl http://localhost:8080/sayHello/Margo
Hello, Margo!

```


The application has some creepy big brother tendencies, however, by occasionally volunteering additional knowledge about the person:

```bash

$ curl http://localhost:8080/sayHello/Vector
Hello, Vector! Committing crimes with both direction and magnitude!
$ curl http://localhost:8080/sayHello/Nefario
Hello, Dr. Nefario! Why ... why are you so old?

```


It looks up the information in the MySQL database that we created and seeded earlier. In the later exercises, we will extend this application to run several microservices.