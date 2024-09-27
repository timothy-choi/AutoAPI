const axios = require('axios');
const webpush = require('web-push');

exports.SendNotification = async (req, res) => {
    try {
        var subscription = null;

        var notificationAccountId = null;

        axios.get("/notificationAccounts/userId/" + req.userId)
            .then(response => {
                if (!response.data.NotificationsOn) {
                    return res.status(404).json({'msg': 'user does not accept notifications'});
                }

                if (!response.data.PushSubscription) {
                    subscription = response.data.PushSubscription;
                }

                notificationAccountId = response.data.Id;
            }).catch(error => {
                return res.status(500).json({'error': error.message});
            });
        
        var userInfo = await axios.get("/user/" + req.userId);

        var notification = null;

        await axios.post("/notifications/", {
            userId: req.userId,
            notificationType: userInfo.data.NotificationType,
            notificationTopic: req.body.topic,
            notificationMessage: req.body.message
        }).then(response => {
            notification = response.data;
        }).catch(error => {
            return res.status(500).json({'error': error.message});
        });

        if (userInfo.data.NotificationType == 'email') {
            var emailNotification = null;
            await axios.post("/emailNotifications/", {
                userId: req.userId,
                emailRecipient: userInfo.data.email,
                topic: req.body.topic,
                message: req.body.message
            }).then(response => {
                emailNotification = response.data;
            }).catch(error => {
                return res.status(500).json({'error': error.message});
            });

            await axios.put("/notifications/emailId/" + notification.Id + "/" + emailNotification.Id);
        } else {
            await webpush.sendNotification(subscription, notification);
        }

        await axios.put("/notificationAccount/AddNotification/add/" + notificationAccountId + "/" + notification.Id);

        return res.status(201).json(null);
    } catch (error) {
        return res.status(500).json({'error': error.message});
    }
}