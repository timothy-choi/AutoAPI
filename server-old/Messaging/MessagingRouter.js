const express = require('express');
const router = express.Router();
const MessagingController = require('./MessagingController');

router.get("/:messagingId", MessagingController.GetMessagingById);

router.get("/chatroomId/:chatroomId", MessagingController.GetMessagingByChatroomId);

router.post("/", MessagingController.CreateMessaging);

router.put("/user/add/:messagingId/:user", MessagingController.AddUser);

router.put("/user/remove/:messagingId/:user", MessagingController.RemoveUser);

router.put("/session/add/:messagingId/:sessionId", MessagingController.AddSession);

router.put("/session/remove/:messagingId/:sessionId", MessagingController.RemoveSession);

router.put("/message/add/:messagingId/:messageId", MessagingController.AddMessage);

router.put("/message/remove/:messagingId/:messageId", MessagingController.RemoveMessage);

router.delete("/:messagingId", MessagingController.DeleteMessaging);

module.exports = router;