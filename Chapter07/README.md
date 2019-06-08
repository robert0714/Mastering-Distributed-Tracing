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
需要注意的是istio-ingressgateway對port轉換的設定，這邊HTTP2是使用port 80 轉成31380，如果測試程式是使用port 8080，也是需要下指令 ( kubectl edit svc istio-ingressgateway -n istio-system ) 進行修改

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
