const EmailNotifications = require('./EmailNotifications');
const nodemailer = require('nodemailer');
require('dotenv').config();

exports.GetEmailById = async (emailId) => {
    var emailNotification = await EmailNotifications.getByPk(emailId);

    return emailNotification;
}

exports.SendEmail = async (emailBody) => {
    try {
        const transporter = nodemailer.createTransport({
            service: 'gmail', 
            auth: {
                user: process.env.NOTIFICATION_EMAIL,
                pass: process.env.NOTIFICATION_EMAIL_PASSWORD,   
            },
        });
    
        const mailOptions = {
            from: process.env.NOTIFICATION_EMAIL,
            to: emailBody.emailRecipient,
            subject: emailBody.topic,
            message: emailBody.message
        };
    
        transporter.sendMail(mailOptions, (error, info) => {
            if (error) {
                throw new Error("could not send email notification");
            }
        });
    
        var emailNotification = await EmailNotifications.create({UserRecipientId: emailBody.userId, EmailRecipient: emailBody.emailRecipient, EmailSent: Date.now(), EmailSubject: emailBody.topic, EmailMessage: emailBody.message});

        return emailNotification;
    } catch (error) {
        throw new Error(error.message);
    }
}

exports.DeleteEmail = async (emailId) => {
    try {
        var emailNotification = this.GetEmailById(emailId);

        if (!emailNotification) {
            throw new Error("could not delete email notification");
        }

        await emailNotification.destroy();
    } catch (error) {
        throw new Error(error.message);
    }
}