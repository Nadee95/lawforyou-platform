import {
  Body,
  Controller,
  HttpCode,
  HttpStatus,
  Logger,
  Post,
} from '@nestjs/common';
import { ApiAcceptedResponse, ApiBearerAuth, ApiOperation, ApiTags } from '@nestjs/swagger';
import { SendNotificationDto } from './dto/send-notification.dto';
import { NotificationService } from './notification.service';

/**
 * REST controller for manual notification triggers.
 *
 * All endpoints are prefixed with {@code /api/v1/notifications}.
 */
@ApiTags('Notifications')
@ApiBearerAuth()
@Controller('api/v1/notifications')
export class NotificationController {
  private readonly logger = new Logger(NotificationController.name);
  constructor(private readonly notificationService: NotificationService) {}

  @Post()
  @HttpCode(HttpStatus.ACCEPTED)
  @ApiOperation({ summary: 'Dispatch a notification (email or SMS)' })
  @ApiAcceptedResponse({ description: 'Notification accepted for delivery', schema: { example: { success: true, message: 'Notification dispatched to user@example.com' } } })
  async send(@Body() dto: SendNotificationDto): Promise<{ success: boolean; message: string }> {
    this.logger.log(`Manual notification request: type=${dto.type} to=${dto.to}`);
    const result = await this.notificationService.dispatch(dto);
    return {
      success: result.success,
      message: result.success
        ? `Notification dispatched to ${result.to}`
        : `Dispatch failed: ${result.error}`,
    };
  }
}
