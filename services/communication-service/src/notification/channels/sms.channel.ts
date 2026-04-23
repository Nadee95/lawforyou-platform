import { Injectable, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
/**
 * SMS delivery channel.
 *
 * <p>Uses the Twilio REST API when {@code TWILIO_ACCOUNT_SID} and {@code TWILIO_AUTH_TOKEN}
 * are set.  Otherwise falls back to a no-op stub that logs the message — safe for local dev
 * without Twilio credentials.</p>
 */
@Injectable()
export class SmsChannel {
  private readonly logger = new Logger(SmsChannel.name);
  private twilioClient: any | null = null;
  constructor(private readonly config: ConfigService) {
    const { accountSid, authToken } = this.config.get('twilio');
    if (accountSid && authToken) {
      // Dynamically require so it is not a hard dependency in dev
      // eslint-disable-next-line @typescript-eslint/no-var-requires
      const Twilio = require('twilio');
      this.twilioClient = new Twilio(accountSid, authToken);
      this.logger.log('Twilio client initialised.');
    } else {
      this.logger.warn('Twilio credentials not configured — SMS will be logged only (stub mode).');
    }
  }
  /**
   * Sends an SMS.
   *
   * @param to      E.164 phone number, e.g. {@code +94771234567}
   * @param body    Message text (max 160 chars for single segment)
   */
  async send(to: string, body: string): Promise<void> {
    if (!this.twilioClient) {
      this.logger.warn(`[STUB] SMS to=${to} body="${body}"`);
      return;
    }
    const from = this.config.get<string>('twilio.fromNumber');
    const message = await this.twilioClient.messages.create({ body, from, to });
    this.logger.log(`SMS sent: sid=${message.sid} to=${to}`);
  }
}
