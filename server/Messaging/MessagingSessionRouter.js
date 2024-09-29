const express = require('expres')
const router = express.Router();
const MessagingSessionController = require('./MessagingSessionController');


router.get("/:messagingSessionId", MessagingSessionController.GetMessagingSessionById);

router.get("/userId/:userId", MessagingSessionController.GetMessagingSessionByUserId);

router.post("/", MessagingSessionController.CreateMessagingSession);

router.put("/username/:messagingSessionId/:user", MessagingSessionController.SetUsername);

router.put("/lastMessageReadId/:messageSessionId/:messageId", MessagingSessionController.SetLastMessageReadId);

router.put("/joinedAt/:messagingSessionId", MessagingSessionController.SetJoinedAt);

router.put("/lastActiveAt/:messagingSessionId", MessagingSessionController.SetLastActiveAt);

router.put("/closedChatAt/:messagingSessionId", MessagingSessionController.SetClosedChatAt);

router.put("/sessionStatus/:messageSessionId/:sessionStatus", MessagingSessionController.SetSessionStatus);

router.delete("/:messageSessionId", MessageSessionController.DeleteMessagingSession);

module.exports = router;



