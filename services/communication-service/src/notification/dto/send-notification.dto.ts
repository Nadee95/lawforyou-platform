import { IsEnum, IsNotEmpty, IsOptional, IsString, IsUUID } from 'class-validator';
import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import { NotificationChannel } from './notification-event.dto';
/**
 * Request body for the manual {@code POST /api/v1/notifications} endpoint.
 * Allows other services (or operators) to trigger a notification without Kafka.
 */
export class SendNotificationDto {
  @ApiProperty({ enum: NotificationChannel, example: NotificationChannel.EMAIL })
  @IsEnum(NotificationChannel)
  type: NotificationChannel;

  @ApiProperty({ example: 'user@example.com', description: 'Email address or E.164 phone number' })
  @IsString()
  @IsNotEmpty()
  to: string;

  @ApiPropertyOptional({ example: 'Your case has been updated' })
  @IsOptional()
  @IsString()
  subject?: string;

  @ApiProperty({ example: 'Dear client, your case status changed to IN_REVIEW.' })
  @IsString()
  @IsNotEmpty()
  body: string;

  @ApiProperty({ example: '550e8400-e29b-41d4-a716-446655440000' })
  @IsUUID('all')
  tenantId: string;

  @ApiPropertyOptional({ example: '6ba7b810-9dad-11d1-80b4-00c04fd430c8' })
  @IsOptional()
  @IsUUID('all')
  correlationId?: string;
}
