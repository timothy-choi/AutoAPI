const express = require('express');
const router = express.Router();
const GroupController = require('./GroupController');

router.get("/:groupId", GroupController.GetGroupById);

router.get("/groupName/:groupName", GroupController.GetGroupByGroupName);

router.post("/", GroupController.CreateGroup);

router.put("/groupUser/add/:groupId", GroupController.AddGroupUsers);

router.put("/groupUser/remove/:groupId", GroupController.RemoveGroupUsers);

router.put("/canJoin/:groupId", GroupController.SetCanJoin);

router.put("/project/:groupId", GroupController.SetProject);

router.put("/privateMode/:groupId", GroupController.SetPrivateMode);

router.put("/userJoinRequests/add/:groupId", GroupController.AddUserJoinRequests);

router.put("/userJoinRequests/remove/:groupId", GroupController.RemoveUserJoinRequests);

router.delete("/:groupId", GroupController.DeleteGroup);

module.exports = router;