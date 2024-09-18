const express = require('express');
const router = express.Router();
const NotificationAccountController = require('./NotificationAccountController');

router.get("/:notificationAccountId", NotificationAccountController.GetNotificationAccount);

router.get("/userId/:userId", NotificationAccountController.GetNotificationAccountByUserId);

router.post("/", NotificationAccountController.CreateNotificationAccount);

router.put("/NotificationsOn/:notificationAccountId", NotificationAccountController.UpdateNotificationsOn);

router.put("/AddNotification/:notificationAccountId/:notificationId", NotificationAccountController.AddNewNotification);

router.delete("/:notificationAccountId", NotificationAccountController.DeleteNotificationAccount);

module.exports = router;