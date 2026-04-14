# Guide d'installation de MicroK8S

1. Installer la distribution MicroK8S : Suivre les instructions depuis la page d'installation officielle: https://canonical.com/microk8s#install-microk8s

2. Installer Docker :
```bash
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER
newgrp docker
```

3. Activer les addons nécessaires : `microk8s enable dns storage ingress cert-manager`

4. Créer un alias kubectl : `sudo snap alias microk8s.kubectl kubectl`

5. Créer le namespace : `kubectl create namespace unige-events`

6. Récupérer la configuration `KUBE_CONFIG` : copier la sortie de `microk8s config`

7. Installer le webhook cert-manager pour DuckDNS (DNS-01 challenge Let's Encrypt) :

```bash
git clone https://github.com/ebrianne/cert-manager-webhook-duckdns.git
cd cert-manager-webhook-duckdns

microk8s helm install cert-manager-webhook-duckdns \
  --namespace cert-manager \
  --set duckdns.token='placeholder' \
  --set clusterIssuer.production.create=false \
  --set clusterIssuer.staging.create=false \
  ./deploy/cert-manager-webhook-duckdns
```

> Le token DuckDNS est géré via le secret `dns-secret` dans le namespace `cert-manager`, créé automatiquement par le CD à partir du secret GitHub `DUCKDNS_TOKEN`. Le `placeholder` ci-dessus est requis par le chart mais n'est pas utilisé.

8. Vérifier que le webhook et cert-manager tournent correctement :

```bash
kubectl get pods -n cert-manager
kubectl get certificate -n unige-events
```