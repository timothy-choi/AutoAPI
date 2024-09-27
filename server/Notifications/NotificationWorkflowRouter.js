const express = require('express');
const router = express.Router();
const NotificationWorkflowController = require('./NotificationWorkflowController');

router.post('/' , NotificationWorkflowController.SendNotification);

module.exports = router;