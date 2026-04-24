import { Module } from '@nestjs/common';
import { EurekaService } from './eureka.service';

/**
 * Handles Eureka service registration lifecycle.
 * Import into {@link AppModule} to auto-register on startup.
 */
@Module({
  providers: [EurekaService],
})
export class EurekaModule {}

