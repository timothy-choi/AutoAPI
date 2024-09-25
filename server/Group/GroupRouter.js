const express = require('express');
const router = express.Router();
const GroupController = require('./GroupController');

router.get("/:groupId", GroupController.GetGroupById);

router.get("/groupName/:groupName", GroupController.GetGroupByGroupName);

router.post("/", GroupController.CreateGroup);

router.delete("/:groupId", GroupController.DeleteGroup);

module.exports = router;