const express = require('express');
const router = express.Router();
const GroupController = require('./GroupController');

router.get("/:groupId", GroupController.GetGroupById);

router.get("/groupName/:groupName", GroupController.GetGroupByGroupName);

module.exports = router;