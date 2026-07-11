const express = require('express');
const router = express.Router();
const NotificationController = require('./NotificationController');

router.get("/:notificationId", NotificationController.GetNotificationById);

router.post("/", NotificationController.CreateNotification);

router.put("/emailId/:notificationId/:emailId", NotificationController.SetEmailId);

router.delete("/:notificationId", NotificationController.DeleteNotification);

modules.export = router;