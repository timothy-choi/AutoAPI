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

router.put("/userViewRequests/add/:groupId", GroupController.AddUserViewRequests);

router.put("/userViewRequests/remove/:groupId", GroupController.RemoveUserViewRequests);

router.put("/groupActivtiyLog/:groupId", GroupController.AddGroupActivityLog);

router.put("/groupTag/add/:groupId/:groupTag", GroupController.AddGroupTag);

router.put("/groupTag/remove/:groupId/:groupTag", GroupController.RemoveGroupTag);

router.put("/groupDescription/:groupId", GroupController.SetGroupDescription);

router.put("/groupChatroomId/:groupId/:roomId", GroupController.SetGroupChatroomId);

router.delete("/:groupId", GroupController.DeleteGroup);

module.exports = router;