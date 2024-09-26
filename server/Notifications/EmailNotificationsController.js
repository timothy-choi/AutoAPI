const EmailNotificationsService = require('./EmailNotificationsService');

exports.GetEmailById = async (req, res) => {
    try {
        var emailNotification = await EmailNotificationsService.GetEmailById(req.emailId);

        return res.status(200).json(emailNotification);
    } catch (error) {
        return res.status(500).json({'error': error.message});
    }
}

exports.SendEmail = async (req, res) => {
    try {
        var emailNotification = await EmailNotificationsService.SendEmail(req.body);

        return res.status(201).json(emailNotification);
    } catch (error) {
        return res.status(500).json({'error': error.message});
    }
}

exports.DeleteEmail = async (req, res) => {
    try {
        await EmailNotificationsService.DeleteEmail(req.emailId);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({'error': error.message});
    }
}