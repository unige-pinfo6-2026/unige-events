{{/*
Reusable init-container that blocks the pod until kafka:9092 accepts a TCP
connection. Required because the Quarkus Kafka client constructs its
producer / consumer at boot time and calls `parseAndValidateAddresses` on
the bootstrap list ; if the DNS for `kafka` does not resolve yet (kafka pod
not READY ⇒ no endpoint behind the Service), the client throws
`No resolvable bootstrap urls given in bootstrap.servers` and the pod
enters CrashLoopBackOff. The kafka-topics-init Helm post-install hook has
its own wait loop ; this template is the equivalent for the 5 microservices
that consume / produce on those topics at startup.

Usage in a Deployment spec.template.spec :

  initContainers:
    {{- include "unige-events.waitForKafka" . | nindent 8 }}
*/}}
{{- define "unige-events.waitForKafka" -}}
- name: wait-for-kafka
  image: busybox:1.37
  command:
    - sh
    - -c
    - |
      echo "Waiting for kafka:9092 to accept TCP connections..."
      i=0
      until nc -z kafka 9092; do
        i=$((i + 1))
        if [ "$i" -gt 180 ]; then
          echo "ERROR: kafka:9092 still unreachable after 6 minutes — aborting"
          exit 1
        fi
        sleep 2
      done
      echo "kafka:9092 is reachable — proceeding"
  resources:
    requests:
      cpu: "10m"
      memory: "16Mi"
    limits:
      memory: "32Mi"
{{- end }}
