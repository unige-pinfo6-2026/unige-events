# Guide d'installation de MicroK8S

1. Installer la distribution MicroK8S : Suivre les instructions depuis la page d'installation officielle: https://canonical.com/microk8s#install-microk8s

2. Activer les addons nécessaires : `microk8s enable dns storage ingress cert-manager`

3. Créer un alias kubectl : `sudo snap alias microk8s.kubectl kubectl`

4. Créer le namespace : `kubectl create namespace unige-events`

5. Récupérer la configuration `KUBE_CONFIG` : copier la sortie de `microk8s config`