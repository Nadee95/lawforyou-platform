import { Controller, Logger } from '@nestjs/common';
import { EventPattern, Payload, KafkaContext, Ctx } from '@nestjs/microservices';
import { NotificationEventDto } from '../dto/notification-event.dto';
import { NotificationService } from '../notification.service';
/**
 * Kafka consumer — listens on the {@code notification-events} topic.
 *
 * <p>All services that need to send a notification publish to this topic.
 * The consumer deserialises the event, validates it, and delegates to
 * {@link NotificationService#dispatch}.</p>
 *
 * <p>Failed deliveries are logged but do NOT cause Kafka offset commit failures —
 * dead-lettering / retry logic will be added in Phase 3.</p>
 */
@Controller()
export class NotificationConsumer {
  private readonly logger = new Logger(NotificationConsumer.name);
  constructor(private readonly notificationService: NotificationService) {}
  /**
   * Handles incoming notification events from Kafka.
   *
   * @param event    Deserialised event payload
   * @param context  Kafka context (topic, partition, offset)
   */
  @EventPattern('notification-events')
  async handleNotificationEvent(
    @Payload() event: NotificationEventDto,
    @Ctx() context: KafkaContext,
  ): Promise<void> {
    const message = context.getMessage();
    const offset = message.offset;
    this.logger.log(
      `Received notification-event: topic=notification-events offset=${offset}` +
      ` type=${event?.type} to=${event?.to}`,
    );
    if (!event || !event.type || !event.to || !event.body) {
      this.logger.warn(`Invalid notification event — skipping. payload=${JSON.stringify(event)}`);
      return;
    }
    const result = await this.notificationService.dispatch(event);
    if (!result.success) {
      this.logger.error(
        `Notification delivery failed: type=${result.channel} to=${result.to} error=${result.error}`,
      );
      // TODO Phase 3: publish to dead-letter topic for retry
    }
  }
}
