# Chapter 7: Tracing with Service Mesh

This chapter illustreates how an application running on Kubernetes can be traced with the help of a service mesh Istio.
It compares the traces obtained from an application that only propagates tracing headers (a minimum requirement for
tracing via service mesh to work) with traces from an application that is explicitly instrumented with OpenTracing.
It also shows how _baggage_ (a form of distributed context propagation) can be used to inform routing decisions in Istio.


```

minikube start --cpus 4 --memory 8192 

kubectl cluster-info

```

or 

```

sudo -E  minikube  start 

kubectl cluster-info
```

https://istio.io/docs/setup/kubernetes/install/kubernetes/

## Istio
I used Istio version 1.0.7 to run the examples in the chapter. Full installation
instructions can be found at https://istio.io/docs/setup/kubernetes/quick-
start/ . Here, I will summarize the steps I took to get it working on minikube.
First, download the release from https://github.com/istio/istio/releases/
tag/1.0.7/ . Unpack the archive and switch to the root directory of the installation,
for example, ~/Downloads/istio-1.0.7/ . Add the /bin directory to the path to
allow you to run the istioctl command later:

```bash

$ cd ~/Downloads/istio-1.0.7/
$ export PATH=$PWD/bin:$PATH

```

Install the custom resource definitions:

```bash

$ kubectl apply -f install/kubernetes/helm/istio/templates/crds.yaml

```

Install Istio without mutual TLS authentication between components:

```bash

$ kubectl apply -f install/kubernetes/istio-demo.yaml

```

Ensure that the pods are deployed and running:

```bash

$ kubectl get pods -n istio-system
NAME                                       READY   STATUS      RESTARTS   AGE
grafana-59b8896965-52l2d                  1/1     Running     0          5m4s
istio-citadel-74b8694796-l7cnz            1/1     Running     0          5m3s
istio-cleanup-secrets-hvx8z               0/1     Completed   0          5m5s
istio-egressgateway-78f4ff7cd7-qrp84      1/1     Running     0          5m4s
istio-galley-864c774fcf-5l7hz             1/1     Running     0          5m4s
istio-grafana-post-install-5594p          0/1     Completed   0          5m5s
istio-ingressgateway-58bd55686-j7t96      1/1     Running     0          5m4s
istio-pilot-7fb97c7484-vkz5p              2/2     Running     0          5m4s
istio-policy-5c46d6f859-mjxc5             2/2     Running     0          5m4s
istio-security-post-install-w2m6d         0/1     Completed   0          5m5s
istio-sidecar-injector-67bd7b75b7-lbbhx   1/1     Running     0          5m3s
istio-telemetry-7c44bc885f-qj8xv          2/2     Running     0          5m4s
istio-tracing-6b994895fd-gpcl6            1/1     Running     0          5m2s
prometheus-76b7745b64-tq4q4               1/1     Running     0          5m3s
servicegraph-6c44d7dd58-2kqtm             1/1     Running     0          5m3s


 
```


```bash
kubectl get svc istio-ingressgateway -n istio-system
NAME                   TYPE           CLUSTER-IP      EXTERNAL-IP   PORT(S)                                                                                                                   AGE
istio-ingressgateway   LoadBalancer   10.105.171.39   <pending>     80:31380/TCP,443:31390/TCP,31400:31400/TCP,15011:31171/TCP,8060:31505/TCP,853:30056/TCP,15030:30693/TCP,15031:32334/TCP   20h
```

當前EXTERNAL-IP處於pending狀態，我們目前的環境並沒有可用於Istio Ingress Gateway外部的負載均衡器，為了使得可以從外部訪問，通過修改istio-ingressgateway這個Service的externalIps，以為當前Kubernetes集群的kube-proxy啟用了ipvs，所以這個指定一個VIP 192.168.61.9作為externalIp。


```bash

kubectl edit svc istio-ingressgateway -n istio-system

......
spec:
  externalIPs:
  - 192.168.61.9

......

```

