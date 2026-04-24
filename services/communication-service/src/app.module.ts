import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import configuration from './config/configuration';
import { EurekaModule } from './eureka/eureka.module';
import { HealthModule } from './health/health.module';
import { NotificationModule } from './notification/notification.module';
/**
 * Root application module.
 *
 * Wires together:
 * - {@link ConfigModule}        — loads env vars via configuration factory
 * - {@link EurekaModule}        — registers service with Eureka on startup
 * - {@link NotificationModule}  — Kafka consumer + REST endpoint + delivery channels
 * - {@link HealthModule}        — GET /health via @nestjs/terminus
 */
@Module({
  imports: [
    ConfigModule.forRoot({
      isGlobal: true,
      load: [configuration],
    }),
    EurekaModule,
    NotificationModule,
    HealthModule,
  ],
})
export class AppModule {}
