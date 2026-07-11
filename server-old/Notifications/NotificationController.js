const NotificationService = require('./NotificationService');

exports.GetNotificationById = async (req, res) => {
    try {
        var notification = await NotificationService.GetNotificationById(req.notificationId);

        return res.status(200).json(notification);
    } catch (error) {
        return res.status(500).json({'error': error.message});
    }
}

exports.CreateNotification = async (req, res) => {
    try {
        var notification = await NotificationService.CreateNotification(req.body);

        return res.status(201).json(notification);
    } catch (error) {
        return res.status(500).json({'error': error.message});
    }
}

exports.SetEmailId = async (req, res) => {
    try {
        await NotificationService.SetEmailId(req.notificationId, req.emailId);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({'error': error.message});
    }
}

exports.DeleteNotification = async (req, res) => {
    try {
        await NotificationService.DeleteNotification(req.notificationId);

        return res.status(200).json(null);
    } catch (error) {
        return res.status(500).json({'error': error.message});
    }
}