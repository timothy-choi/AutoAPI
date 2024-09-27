const express = require('express');
const router = express.Router();
const MessageController = require('./MessageController');

router.get("/:messageId", MessageController.GetMessageById);

router.post("/", MessageController.CreateMessage);

router.put("/messageText/:messageId", MessageController.EditMessage);

router.delete("/:messageId", MessageController.DeleteMessage);

module.exports = router;