const NotificationAccount = require('./NotificationAccount');

exports.GetNotificationAccount = async (notificationAccountId) => {
    var notificationAccount = NotificationAccount.findByPk(notificationAccountId);

    return notificationAccount;
};

exports.GetNotificationAccountByUserId = async (userId) => {
    var notificationAccount = NotificationAccount.findOne({where: {UserId: userId }});

    return notificationAccount;
};

exports.CreateNotificationAccount = async (userId) => {
    var notificationAccount = await NotificationAccount.create({UserId: userId, NotificationsOn: true});

    return notificationAccount.Id;
};

exports.UpdateNotificationsOn = async (notificationAccountId) => {
    try {
        var notificationAccount = NotificationAccount.findByPk(notificationAccountId);

        if (notificationAccount == null) {
            throw new Error("account not found");
        }

        notificationAccount.NotificationsOn = notificationAccount.NotificationsOn ? false : true;

        await notificationAccount.save();
    } catch (error) {
        throw new Error(`New Error: ${error}`);
    }
};

exports.AddNewNotification = async (notificationAccountId, notificationId) => {
    try {
        var notificationAccount = NotificationAccount.findByPk(notificationAccountId);
        if (notificationAccount == null) {
            throw new Error("No Account Found");
        }

       notificationAccount.AllNotifications.push(notificationId);

       await notificationAccount.save();
    } catch (error) {
        throw new Error(`New Error: ${error}`);
    }
}

exports.RemoveNotification = async (notificationAccountId, notificationId) => {
    try {
        var notificationAccount = NotificationAccount.findByPk(notificationAccountId);
        if (notificationAccount == null) {
            throw new Error("No Account Found");
        }

       notificationAccount.AllNotifications.remove(notificationId);

       await notificationAccount.save();
    } catch (error) {
        throw new Error(`New Error: ${error}`);
    }
}

exports.SetPushSubscription = async (notificationAccountId, pushSubscription) => {
    try {
        var notificationAccount = NotificationAccount.findByPk(notificationAccountId);
        if (notificationAccount == null) {
            throw new Error("No Account Found");
        }

       notificationAccount.PushSubscription = pushSubscription;

       await notificationAccount.save();
    } catch (error) {
        throw new Error(`New Error: ${error}`);
    }
}

exports.DeleteNotificationAccount = async (notificationAccountId) => {
    try {
        var notificationAccount = NotificationAccount.findByPk(notificationAccountId);
        if (notificationAccount == null) {
            throw new Error("No Account Found");
        }

        await notificationAccount.destroy();
    } catch (error) {
        throw new Error(`New Error: ${error}`);
    }
}