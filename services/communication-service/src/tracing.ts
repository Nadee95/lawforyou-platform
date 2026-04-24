/**
 * OpenTelemetry bootstrap — must be imported BEFORE any other module.
 *
 * Sends traces to Jaeger via the Zipkin-compatible endpoint at localhost:9411.
 * In production, set OTEL_EXPORTER_ZIPKIN_ENDPOINT env var to override.
 */
import { NodeSDK } from '@opentelemetry/sdk-node';
import { getNodeAutoInstrumentations } from '@opentelemetry/auto-instrumentations-node';
import { ZipkinExporter } from '@opentelemetry/exporter-zipkin';

const zipkinEndpoint =
  process.env.OTEL_EXPORTER_ZIPKIN_ENDPOINT ?? 'http://localhost:9411/api/v2/spans';

const sdk = new NodeSDK({
  serviceName: 'communication-service',
  traceExporter: new ZipkinExporter({ url: zipkinEndpoint }),
  instrumentations: [
    getNodeAutoInstrumentations({
      '@opentelemetry/instrumentation-fs': { enabled: false }, // too noisy
    }),
  ],
});

sdk.start();
console.log(`[Tracing] OpenTelemetry started → ${zipkinEndpoint}`);

process.on('SIGTERM', () => {
  sdk.shutdown().finally(() => process.exit(0));
});

