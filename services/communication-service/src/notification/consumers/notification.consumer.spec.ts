import { Test, TestingModule } from '@nestjs/testing';
import { KafkaContext } from '@nestjs/microservices';
import { NotificationConsumer } from './notification.consumer';
import { NotificationService } from '../notification.service';
import { NotificationChannel as NC, NotificationEventDto } from '../dto/notification-event.dto';
/** Minimal KafkaContext stub matching what the consumer reads. */
function kafkaCtx(partition = 0, offset = '0'): KafkaContext {
  return {
    getMessage: () => ({ partition, offset }),
  } as unknown as KafkaContext;
}
describe('NotificationConsumer', () => {
  let consumer: NotificationConsumer;
  let notificationService: jest.Mocked<NotificationService>;
  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      controllers: [NotificationConsumer],
      providers: [
        {
          provide: NotificationService,
          useValue: {
            dispatch: jest.fn().mockResolvedValue({ success: true, channel: NC.EMAIL, to: 'x@x.com' }),
          },
        },
      ],
    }).compile();
    consumer            = module.get(NotificationConsumer);
    notificationService = module.get(NotificationService);
  });
  it('should dispatch valid event to NotificationService', async () => {
    const event: NotificationEventDto = {
      type:     NC.EMAIL,
      to:       'client@example.com',
      subject:  'Case assigned',
      body:     'Your case has been assigned.',
      tenantId: '00000000-0000-0000-0000-000000000001',
    };
    await consumer.handleNotificationEvent(event, kafkaCtx());
    expect(notificationService.dispatch).toHaveBeenCalledWith(event);
  });
  it('should skip invalid event without throwing', async () => {
    await consumer.handleNotificationEvent(null as any, kafkaCtx());
    expect(notificationService.dispatch).not.toHaveBeenCalled();
  });
  it('should log error but not throw when dispatch fails', async () => {
    notificationService.dispatch.mockResolvedValueOnce({
      success: false,
      channel: NC.EMAIL,
      to: 'bad@example.com',
      error: 'SMTP timeout',
    });
    const event: NotificationEventDto = {
      type: NC.EMAIL, to: 'bad@example.com', body: 'Msg', tenantId: '00000000-0000-0000-0000-000000000001',
    };
    // Should not throw
    await expect(consumer.handleNotificationEvent(event, kafkaCtx())).resolves.toBeUndefined();
  });
});
