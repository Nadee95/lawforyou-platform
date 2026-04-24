import './tracing'; // must be first — initialises OpenTelemetry before any other module
import { ValidationPipe } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { NestFactory } from '@nestjs/core';
import { MicroserviceOptions, Transport } from '@nestjs/microservices';
import { DocumentBuilder, SwaggerModule } from '@nestjs/swagger';
import { AppModule } from './app.module';
/**
 * Bootstrap function.
 *
 * Creates a hybrid NestJS application:
 * - HTTP server on PORT (default 8084) for REST endpoints and health checks
 * - Kafka microservice listener on the {@code notification-events} topic
 */
async function bootstrap() {
  // ── HTTP application ──────────────────────────────────────────────────────
  const app = await NestFactory.create(AppModule, {
    logger: ['log', 'warn', 'error', 'debug'],
  });
  const config = app.get(ConfigService);
  const port   = config.get<number>('port');
  const brokers: string[] = config.get('kafka.brokers');
  const groupId: string   = config.get<string>('kafka.groupId');
  const clientId: string  = config.get<string>('kafka.clientId');
  // ── Global validation pipe ────────────────────────────────────────────────
  app.useGlobalPipes(
    new ValidationPipe({
      whitelist: true,
      forbidNonWhitelisted: false,
      transform: true,
    }),
  );
  // ── Swagger / OpenAPI ─────────────────────────────────────────────────────
  const swaggerConfig = new DocumentBuilder()
    .setTitle('Communication Service API')
    .setDescription('LawForYou — notification dispatch (email & SMS) via REST or Kafka events')
    .setVersion('1.0')
    .addBearerAuth()
    .build();
  const document = SwaggerModule.createDocument(app, swaggerConfig);
  SwaggerModule.setup('api/docs', app, document);

  // ── Kafka microservice (hybrid) ───────────────────────────────────────────
  app.connectMicroservice<MicroserviceOptions>({
    transport: Transport.KAFKA,
    options: {
      client: {
        clientId,
        brokers,
      },
      consumer: {
        groupId,
      },
    },
  });
  await app.startAllMicroservices();
  await app.listen(port);
  console.log(`[CommunicationService] HTTP  listening on http://localhost:${port}`);
  console.log(`[CommunicationService] Swagger UI        http://localhost:${port}/api/docs`);
  console.log(`[CommunicationService] Kafka brokers=${brokers.join(',')} group=${groupId}`);
}
bootstrap().catch((err) => {
  console.error('[CommunicationService] Failed to start:', err);
  process.exit(1);
});
