const express = require('express');
const router = express.Router();
const EmailNotificationsController = require('./EmailNotificationsController');

router.get("/:emailId", EmailNotificationsController.GetEmailById);

router.post("/", EmailNotificationsController.SendEmail);

router.delete("/:emailId", EmailNotificationsController.DeleteEmail);

modules.export = router;