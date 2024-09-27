const express = require('express');
const router = express.Router();
const NotificationWorkflowController = require('./NotificationWorkflowController');
const middleware = require('../middleware');

router.post('/', middleware.AuthenticateToken, NotificationWorkflowController.SendNotification);

module.exports = router;