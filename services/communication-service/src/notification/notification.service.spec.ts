import { Test, TestingModule } from '@nestjs/testing';
import { NotificationChannel, NotificationEventDto } from '../notification/dto/notification-event.dto';
import { SendNotificationDto } from '../notification/dto/send-notification.dto';
import { EmailChannel } from '../notification/channels/email.channel';
import { SmsChannel } from '../notification/channels/sms.channel';
import { NotificationService } from '../notification/notification.service';
describe('NotificationService', () => {
  let service: NotificationService;
  let emailChannel: jest.Mocked<EmailChannel>;
  let smsChannel: jest.Mocked<SmsChannel>;
  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        NotificationService,
        {
          provide: EmailChannel,
          useValue: { send: jest.fn().mockResolvedValue(undefined) },
        },
        {
          provide: SmsChannel,
          useValue: { send: jest.fn().mockResolvedValue(undefined) },
        },
      ],
    }).compile();
    service      = module.get(NotificationService);
    emailChannel = module.get(EmailChannel);
    smsChannel   = module.get(SmsChannel);
  });
  describe('dispatch — EMAIL', () => {
    it('should delegate to EmailChannel and return success', async () => {
      const dto: NotificationEventDto = {
        type:          NotificationChannel.EMAIL,
        to:            'client@example.com',
        subject:       'Case assigned',
        body:          'Your case has been assigned.',
        tenantId:      '00000000-0000-0000-0000-000000000001',
        correlationId: '00000000-0000-0000-0000-000000000002',
        occurredAt:    new Date().toISOString(),
      };
      const result = await service.dispatch(dto);
      expect(emailChannel.send).toHaveBeenCalledWith(
        'client@example.com',
        'Case assigned',
        'Your case has been assigned.',
      );
      expect(smsChannel.send).not.toHaveBeenCalled();
      expect(result.success).toBe(true);
      expect(result.channel).toBe(NotificationChannel.EMAIL);
    });
    it('should use default subject when none provided', async () => {
      const dto: NotificationEventDto = {
        type:     NotificationChannel.EMAIL,
        to:       'client@example.com',
        body:     'Hello.',
        tenantId: '00000000-0000-0000-0000-000000000001',
      };
      await service.dispatch(dto);
      expect(emailChannel.send).toHaveBeenCalledWith(
        'client@example.com',
        'LawForYou Notification',
        'Hello.',
      );
    });
    it('should return failure result when EmailChannel throws', async () => {
      emailChannel.send.mockRejectedValueOnce(new Error('SMTP connection refused'));
      const dto: NotificationEventDto = {
        type:     NotificationChannel.EMAIL,
        to:       'bad@example.com',
        body:     'Test',
        tenantId: '00000000-0000-0000-0000-000000000001',
      };
      const result = await service.dispatch(dto);
      expect(result.success).toBe(false);
      expect(result.error).toContain('SMTP connection refused');
    });
  });
  describe('dispatch — SMS', () => {
    it('should delegate to SmsChannel and return success', async () => {
      const dto: SendNotificationDto = {
        type:     NotificationChannel.SMS,
        to:       '+94771234567',
        body:     'Your case has been assigned.',
        tenantId: '00000000-0000-0000-0000-000000000001',
      };
      const result = await service.dispatch(dto);
      expect(smsChannel.send).toHaveBeenCalledWith('+94771234567', 'Your case has been assigned.');
      expect(emailChannel.send).not.toHaveBeenCalled();
      expect(result.success).toBe(true);
    });
    it('should return failure result when SmsChannel throws', async () => {
      smsChannel.send.mockRejectedValueOnce(new Error('Twilio auth failed'));
      const dto: SendNotificationDto = {
        type:     NotificationChannel.SMS,
        to:       '+0000000000',
        body:     'Test message',
        tenantId: '00000000-0000-0000-0000-000000000001',
      };
      const result = await service.dispatch(dto);
      expect(result.success).toBe(false);
      expect(result.error).toContain('Twilio auth failed');
    });
  });
});
