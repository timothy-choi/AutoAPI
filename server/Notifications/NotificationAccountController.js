const NotificationAccountService = require('./NotificationAccountService');

exports.GetNotificationAccount = async (req, res) => {
    try {
        var notificationAccount = await NotificationAccountService.GetNotificationAccount(req.notificationAccountId);

        return res.status(200).json({'notificationAccount': notificationAccount});
    } catch (error) {
        return res.status(500).json({'error': error});
    }
};

exports.GetNotificationAccountByUserId = async (req, res) => {
    try {
        var notificationAccount = await NotificationAccountService.GetNotificationAccountByUserId(req.userId);

        return res.status(200).json({'notificationAccount': notificationAccount});
    } catch (error) {
        return res.status(500).json({'error': error});
    }
};

exports.CreateNotificationAccount = async (req, res) => {
    try {
        var notificationAccountId = await NotificationAccountService.CreateNotificationAccount(req.body.userId);

        return res.status(202).json({'notificationAccountId': notificationAccountId});
    } catch (error) {
        return res.status(500).json({'error': error});
    }
};

exports.UpdateNotificationsOn = async (req, res) => {
    try {
        await NotificationAccountService.UpdateNotificationsOn(req.notificationAccountId);

        return res.status(202).json({'success': true});
    } catch (error) {
        return res.status(500).json({'error': error});
    }
};

exports.AddNewNotification = async (req, res) => {
    try {
        await NotificationAccountService.AddNewNotification(res.notificationAccountId, res.notificationId);

        return res.status(202).json({'success': true});
    } catch (error) {
        return res.status(500).json({'error': error});
    }
};

exports.DeleteNotificationAccount = async (req, res) => {
    try {
        await NotificationAccountService.DeleteNotificationAccount(req.notificationAccountId);

        return res.status(202).json({'success': true});
    } catch (error) {
        return res.status(500).json({'error': error});
    }
};
