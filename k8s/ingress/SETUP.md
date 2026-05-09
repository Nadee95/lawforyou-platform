# minikube Ingress Setup

## 1. Enable the NGINX Ingress Controller

```bash
minikube addons enable ingress
minikube addons enable ingress-dns
```

Verify:
```bash
kubectl get pods -n ingress-nginx
```

## 2. Apply the namespace and ingress

```bash
kubectl apply -f k8s/namespaces/lawforyou.yaml
kubectl apply -f k8s/ingress/lawforyou-ingress.yaml
```

## 3. Expose via minikube tunnel

Run in a **separate terminal** (keep it open):
```bash
minikube tunnel
```

## 4. Add hosts entry

```bash
# Linux/macOS
echo "127.0.0.1  lawforyou.local" | sudo tee -a /etc/hosts

# Windows (PowerShell as Administrator)
Add-Content -Path "C:\Windows\System32\drivers\etc\hosts" -Value "127.0.0.1  lawforyou.local"
```

## 5. Test

```bash
curl http://lawforyou.local/api/auth/health
```

