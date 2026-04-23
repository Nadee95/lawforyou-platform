import { IsEnum, IsNotEmpty, IsOptional, IsString, IsUUID } from 'class-validator';
/** Notification channel — determines which delivery channel is used. */
export enum NotificationChannel {
  EMAIL = 'EMAIL',
  SMS   = 'SMS',
}
/**
 * Shape of the Kafka event consumed from the {@code notification-events} topic.
 * Other services publish events matching this contract.
 *
 * Example (email):
 * ```json
 * {
 *   "type": "EMAIL",
 *   "to": "client@example.com",
 *   "subject": "Case assigned",
 *   "body": "Your case has been assigned to Lawyer Smith.",
 *   "tenantId": "uuid",
 *   "correlationId": "uuid",
 *   "occurredAt": "2026-04-23T10:00:00Z"
 * }
 * ```
 */
export class NotificationEventDto {
  /** Channel to use for delivery. */
  @IsEnum(NotificationChannel)
  type: NotificationChannel;
  /** Recipient — email address (EMAIL) or E.164 phone number (SMS). */
  @IsString()
  @IsNotEmpty()
  to: string;
  /** Email subject (EMAIL only — ignored for SMS). */
  @IsOptional()
  @IsString()
  subject?: string;
  /** Message body — plain text or HTML for email. */
  @IsString()
  @IsNotEmpty()
  body: string;
  /** Tenant that originated this notification. */
  @IsUUID('all')
  tenantId: string;
  /** Correlation ID propagated from the originating request. */
  @IsOptional()
  @IsUUID('all')
  correlationId?: string;
  /** ISO-8601 timestamp when the event occurred. */
  @IsOptional()
  @IsString()
  occurredAt?: string;
}
