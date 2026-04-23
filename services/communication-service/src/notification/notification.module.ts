import { Module } from '@nestjs/common';
import { EmailChannel } from './channels/email.channel';
import { SmsChannel } from './channels/sms.channel';
import { NotificationConsumer } from './consumers/notification.consumer';
import { NotificationController } from './notification.controller';
import { NotificationService } from './notification.service';
/**
 * Notification feature module.
 *
 * Provides:
 * - {@link NotificationService}  — delivery orchestration
 * - {@link EmailChannel}         — SMTP email delivery via Nodemailer
 * - {@link SmsChannel}           — SMS via Twilio (or stub)
 * - {@link NotificationConsumer} — Kafka event consumer
 * - {@link NotificationController} — REST API for manual triggers
 */
@Module({
  controllers: [NotificationConsumer, NotificationController],
  providers: [NotificationService, EmailChannel, SmsChannel],
})
export class NotificationModule {}
