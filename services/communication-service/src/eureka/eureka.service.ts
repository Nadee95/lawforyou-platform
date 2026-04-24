import { Injectable, Logger, OnApplicationBootstrap, OnApplicationShutdown } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { Eureka } from 'eureka-js-client';
import * as os from 'os';

/**
 * Registers the communication-service with the Eureka service registry on startup
 * and deregisters on shutdown.
 *
 * Once registered, the Spring Cloud API Gateway can route to this service using
 * {@code lb://communication-service} instead of a hardcoded URL.
 */
@Injectable()
export class EurekaService implements OnApplicationBootstrap, OnApplicationShutdown {
  private readonly logger = new Logger(EurekaService.name);
  private client: Eureka;

  constructor(private readonly config: ConfigService) {}

  onApplicationBootstrap(): void {
    const port: number   = this.config.get<number>('port');
    const eurekaHost     = this.config.get<string>('eureka.host');
    const eurekaPort     = this.config.get<number>('eureka.port');
    const ipAddr         = this.getLocalIp();
    const hostName       = os.hostname();

    this.client = new Eureka({
      instance: {
        app: 'communication-service',
        hostName,
        ipAddr,
        port: { $: port, '@enabled': true },
        vipAddress: 'communication-service',
        dataCenterInfo: {
          '@class': 'com.netflix.appinfo.InstanceInfo$DefaultDataCenterInfo',
          name: 'MyOwn',
        },
        statusPageUrl: `http://${ipAddr}:${port}/health`,
        healthCheckUrl: `http://${ipAddr}:${port}/health`,
        homePageUrl: `http://${ipAddr}:${port}/`,
      },
      eureka: {
        host: eurekaHost,
        port: eurekaPort,
        servicePath: '/eureka/apps/',
        maxRetries: 10,
        requestRetryDelay: 2000,
      },
    });


    this.client.start((error) => {
      if (error) {
        this.logger.error(`Failed to register with Eureka at ${eurekaHost}:${eurekaPort} — ${error}`);
      } else {
        this.logger.log(`Registered with Eureka at ${eurekaHost}:${eurekaPort} as communication-service (${ipAddr}:${port})`);
      }
    });
  }

  onApplicationShutdown(): void {
    if (!this.client) return;
    this.client.stop((error) => {
      if (error) {
        this.logger.warn(`Error deregistering from Eureka: ${error}`);
      } else {
        this.logger.log('Deregistered from Eureka');
      }
    });
  }

  /** Returns the first non-loopback IPv4 address of this machine. */
  private getLocalIp(): string {
    const nets = os.networkInterfaces();
    for (const name of Object.keys(nets)) {
      for (const net of nets[name]) {
        if (net.family === 'IPv4' && !net.internal) {
          return net.address;
        }
      }
    }
    return '127.0.0.1';
  }
}

