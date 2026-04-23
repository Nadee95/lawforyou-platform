import { Injectable, Logger } from '@nestjs/common';
import { EmailChannel } from './channels/email.channel';
import { SmsChannel } from './channels/sms.channel';
import { NotificationChannel, NotificationEventDto } from './dto/notification-event.dto';
import { SendNotificationDto } from './dto/send-notification.dto';
/** Result returned after attempting to deliver a notification. */
export interface NotificationResult {
  success: boolean;
  channel: NotificationChannel;
  to: string;
  error?: string;
}
/**
 * Core notification orchestration service.
 *
 * <p>Receives a {@link NotificationEventDto} (from Kafka or REST) and routes it to
 * the appropriate delivery channel ({@link EmailChannel} or {@link SmsChannel}).</p>
 */
@Injectable()
export class NotificationService {
  private readonly logger = new Logger(NotificationService.name);
  constructor(
    private readonly emailChannel: EmailChannel,
    private readonly smsChannel: SmsChannel,
  ) {}
  /**
   * Dispatches a notification to the correct channel.
   *
   * @param dto  The notification payload (from Kafka event or REST request)
   * @returns    A result object indicating success or failure
   */
  async dispatch(dto: NotificationEventDto | SendNotificationDto): Promise<NotificationResult> {
    this.logger.log(
      `Dispatching notification: type=${dto.type} to=${dto.to} tenant=${dto.tenantId}`,
    );
    try {
      if (dto.type === NotificationChannel.EMAIL) {
        await this.emailChannel.send(
          dto.to,
          (dto as NotificationEventDto).subject ?? 'LawForYou Notification',
          dto.body,
        );
      } else if (dto.type === NotificationChannel.SMS) {
        await this.smsChannel.send(dto.to, dto.body);
      } else {
        throw new Error(`Unsupported notification channel: ${dto.type}`);
      }
      this.logger.log(`Notification delivered: type=${dto.type} to=${dto.to}`);
      return { success: true, channel: dto.type, to: dto.to };
    } catch (err) {
      this.logger.error(
        `Failed to deliver notification: type=${dto.type} to=${dto.to} error=${err.message}`,
        err.stack,
      );
      return { success: false, channel: dto.type, to: dto.to, error: err.message };
    }
  }
}
