const express = require('express');
const router = express.Router();
const MessagingAccountController = require('./MessagingAccountController');

router.get('/:messagingAccountId', MessagingAccountController.GetMessagingAccountById);

router.get('/userId/:userId', MessagingAccountController.GetMessagingAccountByUserId);

router.post('/', MessagingAccountController.CreateMessagingAccount);

router.put('/chatroom/add/:messagingAccountId', MessagingAccountController.AddChatroom);

router.put('/chatroom/remove/:messagingAccountId/:chatroomId', MessagingAccountController.RemoveChatroom);

router.delete('/:messagingAccountId', MessagingAccountController.DeleteMessagingAccount);

module.exports = router;