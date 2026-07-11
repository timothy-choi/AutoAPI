const Notification = require('./Notification');

exports.GetNotificationById = async (notificationId) => {
    var notification = await Notification.findByPk(notificationId);

    return notification;
}

exports.CreateNotification = async (notificationBody) => {
    try {
        var notification = await Notification.create({UserRecipient: notificationBody.userId, NotificationType: notificationBody.notificationType, NotificationSent: Date.now(), NotificationTopic: notificationBody.notificationTopic, NotificationMessage: notificationBody.notificationMessage});

        return notification;
    } catch (error) {
        throw new Error('Could not create notification');
    }
}

exports.SetEmailId = async (notificationId, emailId) => {
    try {
        var notification = await GetNotificationById(notificationId);

        if (!notification) {
            throw new Error('notification does not exist');
        }

        notification.NotificationEmailId = emailId;

        await notification.save();
    } catch (error) {
        throw new Error('Could not create notification');
    }
}

exports.DeleteNotification = async (notificationId) => {
    try {
        var notification = await GetNotificationById(notificationId);

        if (!notification) {
            throw new Error('notification does not exist');
        }

        await Notification.destroy();
    } catch (error) {
        throw new Error('Could not delete notification');
    }
}