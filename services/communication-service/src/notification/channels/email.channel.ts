import { Injectable, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import * as nodemailer from 'nodemailer';
import { Transporter } from 'nodemailer';
/**
 * Email delivery channel backed by Nodemailer.
 *
 * <p>In development/test (SMTP_HOST=localhost, SMTP_PORT=1025) this connects to
 * Mailhog or any local SMTP sink — no real emails are sent.</p>
 * <p>In production set SMTP_USER/SMTP_PASS to use an authenticated relay (SendGrid, SES, etc.).</p>
 */
@Injectable()
export class EmailChannel {
  private readonly logger = new Logger(EmailChannel.name);
  private readonly transporter: Transporter;
  constructor(private readonly config: ConfigService) {
    const smtp = this.config.get('smtp');
    this.transporter = nodemailer.createTransport({
      host: smtp.host,
      port: smtp.port,
      secure: smtp.secure,
      ...(smtp.user && smtp.pass
        ? { auth: { user: smtp.user, pass: smtp.pass } }
        : {}),
    });
  }
  /**
   * Sends an email.
   *
   * @param to        Recipient email address
   * @param subject   Email subject
   * @param body      Plain-text body (HTML not required but supported)
   * @param from      Optional sender override; defaults to configured EMAIL_FROM
   */
  async send(to: string, subject: string, body: string, from?: string): Promise<void> {
    const smtpFrom = from ?? this.config.get<string>('smtp.from');
    this.logger.log(`Sending email to=${to} subject="${subject}"`);
    const info = await this.transporter.sendMail({
      from: smtpFrom,
      to,
      subject,
      text: body,
      html: `<pre style="font-family:sans-serif">${body}</pre>`,
    });
    this.logger.log(`Email sent: messageId=${info.messageId}`);
  }
}