```
kubectl get svc istio-ingressgateway -n istio-system
NAME                   TYPE           CLUSTER-IP      EXTERNAL-IP    PORT(S)                                                                                                                   AGE
istio-ingressgateway   LoadBalancer   10.105.171.39   192.168.61.9   80:31380/TCP,443:31390/TCP,31400:31400/TCP,15011:31171/TCP,8060:31505/TCP,853:30056/TCP,15030:30693/TCP,15031:32334/TCP   20h

```
此時EXTERNAL-IP已經設置為192.168.61.9這個VIP了，http相關的端口為80和443

需要注意的是istio-ingressgateway對port轉換的設定，這邊HTTP2是使用port 80 轉成31380，如果測試程式是使用port 8080，也是需要下指令 ( kubectl edit svc istio-ingressgateway -n istio-system ) 進行修改

```bash
$ kubectl edit svc istio-ingressgateway -n istio-system
```

content

```yaml

apiVersion: v1
kind: Service
...
spec:
  clusterIP: 10.110.188.50
  externalTrafficPolicy: Cluster
  ports:
  - name: http2
    nodePort: 31380
    port: 8080
    protocol: TCP
    targetPort: 8080
  - name: https
    nodePort: 31390
    port: 443
    protocol: TCP
    targetPort: 443
....
  selector:
    app: istio-ingressgateway
    istio: ingressgateway
  sessionAffinity: None
  type: LoadBalancer
status:
  loadBalancer: {}

```

# The Hello application

## Distributed tracing with Istio
We are now ready to run the Hello application. First, we need to build a Docker image, so that we can deploy it to Kubernetes. The build process will store the image in the local Docker registry, but that's not good since minikube is run entirely in a virtual machine and we need to push the image to the image registry in that installation. Therefore, we need to define some environment variables to instruct Docker where to push the build. This can be done with the following command:


```bash
$ eval $(minikube docker-env)

```
After that, we can build the application:


```bash
$ make build-app
mvn install
[INFO] Scanning for projects...
[... skipping lots of logs ...]
[INFO] BUILD SUCCESS
[INFO] -----------------------------------------------------------------
docker build -t hello-app:latest .
Sending build context to Docker daemon 44.06MB
Step 1/7 : FROM openjdk:alpine
[... skipping lots of logs ...]
Successfully built 67659c954c30
Successfully tagged hello-app:latest
*** make sure the right docker repository is used
*** on minikube run this first: eval $(minikube docker-env)

```

We added a few help messages at the end to remind you to build against the
right Docker registry. After the build is done, we can deploy the application:



```bash
$ make deploy-app

```

The make target executes these commands:
deploy-app:



```bash
istioctl kube-inject -f app.yml | kubectl apply -f -
kubectl apply -f gateway.yml
istioctl create -f routing.yml

```

The first one instructs Istio to decorate our deployment instructions in app.yml with the sidecar integration, and applies the result. The second command configures the ingress path, so that we can access the hello service from outside of the networking namespace created for the application. The last command adds some extra routing based on the request headers, which we will discuss later in this chapter.

To verify that the services have been deployed successfully, we can list the running pods:



```bash
$ kubectl get pods
NAME                                READY   STATUS    RESTARTS   AGE
formatter-svc-v1-5dd5774dbf-94v7p   2/2     Running   0          12h
formatter-svc-v2-6cff8d65b9-zrjsz   2/2     Running   0          12h
hello-svc-6b5c88f594-f2pxb          2/2     Running   0          12h
$

```

As expected, we see the hello service and two versions of the formatter service.
In case you run into issues deploying the application, the Makefile includes useful
targets to get the logs from the pods:

```bash
$ make logs-hello
$ make logs-formatter-v1
$ make logs-formatter-v2


```

We are almost ready to access the application via curl, but first we need to get the address of the Istio ingress endpoint. I have defined a helper target in the Makefile
for that:


```bash
$ make hostport
export GATEWAY_URL=192.168.99.103:31380

```
Either execute the export command manually or run


