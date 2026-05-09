{{/*
Expand the name of the chart.
*/}}
{{- define "eureka-server.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
*/}}
{{- define "eureka-server.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- include "eureka-server.name" . }}
{{- end }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "eureka-server.labels" -}}
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version }}
app.kubernetes.io/name: {{ include "eureka-server.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "eureka-server.selectorLabels" -}}
app.kubernetes.io/name: {{ include "eureka-server.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

