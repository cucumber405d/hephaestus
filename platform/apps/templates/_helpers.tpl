{{/* Common sync policy: Git is the desired state, drift is reverted, deleted manifests are pruned. */}}
{{- define "platform.syncPolicy" -}}
syncPolicy:
  automated:
    prune: true
    selfHeal: true
  syncOptions:
    - CreateNamespace=true
    # Large CRDs (Envoy Gateway, Strimzi) exceed the client-side last-applied annotation limit
    - ServerSideApply=true
    # Custom resources whose CRD is installed by another Application (an earlier wave)
    - SkipDryRunOnMissingResource=true
  retry:
    limit: 10
    backoff:
      duration: 10s
      factor: 2
      maxDuration: 3m
{{- end }}

{{/* An Application for a directory of plain manifests under platform/components. */}}
{{- define "platform.componentApp" -}}
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: {{ .name }}
  namespace: argocd
  annotations:
    argocd.argoproj.io/sync-wave: {{ .wave | quote }}
  finalizers:
    - resources-finalizer.argocd.argoproj.io
spec:
  project: default
  source:
    repoURL: {{ .root.Values.repoURL }}
    targetRevision: {{ .root.Values.targetRevision }}
    path: platform/components/{{ .path }}
  destination:
    server: https://kubernetes.default.svc
    namespace: {{ .namespace }}
  {{- include "platform.syncPolicy" . | nindent 2 }}
{{- end }}