```bash
eval $(make hostport).
```
Then use the GATEWAY_URL variable to send a request to the application using curl:



```bash
$ curl http://$GATEWAY_URL/sayHello/Brian
Hello, puny human Brian! Morbo asks: how do you like running on
Kubernetes?

```
or


```bash

[Chapter07]$ curl http://$GATEWAY_URL/sayHello/Brian

<!doctype html><html lang="en"><head><title>HTTP Status 500 – Internal Server Error</title><style type="text/css">h1 {font-family:Tahoma,Arial,sans-serif;color:white;background-color:#525D76;font-size:22px;} h2 {font-family:Tahoma,Arial,sans-serif;color:white;background-color:#525D76;font-size:16px;} h3 {font-family:Tahoma,Arial,sans-serif;color:white;background-color:#525D76;font-size:14px;} body {font-family:Tahoma,Arial,sans-serif;color:black;background-color:white;} b {font-family:Tahoma,Arial,sans-serif;color:white;background-color:#525D76;} p {font-family:Tahoma,Arial,sans-serif;background:white;color:black;font-size:12px;} a {color:black;} a.name {color:black;} .line {height:1px;background-color:#525D76;border:none;}</style></head><body><h1>HTTP Status 500 – Internal Server Error</h1></body></html>

[Chapter07]$
```

 
if yuo encounter the porlem,you coould try

```bash
$ make logs-hello
$ make logs-formatter-v1
$ make logs-formatter-v2


```

for Retries :


```bash
$  make   delete-app
$  kubectl delete  service hello-svc formatter-svc
$  kubectl get   service
$  kubectl delete pods --all

```

DashBoard:

```bash

minikube  dashboard

```
## NodePort

This way of accessing Dashboard is only recommended for development environments in a single node setup. 

Edit `kubernetes-dashboard` service.
```sh
$ kubectl -n kube-system edit service kubernetes-dashboard
```

You should see `yaml` representation of the service. Change `type: ClusterIP` to `type: NodePort` and save file. If it's already changed go to next step.
```yaml
# Please edit the object below. Lines beginning with a '#' will be ignored,
# and an empty file will abort the edit. If an error occurs while saving this file will be
# reopened with the relevant failures.
#
apiVersion: v1
...
  name: kubernetes-dashboard
  namespace: kube-system
  resourceVersion: "343478"
  selfLink: /api/v1/namespaces/kube-system/services/kubernetes-dashboard-head
  uid: 8e48f478-993d-11e7-87e0-901b0e532516
spec:
  clusterIP: 10.100.124.90
  externalTrafficPolicy: Cluster
  ports:
  - port: 443
    protocol: TCP
    targetPort: 8443
  selector:
    k8s-app: kubernetes-dashboard
  sessionAffinity: None
  type: ClusterIP
status:
  loadBalancer: {}
```

Next we need to check port on which Dashboard was exposed.
```sh
$ kubectl -n kube-system get service kubernetes-dashboard
NAME                   CLUSTER-IP       EXTERNAL-IP   PORT(S)        AGE
kubernetes-dashboard   10.100.124.90   <nodes>       443:31707/TCP   21h
```

Dashboard has been exposed on port `31707 (HTTPS)`. Now you can access it from your browser at: `https://<master-ip>:31707`. `master-ip` can be found by executing `kubectl cluster-info`. Usually it is either `127.0.0.1` or IP of your machine, assuming that your cluster is running directly on the machine, on which these commands are executed.

In case you are trying to expose Dashboard using NodePort on a multi-node cluster, then you have to find out IP of the node on which Dashboard is running to access it. Instead of accessing `https://<master-ip>:<nodePort>` you should access `https://<node-ip>:<nodePort>`.


As you can see, the application is working. Now it's time to look at the trace collected from this request. The Istio demo we installed includes Jaeger installation, but it is running in the virtual machine and we need to set up port forwarding to access it from the local host. Fortunately, I have included another Makefile target for that:
