# Chapter 7: Tracing with Service Mesh

This chapter illustreates how an application running on Kubernetes can be traced with the help of a service mesh Istio.
It compares the traces obtained from an application that only propagates tracing headers (a minimum requirement for
tracing via service mesh to work) with traces from an application that is explicitly instrumented with OpenTracing.
It also shows how _baggage_ (a form of distributed context propagation) can be used to inform routing decisions in Istio.


```

minikube start --cpus 4 --memory 8192 --kubernetes-version v1.13.7

kubectl cluster-info

```

or 

```

sudo -E  minikube  start  --vm-driver=none --cpus 4 --memory 8192

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
