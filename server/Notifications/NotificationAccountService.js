const NotificationAccount = require('./NotificationAccount');

exports.GetNotificationAccount = async (notificationId) => {
    var notificationAccount = NotificationAccount.findByPk(notificationId);

    return notificationAccount;
};

exports.GetNotificationAccountByUserId = async (userId) => {
    var notificationAccount = NotificationAccount.findOne({where: {UserId: userId }});

    return notificationAccount;
};