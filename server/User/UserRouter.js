const express = require('express');
const router = express.Router();
const UserController = require('./UserController');

router.get("/:userId", UserController.GetUserById);

router.get("/username/:username", UserController.GetUserByUsername);

router.post("/", UserController.CreateUser);

router.delete("/:userId", UserController.DeleteUser);

router.put("/username/:userId/:username", UserController.ReplaceUsername);

router.put("/email/:userId/:email", UserController.ReplaceEmail);

router.put("/groupJoined/:userId", UserController.SetJoinedGroup);

router.put("/pastGroupId/add/:userId/:groupId", UserController.AddPastGroupId);

module.exports = router;