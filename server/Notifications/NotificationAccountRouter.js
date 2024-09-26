const express = require('express');
const router = express.Router();
const NotificationAccountController = require('./NotificationAccountController');

router.get("/:notificationAccountId", NotificationAccountController.GetNotificationAccount);

router.get("/userId/:userId", NotificationAccountController.GetNotificationAccountByUserId);

router.post("/", NotificationAccountController.CreateNotificationAccount);

router.put("/NotificationsOn/:notificationAccountId", NotificationAccountController.UpdateNotificationsOn);

router.put("/AddNotification/add/:notificationAccountId/:notificationId", NotificationAccountController.AddNewNotification);

router.put("/AddNotification/remove/:notificationAccountId/:notificationId", NotificationAccountController.RemoveNotification);

router.delete("/:notificationAccountId", NotificationAccountController.DeleteNotificationAccount);

module.exports = router;